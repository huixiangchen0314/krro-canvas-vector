(ns top.kzre.krro.canvas.vector.core
  "矢量渲染核心：提供独立于图层的曲线渲染函数，以及矢量图层的渲染入口。"
  (:require
   [clojure.pprint :refer [pprint]]
   [taoensso.timbre :as log]
   [taoensso.tufte :as p]
   [top.kzre.krro.canvas.core.core :as c]
   [top.kzre.krro.canvas.core.layer.util :as lu]
   [top.kzre.krro.curve.bezier2d.core :as bezier]
   [top.kzre.krro.curve.catmullrom2d.core :as cr])
  (:import
   (java.util Collection UUID)
   (java.util.function DoubleUnaryOperator)
   (top.kzre.curve.bezier2d Bezier2D)
   (top.kzre.krro.canvas.core.layer LayerUtils PixelBlitter)
   (top.kzre.krro.canvas.vector
     AntiAlias
     ArcLengthSampleWidthFunc
     Cap
     FillRule
     FixedWidthFunction Join
     RenderCurveTaskBuilder)
   (top.kzre.krro.util.math KMath)
   (top.kzre.krro.util.tile TiledCanvas)))

(defn make-vector-layer
  "创建矢量图层数据结构。"
  [& {:keys [id name opacity blend-mode visible backend antialias]
      :or   {id         (keyword (str "layer-" (UUID/randomUUID)))
             name       "Vector Layer"
             opacity    1.0
             blend-mode :normal
             visible    true
             antialias  true
             backend    :default}
      :as   opts}]
  (merge
    {:id           id
     :type         :vector
     :name         name
     :opacity      opacity
     :blend-mode   blend-mode
     :visible      visible
     :backend      backend
     :paths    {}
     :path-order   []
     :antialias   antialias}
    (select-keys opts [:x :y :scale-x :scale-y :rotation :transform])))

;; ============================================================================
;; 辅助转换函数
;; ============================================================================

(defn keyword->antialias
  "将 Clojure 关键字转换为 AntiAlias 枚举。"
  [kw]
  (if kw AntiAlias/ANALYTIC AntiAlias/NONE))

(defn keyword->cap
  "将 Clojure 关键字转换为 Cap 枚举。"
  [kw]
  (case kw
    :butt Cap/BUTT
    :round Cap/ROUND
    Cap/SQUARE))

(defn keyword->join
  "将 Clojure 关键字转换为 Join 枚举。"
  [kw]
  (case kw
    :miter Join/MITER
    :round Join/ROUND
    Join/BEVEL))

(defn keyword->fill-rule
  "将 Clojure 关键字转换为 FillRule 枚举。"
  [kw]
  (case kw
    :even-odd FillRule/EVEN_ODD
    :non-zero FillRule/NON_ZERO
    (throw (ex-info "Unknown fill rule" {:mode kw}))))

(defn build-width-func
  "从描边样式构建宽度函数。
   支持固定宽度（:width）或可变宽度（:width-samples + :arc-params）。
   返回 DoubleUnaryOperator，若无法构建则返回 nil。"
  [stroke-style]
  (let [width (:width stroke-style)
        width-samples (:width-samples stroke-style)
        arc-params (:arc-params stroke-style)]
    (cond
      (and width-samples arc-params
           (= (count width-samples) (count arc-params))
           (> (count width-samples) 1))
      (ArcLengthSampleWidthFunc. (double-array arc-params)
                                 (double-array width-samples))

      (number? width)
      (FixedWidthFunction. width)

      :else nil)))

;; ============================================================================
;; 公共渲染 API
;; ============================================================================

(defn build-render-task
  "使用 RenderCurveTaskBuilder 构建渲染任务。
   返回 RenderCurveTask 实例，调用 .run() 执行。
   参数:
   - canvas: TiledCanvas
   - canvas-w, canvas-h: 画布尺寸
   - dirty-tiles: Set<Long> 脏瓦片
   - antialias: 关键字 :disabled, :analytic, :ssaa
   - opts: 向量，每个元素为 [curve, opts-map]
   opts-map 支持：
     :flatness 展平度
     :width-tolerance 宽度容差
     :fill {:color [r g b a] :rule :even-odd/:non-zero}
     :stroke {:color [r g b a] :width 1.0 :cap :butt/:round/:square :join :miter/:round/:bevel
              :width-samples, :arc-params 或 :width-fn 函数}"
  [canvas canvas-w canvas-h
   {:keys [scale-x scale-y flatness antialias dirty-tiles]
    :or {scale-x 1.0
         scale-y 1.0
         flatness 0.25}}
   & paths]
  (let [builder (RenderCurveTaskBuilder/create)]
    (doto (.config builder)
      (.canvas canvas)
      (.size canvas-w canvas-h)
      (.dirtyTiles (or dirty-tiles (LayerUtils/canvasTiles (.getTileSize canvas) canvas-w canvas-h)))
      (.scale scale-x scale-y)
      (.aa (keyword->antialias antialias)))
    (doseq [{:keys [style bezier-curve width-samples arc-params width-tolerance]} paths]
      (let [curve-config (.curve builder bezier-curve)]
        (.flatness curve-config (float flatness))
        (when width-tolerance
          (.widthTolerance curve-config (float width-tolerance)))
        (when-let [fill (:fill style)]
          (let [fill-config (.fill curve-config)
                color (float-array (:color fill))
                rule (keyword->fill-rule (:rule fill :non-zero))]
            (.color fill-config color)
            (.fillRule fill-config rule)))
        (when-let [stroke (:stroke style)]
          (let [stroke-config (.stroke curve-config)
                color (float-array (:color stroke [1.0 1.0 1.0 1.0]))
                cap (keyword->cap (:cap stroke :butt))
                join (keyword->join (:join stroke :miter))]
            (.color stroke-config color)
            (.cap stroke-config cap)
            (.join stroke-config join)
            (if (and (seq arc-params) (seq width-samples))
              (.widthFunc stroke-config
                          (ArcLengthSampleWidthFunc.
                            (double-array arc-params)
                            (double-array width-samples)))
              (let [width (:width stroke 1.0)]
                (.widthFunc stroke-config (FixedWidthFunction. width))))
            (when-let [ml (:miter-limit stroke)]
              (.miterLimit stroke-config (float ml)))))))
    (.build builder)))

(defn render-paths!
  "批量渲染多条曲线到临时画布（同步执行）。
   参数同 render-path!，但 curves 为集合 (Collection<Curve>)。
   所有曲线共用同一配置。"
  [^TiledCanvas canvas canvas-w canvas-h ^Collection curves
   & {:keys [antialias flatness dirty-tiles fill stroke]
      :or {antialias true
           flatness 0.25}}]
  (when (seq curves)
    (let [curves-config
          (mapv
            (fn [c]
              {:bezier-curve c
               :flatness flatness
               :fill fill
               :stroke stroke})
            curves)
          task (apply build-render-task
                      canvas canvas-w canvas-h
                      {:antialias antialias
                       :dirty-tiles dirty-tiles}
                      curves-config)]
      (.run task)
      nil)))


(defmethod c/render-layer! :vector
  [layer ^TiledCanvas dest-canvas canvas-w canvas-h {:keys [dirty-tiles]}]
  (p/profile
    {:id :vector/render}
    (p/p :render-vector-layer
         (let [tile-size (.getTileSize dest-canvas)
               antialias (:antialias layer true)
               flatness  (:flatness layer 0.25)
               tmp-canvas (TiledCanvas. tile-size)
               ;; 图层坐标到视口坐标的缩放，用来控制路径Stroke 的扩张
               ^floats transform (:transform layer)
               scale-x (if transform
                       (KMath/mat2dScaleX transform)
                       1.0)
               scale-y (if transform
                        (KMath/mat2dScaleY transform)
                        1.0)

               transformed-paths (for [path-id (:path-order layer)
                                   :let [path (get (:paths layer) path-id)
                                         curve (case (:path-type path)
                                                 :bezier (bezier/edn->curve (:bezier-curve path))
                                                 :catmull-rom (let [cr-obj (cr/edn->crcurve (:cr-curve path))]
                                                                (.getBezierCurve cr-obj))
                                                 nil)
                                         transformed-curve (if (KMath/mat2dIsIdentity transform)
                                                             curve
                                                             (Bezier2D/transform curve (float-array transform)))]
                                   :when curve]
                               (assoc path :bezier-curve transformed-curve))

               task (apply build-render-task
                           tmp-canvas canvas-w canvas-h
                           {:scale-x scale-x
                            :scale-y scale-y
                            :flatness flatness
                            :dirty-tiles dirty-tiles
                            :antialias antialias}
                           transformed-paths)]
           (log/debug (str "transformed paths\n"
                           (with-out-str (pprint transformed-paths))))
           (.run task)
           (let [blend-mode (lu/blend-mode-str (:blend-mode layer) :normal)
                 opacity (float (get layer :opacity 1.0))
                 aa (top.kzre.krro.util.tile.AntiAlias/noAntiAlias)]
             (PixelBlitter/blit dest-canvas canvas-w canvas-h tmp-canvas
                                lu/identity-matrix blend-mode opacity aa dirty-tiles false)
             (.clear tmp-canvas))
           layer))))
(ns top.kzre.krro.canvas.vector.core
  "矢量渲染核心：提供独立于图层的曲线渲染函数，以及矢量图层的渲染入口。"
  (:require
   [taoensso.tufte :as p]
   [top.kzre.krro.canvas.core.core :as c]
   [top.kzre.krro.canvas.core.layer.util :as lu]
   [top.kzre.krro.curve.bezier2d.core :as bezier]
   [top.kzre.krro.curve.catmullrom2d.core :as cr])
  (:import
   (java.util Collection UUID)
   (java.util.function DoubleUnaryOperator)
   (top.kzre.curve.bezier2d Bezier2D )
   (top.kzre.krro.canvas.core.layer LayerUtils PixelBlitter)
   (top.kzre.krro.canvas.vector
     AntiAlias
     ArcLengthSampleWidthFunc
     Cap
     FillRule
     Join
      RenderCurveTaskBuilder)
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
     :paths-map    {}
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
    :square Cap/SQUARE
    (throw (ex-info "Unknown cap style" {:mode kw}))))

(defn keyword->join
  "将 Clojure 关键字转换为 Join 枚举。"
  [kw]
  (case kw
    :miter Join/MITER
    :round Join/ROUND
    :bevel Join/BEVEL
    (throw (ex-info "Unknown join style" {:mode kw}))))

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
      (reify DoubleUnaryOperator
        (applyAsDouble [_ _] (double width)))

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
   - aa-mode: 关键字 :disabled, :analytic, :ssaa
   - curves-config: 向量，每个元素为 [curve, opts-map]
   opts-map 支持：
     :flatness 展平度
     :width-tolerance 宽度容差
     :fill {:color [r g b a] :rule :even-odd/:non-zero}
     :stroke {:color [r g b a] :width 1.0 :cap :butt/:round/:square :join :miter/:round/:bevel
              :width-samples, :arc-params 或 :width-fn 函数}"
  [canvas canvas-w canvas-h dirty-tiles aa-mode & curves-config]
  (let [builder (RenderCurveTaskBuilder/create)]
    (doto (.config builder)
      (.canvas canvas)
      (.size canvas-w canvas-h)
      (.dirtyTiles dirty-tiles)
      (.aa (keyword->antialias aa-mode)))
    (doseq [[curve opts] curves-config]
      (let [curve-config (.curve builder curve)]
        (when-let [flatness (:flatness opts)]
          (.flatness curve-config (float flatness)))
        (when-let [wt (:width-tolerance opts)]
          (.widthTolerance curve-config (float wt)))
        (when-let [fill (:fill opts)]
          (let [fill-config (.fill curve-config)
                color (float-array (:color fill))
                rule (keyword->fill-rule (:rule fill :non-zero))]
            (.color fill-config color)
            (.fillRule fill-config rule)))
        (when-let [stroke (:stroke opts)]
          (let [stroke-config (.stroke curve-config)
                color (float-array (:color stroke))
                cap (keyword->cap (:cap stroke :butt))
                join (keyword->join (:join stroke :miter))]
            (.color stroke-config color)
            (.cap stroke-config cap)
            (.join stroke-config join)
            (if-let [width-fn (or (:width-fn stroke) (build-width-func stroke))]
              (.widthFunc stroke-config width-fn)
              (.width stroke-config (float (or (:width stroke) 1.0))))
            (when-let [ml (:miter-limit stroke)]
              (.miterLimit stroke-config (float ml)))))))
    (.build builder)))

(defn render-paths!
  "批量渲染多条曲线到临时画布（同步执行）。
   参数同 render-path!，但 curves 为集合 (Collection<Curve>)。
   所有曲线共用同一配置。"
  [^TiledCanvas canvas canvas-w canvas-h ^Collection curves
   & {:keys [antialias flatness dirty-tiles fill stroke]
      :or {antialias :analytic
           flatness 0.25}}]
  (when (seq curves)
    (let [curves-config (mapv (fn [c] [c {:flatness flatness
                                          :fill fill
                                          :stroke stroke}]) curves)
          task (apply build-render-task canvas canvas-w canvas-h
                      (or dirty-tiles (LayerUtils/canvasTiles (.getTileSize canvas) (int canvas-w) (int canvas-h)))
                      antialias
                      curves-config)]
      (.run task)
      nil)))


(defmethod c/render-layer! :vector
  [layer ^TiledCanvas dest-canvas canvas-w canvas-h {:keys [dirty-tiles]}]
  (p/profile
    {:id :vector/render}
    (p/p :render-vector-layer
         (let [tile-size (.getTileSize dest-canvas)
               antialias (:antialias layer)
               flatness  (:flatness layer 0.25)
               tmp-canvas (TiledCanvas. tile-size)
               curves-config (for [path-id (:path-order layer)
                                   :let [path (get (:paths-map layer) path-id)
                                         curve (case (:path-type path)
                                                 :bezier (bezier/edn->curve (:bezier-curve path))
                                                 :catmull-rom (let [cr-obj (cr/edn->crcurve (:cr-curve path))]
                                                                (.getBezierCurve cr-obj))
                                                 nil)
                                         style (:style path)
                                         transformed-curve (if-let [transform (:transform layer)]
                                                             (Bezier2D/transform curve (float-array transform))
                                                             curve)
                                         opts (cond-> {:flatness flatness}
                                                      (and style (:fill style)) (assoc :fill (:fill style))
                                                      (and style (:stroke style)) (assoc :stroke (:stroke style)))]
                                   :when curve]
                               [transformed-curve opts])
               task (apply build-render-task
                           tmp-canvas canvas-w canvas-h
                           (or dirty-tiles (LayerUtils/canvasTiles tile-size (int canvas-w) (int canvas-h)))
                           antialias
                           curves-config)]
           (.run task)
           (let [blend-mode (lu/blend-mode-str (:blend-mode layer) :normal)
                 opacity (float (get layer :opacity 1.0))
                 aa (top.kzre.krro.util.tile.AntiAlias/noAntiAlias)]
             (PixelBlitter/blit dest-canvas canvas-w canvas-h tmp-canvas
                                lu/identity-matrix blend-mode opacity aa dirty-tiles false)
             (.clear tmp-canvas))
           layer))))
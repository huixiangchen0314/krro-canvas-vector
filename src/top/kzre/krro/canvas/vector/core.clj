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
   [top.kzre.colorutils.color RGB]
   (top.kzre.curve.bezier2d Bezier2D Curve)
   (top.kzre.krro.canvas.core.layer LayerUtils PixelBlitter)
   (top.kzre.krro.canvas.vector
    AntiAlias
    ArcLengthSampleWidthFunc
    Cap
    FillRule
    Join
    RasterizeCurveFacade)
   (top.kzre.krro.util.tile TiledCanvas)))

(defn make-vector-layer
  "创建矢量图层数据结构。"
  [& {:keys [id name opacity blend-mode visible backend antialias]
      :or   {id         (keyword (str "layer-" (UUID/randomUUID)))
             name       "Vector Layer"
             opacity    1.0
             blend-mode :normal
             visible    true
             antialias true
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
  (case kw
    :disabled AntiAlias/DISABLED
    :analytic AntiAlias/ANALYTIC
    :ssaa AntiAlias/SSAA_2x2
    (throw (ex-info "Unknown anti-alias mode" {:mode kw}))))

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

(defn render-path!
  "渲染单条曲线到临时画布。
   参数:
   - canvas: 临时画布 (TiledCanvas)
   - canvas-w, canvas-h: 画布尺寸
   - curve: 待渲染的曲线 (Curve)
   - opts: 可选参数 map，支持:
       :antialias  - :disabled, :analytic, :ssaa (默认 :analytic)
       :flatness   - 展平参数 (默认 0.25)
       :fill       - 填充样式 {:color [r g b a] :rule :even-odd 或 :non-zero}
       :stroke     - 描边样式 {:color [r g b a] :canvas-w 1.0
                                :cap :butt/:round/:square
                                :join :miter/:round/:bevel
                                :canvas-w-samples 和 :arc-params 用于可变宽度}"
  [^TiledCanvas canvas canvas-w canvas-h ^Curve curve
   & {:keys [antialias flatness dirty-tiles fill stroke]
      :or {antialias :analytic
           flatness 0.25}}]
  (let [builder (-> (RasterizeCurveFacade/builder)
                    (.canvas canvas)
                    (.size canvas-w canvas-h)
                    (.dirtyTiles (or dirty-tiles (LayerUtils/canvasTiles (.getTileSize canvas) (int canvas-w) (int canvas-h))))
                    (.aaMode (keyword->antialias antialias))
                    (.flatness flatness)
                    (.curves curve))]
    ;; 填充
    (when fill
      (let [color (float-array (:color fill))
            rule (keyword->fill-rule (:rule fill :non-zero))]
        (.fill builder color rule)))
    ;; 描边
    (when stroke
      (let [color (float-array (:color stroke))
            cap (keyword->cap (:cap stroke :butt))
            join (keyword->join (:join stroke :miter))
            width-fn (build-width-func stroke)]
        (if width-fn
          (.stroke builder color cap join width-fn)
          (let [width (or (:width stroke) 1.0)]
            (.strokeFixed builder color cap join width)))))
    (-> builder .build .render)))

(defn render-paths!
  "批量渲染多条曲线到临时画布（共用同一配置，性能更优）。
   参数同 render-path!，但 curves 为集合 (Collection<Curve>)。"
  [^TiledCanvas canvas canvas-w  canvas-h ^Collection curves
   & {:keys [antialias flatness dirty-tiles fill stroke]
      :or {antialias :analytic
           flatness 0.25}}]
  (when-not (seq curves)
    (let [builder (-> (RasterizeCurveFacade/builder)
                      (.canvas canvas)
                      (.size canvas-w canvas-h)
                      (.dirtyTiles (or dirty-tiles (LayerUtils/canvasTiles (.getTileSize canvas) (int canvas-w) (int canvas-h))))
                      (.aaMode (keyword->antialias antialias))
                      (.flatness flatness)
                      (.curves curves))]
      ;; 填充
      (when fill
        (let [color (float-array (:color fill))
              rule (keyword->fill-rule (:rule fill :non-zero))]
          (.fill builder color rule)))
      ;; 描边
      (when stroke
        (let [color (float-array (:color stroke))
              cap (keyword->cap (:cap stroke :butt))
              join (keyword->join (:join stroke :miter))
              width-fn (build-width-func stroke)]
          (if width-fn
            (.stroke builder color cap join width-fn)
            (let [width (or (:width stroke) 1.0)]
              (.strokeFixed builder color cap join width)))))
      (-> builder .build .render))))



(defmethod c/render-layer! :vector
  [layer ^TiledCanvas dest-canvas canvas-w canvas-h {:keys [dirty-tiles]}]
  (p/profile {:id :vector/render}
             (let [tile-size (.getTileSize dest-canvas)
                   antialias (if (:antialias layer) :analytic :disabled)
                   flatness (or (get-in layer [:rasterizer :flatness]) 0.25)
                   tmp-canvas (TiledCanvas. tile-size)      ; 临时画布，全量渲染
                   ]

               ;; 遍历所有路径，逐条渲染到临时画布
               (doseq [path-id (:path-order layer)]
                 (let [path (get (:paths-map layer) path-id)]
                   (when-let [curve (case (:path-type path)
                                      :bezier
                                      (bezier/edn->curve (:bezier-curve path))
                                      :catmull-rom
                                      (let [cr-obj (cr/edn->crcurve (:cr-curve path))]
                                        (.getBezierCurve cr-obj))
                                      nil)]
                     (let [style (:style path)
                           transformed-curve (if-let [transform (:transform layer)]
                                               (Bezier2D/transform curve (float-array transform))
                                               curve)
                           opts {:antialias antialias
                                 :flatness flatness}]
                       (when-let [fill (:fill style)]
                         (render-path! tmp-canvas canvas-w canvas-h transformed-curve
                                       (assoc opts :fill fill
                                                   :dirty-tiles dirty-tiles)))
                       (when-let [stroke (:stroke style)]
                         (render-path! tmp-canvas canvas-w canvas-h transformed-curve
                                       (assoc opts :stroke stroke
                                                   :dirty-tiles dirty-tiles)))))))

               (let [blend-mode (lu/blend-mode-str (:blend-mode layer) :normal)
                     opacity (float (get layer :opacity 1.0))
                     aa (top.kzre.krro.util.tile.AntiAlias/noAntiAlias)]
                 (PixelBlitter/blit dest-canvas canvas-w canvas-h tmp-canvas
                                    lu/identity-matrix blend-mode opacity aa dirty-tiles false)
                 (.clear tmp-canvas)))
             layer))
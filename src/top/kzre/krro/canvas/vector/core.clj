(ns top.kzre.krro.canvas.vector.core
  "矢量图层门面：内部使用 Canvas 高效光栅化，最终混合到 Canvas。"
  (:require
    [top.kzre.krro.canvas.core.core :as c]
    [top.kzre.krro.canvas.core.layer.util :as lu]
    [top.kzre.krro.canvas.vector.spec]
    [top.kzre.krro.curve.bezier2d.core :as bezier]
    [top.kzre.krro.curve.catmullrom2d.core :as cr]
    [taoensso.tufte :as p])
  (:import
    (java.util Collection HashSet Set UUID)
    (top.kzre.curve.bezier2d Bezier2D Curve)
    (top.kzre.krro.canvas.vector
      AntiAlias ArcLengthSampleWidthFunc Cap CanvasCurveRasterizer FillRule Join RasterizerConfig)
    (top.kzre.krro.canvas.core.layer PixelBlitter)
    (top.kzre.krro.util.tile Canvas TiledCanvas)))

;; ═══════════════════════════════════════════════
;; 图层构造函数（不变）
;; ═══════════════════════════════════════════════
(defn make-vector-layer
  [& {:keys [id name opacity blend-mode visible backend]
      :or   {id         (keyword (str "layer-" (UUID/randomUUID)))
             name       "Vector Layer"
             opacity    1.0
             blend-mode :normal
             visible   true
             backend    :default}
      :as   opts}]
  (merge
    {:id           id
     :type         :vector
     :name         name
     :opacity      opacity
     :blend-mode   blend-mode
     :visible     visible
     :backend      backend
     :paths-map    {}
     :path-order   []
     :antialiased true}
    (select-keys opts [:x :y :scale-x :scale-y :rotation])))

;; ═══════════════════════════════════════════════
;; 创建光栅化器（使用新的 CanvasCurveRasterizer）
;; ═══════════════════════════════════════════════
(defn build-rasterizer [layer]
  (let [config (RasterizerConfig.)]
    (if (:antialiased layer)
      (.setAntiAlias config AntiAlias/ANALYTIC)
      (.setAntiAlias config AntiAlias/DISABLED))
    (CanvasCurveRasterizer. config)))

;; ---------- 绘制辅助函数（直接写入 Canvas）----------
(defn draw-fill!
  [^CanvasCurveRasterizer rasterizer ^Canvas canvas w h ^Curve curve fill-style
   ^Set dirty-tiles tile-size]
  (p/p :vector/draw-fill
       (let [color     (float-array (:color fill-style))
             fill-rule (case (:fill-rule fill-style)
                         :even-odd FillRule/EVEN_ODD
                         :non-zero FillRule/NON_ZERO
                         FillRule/EVEN_ODD)]
         (.fill rasterizer canvas w h curve color fill-rule dirty-tiles (int tile-size)))))

(defn draw-stroke!
  [^CanvasCurveRasterizer rasterizer ^Canvas canvas w h ^Curve curve stroke-style
   width-samples arc-params ^Set dirty-tiles tile-size]
  (p/p :vector/draw-stroke
       (let [color (float-array (:color stroke-style))
             cap   (case (:cap stroke-style)
                     :butt Cap/BUTT :round Cap/ROUND :square Cap/SQUARE Cap/BUTT)
             join  (case (:join stroke-style)
                     :miter Join/MITER :round Join/ROUND :bevel Join/BEVEL Join/MITER)
             has-var-width (and (some? width-samples)
                                (some? arc-params)
                                (= (count width-samples) (count arc-params))
                                (> (count width-samples) 1))
             width (:width stroke-style 50)]
         (cond
           has-var-width
           (let [width-fn (ArcLengthSampleWidthFunc. (double-array arc-params) (double-array width-samples))]
             (.strokeVariable rasterizer canvas w h curve width-fn color cap join
                              dirty-tiles (int tile-size)))
           (number? width)
           (.strokeFixed rasterizer canvas w h curve (float width) color cap join
                         dirty-tiles (int tile-size))
           :else nil))))

;; ---------- 单路径渲染（应用图层变换）----------
(defn- render-path-transformed!
  [^CanvasCurveRasterizer rasterizer ^Canvas canvas w h path layer
   ^Set dirty-tiles tile-size]
  (when-let [style (:style path)]
    (p/p :vector/render-path
         (let [transform (get layer :transform lu/identity-matrix)
               a (nth transform 0) b (nth transform 1)
               c (nth transform 2) d (nth transform 3)
               tx (nth transform 4) ty (nth transform 5)
               curve (case (:path-type path)
                       :bezier
                       (let [c (Curve.)]
                         (bezier/edn->curve! c (:bezier-curve path))
                         c)
                       :catmull-rom
                       (let [cr-obj (cr/edn->crcurve (:cr-curve path))]
                         (.getBezierCurve cr-obj))
                       nil)]
           (when curve
             (let [transformed (Bezier2D/transform curve a b c d tx ty)]
               (when-let [fill (:fill style)]
                 (draw-fill! rasterizer canvas w h transformed fill dirty-tiles tile-size))
               (when-let [stroke (:stroke style)]
                 (draw-stroke! rasterizer canvas w h transformed stroke
                               (:width-samples path) (:arc-params path)
                               dirty-tiles tile-size))))))))

;; ---------- 主光栅化逻辑（返回 Canvas）----------
(defn- rasterize-paths!
  [layer w h ^CanvasCurveRasterizer rasterizer ^Set dirty-tiles tile-size]
  (p/p :vector/rasterize-paths
       (let [canvas (TiledCanvas. (int tile-size))]   ;; 中间画布，瓦片尺寸与目标一致
         (doseq [id (:path-order layer)]
           (when-let [path (get (:paths-map layer) id)]
             (render-path-transformed! rasterizer canvas w h path layer dirty-tiles tile-size)))
         canvas)))   ;; 返回画布供后续混合

;; ---------- 图层渲染入口 ----------
(defmethod c/render-layer! :vector
  [layer ^Canvas dest-canvas w h {:keys [dirty-tiles]}]
  (p/profile {:id :vector/render}
             (let [tile-size  (.getTileSize dest-canvas)   ;; 使用目标画布的瓦片大小
                   rasterizer (build-rasterizer layer)
                   src-canvas (rasterize-paths! layer w h rasterizer dirty-tiles tile-size)
                   blend-mode (lu/blend-mode-str (:blend-mode layer) :normal)
                   opacity    (float (get layer :opacity 1.0))]
               ;; 混合到目标画布（使用单位矩阵，关闭亚像素）
               (PixelBlitter/blit dest-canvas w h src-canvas
                                  lu/identity-matrix blend-mode opacity
                                  (top.kzre.krro.util.tile.AntiAlias/noAntiAlias) dirty-tiles false)
               layer)))
(ns top.kzre.krro.canvas.vector.core
  "矢量图层门面：内部使用 float[] 高效光栅化，最终混合到 Canvas。"
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
      AntiAlias ArcLengthSampleWidthFunc Cap CurveRasterizer FillRule Join RasterizerConfig TiledPixelRenderer)
    (top.kzre.krro.util.tile Canvas TiledCanvas)))

;; ═══════════════════════════════════════════════
;; 图层构造函数（不变）
;; ═══════════════════════════════════════════════
(defn make-vector-layer
  [& {:keys [id name opacity blend-mode visible? backend]
      :or   {id         (keyword (str "layer-" (UUID/randomUUID)))
             name       "Vector Layer"
             opacity    1.0
             blend-mode :normal
             visible?   true
             backend    :default}
      :as   opts}]
  (merge
    {:id           id
     :type         :vector
     :name         name
     :opacity      opacity
     :blend-mode   blend-mode
     :visible?     visible?
     :backend      backend
     :paths-map    {}
     :path-order   []
     :antialiased true}
    (select-keys opts [:x :y :scale-x :scale-y :rotation])))

;; ═══════════════════════════════════════════════
;; 创建光栅化器（根据图层配置）
;; ═══════════════════════════════════════════════
(defn build-rasterizer [layer]
  (let [config (RasterizerConfig.)]
    (if (:antialiased layer)
      (.setAntiAlias config AntiAlias/ANALYTIC)
      (.setAntiAlias config AntiAlias/DISABLED))
    (CurveRasterizer. config)))

;; ---------- 绘制辅助函数（操作 float[]）----------
(defn draw-fill!
  [^CurveRasterizer rasterizer ^floats cache w h ^Curve curve fill-style
   ^Set dirty-tiles tile-size]
  (p/p :vector/draw-fill
       (let [color     (float-array (:color fill-style))
             fill-rule (case (:fill-rule fill-style)
                         :even-odd FillRule/EVEN_ODD
                         :non-zero FillRule/NON_ZERO
                         FillRule/EVEN_ODD)]
         (.fill rasterizer cache w h curve color fill-rule dirty-tiles (int tile-size)))))

(defn draw-stroke!
  [^CurveRasterizer rasterizer ^floats cache w h ^Curve curve stroke-style
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
             (.strokeVariable rasterizer cache w h curve width-fn color cap join
                              dirty-tiles (int tile-size)))
           (number? width)
           (.strokeFixed rasterizer cache w h curve (float width) color cap join
                         dirty-tiles (int tile-size))
           :else nil))))

;; ---------- 单路径渲染（应用图层变换）----------
(defn- render-path-transformed!
  [^CurveRasterizer rasterizer ^floats cache w h path layer
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
                 (draw-fill! rasterizer cache w h transformed fill dirty-tiles tile-size))
               (when-let [stroke (:stroke style)]
                 (draw-stroke! rasterizer cache w h transformed stroke
                               (:width-samples path) (:arc-params path)
                               dirty-tiles tile-size))))))))

;; ---------- 主光栅化逻辑（生成 float[]）----------
(defn- rasterize-paths!
  [layer w h ^CurveRasterizer rasterizer ^Set dirty-tiles tile-size]
  (p/p :vector/rasterize-paths
       (let [^floats pixels (float-array (* w h 4) 0.0)]
         (doseq [id (:path-order layer)]
           (when-let [path (get (:paths-map layer) id)]
             (render-path-transformed! rasterizer pixels w h path layer dirty-tiles tile-size)))
         pixels)))

;; ---------- 图层渲染入口 ----------
(defmethod c/render-layer! :vector
  [layer ^Canvas dest-canvas w h {:keys [dirty-tiles tile-size] :or {tile-size 64}}]
  (p/profile {:id :vector/render}
             (let [rasterizer (build-rasterizer layer)
                   ;; 内部高效光栅化到 float[]
                   pixels      (rasterize-paths! layer w h rasterizer dirty-tiles tile-size)
                   ;; 混合参数
                   blend-mode (lu/blend-mode-str (:blend-mode layer) :normal)
                   opacity    (float (get layer :opacity 1.0))
                   ;; 图层的 2D 变换已在光栅化时应用，混合使用单位矩阵
                   transform  lu/identity-matrix
                   matrix2d   (float-array transform)
                   java-dirty (when (seq dirty-tiles) (HashSet. ^Collection dirty-tiles))]
               ;; 将像素数组混合到目标 Canvas（支持脏区裁剪）
               (TiledPixelRenderer/blendTransformedTiled dest-canvas w h pixels tile-size matrix2d blend-mode opacity java-dirty)
               layer)))
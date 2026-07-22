(ns top.kzre.krro.canvas.vector.core
  "矢量图层门面：负责矢量路径的光栅化与合成。
   使用图层 :transform 矩阵，通过 Bezier2D.affine 变换曲线后再光栅化。"
  (:require
   [top.kzre.krro.canvas.core.core :as c]
   [top.kzre.krro.canvas.core.layer.util :as lu]
   [top.kzre.krro.canvas.vector.spec]
   [top.kzre.krro.curve.bezier2d.core :as bezier]
   [top.kzre.krro.curve.catmullrom2d.core :as cr])
  (:import
    (java.util Collection HashSet UUID)
   (top.kzre.curve.bezier2d Bezier2D Curve)
   (top.kzre.krro.canvas.vector
    ArcLengthSampleWidthFunc
    Cap
    FillRule
    Join
    Rasterizer
    TiledPixelRenderer)))

;; ═══════════════════════════════════════════════
;; 图层构造函数
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
     :cache        nil}
    (select-keys opts [:x :y :scale-x :scale-y :rotation])))

;; ═══════════════════════════════════════════════
;; 绘制辅助函数（不变）
;; ═══════════════════════════════════════════════
(defn- draw-fill!
  [^floats cache w h ^Curve curve fill-style]
  (let [color     (:color fill-style)
        fill-rule (case (:fill-rule fill-style)
                    :even-odd FillRule/EVEN_ODD
                    :non-zero FillRule/NON_ZERO
                    FillRule/EVEN_ODD)]
    (Rasterizer/fill cache w h curve color fill-rule)))

(defn- draw-stroke!
  [^floats cache w h ^Curve curve stroke-style width-samples arc-params]
  (let [color     (:color stroke-style)
        cap       (case (:cap stroke-style)
                    :butt Cap/BUTT :round Cap/ROUND :square Cap/SQUARE Cap/BUTT)
        join      (case (:join stroke-style)
                    :miter Join/MITER :round Join/ROUND :bevel Join/BEVEL Join/MITER)
        has-var-width (and (some? width-samples)
                           (some? arc-params)
                           (= (count width-samples) (count arc-params))
                           (> (count width-samples) 1))
        width     (:width stroke-style)]
    (cond
      has-var-width
      (let [width-fn (ArcLengthSampleWidthFunc. (double-array arc-params) (double-array width-samples))]
        (Rasterizer/strokeVariable cache w h curve width-fn color cap join))

      (number? width)
      (Rasterizer/strokeFixed cache w h curve (float width) color cap join)
      :else
      nil)))


;; ═══════════════════════════════════════════════
;; 带变换的单路径渲染
;; ═══════════════════════════════════════════════
(defn- render-path-transformed!
  [^floats cache w h path layer]
  (when-let [style (:style path)]
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
            (draw-fill! cache w h transformed fill))
          (when-let [stroke (:stroke style)]
            (draw-stroke! cache w h transformed stroke
                          (:width-samples path) (:arc-params path))))))))

;; ═══════════════════════════════════════════════
;; 主光栅化逻辑（总是全量重建，应用变换）
;; ═══════════════════════════════════════════════
(defn- rasterize-paths!
  [layer w h]
  (let [order (:path-order layer)
        paths (:paths-map layer)
        ^floats pixels (float-array (* w h 4) 0.0)]
    (doseq [id order]
      (when-let [path (get paths id)]
        (render-path-transformed! pixels w h path layer)))
    pixels))

(defmethod c/render-layer! :vector
  [layer ^floats data w h {:keys [dirty-tiles
                                  tile-size] :as opts
                           :or {tile-size 64}}]
  (let [^floats pixles  (rasterize-paths! layer w h)
        blend-mode (lu/blend-mode-str (:blend-mode layer) :normal)
        opacity   (float (get layer :opacity 1.0))
        java-dirty  (when (seq dirty-tiles)
                      (HashSet. ^Collection dirty-tiles))]
    (TiledPixelRenderer/blendTransformedTiled data  w h
                                              pixles tile-size
                                              lu/identity-matrix blend-mode opacity java-dirty)))
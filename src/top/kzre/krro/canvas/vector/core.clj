(ns top.kzre.krro.canvas.vector.core
  "矢量图层门面：负责矢量路径的光栅化与合成。"
  (:require
    [top.kzre.krro.canvas.core.core :as c]
    [top.kzre.krro.canvas.core.layer.util :as lu]
    [top.kzre.krro.canvas.raster.util :as util]
    [top.kzre.krro.canvas.vector.spec]
    [top.kzre.krro.curve.bezier2d.core :as bezier]
    [top.kzre.krro.curve.catmullrom2d.core :as cr])
  (:import
    (java.util UUID)
    (top.kzre.krro.canvas.raster Renderer)
    (top.kzre.krro.canvas.vector ArcLengthSampleWidthFunc Cap FillRule Join Rasterizer)
    (top.kzre.curve.bezier2d Curve)
    (top.kzre.krro.curve.bezier2d CurvePool)))

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
     :cache        nil
     :dirty-start  nil
     :dirty-rect   nil}
    (select-keys opts [:x :y :scale-x :scale-y :rotation :mask])))

;; ═══════════════════════════════════════════════
;; 状态管理（纯函数）
;; ═══════════════════════════════════════════════

(defn clear-cache [layer]
  (assoc layer :cache nil :dirty-start nil :dirty-rect nil))

(defn invalidate-from
  "标记从指定路径 ID（含）开始的所有路径为脏。"
  [layer id]
  (let [order (:path-order layer)
        idx   (if id
                (let [i (.indexOf order id)]
                  (if (>= i 0) i 0))
                0)]
    (assoc layer :dirty-start idx)))

(defn add-dirty-rect [layer rect]
  (assoc layer :dirty-rect rect))

;; ═══════════════════════════════════════════════
;; 绘制辅助函数
;; ═══════════════════════════════════════════════

(defn- draw-fill!
  [^floats cache w h ^Curve curve fill-style dirty-rect]
  (let [color     (:color fill-style)
        fill-rule (case (:fill-rule fill-style)
                    :even-odd FillRule/EVEN_ODD
                    :non-zero FillRule/NON_ZERO
                    FillRule/EVEN_ODD)]
    (if dirty-rect
      (Rasterizer/fill cache w h curve color fill-rule (int-array dirty-rect))
      (Rasterizer/fill cache w h curve color fill-rule))))

(defn- draw-stroke!
  [^floats cache w h ^Curve curve stroke-style dirty-rect width-samples arc-params]
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
        (if dirty-rect
          (Rasterizer/strokeVariable cache w h curve width-fn color cap join (int-array dirty-rect))
          (Rasterizer/strokeVariable cache w h curve width-fn color cap join)))

      (number? width)
      (if dirty-rect
        (Rasterizer/strokeFixedDirty cache w h curve (float width) color cap join (int-array dirty-rect))
        (Rasterizer/strokeFixed cache w h curve (float width) color cap join))
      :else
      nil)))

;; ═══════════════════════════════════════════════
;; 单路径渲染
;; ═══════════════════════════════════════════════

(defn- render-path!
  [^floats cache w h path dirty-rect]
  (when-let [style (:style path)]
    (let [{:keys [^Curve curve borrow?]}
          (case (:path-type path)
            :bezier
            (let [c (CurvePool/borrowCurve)]
              (bezier/edn->curve! c (:bezier-curve path))
              {:curve c :borrow? true})
            :catmull-rom
            (let [cr-obj (cr/edn->crcurve (:cr-curve path))
                  c (.getBezierCurve cr-obj)]
              {:curve c :borrow? false})
            nil)]
      (when curve
        (try
          (when-let [fill (:fill style)]
            (draw-fill! cache w h curve fill dirty-rect))
          (when-let [stroke (:stroke style)]
            (draw-stroke! cache w h curve stroke dirty-rect
                          (:width-samples path) (:arc-params path)))
          (finally
            (when borrow?
              (CurvePool/returnCurve curve))))))))

;; ═══════════════════════════════════════════════
;; 主光栅化逻辑
;; ═══════════════════════════════════════════════

(defn- rasterize-paths!
  [layer w h]
  (let [order      (:path-order layer)
        paths      (:paths-map layer)
        dirty-i    (:dirty-start layer)
        rect       (:dirty-rect layer)
        old-cache  (:cache layer)
        full-redraw? (or (nil? old-cache)
                         (and (nil? dirty-i) (nil? rect))
                         (nil? dirty-i))
        start-idx  (if full-redraw? 0 (or dirty-i 0))
        ^floats cache (if full-redraw?
                        (float-array (* w h 4) 0.0)
                        old-cache)]
    (doseq [idx (range start-idx (count order))]
      (let [id   (nth order idx)
            path (get paths id)]
        (render-path! cache w h path rect)))
    (assoc layer :cache cache :dirty-start nil :dirty-rect nil)))

;; ═══════════════════════════════════════════════
;; 合成与渲染 API
;; ═══════════════════════════════════════════════

(defn render-vector-layer!
  [layer ^floats data w h]
  (let [pre-rect (:dirty-rect layer)
        layer'   (rasterize-paths! layer w h)
        ^floats cache (:cache layer')
        blend-mode (util/blend-mode-str (:blend-mode layer') :normal)
        opacity   (float (get layer' :opacity 1.0))
        transform (get layer' :transform lu/identity-matrix)]
    (if pre-rect
      (let [dirty-arr (int-array pre-rect)]
        (Renderer/blendTransformedDirty data cache w h transform blend-mode opacity dirty-arr))
      (Renderer/blendTransformed data cache w h transform blend-mode opacity))
    layer'))

(defmethod c/render-layer! :vector
  [layer ^floats data w h]
  (render-vector-layer! layer data w h))
(ns top.kzre.krro.canvas.vector.core
  "矢量渲染核心：提供独立于图层的曲线渲染函数，以及矢量图层的渲染入口。"
  (:require
    [top.kzre.krro.canvas.vector.layer]
    [top.kzre.krro.canvas.vector.path]
    [top.kzre.krro.canvas.vector.anchor]
    [top.kzre.krro.canvas.vector.composite]
    [top.kzre.krro.core.util.re-export :refer [re-export]]))


(re-export
  [top.kzre.krro.canvas.vector.layer
   :refer
   [paths path-order fresh-path-id
    save-path delete-path make-vector-layer]])

(re-export
  [top.kzre.krro.canvas.vector.path
   :refer
   [path-curve path-points path-point-count
    point-t-params uniform-t-params
    path-width-type max-path-width path-tiles
    ]])


(re-export
  [top.kzre.krro.canvas.vector.anchor
   :refer
   [path-of-anchor]])


(re-export
  [top.kzre.krro.canvas.vector.composite
   :refer
   [render-paths! render-to-canvas!]])



(defmacro ensure-width-type
  "确保路径具有指定的宽度类型。可选参数被包装为 delay，只在需要时求值，避免浪费计算。"
  [path width-type & {:keys [t-params width-samples arc-params width-curve compute-arc?]}]
  (let [wrap-delay (fn [expr]
                     (if (some? expr)
                       `(delay ~expr)
                       nil))]
    `(top.kzre.krro.canvas.vector.path/ensure-width-type*
       ~path ~width-type
       :t-params ~(wrap-delay t-params)
       :width-samples ~(wrap-delay width-samples)
       :arc-params ~(wrap-delay arc-params)
       :width-curve ~(wrap-delay width-curve)
       :compute-arc? ~(if (some? compute-arc?) compute-arc? true))))
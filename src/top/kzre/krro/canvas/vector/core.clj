(ns top.kzre.krro.canvas.vector.core
  "矢量图层统一入口。所有公开 API 从子 ns re-export。"
  (:require
    [top.kzre.krro.canvas.vector.layer]
    [top.kzre.krro.canvas.vector.path]
    [top.kzre.krro.canvas.vector.anchor]
    [top.kzre.krro.canvas.vector.composite]
    [top.kzre.krro.core.util.re-export :refer [re-export]]))

;; ═══════════════════════════════════════
;; Layer 结构
;; ═══════════════════════════════════════

(re-export
  [top.kzre.krro.canvas.vector.layer
   :refer
   [paths path-order fresh-path-id
    save-path delete-path make-vector-layer
    valid-path?]])

;; ═══════════════════════════════════════
;; Path 结构 / 宽度 / 几何 / 脏区域
;; ═══════════════════════════════════════

(re-export
  [top.kzre.krro.canvas.vector.path
   :refer
   [path-curve path-points path-point-count path-closed?
    path-width-type max-path-width max-path-half-width
    path-aabb seg-aabb
    path-tiles seg-tiles
    uniform-t-params]])

;; ═══════════════════════════════════════
;; Anchor 类型 / 操作
;; ═══════════════════════════════════════

(re-export
  [top.kzre.krro.canvas.vector.anchor
   :refer
   [->Anchor map->Anchor
    ->AnchorTranslation map->AnchorTranslation
    path-of-anchor
    all-anchors anchor-point
    end-anchor?
    anchors-centroid
    translate-anchor translate-anchors apply-translations
    active-anchor-after-extrude extrude-anchor
    anchor-tiles]])

;; ═══════════════════════════════════════
;; 渲染
;; ═══════════════════════════════════════

(re-export
  [top.kzre.krro.canvas.vector.composite
   :refer
   [render-paths! render-to-canvas!]])

;; ═══════════════════════════════════════
;; 宏（re-export 有工具链问题，直接定义）
;; ═══════════════════════════════════════

(defmacro ensure-width-type
  "确保路径具有指定的宽度类型。可选参数被包装为 delay，
   只在需要时求值，避免浪费计算。"
  [path width-type & {:keys [t-params width-samples arc-params width-curve compute-arc?]}]
  (let [wrap-delay (fn [expr]
                     (if (some? expr)
                       `(delay ~expr)
                       nil))]
    `(top.kzre.krro.canvas.vector.path/ensure-width-type*
       ~path ~width-type
       :t-params      ~(wrap-delay t-params)
       :width-samples ~(wrap-delay width-samples)
       :arc-params    ~(wrap-delay arc-params)
       :width-curve   ~(wrap-delay width-curve)
       :compute-arc?  ~(if (some? compute-arc?) compute-arc? true))))
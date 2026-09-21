(ns top.kzre.krro.canvas.vector.core
  "矢量图层统一入口。
   类型无关的操作直接从各 ns re-export；
   类型相关的操作（变换 / 插点 / 挤出）在本 ns 按 :path-type 分派。
   当前仅支持 :bezier——catmull-rom 分派时抛异常。"
  (:require
   [top.kzre.krro.canvas.vector.anchor]
   [top.kzre.krro.canvas.vector.bezier.closure :as bezier.closure]
   [top.kzre.krro.canvas.vector.bezier.continuity :as bezier.continuity]
   [top.kzre.krro.canvas.vector.bezier.delete :as bezier.delete]
   [top.kzre.krro.canvas.vector.bezier.extrude    :as bezier.extrude]
   [top.kzre.krro.canvas.vector.bezier.handle :as bezier.handle]
   [top.kzre.krro.canvas.vector.bezier.insert     :as bezier.insert]
   [top.kzre.krro.canvas.vector.bezier.join :as bezier.join]
   [top.kzre.krro.canvas.vector.bezier.reverse :as bezier.reverse]
   [top.kzre.krro.canvas.vector.bezier.split :as bezier.split]
   [top.kzre.krro.canvas.vector.bezier.transform :as bezier.transform]
   [top.kzre.krro.canvas.vector.bezier.weld :as bezier.weld]
   [top.kzre.krro.canvas.vector.composite]
   [top.kzre.krro.canvas.vector.layer]
   [top.kzre.krro.canvas.vector.path]
   [top.kzre.krro.core.util.re-export :refer [re-export]]))

;; ═══════════════════════════════════════
;; Layer 结构
;; ═══════════════════════════════════════

(re-export
  [top.kzre.krro.canvas.vector.layer
   :refer
   [paths path-order fresh-path-id
    save-path delete-path make-vector-layer]])

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
    uniform-t-params
    valid-path?]])

;; ═══════════════════════════════════════
;; Anchor 类型 / 类型无关操作
;; ═══════════════════════════════════════

(re-export
  [top.kzre.krro.canvas.vector.anchor
   :refer
   [->Anchor map->Anchor
    ->AnchorTranslation map->AnchorTranslation
    path-of-anchor
    all-anchors anchor-point
    anchor-prev anchor-next anchor-seg-idxs
    end-anchor? active-anchor-after-extrude
    anchors-centroid
    anchor-tiles]])


;; ═══════════════════════════════════════
;; 分派 helper
;; ═══════════════════════════════════════

(defn- path-type-of-anchors
  "从 anchors 推断 path-type。
   全为空、或类型混合时抛异常。"
  [paths anchors]
  (when (empty? anchors)
    (throw (ex-info "no anchors" {})))
  (let [types (into #{} (map #(get-in paths [(:path-id %) :path-type])) anchors)]
    (when (not= 1 (count types))
      (throw (ex-info "anchors must belong to paths of a single type"
                      {:types types})))
    (first types)))

(defn- require-bezier
  "catmull-rom 暂不支持——分派时统一抛异常。"
  [op path-type]
  (when-not (= :bezier path-type)
    (throw (ex-info (str op " not yet supported for path type " path-type)
                    {:op op :path-type path-type}))))

;; ═══════════════════════════════════════
;; 变换（按路径类型分派）
;; ═══════════════════════════════════════

(defn translate-anchor
  "对单个锚点施加偏移。返回新 paths。"
  [paths anchor dx dy]
  (require-bezier "translate-anchor" (path-type-of-anchors paths [anchor]))
  (bezier.transform/translate-anchor paths anchor dx dy))

(defn translate-anchors
  "对一组锚点施加统一偏移。返回新 paths。"
  [paths anchors dx dy]
  (require-bezier "translate-anchors" (path-type-of-anchors paths anchors))
  (bezier.transform/translate-anchors paths anchors dx dy))

(defn apply-translations
  "将一批独立的锚点偏移应用到 paths。
   translations 为 [AnchorTranslation ...]。返回新 paths。"
  [paths translations]
  (require-bezier "apply-translations"
                  (path-type-of-anchors paths (mapv :anchor translations)))
  (bezier.transform/apply-translations paths translations))

(defn rotate-anchors
  "以 center 为中心旋转指定锚点。angle 为弧度。返回新 paths。"
  [paths anchors center angle]
  (require-bezier "rotate-anchors" (path-type-of-anchors paths anchors))
  (bezier.transform/rotate-anchors paths anchors center angle))

(defn scale-anchors
  "以 center 为中心缩放指定锚点。
   sx / sy 为缩放因子。返回新 paths。"
  [paths anchors center sx sy]
  (require-bezier "scale-anchors" (path-type-of-anchors paths anchors))
  (bezier.transform/scale-anchors paths anchors center sx sy))

(defn mirror-anchors
  "以 axis 为轴镜像指定锚点。
   axis 为 {:point {:x :y} :direction {:x :y}}。返回新 paths。"
  [paths anchors axis]
  (require-bezier "mirror-anchors" (path-type-of-anchors paths anchors))
  (bezier.transform/mirror-anchors paths anchors axis))

(defn skew-anchors
  "以 center 为中心斜切指定锚点。
   kx / ky 为斜切系数。返回新 paths。"
  [paths anchors center kx ky]
  (require-bezier "skew-anchors" (path-type-of-anchors paths anchors))
  (bezier.transform/skew-anchors paths anchors center kx ky))

;; ═══════════════════════════════════════
;; 拓扑编辑（按路径类型分派）
;; ═══════════════════════════════════════

(defn insert-anchor
  "在指定全局归一化参数 t 处插入锚点。
   返回 {:paths new-paths :anchor new-anchor}。"
  [paths path-id t]
  (require-bezier "insert-anchor" (get-in paths [path-id :path-type]))
  (bezier.insert/insert-anchor paths path-id t))


(defn delete-anchor
  "删除锚点。返回 {:paths new-paths :deleted-idx idx}。
   允许退化到 1 个点的路径。要求 :bezier 非闭合。"
  [paths anchor]
  (require-bezier "delete-anchor"
                  (get-in paths [(:path-id anchor) :path-type]))
  (bezier.delete/delete-anchor paths anchor))

(defn extrude-anchor
  "若锚点是路径端点，则在该端挤出一点。
   返回 {:paths new-paths :anchor new-anchor}，非端点返回 nil。"
  [paths anchor point]
  (require-bezier "extrude-anchor" (get-in paths [(:path-id anchor) :path-type]))
  (bezier.extrude/extrude-anchor paths anchor point))

(defn join-paths
  "合并两条曲线。要求 :bezier 非闭合。
   返回新 path——从 left 派生。"
  [left right]
  (require-bezier "join-paths" (:path-type left))
  (require-bezier "join-paths" (:path-type right))
  (bezier.join/join-paths left right))
(defn split-at-anchor
  "在锚点处切分曲线为 [left-path right-path]。
   要求 :bezier 非闭合路径，idx ∈ [1, n-2]。"
  [path idx]
  (require-bezier "split-at-anchor" (:path-type path))
  (bezier.split/split-at-anchor path idx))

(defn reverse-path
  "反向曲线。返回新 path。要求 :bezier。"
  [path]
  (require-bezier "reverse-path" (:path-type path))
  (bezier.reverse/reverse-path path))

(defn weld-anchors
  "焊接两个锚点。返回 {:paths new-paths :weld-anchor new-anchor}。
   同路径：相邻；跨路径：均为端点且路径非闭合。"
  [paths anchor-active anchor-passive]
  (require-bezier "weld-anchors"
                  (get-in paths [(:path-id anchor-active) :path-type]))
  (require-bezier "weld-anchors"
                  (get-in paths [(:path-id anchor-passive) :path-type]))
  (bezier.weld/weld-anchors paths anchor-active anchor-passive))

(defn close-path [path]
  (require-bezier "close-path" (:path-type path))
  (bezier.closure/close-path path))

(defn open-path [path]
  (require-bezier "open-path" (:path-type path))
  (bezier.closure/open-path path))

(defn set-handle-in [path idx dx dy]
  (require-bezier "set-handle-in" (:path-type path))
  (bezier.handle/set-handle-in path idx dx dy))

(defn set-handle-out [path idx dx dy]
  (require-bezier "set-handle-out" (:path-type path))
  (bezier.handle/set-handle-out path idx dx dy))

(defn clear-handles [path idx]
  (require-bezier "clear-handles" (:path-type path))
  (bezier.handle/clear-handles path idx))

(defn mirror-in-from-out [path idx]
  (require-bezier "mirror-in-from-out" (:path-type path))
  (bezier.handle/mirror-in-from-out path idx))

(defn mirror-out-from-in [path idx]
  (require-bezier "mirror-out-from-in" (:path-type path))
  (bezier.handle/mirror-out-from-in path idx))

(defn move-handle-in [path idx dx dy]
  (require-bezier "move-handle-in" (:path-type path))
  (bezier.handle/move-handle-in path idx dx dy))

(defn move-handle-out [path idx dx dy]
  (require-bezier "move-handle-out" (:path-type path))
  (bezier.handle/move-handle-out path idx dx dy))

(defn set-continuity
  "设置锚点 idx 的连续性。返回新 path。不触发约束求解。"
  [path idx continuity]
  (require-bezier "set-continuity" (:path-type path))
  (bezier.continuity/set-continuity path idx continuity))

(defn apply-constraints
  "对整条曲线应用所有锚点的连续性约束。返回新 path。"
  [path]
  (require-bezier "apply-constraints" (:path-type path))
  (bezier.continuity/apply-constraints path))

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
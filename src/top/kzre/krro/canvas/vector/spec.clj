(ns top.kzre.krro.canvas.vector.spec
  "矢量图层规格定义。在统一的 :vector 图层中，通过路径的 :path-type 分派到不同曲线类型。
   路径以 map 存储（键为 id），顺序由 vector 定义，支持渲染缓存与单个脏矩形增量更新。
   路径支持携带宽度采样数据 (width-samples + arc-params) 以实现可变宽度描边。"
  (:require [clojure.spec.alpha :as s]
            [top.kzre.krro.curve.bezier2d.spec :as bezier]
            [top.kzre.krro.curve.catmullrom2d.spec :as cr]
            [top.kzre.krro.canvas.core.layer.spec :as layer-spec]))

;; ── 路径类型分派 ──────────────────────────────────
(s/def ::path-type #{:bezier :catmull-rom})

;; ── Bézier 路径 ───────────────────────────────────
(s/def ::bezier-curve ::bezier/curve)
;; 宽度采样数据：等弧长采样宽度序列 与 对应的弧长参数序列
(s/def ::width-samples (s/coll-of number? :kind vector?))
(s/def ::arc-params (s/coll-of number? :kind vector?))
(s/def ::bezier-path (s/keys :req-un [::path-type ::bezier-curve]
                             :opt-un [::width-samples ::arc-params]))

;; ── Catmull‑Rom 路径 ─────────────────────────────
(s/def ::cr-curve ::cr/curve)
(s/def ::catmull-rom-path (s/keys :req-un [::path-type ::cr-curve]
                                  :opt-un [::width-samples ::arc-params]))

(defmulti path-spec :path-type)
(defmethod path-spec :bezier [_] ::bezier-path)
(defmethod path-spec :catmull-rom [_] ::catmull-rom-path)

(s/def ::path (s/multi-spec path-spec :path-type))

;; ── 路径集合：map + order ────────────────────────
(s/def ::id keyword?)
(s/def ::paths-map (s/map-of ::id ::path :conform-keys true))
(s/def ::path-order (s/coll-of ::id :kind vector? :distinct true))



;; ── 矢量图层属性 ────────────────────────────────
(s/def ::antialiased boolean?)
(s/def ::vector-props
  (s/keys :req-un [::paths-map ::path-order]
          :opt-un [::antialiased]))

;; 注册到图层系统
(defmethod layer-spec/layer-spec :vector [_] ::vector-props)
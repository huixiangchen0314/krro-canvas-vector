(ns top.kzre.krro.canvas.vector.bezier.continuity
  "锚点的连续性约束：声明与求解。

   两个关注点分离：
     1. 声明 —— set-continuity 写入 :continuity 字段
     2. 求解 —— apply-constraints 调 Java Curve.applyConstraints 重算手柄

   本 ns 不自动触发求解——调用方显式调 apply-constraints。
   约束是整条曲线的事——apply-constraints 对整段曲线应用，不提供单锚点版本。

   连续性语义：
     :none —— 角点，两侧手柄独立
     :g1   —— 方向共线（大小独立）
     :c1   —— 方向共线 + 大小相等
     :g2   —— G1 + 曲率连续（Java 已实现）
     :c2   —— C1 + 二阶连续（Java 未实现——apply 时抛异常）

   arc-params 不清理——它是渲染参数，不影响数据合法性。
   用户如需重算，显式调用 path 层的重算 API。

   仅支持 :bezier 路径。"
  (:require
    [top.kzre.krro.canvas.vector.path  :as path]
    [top.kzre.krro.canvas.vector.curve :as curve]))

;; ═══════════════════════════════════════
;; 内部
;; ═══════════════════════════════════════

(def ^:private valid-continuities
  #{:none :g1 :c1 :g2 :c2})

(defn- check-path [path]
  (when-not (= :bezier (:path-type path))
    (throw (ex-info "continuity ops only support :bezier paths"
                    {:path-type (:path-type path)}))))

(defn- check-idx [path idx]
  (let [n (path/path-point-count path)]
    (when-not (<= 0 idx (dec n))
      (throw (ex-info "continuity index out of bounds"
                      {:idx idx :point-count n})))))

;; ═══════════════════════════════════════
;; 声明
;; ═══════════════════════════════════════

(defn set-continuity
  "设置锚点 idx 的连续性。返回新 path。

   只写字段——不触发约束求解。调用方随后显式调用 apply-constraints。

   continuity ∈ #{:none :g1 :c1 :g2 :c2}。
   :c2 合法声明，但 apply-constraints 会抛异常（Java 未实现）。"
  [path idx continuity]
  {:pre [(map? path) (int? idx)]}
  (check-path path)
  (check-idx path idx)
  (when-not (contains? valid-continuities continuity)
    (throw (ex-info "invalid continuity"
                    {:continuity continuity
                     :valid      valid-continuities})))
  (update-in path [:curve :points idx]
             assoc :continuity continuity))

;; ═══════════════════════════════════════
;; 求解
;; ═══════════════════════════════════════

(defn apply-constraints
  "对整条曲线应用所有锚点的连续性约束。返回新 path。

   遍历所有控制点，按各自的 :continuity 重算手柄：
     :none —— 不动
     :g1   —— 入切方向与出切反向共线，长度比例保持
     :c1   —— 入切 = -出切
     :g2   —— G1 + 曲率相等
     :c2   —— 抛 UnsupportedOperationException（Java 未实现）

   不修改 :continuity 字段。
   不清理 :arc-params —— 用户如需重算，显式调用。

   纯函数——返回新 path。"
  [path]
  {:pre [(map? path)]}
  (check-path path)
  (let [jc (curve/edn->curve (:curve path))]
    (.applyConstraints jc)
    (assoc path :curve (curve/curve->edn jc))))
(ns top.kzre.krro.canvas.vector.bezier.handle
  "Bézier 手柄编辑。

   只做数据处理——不强制约束一致性。约束由 continuity ns 显式应用。

   覆盖的操作：
     1. 设置——直接写入手柄向量 (dx, dy)
     2. 清空——两侧手柄置零
     3. 镜像——单侧手柄按反向复制到对侧（等长反向）
     4. 移动——手柄向量按偏移量平移

   手柄的仿射变换（旋转 / 缩放 / 镜像 / 斜切）由 bezier.transform 完成——
   它作用于整个控制点（位置 + 手柄一起）。本 ns 只管手柄单独编辑。

   仅支持 :bezier 路径。"
  (:require
    [top.kzre.krro.canvas.vector.path :as path]))

;; ═══════════════════════════════════════
;; 内部：校验 / 更新
;; ═══════════════════════════════════════

(defn- check-path [path]
  (when-not (= :bezier (:path-type path))
    (throw (ex-info "handle ops only support :bezier paths"
                    {:path-type (:path-type path)}))))

(defn- check-idx [path idx]
  (let [n (path/path-point-count path)]
    (when-not (<= 0 idx (dec n))
      (throw (ex-info "handle index out of bounds"
                      {:idx idx :point-count n})))))

(defn- update-point-at
  [path idx f]
  (update-in path [:curve :points idx] f))

;; ═══════════════════════════════════════
;; 1. 设置
;; ═══════════════════════════════════════

(defn set-handle-in
  "设置锚点 idx 的入切向量 (dx1, dy1)。返回新 path。"
  [path idx dx dy]
  {:pre [(map? path) (int? idx) (number? dx) (number? dy)]}
  (check-path path)
  (check-idx path idx)
  (update-point-at path idx
                   #(assoc % :dx1 (double dx) :dy1 (double dy))))

(defn set-handle-out
  "设置锚点 idx 的出切向量 (dx2, dy2)。返回新 path。"
  [path idx dx dy]
  {:pre [(map? path) (int? idx) (number? dx) (number? dy)]}
  (check-path path)
  (check-idx path idx)
  (update-point-at path idx
                   #(assoc % :dx2 (double dx) :dy2 (double dy))))

;; ═══════════════════════════════════════
;; 2. 清空
;; ═══════════════════════════════════════

(defn clear-handles
  "将锚点 idx 的入切和出切都清零。返回新 path。

   连续性字段不动——由调用方决定是否同步改为 :none。
   若要保持约束一致性，调用方随后需调 continuity 层的 apply-constraints。"
  [path idx]
  {:pre [(map? path) (int? idx)]}
  (check-path path)
  (check-idx path idx)
  (update-point-at path idx
                   #(assoc % :dx1 0.0 :dy1 0.0 :dx2 0.0 :dy2 0.0)))

;; ═══════════════════════════════════════
;; 3. 镜像
;; ═══════════════════════════════════════

(defn mirror-in-from-out
  "入切 ← 出切的反向（等长反向）。返回新 path。

   (dx1, dy1) ← (-dx2, -dy2)。出切保持不变。"
  [path idx]
  {:pre [(map? path) (int? idx)]}
  (check-path path)
  (check-idx path idx)
  (update-point-at path idx
                   (fn [pt]
                     (assoc pt :dx1 (- (double (:dx2 pt)))
                               :dy1 (- (double (:dy2 pt)))))))

(defn mirror-out-from-in
  "出切 ← 入切的反向（等长反向）。返回新 path。

   (dx2, dy2) ← (-dx1, -dy1)。入切保持不变。"
  [path idx]
  {:pre [(map? path) (int? idx)]}
  (check-path path)
  (check-idx path idx)
  (update-point-at path idx
                   (fn [pt]
                     (assoc pt :dx2 (- (double (:dx1 pt)))
                               :dy2 (- (double (:dy1 pt)))))))

;; ═══════════════════════════════════════
;; 4. 移动
;; ═══════════════════════════════════════

(defn move-handle-in
  "平移锚点 idx 的入切向量。返回新 path。

   (dx1, dy1) += (dx, dy)。"
  [path idx dx dy]
  {:pre [(map? path) (int? idx) (number? dx) (number? dy)]}
  (check-path path)
  (check-idx path idx)
  (update-point-at path idx
                   (fn [pt]
                     (assoc pt :dx1 (+ (double (:dx1 pt)) (double dx))
                               :dy1 (+ (double (:dy1 pt)) (double dy))))))

(defn move-handle-out
  "平移锚点 idx 的出切向量。返回新 path。

   (dx2, dy2) += (dx, dy)。"
  [path idx dx dy]
  {:pre [(map? path) (int? idx) (number? dx) (number? dy)]}
  (check-path path)
  (check-idx path idx)
  (update-point-at path idx
                   (fn [pt]
                     (assoc pt :dx2 (+ (double (:dx2 pt)) (double dx))
                               :dy2 (+ (double (:dy2 pt)) (double dy))))))
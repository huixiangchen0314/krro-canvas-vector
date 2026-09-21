(ns top.kzre.krro.canvas.vector.anchor
  "锚点领域操作：定位、遍历、判定、质心、脏区域。
   变换操作按曲线类型分派——见 bezier.transform / catmull.transform。

   参数约定：
     paths —— 路径映射，总是锚点级操作的入口
     Anchor —— 携带 :path-id / :point-idx 的完整领域对象
     path —— 只在 path 级操作出现（本 ns 内部使用）"
  (:require
    [top.kzre.krro.canvas.vector.path :as path])
  (:import
    (top.kzre.krro.canvas.vector CurveClipper)))

;; ═══════════════════════════════════════
;; 类型
;; ═══════════════════════════════════════

(defrecord Anchor [path-id point-idx])
(defrecord AnchorTranslation [^Anchor anchor dx dy])

(defn active-anchor-after-extrude
  "挤出后原锚点的新索引：起点挤出索引 0 → 1；终点挤出索引不变。"
  [^Anchor anchor is-start?]
  {:pre [(some? anchor) (boolean? is-start?)]}
  (if is-start?
    (->Anchor (:path-id anchor) 1)
    anchor))

;; ═══════════════════════════════════════
;; 定位
;; ═══════════════════════════════════════

(defn path-of-anchor
  "从 paths 映射中取出 anchor 所属的路径。不存在返回 nil。"
  [paths ^Anchor anchor]
  (get paths (:path-id anchor)))

;; ═══════════════════════════════════════
;; 遍历 / 读取
;; ═══════════════════════════════════════

(defn all-anchors
  "返回指定路径中所有锚点的列表。路径不存在时抛异常。"
  [paths path-id]
  {:pre [(map? paths) (some? path-id)]}
  (let [p (get paths path-id)]
    (when-not p
      (throw (ex-info "path not found" {:path-id path-id})))
    (mapv #(->Anchor path-id %) (range (path/path-point-count p)))))

(defn anchor-point
  "锚点坐标 {:x :y}。路径不存在或索引越界返回 nil。"
  [paths ^Anchor anchor]
  {:pre [(map? paths) (instance? Anchor anchor)]}
  (when-let [p (path-of-anchor paths anchor)]
    (get (path/path-points p) (:point-idx anchor))))

;; ═══════════════════════════════════════
;; 导航
;; ═══════════════════════════════════════

(defn anchor-prev
  "前一个锚点。
   闭合曲线首点的前一个 = 末点；
   非闭合曲线首点返回 nil。
   路径不存在返回 nil；索引越界抛异常。"
  [paths ^Anchor anchor]
  {:pre [(map? paths) (instance? Anchor anchor)]}
  (when-let [p (path-of-anchor paths anchor)]
    (let [idx (:point-idx anchor)
          n   (path/path-point-count p)]
      (when-not (<= 0 idx (dec n))
        (throw (ex-info "anchor index out of bounds"
                        {:anchor anchor :point-count n})))
      (let [prev-idx (if (path/path-closed? p)
                       (mod (dec idx) n)
                       (when (pos? idx) (dec idx)))]
        (when (some? prev-idx)
          (->Anchor (:path-id anchor) prev-idx))))))

(defn anchor-next
  "后一个锚点。
   闭合曲线末点的后一个 = 首点；
   非闭合曲线末点返回 nil。
   路径不存在返回 nil；索引越界抛异常。"
  [paths ^Anchor anchor]
  {:pre [(map? paths) (instance? Anchor anchor)]}
  (when-let [p (path-of-anchor paths anchor)]
    (let [idx (:point-idx anchor)
          n   (path/path-point-count p)]
      (when-not (<= 0 idx (dec n))
        (throw (ex-info "anchor index out of bounds"
                        {:anchor anchor :point-count n})))
      (let [next-idx (if (path/path-closed? p)
                       (mod (inc idx) n)
                       (when (< idx (dec n)) (inc idx)))]
        (when (some? next-idx)
          (->Anchor (:path-id anchor) next-idx))))))

(defn anchor-seg-idxs
  "锚点相邻的段索引。返回 [left right]，不存在的侧为 nil。

   非闭合：首点 [nil 0]，尾点 [n-2 nil]，中间 [idx-1 idx]。
   闭合：首点 [n-1 0]，尾点 [n-2 n-1]，中间 [idx-1 idx]。

   段索引语义：段 i 连接控制点 i 和 i+1（闭合时末段连接 n-1 和 0）。
   路径不存在返回 nil；索引越界抛异常。"
  [paths ^Anchor anchor]
  {:pre [(map? paths) (instance? Anchor anchor)]}
  (when-let [p (path-of-anchor paths anchor)]
    (let [idx     (:point-idx anchor)
          n       (path/path-point-count p)
          closed? (path/path-closed? p)]
      (when-not (<= 0 idx (dec n))
        (throw (ex-info "anchor index out of bounds"
                        {:anchor anchor :point-count n})))
      (if closed?
        [(mod (dec idx) n) idx]
        [(when (pos? idx) (dec idx))
         (when (< idx (dec n)) idx)]))))

;; ═══════════════════════════════════════
;; 判定
;; ═══════════════════════════════════════

(defn end-anchor?
  "是否为路径的端点。仅支持非闭合曲线。
   路径不存在返回 nil。"
  [paths ^Anchor anchor]
  {:pre [(map? paths) (instance? Anchor anchor)]}
  (when-let [p (path-of-anchor paths anchor)]
    (let [n   (path/path-point-count p)
          idx (:point-idx anchor)]
      (boolean
        (and (not (path/path-closed? p))
             (pos? n)
             (or (zero? idx) (= idx (dec n))))))))

;; ═══════════════════════════════════════
;; 质心
;; ═══════════════════════════════════════

(defn anchors-centroid
  "所选择锚点的算术平均坐标。anchors 为空或全部无效时返回 nil。"
  [paths anchors]
  {:pre [(map? paths) (sequential? anchors)]}
  (let [points (keep (fn [a] (anchor-point paths a)) anchors)]
    (when (seq points)
      (let [n (count points)]
        {:x (/ (reduce + (map :x points)) n)
         :y (/ (reduce + (map :y points)) n)}))))

;; ═══════════════════════════════════════
;; 脏区域
;; ═══════════════════════════════════════

(defn anchor-tiles
  "锚点两侧段经过的瓦片集合（含描边扩展）。
   返回 Set<Long>（TiledCanvas.pack 格式）。路径不存在时返回 nil。"
  [paths ^Anchor anchor tile-size]
  {:pre [(map? paths)
         (instance? Anchor anchor)
         (pos-int? tile-size)]}
  (when-let [p (path-of-anchor paths anchor)]
    (let [idxs (keep identity (anchor-seg-idxs paths anchor))]
      (if (seq idxs)
        (CurveClipper/segTilesForIdxs (path/path->curve p)
                                      (int-array idxs)
                                      (int tile-size)
                                      (path/max-path-half-width p))
        #{}))))
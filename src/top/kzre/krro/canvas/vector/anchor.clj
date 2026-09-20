(ns top.kzre.krro.canvas.vector.anchor
  "锚点领域操作：定位、遍历、判定、质心、脏区域。
     变换操作按曲线类型分派——见 bezier.transform / catmull.transform。"
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
  "返回路径中所有锚点的列表。"
  [path path-id]
  {:pre [(path/valid-path? path) (some? path-id)]}
  (mapv #(->Anchor path-id %) (range (path/path-point-count path))))

(defn anchor-point
  "锚点坐标 {:x :y}。索引越界返回 nil。"
  [path idx]
  {:pre [(path/valid-path? path) (int? idx)]}
  (get (path/path-points path) idx))

;; ═══════════════════════════════════════
;; 导航
;; ═══════════════════════════════════════

(defn anchor-prev
  "前一个锚点的索引。
   闭合曲线首点的前一个 = 末点；
   非闭合曲线首点返回 nil。
   索引越界抛异常。"
  [path idx]
  {:pre [(path/valid-path? path) (int? idx)]}
  (let [n (path/path-point-count path)]
    (when-not (<= 0 idx (dec n))
      (throw (ex-info "anchor index out of bounds"
                      {:idx idx :point-count n})))
    (if (path/path-closed? path)
      (mod (dec idx) n)
      (when (pos? idx) (dec idx)))))

(defn anchor-next
  "后一个锚点的索引。
   闭合曲线末点的后一个 = 首点；
   非闭合曲线末点返回 nil。
   索引越界抛异常。"
  [path idx]
  {:pre [(path/valid-path? path) (int? idx)]}
  (let [n (path/path-point-count path)]
    (when-not (<= 0 idx (dec n))
      (throw (ex-info "anchor index out of bounds"
                      {:idx idx :point-count n})))
    (if (path/path-closed? path)
      (mod (inc idx) n)
      (when (< idx (dec n)) (inc idx)))))

(defn anchor-seg-idxs
  "锚点相邻的段索引。返回 [left right]，不存在的侧为 nil。

   非闭合：首点 [nil 0]，尾点 [n-2 nil]，中间 [idx-1 idx]。
   闭合：首点 [n-1 0]，尾点 [n-2 n-1]，中间 [idx-1 idx]。

   段索引语义：段 i 连接控制点 i 和 i+1（闭合时末段连接 n-1 和 0）。
   索引越界抛异常。"
  [path idx]
  {:pre [(path/valid-path? path) (int? idx)]}
  (let [n       (path/path-point-count path)
        closed? (path/path-closed? path)]
    (when-not (<= 0 idx (dec n))
      (throw (ex-info "anchor index out of bounds"
                      {:idx idx :point-count n})))
    (if closed?
      [(mod (dec idx) n) idx]
      [(when (pos? idx) (dec idx))
       (when (< idx (dec n)) idx)])))

;; ═══════════════════════════════════════
;; 判定
;; ═══════════════════════════════════════

(defn end-anchor?
  "是否为路径的端点。仅支持非闭合曲线。"
  [path ^Anchor anchor]
  {:pre [(path/valid-path? path) (instance? Anchor anchor)]}
  (let [n   (path/path-point-count path)
        idx (:point-idx anchor)]
    (boolean
      (and (not (path/path-closed? path))
           (pos? n)
           (or (zero? idx) (= idx (dec n)))))))

;; ═══════════════════════════════════════
;; 质心
;; ═══════════════════════════════════════

(defn anchors-centroid
  "所选择锚点的算术平均坐标。anchors 为空或全部无效时返回 nil。"
  [paths anchors]
  {:pre [(map? paths) (sequential? anchors)]}
  (let [points (keep (fn [a]
                       (let [p (path-of-anchor paths a)]
                         (when p (anchor-point p (:point-idx a)))))
                     anchors)]
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
    (let [idxs (keep identity (anchor-seg-idxs p (:point-idx anchor)))]
      (if (seq idxs)
        (CurveClipper/segTilesForIdxs (path/path->curve p)
                                      (int-array idxs)
                                      (int tile-size)
                                      (path/max-path-half-width p))
        #{}))))
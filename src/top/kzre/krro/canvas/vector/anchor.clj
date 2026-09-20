(ns top.kzre.krro.canvas.vector.anchor
  "锚点领域操作：定位、遍历、判定、质心、平移、仿射变换、脏区域。"
  (:require
    [top.kzre.krro.canvas.vector.path :as path])
  (:import
    (top.kzre.krro.canvas.vector CurveClipper)
    (top.kzre.krro.util.math KMath)))

;; ═══════════════════════════════════════
;; 类型
;; ═══════════════════════════════════════

(defrecord Anchor [path-id point-idx])
(defrecord AnchorTranslation [^Anchor anchor dx dy])

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
;; 内部：控制点变换
;; ═══════════════════════════════════════

(defn- map-curve-points
  "对 curve-edn 中指定索引的控制点应用 f。
   f: (idx, control-point) → control-point。
   返回新 curve-edn。

   调用方保证 idxs 合法。"
  [f curve-edn idxs]
  (let [idx-set (set idxs)
        points  (:points curve-edn)]
    (assoc curve-edn :points
                     (mapv (fn [i cp]
                             (if (contains? idx-set i) (f i cp) cp))
                           (range) points))))

(defn- point-affine-transform-fn
  "返回 2D 仿射变换函数，用于 map-curve-points。
   位置含平移；手柄向量只乘线性部分——平移不改变切线方向。"
  [^doubles mat2d]
  (fn [_idx cp]
    (let [pos (KMath/mat2dTransformPointD
                mat2d (double (:x cp)) (double (:y cp)))
          h1  (KMath/mat2dTransformDirectionD
                mat2d (double (:dx1 cp)) (double (:dy1 cp)))
          h2  (KMath/mat2dTransformDirectionD
                mat2d (double (:dx2 cp)) (double (:dy2 cp)))]
      (assoc cp
        :x   (aget pos 0) :y   (aget pos 1)
        :dx1 (aget h1 0)  :dy1 (aget h1 1)
        :dx2 (aget h2 0)  :dy2 (aget h2 1)))))

;; ═══════════════════════════════════════
;; 内部：2D 仿射矩阵构造
;; ═══════════════════════════════════════

(defn- rotation-matrix
  "绕 (cx, cy) 旋转 angle 弧度。返回 double[6] [a b c d tx ty]。"
  [cx cy angle]
  (let [c (Math/cos (double angle))
        s (Math/sin (double angle))]
    (double-array
      [c s (- s) c
       (+ (* cx (- 1.0 c)) (* s cy))
       (- (* cy (- 1.0 c)) (* s cx))])))

(defn- scaling-matrix
  "以 (cx, cy) 为中心缩放 (sx, sy)。返回 double[6]。"
  [cx cy sx sy]
  (double-array
    [sx 0.0 0.0 sy
     (- cx (* sx cx))
     (- cy (* sy cy))]))

(defn- mirror-matrix
  "沿经过 (px, py)、方向 (dx, dy) 的直线镜像。返回 double[6]。"
  [px py dx dy]
  (let [len (Math/sqrt (+ (* dx dx) (* dy dy)))
        nx  (/ (- dy) len)
        ny  (/ dx len)
        a   (- 1.0 (* 2.0 nx nx))
        b   (* -2.0 nx ny)
        d   (- 1.0 (* 2.0 ny ny))
        tx  (- px (+ (* a px) (* b py)))
        ty  (- py (+ (* b px) (* d py)))]
    (double-array [a b b d tx ty])))

(defn- skew-matrix
  "以 (cx, cy) 为中心斜切。
   kx 为 X 方向斜切系数（点沿 Y 位移 x' = x + kx·y）。
   ky 为 Y 方向斜切系数（点沿 X 位移 y' = y + ky·x）。
   返回 double[6]。"
  [cx cy kx ky]
  (double-array
    [1.0  kx  ky  1.0
     (- (* ky cy))
     (- (* kx cx))]))

;; ═══════════════════════════════════════
;; 内部：仿射变换应用
;; ═══════════════════════════════════════

(defn- affine-transform-path
  "对 path 中指定索引的控制点应用 2D 仿射矩阵。
   返回新 path——:curve 更新。
   :arc-params 的处理由调用方决定（显式失效原则）。"
  [p idxs ^doubles mat2d]
  (assoc p :curve
           (map-curve-points
             (point-affine-transform-fn mat2d)
             (:curve p)
             idxs)))

(defn- affine-transform-paths
  "对多个路径的锚点集合应用仿射矩阵。
   anchors 按 :path-id 分组。返回新 paths。"
  [paths anchors ^doubles mat2d]
  (reduce (fn [acc [path-id as]]
            (if-let [p (get acc path-id)]
              (assoc acc path-id
                         (affine-transform-path p (mapv :point-idx as) mat2d))
              acc))
          paths
          (group-by :path-id anchors)))

;; ═══════════════════════════════════════
;; 平移
;; ═══════════════════════════════════════

(defn- translate-curve-points
  "平移路径中曲线指定索引的控制点。纯 EDN 操作。"
  [p translations]
  (let [by-idx (reduce (fn [acc {:keys [anchor dx dy]}]
                         (assoc acc (:point-idx anchor) [dx dy]))
                       {}
                       translations)
        f      (fn [i cp]
                 (let [[dx dy] (get by-idx i)]
                   (assoc cp
                     :x (+ (:x cp) dx)
                     :y (+ (:y cp) dy))))]
    (assoc p :curve (map-curve-points f (:curve p) (keys by-idx)))))

(defn apply-translations
  "将一批独立的锚点偏移应用到 paths。translations 为 [AnchorTranslation ...]。
   用于编辑器层的衰减、跟随、磁吸等批量编辑。
   返回新 paths。"
  [paths translations]
  {:pre [(map? paths)
         (sequential? translations)
         (every? #(instance? AnchorTranslation %) translations)]}
  (reduce (fn [acc [path-id ts]]
            (if-let [p (get acc path-id)]
              (assoc acc path-id (translate-curve-points p ts))
              acc))
          paths
          (group-by (comp :path-id :anchor) translations)))

(defn translate-anchors
  "对一组锚点施加统一偏移。返回新 paths。"
  [paths anchors dx dy]
  {:pre [(map? paths)
         (sequential? anchors)
         (every? #(instance? Anchor %) anchors)
         (number? dx)
         (number? dy)]}
  (apply-translations paths (mapv #(->AnchorTranslation % dx dy) anchors)))

(defn translate-anchor
  "对单个锚点施加偏移。返回新 paths。"
  [paths ^Anchor anchor dx dy]
  {:pre [(map? paths)
         (instance? Anchor anchor)
         (number? dx)
         (number? dy)]}
  (translate-anchors paths [anchor] dx dy))

;; ═══════════════════════════════════════
;; 旋转 / 缩放 / 镜像
;; ═══════════════════════════════════════

(defn rotate-anchors
  "以 center 为中心旋转指定锚点。angle 为弧度。
   center 为 {:x :y}。返回新 paths。"
  [paths anchors center angle]
  {:pre [(map? paths)
         (sequential? anchors)
         (every? #(instance? Anchor %) anchors)
         (map? center)
         (number? (:x center)) (number? (:y center))
         (number? angle)]}
  (affine-transform-paths
    paths anchors
    (rotation-matrix (:x center) (:y center) angle)))

(defn scale-anchors
  "以 center 为中心缩放指定锚点。
   sx / sy 为缩放因子（1.0 表示不缩放）。返回新 paths。"
  [paths anchors center sx sy]
  {:pre [(map? paths)
         (sequential? anchors)
         (every? #(instance? Anchor %) anchors)
         (map? center)
         (number? (:x center)) (number? (:y center))
         (number? sx) (number? sy)]}
  (affine-transform-paths
    paths anchors
    (scaling-matrix (:x center) (:y center) sx sy)))

(defn mirror-anchors
  "以 axis 为轴镜像指定锚点。
   axis 为 {:point {:x :y} :direction {:x :y}}。
   direction 是轴线方向向量，必须非零。返回新 paths。"
  [paths anchors axis]
  {:pre [(map? paths)
         (sequential? anchors)
         (every? #(instance? Anchor %) anchors)
         (map? axis)
         (map? (:point axis))
         (map? (:direction axis))
         (number? (get-in axis [:point :x]))
         (number? (get-in axis [:point :y]))
         (number? (get-in axis [:direction :x]))
         (number? (get-in axis [:direction :y]))]}
  (let [px (get-in axis [:point :x])     py (get-in axis [:point :y])
        dx (get-in axis [:direction :x]) dy (get-in axis [:direction :y])]
    (when (and (zero? dx) (zero? dy))
      (throw (ex-info "mirror axis direction must be non-zero"
                      {:axis axis})))
    (affine-transform-paths paths anchors
                            (mirror-matrix px py dx dy))))





(defn skew-anchors
  "以 center 为中心斜切指定锚点。
   kx 为 X 方向斜切系数，ky 为 Y 方向斜切系数。
   系数为 0 表示该方向不斜切。
   center 为 {:x :y}。返回新 paths。"
  [paths anchors center kx ky]
  {:pre [(map? paths)
         (sequential? anchors)
         (every? #(instance? Anchor %) anchors)
         (map? center)
         (number? (:x center)) (number? (:y center))
         (number? kx) (number? ky)]}
  (affine-transform-paths
    paths anchors
    (skew-matrix (:x center) (:y center) kx ky)))

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
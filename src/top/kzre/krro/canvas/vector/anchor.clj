(ns top.kzre.krro.canvas.vector.anchor
  "锚点领域操作：定位、遍历、判定、质心、平移、挤出、脏区域。"
  (:require
    [top.kzre.krro.canvas.vector.path  :as path]
    [top.kzre.krro.canvas.vector.curve :as curve])
  (:import
    (top.kzre.curve.bezier2d
      ArcLengthUtils
      Curve
      CurveExtrusionUtils
      TableMapping)
    (top.kzre.krro.canvas.vector CurveClipper)))

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
;; 平移
;; ═══════════════════════════════════════

(defn- translate-curve-points
  "平移路径中曲线指定索引的控制点。纯 EDN 操作。"
  [p translations]
  (let [by-delta (group-by (juxt :dx :dy) translations)
        new-ce   (reduce (fn [ce [[dx dy] ts]]
                           (path/translate-points
                             ce dx dy
                             (mapv #(-> % :anchor :point-idx) ts)))
                         (:curve p)
                         by-delta)]
    (assoc p :curve new-ce)))

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
;; 挤出
;; ═══════════════════════════════════════

(defn- extrude-curve
  "执行曲线挤出，返回 {:curve :t-params}。"
  [^Curve old-curve old-t-params point is-start?]
  (let [new-curve    (Curve.)
        pair         (curve/edn->pair point)
        new-t-params (if is-start?
                       (CurveExtrusionUtils/extrudeHead
                         old-curve (double-array old-t-params) pair new-curve)
                       (CurveExtrusionUtils/extrudeTail
                         old-curve (double-array old-t-params) pair new-curve))]
    {:curve    new-curve
     :t-params (vec new-t-params)}))

(defn active-anchor-after-extrude
  "挤出后原锚点的新索引：起点挤出索引 0 → 1；终点挤出索引不变。"
  [^Anchor anchor is-start?]
  {:pre [(instance? Anchor anchor) (boolean? is-start?)]}
  (if is-start?
    (->Anchor (:path-id anchor) 1)
    anchor))

(defn extrude-anchor
  "若锚点是路径端点，则在该端挤出一点。
   返回 {:paths new-paths :anchor updated-anchor :new-anchor new-anchor}。
   非端点返回 nil。仅支持 :bezier 路径。

   paths 中不存在该 path-id 时抛异常——这是数据错误，
   与「锚点非端点」的正常业务分支区分。"
  [paths ^Anchor anchor point]
  {:pre [(map? paths)
         (instance? Anchor anchor)
         (map? point)
         (number? (:x point))
         (number? (:y point))]}
  (let [path-id (:path-id anchor)
        p       (get paths path-id)]
    (when-not p
      (throw (ex-info "path not found for anchor"
                      {:path-id       path-id
                       :available-ids (keys paths)})))
    (when (end-anchor? p anchor)
      (when-not (= :bezier (:path-type p))
        (throw (ex-info "extrude-anchor only supports :bezier paths"
                        {:path-id path-id :path-type (:path-type p)})))
      (let [idx            (:point-idx anchor)
            is-start?      (zero? idx)
            width-type     (path/path-width-type p)
            old-curve      (curve/edn->curve (:curve p))
            old-t-params   (or (:t-params p)
                               (path/uniform-t-params (path/path-point-count p)))
            {:keys [curve t-params]}
            (extrude-curve old-curve old-t-params point is-start?)
            new-num-points (count (.getPoints curve))
            new-curve-edn  (curve/curve->edn curve)
            new-path
            (case width-type
              :fixed
              (-> p
                  (assoc :curve new-curve-edn)
                  (dissoc :width-samples :arc-params :t-params))

              :point-width
              (let [old-samples (:width-samples p)
                    w           (if is-start? (first old-samples) (last old-samples))
                    new-samples (if is-start?
                                  (into [w] old-samples)
                                  (into old-samples [w]))]
                (-> p
                    (assoc :curve new-curve-edn)
                    (assoc :width-samples new-samples)
                    (path/ensure-width-type* :point-width :compute-arc? true)))

              :t-width
              (let [old-samples (:width-samples p)
                    w           (if is-start? (first old-samples) (last old-samples))
                    new-samples (if is-start?
                                  (into [w] old-samples)
                                  (into old-samples [w]))]
                (-> p
                    (assoc :curve new-curve-edn)
                    (assoc :width-samples new-samples)
                    (assoc :t-params t-params)
                    (path/ensure-width-type* :t-width :compute-arc? true)))

              :curve
              (throw (ex-info "Curve width type not supported for extrusion"
                              {:path-id path-id :width-type width-type})))
            new-anchor     (if is-start?
                             (->Anchor path-id 0)
                             (->Anchor path-id (dec new-num-points)))
            updated-anchor (active-anchor-after-extrude anchor is-start?)]
        {:paths      (assoc paths path-id new-path)
         :anchor     updated-anchor
         :new-anchor new-anchor}))))

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
    (CurveClipper/anchorTiles (path/path->curve p)
                              (int (:point-idx anchor))
                              (int tile-size)
                              (path/max-path-half-width p))))
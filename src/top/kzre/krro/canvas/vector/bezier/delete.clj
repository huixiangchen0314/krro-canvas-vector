(ns top.kzre.krro.canvas.vector.bezier.delete
  "控制点删除。

   删除后：
     - 曲线：Bezier2D/deletePoint（中间点局部拟合，端点直接删）
     - t-params / width-samples：按被删段重映射
     - arc-params：清除

   退化场景：n = 2 删到 n = 1——Java 侧 deletePoint 拒绝，
   Clojure 侧直接构造单点 EDN（顶点 + 关联数据清理）。

   允许降到 1 个点的退化路径——数据合法，渲染层自行跳过。
   1 个点的路径不能再删。

   删除规则（依索引位置）：
     idx = 0       —— 删除首段（段 0）
     idx = n-1     —— 删除末段（段 n-2）
     0 < idx < n-1 —— 段 idx-1 与段 idx 合并（局部拟合），视作删段 idx-1

   闭合路径需先 open。"
  (:require
    [top.kzre.krro.canvas.vector.path   :as path]
    [top.kzre.krro.canvas.vector.curve  :as curve]
    [top.kzre.krro.canvas.vector.width  :as width])
  (:import
    (top.kzre.curve.bezier2d Bezier2D)
    (top.kzre.krro.canvas.vector.anchor Anchor)
    (top.kzre.krro.canvas.vector TParamsUtils TParamsUtils$DeleteResult)
    ))

;; ═══════════════════════════════════════
;; 内部：被删段索引
;; ═══════════════════════════════════════

(defn- deleted-seg-idx
  "删除锚点 idx 后被删的段索引。
   idx = 0       → 段 0
   idx = n-1     → 段 n-2
   0 < idx < n-1 → 段 idx-1"
  [idx n]
  (let [seg-count (dec n)]
    (cond
      (zero? idx)    0
      (= idx (dec n)) (dec seg-count)
      :else           (dec idx))))

;; ═══════════════════════════════════════
;; 内部：退化路径（n = 2 → n = 1）
;; ═══════════════════════════════════════

(defn- degenerate-path
  "构造退化路径——1 个控制点。

   保留未删除的那个点，手柄清零、连续性设 :none。
   清空所有派生字段（t-params / width-samples / arc-params）。"
  [p idx]
  (let [points   (get-in p [:curve :points])
        keep-idx (if (zero? idx) 1 0)
        pt       (nth points keep-idx)]
    (-> p
        (assoc-in [:curve :points]
                  [(-> pt
                       (assoc :dx1 0.0 :dy1 0.0
                              :dx2 0.0 :dy2 0.0
                              :continuity :none))])
        (assoc-in [:curve :closed] false)
        (dissoc :arc-params :t-params :width-samples))))

;; ═══════════════════════════════════════
;; 公开 API
;; ═══════════════════════════════════════

(defn delete-anchor
  "删除锚点。返回 {:paths new-paths :deleted-idx idx}。

   要求：
     - 路径类型为 :bezier
     - 路径非闭合（闭合需先 open）
     - 点数 >= 2
     - idx ∈ [0, n-1]

   删除后可以是 1 个点的退化路径——数据合法，渲染层自行跳过。"
  [paths ^Anchor anchor]
  {:pre [(map? paths) (some? anchor)]}
  (let [path-id (:path-id anchor)
        p       (get paths path-id)]
    (when-not p
      (throw (ex-info "path not found"
                      {:path-id path-id :available-ids (keys paths)})))
    (when-not (= :bezier (:path-type p))
      (throw (ex-info "delete-anchor only supports :bezier paths"
                      {:path-id path-id :path-type (:path-type p)})))
    (when (path/path-closed? p)
      (throw (ex-info "delete-anchor requires non-closed path; open first"
                      {:path-id path-id})))
    (let [n   (path/path-point-count p)
          idx (:point-idx anchor)]
      (when (< n 2)
        (throw (ex-info "cannot delete: path has fewer than 2 points"
                        {:path-id path-id :point-count n})))
      (when-not (<= 0 idx (dec n))
        (throw (ex-info "index out of range"
                        {:idx idx :point-count n})))

      (if (= n 2)
        ;; 退化——不走 Java 曲线操作，直接构造单点
        {:paths       (assoc paths path-id (degenerate-path p idx))
         :deleted-idx idx}

        ;; 常规——曲线走 Java，采样走 width / TParamsUtils
        (let [seg-count     (dec n)
              del-seg       (deleted-seg-idx idx n)
              new-jcurve    (Bezier2D/deletePoint (curve/edn->curve (:curve p)) idx)
              new-curve-edn (curve/curve->edn new-jcurve)
              width-type    (path/path-width-type p)
              old-tp        (:t-params p)
              old-samples   (:width-samples p)

              ;; t-params 重映射（Java）+ 保留索引
              ^TParamsUtils$DeleteResult dr (when (seq old-tp)
                                 (TParamsUtils/deleteSegmentAt
                                   (double-array old-tp) seg-count del-seg))

              new-tp        (when dr (vec (.-tParams dr)))
              kept-idx      (when dr (.-keptIdx dr))

              new-path
              (case width-type
                :fixed
                (-> p
                    (assoc :curve new-curve-edn)
                    (dissoc :arc-params))

                :point-width
                (let [new-samples (width/delete-sample :point-width old-samples idx)]
                  (-> p
                      (assoc :curve new-curve-edn)
                      (assoc :width-samples new-samples)
                      (dissoc :arc-params)))

                :t-width
                (let [new-samples (when (and (seq old-samples) kept-idx)
                                    (mapv #(nth old-samples %) (seq kept-idx)))]
                  (cond-> (-> p
                              (assoc :curve new-curve-edn)
                              (dissoc :arc-params))
                          (some? new-tp)      (assoc :t-params new-tp)
                          (some? new-samples) (assoc :width-samples new-samples)))

                :curve
                (throw (ex-info "Curve width type not supported for deletion"
                                {:path-id path-id :width-type width-type})))]

          {:paths       (assoc paths path-id new-path)
           :deleted-idx idx})))))
(ns top.kzre.krro.canvas.vector.bezier.closure
  "闭合 / 断开曲线。

   最小惊讶原则下的语义：

   close —— 只设 :closed true，新增首尾段（用首尾现有手柄）。
            点数不变，段数 +1。不动已有点位置。
            首尾手柄通常为 0 → 新段是直线。

   open  —— 只设 :closed false，删除首尾段。
            点数不变，段数 -1。

   对称且可逆：close 后 open 回到原状态。

   采样维护：
     - :fixed / :point-width —— 不动（点数不变）
     - :t-width               —— close 插段，open 删段（丢弃首尾段内采样）
     - :arc-params            —— 都清

   不允许：
     - close 对已闭合曲线
     - open 对已开曲线
     - close 前点数 < 3（闭合曲线最少 3 点）"
  (:require
    [top.kzre.krro.canvas.vector.path   :as path])
  (:import
    (top.kzre.krro.canvas.vector TParamsUtils)
    (top.kzre.krro.canvas.vector.TParamsUtils DeleteResult)))

;; ═══════════════════════════════════════
;; 内部：t-params 维护
;; ═══════════════════════════════════════

(defn- close-t-params
  "close 时 :t-width 的 t-params 维护——末尾插段。
   old-tp   —— 旧 t-params
   old-seg  —— 旧段数（= 点数 - 1）
   返回新 t-params 或 nil。"
  [old-tp old-seg]
  (when (seq old-tp)
    (vec (TParamsUtils/insertSegmentAt
           (double-array old-tp) old-seg old-seg))))

(defn- open-t-params
  "open 时 :t-width 的 t-params 维护——删除末段（首尾段）。
   old-tp    —— 旧 t-params
   old-seg   —— 旧段数（= 点数，因为是闭合曲线）
   返回 {:t-params [...] :kept-idx [...]} 或 {:t-params nil :kept-idx nil}。"
  [old-tp old-seg]
  (if (seq old-tp)
    (let [^DeleteResult dr (TParamsUtils/deleteSegmentAt
                             (double-array old-tp) old-seg (dec old-seg))]
      {:t-params (vec (.-tParams dr))
       :kept-idx (.-keptIdx dr)})
    {:t-params nil :kept-idx nil}))

;; ═══════════════════════════════════════
;; 公开 API
;; ═══════════════════════════════════════

(defn close-path
  "闭合曲线。返回新 path。

   要求：
     - 路径类型为 :bezier
     - 路径当前非闭合
     - 点数 >= 3

   行为：设 :closed true，新增首尾段（用首尾现有手柄）。
   :t-width 的 t-params 末尾插段；:point-width / :fixed 不动。"
  [path]
  {:pre [(map? path)]}
  (when-not (= :bezier (:path-type path))
    (throw (ex-info "close-path only supports :bezier paths"
                    {:path-type (:path-type path)})))
  (when (path/path-closed? path)
    (throw (ex-info "close-path requires non-closed path"
                    {:path-id (:path-id path)})))
  (let [n (path/path-point-count path)]
    (when (< n 3)
      (throw (ex-info "close-path requires at least 3 points"
                      {:path-id (:path-id path) :point-count n})))
    (let [width-type (path/path-width-type path)
          old-seg    (dec n)   ; 非闭合曲线的段数
          new-tp     (when (= :t-width width-type)
                       (close-t-params (:t-params path) old-seg))]
      (cond-> (-> path
                  (assoc-in [:curve :closed] true)
                  (dissoc :arc-params))
              (some? new-tp) (assoc :t-params new-tp)))))

(defn open-path
  "断开闭合曲线。返回新 path。

   要求：
     - 路径类型为 :bezier
     - 路径当前闭合

   行为：设 :closed false，删除首尾段。
   :t-width 的 t-params 删末段（丢弃首尾段内采样）；
   :point-width / :fixed 不动。"
  [path]
  {:pre [(map? path)]}
  (when-not (= :bezier (:path-type path))
    (throw (ex-info "open-path only supports :bezier paths"
                    {:path-type (:path-type path)})))
  (when-not (path/path-closed? path)
    (throw (ex-info "open-path requires closed path"
                    {:path-id (:path-id path)})))
  (let [n          (path/path-point-count path)
        width-type (path/path-width-type path)
        old-seg    n   ; 闭合曲线的段数 = 点数
        old-tp     (:t-params path)
        old-samples (:width-samples path)

        {:keys [t-params kept-idx]}
        (when (= :t-width width-type)
          (open-t-params old-tp old-seg))

        new-samples (when (and (seq old-samples) (seq kept-idx))
                      (mapv #(nth old-samples %) kept-idx))]

    (cond-> (-> path
                (assoc-in [:curve :closed] false)
                (dissoc :arc-params))
            (some? t-params)    (assoc :t-params t-params)
            (some? new-samples) (assoc :width-samples new-samples))))
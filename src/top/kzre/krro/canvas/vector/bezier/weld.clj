(ns top.kzre.krro.canvas.vector.bezier.weld
  "焊接两个锚点为一点。

   两种场景：
     1. 同路径相邻锚点——控制点数 -1
     2. 不同路径端点——合并为一条路径（保留 anchor-active 所在路径的 :path-id）

   焊接后：
     - 位置、连续性取 anchor-active
     - 入切取前点的入切，出切取后点的出切
     - 两点之间的手柄（前点的出切、后点的入切）消失
     - t-params / width-samples 按被删段重映射
     - arc-params 清除

   限制：
     - 同路径 weld 要求两锚点索引相邻（普通相邻或闭合首尾相邻）
     - 闭合曲线 weld 后至少剩 3 个点（2 段）——不满足由 Java 抛异常
     - 跨路径 weld 要求两锚点分别是各自路径的端点，且两条路径均非闭合

   返回 {:paths new-paths :weld-anchor new-anchor}。"
  (:require
    [top.kzre.krro.canvas.vector.path            :as path]
    [top.kzre.krro.canvas.vector.curve           :as curve]
    [top.kzre.krro.canvas.vector.anchor          :as anchor]
    [top.kzre.krro.canvas.vector.width           :as width]
    [top.kzre.krro.canvas.vector.bezier.reverse  :as bezier.reverse])
  (:import
    (top.kzre.curve.bezier2d Bezier2D)
    (top.kzre.krro.canvas.vector.anchor Anchor)
    (top.kzre.krro.canvas.vector TParamsUtils TParamsUtils$DeleteResult TParamsUtils$JoinResult)
    ))

;; ═══════════════════════════════════════
;; 内部：索引计算
;; ═══════════════════════════════════════

(defn- welded-seg-idx
  "weld 后被消除的段索引。

   普通相邻 (i, i+1) —— 段 i（连接 i 与 i+1）
   闭合首尾 (0, n-1) —— 段 n-1（连接 n-1 与 0）"
  [ia ip n closed?]
  (if (and closed? (= (Math/abs (- (long ia) (long ip))) (dec n)))
    (dec n)
    (min ia ip)))

(defn- merged-point-idx
  "weld 后合并点在数组中的索引。

   若被动点在主动点之前（ip < ia），删除被动点后主动点索引 -1。
   否则主动点位置不变。"
  [ia ip]
  (if (< ip ia) (dec ia) ia))

;; ═══════════════════════════════════════
;; 内部：同路径 weld 的采样维护
;; ═══════════════════════════════════════

(defn- weld-same-path-samples
  "同路径 weld 后的采样维护。
   del-seg  —— 被删段索引
   del-idx  —— 被删采样的锚点索引（被动点）
   返回 {:t-params :width-samples}，两者都可为 nil。"
  [width-type old-tp old-samples seg-count del-seg del-idx]
  (case width-type
    :fixed       {:t-params nil :width-samples nil}

    :point-width {:t-params     nil
                  :width-samples (width/delete-sample :point-width
                                                      old-samples del-idx)}

    :t-width     (let [^TParamsUtils$DeleteResult dr (when (seq old-tp)
                                          (TParamsUtils/deleteSegmentAt
                                            (double-array old-tp) seg-count del-seg))
                       new-tp   (when dr (vec (.-tParams dr)))
                       kept-idx (when dr (.-keptIdx dr))
                       new-ws   (when (and (seq old-samples) kept-idx)
                                  (mapv #(nth old-samples %) (seq kept-idx)))]
                   {:t-params new-tp :width-samples new-ws})

    :curve       (throw (ex-info "Curve width type not supported for weld"
                                 {:width-type width-type}))))

;; ═══════════════════════════════════════
;; 内部：同路径相邻 weld
;; ═══════════════════════════════════════

(defn- weld-same-path
  [paths ^Anchor anchor-active ^Anchor anchor-passive]
  (let [path-id   (:path-id anchor-active)
        p         (get paths path-id)
        ia        (:point-idx anchor-active)
        ip        (:point-idx anchor-passive)
        n         (path/path-point-count p)
        closed?   (path/path-closed? p)
        seg-count (dec n)

        ;; 索引计算
        del-seg   (welded-seg-idx ia ip n closed?)
        new-idx   (merged-point-idx ia ip)

        ;; 曲线：Java 侧含全部合法性检查
        new-jcurve    (Bezier2D/weldAdjacent
                        (curve/edn->curve (:curve p)) ia ip)
        new-curve-edn (curve/curve->edn new-jcurve)

        width-type    (path/path-width-type p)
        {:keys [t-params width-samples]}
        (weld-same-path-samples width-type
                                (:t-params p) (:width-samples p)
                                seg-count del-seg ip)

        new-path (cond-> (-> p
                             (assoc :curve new-curve-edn)
                             (dissoc :arc-params))
                         (some? t-params)      (assoc :t-params t-params)
                         (some? width-samples) (assoc :width-samples width-samples))]

    {:paths       (assoc paths path-id new-path)
     :weld-anchor (anchor/->Anchor path-id new-idx)}))

;; ═══════════════════════════════════════
;; 内部：定向——把指定锚点转到目标端
;; ═══════════════════════════════════════

(defn- orient-path
  "调整路径方向，使 anchor 位于指定端。
   target-end —— :head 或 :tail。
   若已在目标端，返回原 path；否则 reverse。"
  [p ^Anchor anchor target-end]
  (let [idx     (:point-idx anchor)
        n       (path/path-point-count p)
        is-head? (zero? idx)
        is-tail? (= idx (dec n))]
    (case target-end
      :head (if is-head? p (bezier.reverse/reverse-path p))
      :tail (if is-tail? p (bezier.reverse/reverse-path p)))))

;; ═══════════════════════════════════════
;; 内部：跨路径端点 weld
;; ═══════════════════════════════════════

(defn- weld-cross-path
  [paths ^Anchor anchor-active ^Anchor anchor-passive]
  (let [aid (:path-id anchor-active)
        pid (:path-id anchor-passive)
        pa  (get paths aid)
        pb  (get paths pid)]

    (when-not pa (throw (ex-info "path not found" {:path-id aid})))
    (when-not pb (throw (ex-info "path not found" {:path-id pid})))

    (doseq [[id p] [[aid pa] [pid pb]]]
      (when-not (= :bezier (:path-type p))
        (throw (ex-info "weld requires :bezier paths" {:path-id id})))
      (when (path/path-closed? p)
        (throw (ex-info "cross-path weld requires non-closed paths"
                        {:path-id id}))))

    (when-not (anchor/end-anchor? paths anchor-active)
      (throw (ex-info "anchor-active must be endpoint of its path"
                      {:path-id aid :idx (:point-idx anchor-active)})))
    (when-not (anchor/end-anchor? paths anchor-passive)
      (throw (ex-info "anchor-passive must be endpoint of its path"
                      {:path-id pid :idx (:point-idx anchor-passive)})))

    (let [left      (orient-path pa anchor-active :tail)    ; active 在左末位
          right     (orient-path pb anchor-passive :head)   ; passive 在右首位
          left-n    (path/path-point-count left)
          right-n   (path/path-point-count right)
          left-seg  (dec left-n)
          right-seg (dec right-n)

          wt-l (path/path-width-type left)
          wt-r (path/path-width-type right)
          _    (when (not= wt-l wt-r)
                 (throw (ex-info "width types mismatch"
                                 {:left wt-l :right wt-r})))

          left-tp  (:t-params left)
          right-tp (:t-params right)
          left-ws  (:width-samples left)
          right-ws (:width-samples right)

          _ (when (not= (boolean (seq left-tp)) (boolean (seq right-tp)))
              (throw (ex-info "t-params presence mismatch" {})))
          _ (when (not= (boolean (seq left-ws)) (boolean (seq right-ws)))
              (throw (ex-info "width-samples presence mismatch" {})))

          new-jcurve    (Bezier2D/weldJoin
                          (curve/edn->curve (:curve left))
                          (curve/edn->curve (:curve right))
                          true)   ; active 是左末点
          new-curve-edn (curve/curve->edn new-jcurve)

          ^TParamsUtils$JoinResult jr (when (and (seq left-tp) (seq right-tp))
                           (TParamsUtils/join
                             (double-array left-tp) left-seg
                             (double-array right-tp) right-seg))
          new-tp         (when jr (vec (.-tParams jr)))
          new-ws         (when (and (seq left-ws) (seq right-ws))
                           (width/join-samples wt-l left-ws right-ws jr))

          new-path (cond-> (-> left
                               (assoc :curve new-curve-edn)
                               (dissoc :arc-params))
                           (some? new-tp) (assoc :t-params new-tp)
                           (some? new-ws) (assoc :width-samples new-ws))]

      {:paths       (-> paths
                        (assoc aid new-path)
                        (dissoc pid))
       :weld-anchor (anchor/->Anchor aid (dec left-n))})))

;; ═══════════════════════════════════════
;; 公开 API
;; ═══════════════════════════════════════

(defn weld-anchors
  "焊接两个锚点。返回 {:paths new-paths :weld-anchor new-anchor}。

   anchor-active  —— 主动锚点（位置、连续性以它为准）
   anchor-passive —— 被动锚点（被合并掉）

   同路径：两锚点必须相邻（普通相邻或闭合首尾相邻）。
   跨路径：两锚点必须分别是各自路径的端点，两条路径均非闭合。"
  [paths ^Anchor anchor-active ^Anchor anchor-passive]
  {:pre [(map? paths)
         (some? anchor-active)
         (some? anchor-passive)]}
  (let [aid (:path-id anchor-active)
        pid (:path-id anchor-passive)]
    (if (= aid pid)
      (weld-same-path paths anchor-active anchor-passive)
      (weld-cross-path paths anchor-active anchor-passive))))
(ns top.kzre.krro.canvas.vector.bezier.join
  "合并两条曲线为一条。

   连接点位置取两端锚点的中点；切线由两条曲线在连接端的一阶导数推导，
   保证连接点 C1 连续。两条曲线必须都是 :bezier 且非闭合。

   t-params / width-samples 按采样位置合并——连接点采样只保留一份，
   宽度取左右平均。arc-params 清除。"
  (:require
   [top.kzre.krro.canvas.vector.curve :as curve]
   [top.kzre.krro.canvas.vector.path  :as path]
   [top.kzre.krro.canvas.vector.width :as width])
  (:import
   (top.kzre.curve.bezier2d Bezier2D)
   (top.kzre.krro.canvas.vector TParamsUtils TParamsUtils$JoinResult)))

;; ═══════════════════════════════════════
;; 内部：t-params / width-samples 合并
;; ═══════════════════════════════════════

(defn- join-t-params
  "合并左右曲线的 t-params。返回 JoinResult，无输入返回 nil。"
  [left-tp left-seg right-tp right-seg]
  (when (and (seq left-tp) (seq right-tp))
    (TParamsUtils/join (double-array left-tp) left-seg
                       (double-array right-tp) right-seg)))

(defn- join-width-samples
  "合并 width-samples。返回合并后的 vector 或 nil。

   :fixed       —— nil
   :point-width —— 左末 / 右首是连接点；其余按索引保留；连接点宽度取左右平均
   :t-width     —— 按 JoinResult 的索引映射切分；连接点宽度取左右平均"
  [width-type left-samples right-samples jr]
  (case width-type
    :fixed       nil

    :point-width (let [l-keep (vec (butlast left-samples))   ; 左末 = 连接点
                       r-keep (vec (rest    right-samples))  ; 右首 = 连接点
                       w-l    (last  left-samples)
                       w-r    (first right-samples)
                       w-conn (/ (+ (double w-l) (double w-r)) 2.0)]
                   (vec (concat l-keep [w-conn] r-keep)))

    :t-width     (let [l-idx  (seq (.-leftKeepIdx  jr))
                       r-idx  (seq (.-rightKeepIdx jr))
                       l-cidx (.-leftConnectorIdx  jr)
                       r-cidx (.-rightConnectorIdx jr)
                       ;; 除连接点外的左侧采样
                       l-keep (->> l-idx
                                   (remove #(= % l-cidx))
                                   (mapv #(nth left-samples %)))
                       ;; 全部右侧保留采样
                       r-keep (mapv #(nth right-samples %) r-idx)
                       ;; 连接点：左右取平均
                       w-l    (nth left-samples  l-cidx)
                       w-r    (nth right-samples r-cidx)
                       w-conn (/ (+ (double w-l) (double w-r)) 2.0)]
                   (vec (concat l-keep [w-conn] r-keep)))))

;; ═══════════════════════════════════════
;; 公开 API
;; ═══════════════════════════════════════

(defn join-paths
  "合并 left 和 right 为一条曲线。

   要求：
     - 两者均为 :bezier 类型
     - 两者均非闭合
     - 两者宽度类型一致
     - t-params / width-samples 存在性一致（都有或都没有）

   返回新 path——从 left 派生（保留 :path-id / :style 等字段）。
   arc-params 清除。"
  [left right]
  {:pre [(map? left) (map? right)]}
  (doseq [p [left right]]
    (when-not (= :bezier (:path-type p))
      (throw (ex-info "join-paths only supports :bezier paths"
                      {:path-type (:path-type p)})))
    (when (path/path-closed? p)
      (throw (ex-info "join-paths requires non-closed paths"
                      {:path-id (:path-id p)}))))

  (let [nL             (path/path-point-count left)
        nR             (path/path-point-count right)
        left-seg       (dec nL)
        right-seg      (dec nR)

        width-type-l   (path/path-width-type left)
        width-type-r   (path/path-width-type right)
        _              (when (not= width-type-l width-type-r)
                         (throw (ex-info "join-paths requires matching width types"
                                         {:left  width-type-l
                                          :right width-type-r})))

        left-tp        (:t-params left)
        right-tp       (:t-params right)
        left-samples   (:width-samples left)
        right-samples  (:width-samples right)

        _              (when (not= (boolean (seq left-tp))
                                   (boolean (seq right-tp)))
                         (throw (ex-info "join-paths: t-params presence mismatch"
                                         {:left  (some? (seq left-tp))
                                          :right (some? (seq right-tp))})))
        _              (when (not= (boolean (seq left-samples))
                                   (boolean (seq right-samples)))
                         (throw (ex-info "join-paths: width-samples presence mismatch"
                                         {:left  (some? (seq left-samples))
                                          :right (some? (seq right-samples))})))

        joined-curve   (Bezier2D/join (curve/edn->curve (:curve left))
                                      (curve/edn->curve (:curve right)))

        ;; t-params 合并结果（一次计算，索引共用）
        jr             (join-t-params left-tp left-seg right-tp right-seg)
        new-tp         (when jr
                         (vec (.-tParams ^TParamsUtils$JoinResult jr)))

        new-samples    (when (and (seq left-samples) (seq right-samples))
                         (width/join-samples width-type-l
                                             left-samples right-samples jr))]

    (cond-> (-> left
                (assoc :curve (curve/curve->edn joined-curve))
                (dissoc :arc-params))
            (some? new-tp)      (assoc :t-params new-tp)
            (some? new-samples) (assoc :width-samples new-samples))))
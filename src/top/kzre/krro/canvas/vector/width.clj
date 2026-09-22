(ns top.kzre.krro.canvas.vector.width
  "宽度采样（:width-samples）的编辑工具。

   与曲线类型无关——`split` / `join` / `cut` / `delete` / `insert` 等
   拓扑编辑操作共用这些函数维护宽度采样的数据结构不变量。

   ## 三种宽度类型的采样语义

   | 类型 | 采样数 | 采样位置 |
   |---|---|---|
   | `:fixed`       | 无 | —— |
   | `:point-width` | = 控制点数 | 与控制点一一对应 |
   | `:t-width`     | 独立 | 按 t-params 位置分布 |

   ## 索引分组

   `:t-width` 的采样需要按 t-params 的归属切分 / 合并。为避免重复计算
   `TParamsUtils` 的判定结果，本 ns 的所有切分 / 合并函数都接收调用方
   预先算好的 `SplitResult` / `JoinResult`。

   ## 连接点处理

   切分 / 合并时连接点采样只保留一份——切分时两侧各留一份，
   合并时丢弃右侧首份。连接点宽度由调用方决定（当前取左右平均）。"
  (:require [top.kzre.krro.canvas.vector.curve :as curve])
  (:import
    (top.kzre.curve.bezier2d ArcLengthUtils)
    (top.kzre.krro.canvas.vector TParamsUtils TParamsUtils$JoinResult TParamsUtils$SplitResult)
    ))

;; ═══════════════════════════════════════
;; 挤出
;; ═══════════════════════════════════════

(defn extrude-sample
  "挤出端点时在头部或尾部添加宽度采样。返回新 samples（或原 samples）。

   :fixed       —— 不变（无采样）
   :point-width —— 头部：在首部插入 w；尾部：在末尾追加 w
   :t-width     —— 与 :point-width 相同——extrude 新增控制点，
                   该端点需要对应的宽度采样；t-params 由 TParamsUtils
                   的 extrudeHead/Tail 同步加一个位置。

   w 为新采样值，由调用方提供（通常复制相邻端点采样值）。"
  [width-type samples w is-start?]
  (case width-type
    :fixed                samples
    (:point-width :t-width) (when (seq samples)
                              (if is-start?
                                (vec (cons (double w) samples))
                                (conj (vec samples) (double w))))))

;; ═══════════════════════════════════════
;; 切分
;; ═══════════════════════════════════════

(defn split-samples
  "按锚点 idx 切分 width-samples。返回 [left-samples right-samples]。

   sr —— :t-width 时非 nil（由调用方预先计算的 SplitResult）；
         :fixed / :point-width 时忽略（可传 nil）。

   :fixed       —— [nil nil]（无采样）
   :point-width —— 按控制点索引切；连接点两侧各保留一份
                   left = [0..idx]，right = [idx..n-1]
   :t-width     —— 按 sr 的索引分组切；连接点两侧各保留一份"
  [width-type samples ^TParamsUtils$SplitResult sr idx]
  (case width-type
    :fixed       [nil nil]

    :point-width [(vec (subvec samples 0 (inc idx)))
                  (vec (subvec samples idx))]

    :t-width     (let [l-idx (.-leftIdx  sr)
                       r-idx (.-rightIdx sr)
                       l-ws  (mapv #(nth samples %) l-idx)
                       r-ws  (mapv #(nth samples %) r-idx)]
                   [l-ws r-ws])))

;; ═══════════════════════════════════════
;; 合并
;; ═══════════════════════════════════════

(defn- connector-avg
  "连接点宽度：左右平均。"
  [w-l w-r]
  (/ (+ (double w-l) (double w-r)) 2.0))

(defn join-samples
  "合并左右曲线的 width-samples。返回合并后的 vector 或 nil。

   jr —— :t-width 时非 nil（由调用方预先计算的 JoinResult）；
         :fixed / :point-width 时忽略（可传 nil）。

   :fixed       —— nil（无采样）
   :point-width —— 左末 / 右首是连接点；其余按索引保留；连接点宽度取左右平均
   :t-width     —— 按 jr 的索引映射合并；连接点宽度取左右平均"
  [width-type left-samples right-samples ^TParamsUtils$JoinResult jr]
  (case width-type
    :fixed       nil

    :point-width (let [l-keep (vec (butlast left-samples))    ; 左末 = 连接点
                       r-keep (vec (rest    right-samples))   ; 右首 = 连接点
                       w-l    (last  left-samples)
                       w-r    (first right-samples)]
                   (vec (concat l-keep [(connector-avg w-l w-r)] r-keep)))

    :t-width     (let [l-idx  (seq (.-leftKeepIdx  jr))
                       r-idx  (seq (.-rightKeepIdx jr))
                       l-cidx (.-leftConnectorIdx  jr)
                       r-cidx (.-rightConnectorIdx jr)
                       l-keep (->> l-idx
                                   (remove #(= % l-cidx))
                                   (mapv #(nth left-samples %)))
                       r-keep (mapv #(nth right-samples %) r-idx)
                       w-l    (nth left-samples  l-cidx)
                       w-r    (nth right-samples r-cidx)]
                   (vec (concat l-keep [(connector-avg w-l w-r)] r-keep)))))

;; ═══════════════════════════════════════
;; 插入
;; ═══════════════════════════════════════

(defn insert-sample
  "在锚点索引 idx 处插入宽度采样值 w。返回新 samples（或原 samples）。

   :fixed       —— samples 本身为 nil，返回 nil
   :point-width —— 在 idx 处插入 w；长度 +1
   :t-width     —— 采样独立于控制点数，返回原 samples 不变"
  [width-type samples idx w]
  (case width-type
    :fixed       samples

    :point-width (when (seq samples)
                   (vec (concat (subvec samples 0 idx)
                                [(double w)]
                                (subvec samples idx))))

    :t-width     samples))


;; ═══════════════════════════════════════
;; 删除
;; ═══════════════════════════════════════

(defn delete-sample
  "删除锚点索引 idx 处的宽度采样。返回新 samples（或原 samples）。

   :fixed       —— 无操作
   :point-width —— 删除 idx 处的采样；长度 -1
   :t-width     —— 采样独立于控制点数，返回原 samples 不变"
  [width-type samples idx]
  (case width-type
    :fixed       samples

    :point-width (when (seq samples)
                   (vec (concat (subvec samples 0 idx)
                                (subvec samples (inc idx)))))

    :t-width     samples))


(defn reverse-samples
  "反转 width-samples 顺序。

   :fixed       —— 返回原值（无采样）
   :point-width —— 反转（采样跟随控制点顺序）
   :t-width     —— 反转（采样跟随 t-params 的反转顺序）"
  [width-type samples]
  (if (= :fixed width-type)
    samples
    (when (seq samples)
      (vec (reverse samples)))))

;; ═══════════════════════════════════════
;; 内部：按参数位置在采样数组上插值
;; ═══════════════════════════════════════

(defn- sample-at-t
  "在 old-samples 上按参数位置 t ∈ [0,1] 线性插值。
   old-samples[i] 对应参数位置 i/(n-1)。"
  ^double [samples ^double t]
  (let [n   (count samples)
        pos (* t (dec n))
        lo  (int (Math/floor pos))
        hi  (min (dec n) (inc lo))
        frac (- pos lo)]
    (if (= lo hi)
      (double (nth samples lo))
      (+ (* (- 1.0 frac) (double (nth samples lo)))
         (* frac (double (nth samples hi)))))))

;; ═══════════════════════════════════════
;; 按弧长比例重采样
;; ═══════════════════════════════════════

(defn resample-by-arc
  "按弧长比例重采样采样数组。

   新采样点在新曲线上均匀分布（参数位置 j/(new-count-1)），
   每个点对应一个弧长比例；在旧曲线上找相同弧长比例的位置，
   在旧采样值上按该位置插值。

   old-samples     —— 旧采样数组
   old-curve-edn   —— 旧曲线 EDN
   new-curve-edn   —— 新曲线 EDN
   new-count       —— 新采样数

   空输入返回原值。"
  [old-samples old-curve-edn new-curve-edn new-count]
  (if (empty? old-samples)
    old-samples
    (let [j-old   (curve/edn->curve old-curve-edn)
          j-new   (curve/edn->curve new-curve-edn)
          old-map (ArcLengthUtils/sample j-old 200)
          new-map (ArcLengthUtils/sample j-new 200)
          old-max (.getMaxS old-map)
          new-max (.getMaxS new-map)]
      (mapv (fn [j]
              (let [new-t  (/ (double j) (double (dec new-count)))
                    s-new  (.getS new-map new-t)
                    s-norm (if (> new-max 1e-12) (/ s-new new-max) new-t)
                    s-old  (* s-norm old-max)
                    old-t  (if (> old-max 1e-12) (.getT old-map s-old) s-norm)]
                (sample-at-t old-samples old-t)))
            (range new-count)))))


;; ═══════════════════════════════════════
;; 重构
;; ═══════════════════════════════════════

(defn resample-samples
  "曲线形状变化后重采样采样数组。返回 {:width-samples ... :t-params ...}。

   nil 表示「不修改该字段」。

   :fixed       —— 清空 width-samples / t-params
   :point-width —— width-samples 按弧长重采样到 new-point-count
   :t-width     —— width-samples 保留；t-params 按弧长比重映射

   old-curve-edn / new-curve-edn 用于弧长计算。
   new-point-count 是重采样后曲线的新控制点数（reform 时可能变化，
   fit-segment 时不变）。"
  [width-type old-samples old-tp
   old-curve-edn new-curve-edn new-point-count]
  (case width-type
    :fixed       {:width-samples nil :t-params nil}

    :point-width {:width-samples (resample-by-arc
                                   old-samples old-curve-edn new-curve-edn
                                   new-point-count)
                  :t-params      nil}

    :t-width     {:width-samples old-samples
                  :t-params      (when (seq old-tp)
                                   (let [j-old (curve/edn->curve old-curve-edn)
                                         j-new (curve/edn->curve new-curve-edn)]
                                     (vec (TParamsUtils/remapByArcLength
                                            (double-array old-tp) j-old j-new))))}

    :curve       (throw (ex-info "Curve width type not supported for resample"
                                 {:width-type width-type}))))

(defn apply-samples
  "把 reform-samples 返回的 changes 应用到 path。
   :fixed 类型下额外清空 width-samples / t-params 字段。
   —— 也可留在调用方，看是否需要统一。"
  [path width-type {:keys [width-samples t-params]}]
  (cond-> path
          (= width-type :fixed) (dissoc :width-samples :t-params)
          (some? width-samples) (assoc :width-samples width-samples)
          (some? t-params)      (assoc :t-params t-params)))

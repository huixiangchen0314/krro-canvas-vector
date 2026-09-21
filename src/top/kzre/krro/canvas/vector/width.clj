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
  (:import
    (top.kzre.krro.canvas.vector TParamsUtils)
    (top.kzre.krro.canvas.vector.TParamsUtils
      SplitResult JoinResult)))

;; ═══════════════════════════════════════
;; 挤出
;; ═══════════════════════════════════════

(defn extrude-sample
  "挤出端点时在头部或尾部添加宽度采样。返回新 samples（或原 samples）。

   :fixed       —— 不变（无采样）
   :point-width —— 头部：在首部插入 w；尾部：在末尾追加 w
   :t-width     —— 不变（采样独立于控制点数）

   w 为新采样值，由调用方提供（通常复制相邻端点采样值）。"
  [width-type samples w is-start?]
  (case width-type
    :fixed       samples

    :point-width (when (seq samples)
                   (if is-start?
                     (vec (cons (double w) samples))
                     (conj (vec samples) (double w))))

    :t-width     samples))

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
  [width-type samples ^SplitResult sr idx]
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
  [width-type left-samples right-samples ^JoinResult jr]
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
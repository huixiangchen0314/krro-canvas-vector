(ns top.kzre.krro.canvas.vector.bezier.split
  "在锚点处切分曲线为两条非闭合曲线。
   切点必须是已存在的锚点——不支持从任意 t 切分。
   若需从任意 t 切分，调用方先 insert-anchor 定位，再 split-at-anchor。

   两条曲线共享连接点锚点（对象独立），各自可独立编辑。
   t-params / width-samples 按位置切分；连接点采样两侧各保留一份。
   arc-params 一律清除。"
  (:require
    [top.kzre.krro.canvas.vector.path  :as path]
    [top.kzre.krro.canvas.vector.curve :as curve]
    [top.kzre.krro.canvas.vector.width :as width])
  (:import
    (top.kzre.curve.bezier2d Bezier2D Curve)
    (top.kzre.krro.canvas.vector TParamsUtils)
    (top.kzre.krro.canvas.vector.TParamsUtils SplitResult)))

;; ═══════════════════════════════════════
;; 内部：path 派生
;; ═══════════════════════════════════════

(defn- make-path
  "从原 path 派生新 path：替换 curve，可选替换 t-params / width-samples，清除 arc-params。"
  [src ce tp ws]
  (cond-> (-> src
              (assoc :curve ce)
              (dissoc :arc-params))
          (some? tp) (assoc :t-params tp)
          (some? ws) (assoc :width-samples ws)))

;; ═══════════════════════════════════════
;; 公开 API
;; ═══════════════════════════════════════

(defn split-at-anchor
  "在锚点 idx 处切分曲线为 [left-path right-path]。
   两条都是非闭合曲线。

   要求：
     - 路径类型为 :bezier
     - 路径非闭合
     - idx ∈ [1, n-2]（内部锚点，两侧都有段）

   返回的两条 path 都从原 path 派生——保留 :path-id / :style 等字段。"
  [path idx]
  {:pre [(map? path) (int? idx)]}
  (when-not (= :bezier (:path-type path))
    (throw (ex-info "split-at-anchor only supports :bezier paths"
                    {:path-type (:path-type path)})))
  (when (path/path-closed? path)
    (throw (ex-info "split-at-anchor requires non-closed path; open first"
                    {:path-id (:path-id path)})))
  (let [n (path/path-point-count path)]
    (when-not (<= 1 idx (dec (dec n)))
      (throw (ex-info "split index must be interior anchor"
                      {:idx idx :point-count n})))
    (let [old-seg-count (dec n)
          jcurve        (curve/edn->curve (:curve path))
          left-curve    (Curve.)
          right-curve   (Curve.)]
      (Bezier2D/split jcurve idx left-curve right-curve)

      (let [width-type  (path/path-width-type path)
            old-tp      (:t-params path)
            old-samples (:width-samples path)

            ;; 一次判定：t-params + 索引分组
            ^SplitResult sr (when (seq old-tp)
                              (TParamsUtils/splitAt
                                (double-array old-tp) old-seg-count idx))

            left-tp    (when sr (vec (.-leftTParams  sr)))
            right-tp   (when sr (vec (.-rightTParams sr)))

            [left-ws right-ws]
            (when (seq old-samples)
              (width/split-samples width-type old-samples sr idx))]

        [(make-path path (curve/curve->edn left-curve)  left-tp  left-ws)
         (make-path path (curve/curve->edn right-curve) right-tp right-ws)]))))
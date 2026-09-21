(ns top.kzre.krro.canvas.vector.bezier.fit
  "单段拟合。

   调整指定段，使其在段内参数 t 处经过目标点 (x, y)。
   保持端点位置固定，只调整内部控制点 P1 / P2。

   与 reform 的区别：
     reform —— 从点集重新构造整条（或范围）曲线——控制点数可能变
     fit    —— 单段局部调整形状——控制点数不变

   曲线：Segment.of + Segments.fit（bezier2d Java 工具）。
   采样：width/resample-samples（曲线形状变了——按弧长比例重映射）。
   arc-params 清除。

   仅支持 :bezier 路径。"
  (:require
    [top.kzre.krro.canvas.vector.path   :as path]
    [top.kzre.krro.canvas.vector.curve  :as curve]
    [top.kzre.krro.canvas.vector.width  :as width])
  (:import
    (top.kzre.curve.bezier2d Segment Segments)))

;; ═══════════════════════════════════════
;; 公开 API
;; ═══════════════════════════════════════

(defn fit-segment
  "调整段 seg-idx，使其在段内参数 local-t 处经过目标点 (x, y)。

   返回新 path。控制点数不变。
   采样按弧长比重映射；arc-params 清除。"
  [path seg-idx local-t x y]
  {:pre [(map? path) (int? seg-idx) (number? local-t)
         (number? x) (number? y)]}
  (when-not (= :bezier (:path-type path))
    (throw (ex-info "fit-segment only supports :bezier paths"
                    {:path-type (:path-type path)})))
  (let [curve-edn (:curve path)
        points    (:points curve-edn)
        n         (count points)
        seg-count (dec n)]
    (when-not (<= 0 seg-idx (dec seg-count))
      (throw (ex-info "seg-idx out of range"
                      {:seg-idx seg-idx :seg-count seg-count})))
    (when-not (<= 0.0 (double local-t) 1.0)
      (throw (ex-info "local-t out of [0, 1]"
                      {:local-t local-t})))
    (let [jc      (curve/edn->curve curve-edn)
          jpoints (.getPoints jc)
          jp1     (nth jpoints seg-idx)
          jp2     (nth jpoints (inc seg-idx))
          seg     (Segment/of jp1 jp2)
          new-seg (Segments/fit seg (double local-t) (double x) (double y))

          sa (.getA new-seg)
          sb (.getB new-seg)
          sc (.getC new-seg)
          sd (.getD new-seg)

          new-p1 (-> (nth points seg-idx)
                     (assoc :x   (.getX sa)
                            :y   (.getY sa)
                            :dx2 (- (.getX sb) (.getX sa))
                            :dy2 (- (.getY sb) (.getY sa))))
          new-p2 (-> (nth points (inc seg-idx))
                     (assoc :x   (.getX sd)
                            :y   (.getY sd)
                            :dx1 (- (.getX sc) (.getX sd))
                            :dy1 (- (.getY sc) (.getY sd))))

          new-points    (-> points
                            (assoc seg-idx new-p1)
                            (assoc (inc seg-idx) new-p2))
          new-curve-edn (assoc curve-edn :points new-points)

          width-type    (path/path-width-type path)
          changes       (width/resample-samples width-type
                                                (:width-samples path) (:t-params path)
                                                curve-edn new-curve-edn
                                                n)]   ; 点数不变

      (-> path
          (assoc :curve new-curve-edn)
          (dissoc :arc-params)
          (width/apply-samples width-type changes)))))
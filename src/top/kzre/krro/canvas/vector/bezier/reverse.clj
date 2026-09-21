(ns top.kzre.krro.canvas.vector.bezier.reverse
  "曲线反向。

   几何形状不变，参数方向反转——原 t=0 对应新 t=1。
   控制点顺序反转、入/出手柄互换（由 Java Bezier2D/reverse 处理）。

   采样维护：
     - :t-params      —— 反转 + 每个值 t → 1-t（Java TParamsUtils/reverse）
     - :width-samples —— 反转（采样值跟随控制点顺序）
     - :arc-params    —— 清除（采样顺序变化，缓存失效）"
  (:require
    [top.kzre.krro.canvas.vector.path   :as path]
    [top.kzre.krro.canvas.vector.curve  :as curve]
    [top.kzre.krro.canvas.vector.width  :as width])
  (:import
    (top.kzre.curve.bezier2d Bezier2D)
    (top.kzre.krro.canvas.vector TParamsUtils)))

(defn reverse-path
  "反向曲线。返回新 path。

   要求：
     - 路径类型为 :bezier

   :t-params 反转映射；:width-samples 反转；:arc-params 清除。"
  [path]
  {:pre [(map? path)]}
  (when-not (= :bezier (:path-type path))
    (throw (ex-info "reverse-path only supports :bezier paths"
                    {:path-type (:path-type path)})))
  (let [reversed-curve (Bezier2D/reverse (curve/edn->curve (:curve path)))
        width-type     (path/path-width-type path)
        old-tp         (:t-params path)
        old-samples    (:width-samples path)

        new-tp         (when (seq old-tp)
                         (vec (TParamsUtils/reverse (double-array old-tp))))
        new-samples    (width/reverse-samples width-type old-samples)]

    (cond-> (-> path
                (assoc :curve (curve/curve->edn reversed-curve))
                (dissoc :arc-params))
            (some? new-tp)      (assoc :t-params new-tp)
            (some? new-samples) (assoc :width-samples new-samples))))
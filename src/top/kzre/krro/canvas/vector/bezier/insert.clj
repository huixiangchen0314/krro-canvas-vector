(ns top.kzre.krro.canvas.vector.bezier.insert
  "在路径上插入锚点。改变控制点数量——
   维护 t-params / width-samples / arc-params。
   仅支持 :bezier 路径——catmull-rom 需外部先转成 bezier。"
  (:require
    [top.kzre.krro.canvas.vector.path   :as path]
    [top.kzre.krro.canvas.vector.curve  :as curve]
    [top.kzre.krro.canvas.vector.anchor :as anchor])
  (:import
    (top.kzre.curve.bezier2d Bezier2D)
    (top.kzre.krro.canvas.vector TParamsUtils)))

(defn insert-anchor
  "在指定全局归一化参数 t 处插入锚点。
   新锚点位于 t 所在段内，索引 = 该段索引 + 1。

   返回 {:paths new-paths :anchor new-anchor}。

   paths 中不存在该 path-id 时抛异常。
   路径类型不是 :bezier 时抛异常——catmull-rom 需外部先转。
   t 越界由 Java 抛异常。"
  [paths path-id t]
  {:pre [(map? paths) (some? path-id) (number? t)]}
  (let [p (get paths path-id)]
    (when-not p
      (throw (ex-info "path not found"
                      {:path-id path-id :available-ids (keys paths)})))
    (when-not (= :bezier (:path-type p))
      (throw (ex-info "insert-anchor only supports :bezier paths"
                      {:path-id path-id :path-type (:path-type p)})))
    (let [curve-edn     (:curve p)
          old-seg-count (dec (count (:points curve-edn)))
          old-point-n   (inc old-seg-count)
          jcurve        (curve/edn->curve curve-edn)
          seg-idx       (Bezier2D/segmentIndex jcurve (double t))
          new-idx       (inc seg-idx)
          new-jcurve    (Bezier2D/insertPoint jcurve (double t))
          new-curve-edn (curve/curve->edn new-jcurve)
          width-type    (path/path-width-type p)
          new-path
          (case width-type
            :fixed
            (-> p
                (assoc :curve new-curve-edn)
                (dissoc :arc-params))

            :point-width
            (let [old-samples (:width-samples p)
                  w (cond
                      (zero? new-idx)                 (double (first old-samples))
                      (= new-idx (count old-samples)) (double (last old-samples))
                      :else                           (/ (+ (double (nth old-samples (dec new-idx)))
                                                            (double (nth old-samples new-idx)))
                                                         2.0))
                  new-samples (vec (concat (subvec old-samples 0 new-idx)
                                           [w]
                                           (subvec old-samples new-idx)))]
              (-> p
                  (assoc :curve new-curve-edn)
                  (assoc :width-samples new-samples)
                  (path/ensure-width-type* :point-width :compute-arc? true)))

            :t-width
            (let [old-tp (or (:t-params p)
                             (path/uniform-t-params old-point-n))
                  new-tp (vec (TParamsUtils/insertSegmentAt
                                (double-array old-tp) old-seg-count seg-idx))]
              (-> p
                  (assoc :curve new-curve-edn)
                  (assoc :t-params new-tp)
                  (path/ensure-width-type* :t-width :compute-arc? true)))

            :curve
            (throw (ex-info "Curve width type not supported for insertion"
                            {:path-id path-id :width-type width-type})))]
      {:paths  (assoc paths path-id new-path)
       :anchor (anchor/->Anchor path-id new-idx)})))
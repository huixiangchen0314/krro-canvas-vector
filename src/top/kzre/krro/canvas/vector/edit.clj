(ns top.kzre.krro.canvas.vector.edit
  "路径拓扑编辑：插点、删点、挤出、焊接、切断。
   所有操作改变控制点数量——需要维护 t-params / width-samples / arc-params。"
  (:require
    [top.kzre.krro.canvas.vector.path   :as path]
    [top.kzre.krro.canvas.vector.curve  :as curve]
    [top.kzre.krro.canvas.vector.anchor :as anchor])
  (:import
    (top.kzre.curve.bezier2d
      Curve
      CurveExtrusionUtils)
    (top.kzre.krro.canvas.vector.anchor Anchor)))

;; ═══════════════════════════════════════
;; 挤出
;; ═══════════════════════════════════════

(defn- extrude-curve
  "执行曲线挤出，返回 {:curve :t-params}。"
  [^Curve old-curve old-t-params point is-start?]
  (let [new-curve    (Curve.)
        pair         (curve/edn->pair point)
        new-t-params (if is-start?
                       (CurveExtrusionUtils/extrudeHead
                         old-curve (double-array old-t-params) pair new-curve)
                       (CurveExtrusionUtils/extrudeTail
                         old-curve (double-array old-t-params) pair new-curve))]
    {:curve    new-curve
     :t-params (vec new-t-params)}))

(defn active-anchor-after-extrude
  "挤出后原锚点的新索引：起点挤出索引 0 → 1；终点挤出索引不变。"
  [^Anchor anchor is-start?]
  {:pre [(some? anchor)
         (boolean? is-start?)]}
  (if is-start?
    (anchor/->Anchor (:path-id anchor) 1)
    anchor))

(defn extrude-anchor
  "若锚点是路径端点，则在该端挤出一点。
   返回 {:paths new-paths :anchor updated-anchor :new-anchor new-anchor}。
   非端点返回 nil。仅支持 :bezier 路径。

   paths 中不存在该 path-id 时抛异常——这是数据错误，
   与「锚点非端点」的正常业务分支区分。"
  [paths ^Anchor anchor point]
  {:pre [(map? paths)
         (some? anchor)
         (map? point)
         (number? (:x point))
         (number? (:y point))]}
  (let [path-id (:path-id anchor)
        p       (get paths path-id)]
    (when-not p
      (throw (ex-info "path not found for anchor"
                      {:path-id       path-id
                       :available-ids (keys paths)})))
    (when (anchor/end-anchor? p anchor)
      (when-not (= :bezier (:path-type p))
        (throw (ex-info "extrude-anchor only supports :bezier paths"
                        {:path-id path-id :path-type (:path-type p)})))
      (let [idx            (:point-idx anchor)
            is-start?      (zero? idx)
            width-type     (path/path-width-type p)
            old-curve      (curve/edn->curve (:curve p))
            old-t-params   (or (:t-params p)
                               (path/uniform-t-params (path/path-point-count p)))
            {:keys [curve t-params]}
            (extrude-curve old-curve old-t-params point is-start?)
            new-num-points (count (.getPoints curve))
            new-curve-edn  (curve/curve->edn curve)
            new-path
            (case width-type
              :fixed
              (-> p
                  (assoc :curve new-curve-edn)
                  (dissoc :width-samples :arc-params :t-params))

              :point-width
              (let [old-samples (:width-samples p)
                    w           (if is-start? (first old-samples) (last old-samples))
                    new-samples (if is-start?
                                  (into [w] old-samples)
                                  (into old-samples [w]))]
                (-> p
                    (assoc :curve new-curve-edn)
                    (assoc :width-samples new-samples)
                    (path/ensure-width-type* :point-width :compute-arc? true)))

              :t-width
              (let [old-samples (:width-samples p)
                    w           (if is-start? (first old-samples) (last old-samples))
                    new-samples (if is-start?
                                  (into [w] old-samples)
                                  (into old-samples [w]))]
                (-> p
                    (assoc :curve new-curve-edn)
                    (assoc :width-samples new-samples)
                    (assoc :t-params t-params)
                    (path/ensure-width-type* :t-width :compute-arc? true)))

              :curve
              (throw (ex-info "Curve width type not supported for extrusion"
                              {:path-id path-id :width-type width-type})))
            new-anchor     (if is-start?
                             (anchor/->Anchor path-id 0)
                             (anchor/->Anchor path-id (dec new-num-points)))
            updated-anchor (active-anchor-after-extrude anchor is-start?)]
        {:paths      (assoc paths path-id new-path)
         :anchor     updated-anchor
         :new-anchor new-anchor}))))
(ns top.kzre.krro.canvas.vector.bezier.extrude
  "锚点挤出：在路径端点派生一个新锚点。
   改变控制点数量——维护 t-params / width-samples / arc-params。
   仅支持 :bezier 路径。"
  (:require
    [top.kzre.krro.canvas.vector.path   :as path]
    [top.kzre.krro.canvas.vector.anchor :as anchor]
    [top.kzre.krro.canvas.vector.width  :as width])
  (:import
    (top.kzre.krro.canvas.vector.anchor Anchor)
    (top.kzre.krro.canvas.vector TParamsUtils)))

;; ═══════════════════════════════════════
;; 内部：曲线拓扑（纯 EDN）
;; ═══════════════════════════════════════

(defn- extrude-curve-edn
  "在曲线 EDN 的头部/尾部插入一个新控制点。
   手柄为零，连续性 :none——新点只有一侧，不参与任何约束。"
  [curve-edn point is-start?]
  (let [new-cp {:x          (double (:x point))
                :y          (double (:y point))
                :dx1        0.0 :dy1 0.0
                :dx2        0.0 :dy2 0.0
                :continuity :none}]
    (update curve-edn :points
            (fn [pts]
              (if is-start?
                (into [new-cp] pts)
                (conj (vec pts) new-cp))))))

;; ═══════════════════════════════════════
;; 公开 API
;; ═══════════════════════════════════════

(defn extrude-anchor
  "若锚点是路径端点，则在该端挤出一点。
   返回 {:paths new-paths :anchor new-anchor}。
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
            curve-edn      (:curve p)
            old-seg-count  (dec (count (:points curve-edn)))
            new-curve-edn  (extrude-curve-edn curve-edn point is-start?)
            new-num-points (count (:points new-curve-edn))
            width-type     (path/path-width-type p)

            ;; 新采样值：取相邻端点的宽度（挤出点宽度通常与端点一致）
            old-samples    (:width-samples p)
            w              (when (seq old-samples)
                             (if is-start? (first old-samples) (last old-samples)))
            new-samples    (when (seq old-samples)
                             (width/extrude-sample width-type old-samples w is-start?))

            new-path
            (case width-type
              :fixed
              (-> p
                  (assoc :curve new-curve-edn)
                  (dissoc :width-samples :arc-params :t-params))

              :point-width
              (-> p
                  (assoc :curve new-curve-edn)
                  (assoc :width-samples new-samples)
                  (path/ensure-width-type* :point-width :compute-arc? true))

              :t-width
              (let [old-tp (or (:t-params p)
                               (path/uniform-t-params (count (:points curve-edn))))
                    new-tp (vec (if is-start?
                                  (TParamsUtils/extrudeHead
                                    (double-array old-tp) old-seg-count)
                                  (TParamsUtils/extrudeTail
                                    (double-array old-tp) old-seg-count)))]
                (-> p
                    (assoc :curve new-curve-edn)
                    (assoc :width-samples new-samples)
                    (assoc :t-params new-tp)
                    (path/ensure-width-type* :t-width :compute-arc? true)))

              :curve
              (throw (ex-info "Curve width type not supported for extrusion"
                              {:path-id path-id :width-type width-type})))
            new-anchor (if is-start?
                         (anchor/->Anchor path-id 0)
                         (anchor/->Anchor path-id (dec new-num-points)))]
        {:paths  (assoc paths path-id new-path)
         :anchor new-anchor}))))
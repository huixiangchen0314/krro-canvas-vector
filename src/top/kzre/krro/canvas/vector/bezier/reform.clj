(ns top.kzre.krro.canvas.vector.bezier.reform
  "改变曲线控制点数量。

   两种粒度：
     reform-path  —— 整条曲线重采样为 count 个控制点
     reform-range —— 段范围 [fromSeg, toSeg] 重采样为 newSegCount 段

   曲线：Bezier2D/reform 或 Bezier2D/reformRange。
   采样：width/resample-samples（按弧长比例对齐）。
   arc-params 清除。

   仅支持 :bezier 路径。"
  (:require
    [top.kzre.krro.canvas.vector.path   :as path]
    [top.kzre.krro.canvas.vector.curve  :as curve]
    [top.kzre.krro.canvas.vector.width  :as width])
  (:import
    (top.kzre.curve.bezier2d Bezier2D)))

;; ═══════════════════════════════════════
;; 公开 API
;; ═══════════════════════════════════════

(defn reform-path
  "整条曲线重采样为 count 个控制点。返回新 path。

   要求：:bezier 路径，count >= 2，count ≠ 当前点数。

   :point-width 采样按弧长重采样到 count 个。
   :t-width 采样保留，t-params 按弧长比重映射。
   :arc-params 清除。"
  [path count]
  {:pre [(map? path) (int? count) (<= 2 count)]}
  (when-not (= :bezier (:path-type path))
    (throw (ex-info "reform-path only supports :bezier paths"
                    {:path-type (:path-type path)})))
  (let [old-n (path/path-point-count path)]
    (when (= old-n count)
      (throw (ex-info "reform-path: count equals current"
                      {:current old-n})))
    (let [jcurve        (curve/edn->curve (:curve path))
          new-jcurve    (Bezier2D/reform jcurve count)
          new-curve-edn (curve/curve->edn new-jcurve)
          width-type    (path/path-width-type path)
          changes       (width/resample-samples width-type
                                                (:width-samples path) (:t-params path)
                                                (:curve path) new-curve-edn
                                                count)]
      (-> path
          (assoc :curve new-curve-edn)
          (dissoc :arc-params)
          (width/apply-samples width-type changes)))))

(defn reform-range
  "对段范围 [fromSeg, toSeg] 重采样为 newSegCount 段。返回新 path。

   要求：:bezier 路径，范围合法，newSegCount >= 1。

   范围外的锚点完全保留（位置、手柄、连续性）。
   范围内的曲线被替换为 newSegCount 段拟合结果。
   边界的两个锚点位置保留，手柄按新形状重算。
   采样迁移规则与 reform-path 一致。"
  [path from-seg to-seg new-seg-count]
  {:pre [(map? path) (int? from-seg) (int? to-seg)
         (int? new-seg-count) (<= 1 new-seg-count)]}
  (when-not (= :bezier (:path-type path))
    (throw (ex-info "reform-range only supports :bezier paths"
                    {:path-type (:path-type path)})))
  (let [old-n   (path/path-point-count path)
        old-seg (dec old-n)]
    (when-not (<= 0 from-seg to-seg (dec old-seg))
      (throw (ex-info "range out of bounds"
                      {:from-seg from-seg :to-seg to-seg :seg-count old-seg})))
    (let [jcurve        (curve/edn->curve (:curve path))
          new-jcurve    (Bezier2D/reformRange jcurve from-seg to-seg new-seg-count)
          new-curve-edn (curve/curve->edn new-jcurve)
          new-n         (count (:points new-curve-edn))
          width-type    (path/path-width-type path)
          changes       (width/resample-samples width-type
                                                (:width-samples path) (:t-params path)
                                                (:curve path) new-curve-edn
                                                new-n)]
      (-> path
          (assoc :curve new-curve-edn)
          (dissoc :arc-params)
          (width/apply-samples width-type changes)))))
(ns top.kzre.krro.canvas.vector.bezier.insert
  "在路径上插入锚点。改变控制点数量——
   维护 t-params / width-samples / arc-params。
   仅支持 :bezier 路径——catmull-rom 需外部先转成 bezier。"
  (:require
    [top.kzre.krro.canvas.vector.path   :as path]
    [top.kzre.krro.canvas.vector.curve  :as curve]
    [top.kzre.krro.canvas.vector.anchor :as anchor]
    [top.kzre.krro.canvas.vector.width  :as width])
  (:import
    (top.kzre.curve.bezier2d ArcLengthUtils Bezier2D Curve)
    (top.kzre.krro.canvas.vector TParamsUtils)))

;; ═══════════════════════════════════════
;; 内部：弧长比
;; ═══════════════════════════════════════

(def ^:private ^:const arc-subdiv
  "Simpson 积分的分段数——8 段对大多数曲线误差 < 1e-6。"
  8)

(defn- arc-ratio-between
  "计算 t 在区间 [ta, tb] 内的弧长位置比，返回 [0, 1]。
   0 表示位于 ta 附近，1 表示位于 tb 附近。
   ta / t / tb 都是全局归一化参数。

   区间长度接近零（退化数据）时退回参数比。"
  ^double [^Curve curve ^double ta ^double t ^double tb]
  (let [left-len  (ArcLengthUtils/integrateArcLength curve ta t arc-subdiv)
        right-len (ArcLengthUtils/integrateArcLength curve t tb arc-subdiv)
        total     (+ left-len right-len)]
    (if (< total 1e-12)
      (let [dt (- tb ta)]
        (if (< (Math/abs dt) 1e-12)
          0.5
          (/ (- t ta) dt)))
      (/ left-len total))))

;; ═══════════════════════════════════════
;; 内部：新采样值的计算
;; ═══════════════════════════════════════

(defn- sample-at-insert
  "按弧长位置比插值新采样值（:point-width）。

   new-idx —— 插入后的索引；相邻采样点在旧 samples 中为 (dec new-idx) 和 new-idx。
   ratio   —— 新点在两采样点之间的弧长位置比。
   w_new = (1-ratio)·w_a + ratio·w_b"
  [samples new-idx ^double ratio]
  (let [w-a (double (nth samples (dec new-idx)))
        w-b (double (nth samples new-idx))]
    (+ (* (- 1.0 ratio) w-a) (* ratio w-b))))

;; ═══════════════════════════════════════
;; 内部：相邻采样点的 t 位置
;; ═══════════════════════════════════════

(defn- sample-t-at
  "返回 :point-width 下第 idx 个采样点的全局 t 位置。
   :point-width 采样 = 控制点，均匀参数化——t = idx / segCount。"
  ^double [idx seg-count]
  (/ (double idx) (double seg-count)))

;; ═══════════════════════════════════════
;; 公开 API
;; ═══════════════════════════════════════

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
          old-points    (:points curve-edn)
          old-seg-count (dec (count old-points))
          old-point-n   (count old-points)
          jcurve        (curve/edn->curve curve-edn)
          seg-idx       (Bezier2D/segmentIndex jcurve (double t))
          new-idx       (inc seg-idx)
          new-jcurve    (Bezier2D/insertPoint jcurve (double t))
          new-curve-edn (curve/curve->edn new-jcurve)
          width-type    (path/path-width-type p)
          old-samples   (:width-samples p)

          new-path
          (case width-type
            :fixed
            (-> p
                (assoc :curve new-curve-edn)
                (dissoc :arc-params))

            :point-width
            (let [;; 新点两侧的相邻采样点（旧 samples 中）
                  t-a         (sample-t-at seg-idx old-seg-count)
                  t-b         (sample-t-at new-idx old-seg-count)
                  ;; 新点在 [t-a, t-b] 上的弧长位置比
                  ratio       (arc-ratio-between jcurve t-a (double t) t-b)
                  w           (sample-at-insert old-samples new-idx ratio)
                  new-samples (width/insert-sample :point-width
                                                   old-samples new-idx w)]
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
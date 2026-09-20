(ns top.kzre.krro.canvas.vector.path
  "路径级操作：结构访问、宽度、tiles、aabb。"
  (:require
    [top.kzre.krro.canvas.vector.curve   :as curve]
    [top.kzre.krro.canvas.vector.crcurve :as crcurve])
  (:import
    (top.kzre.curve.bezier2d
      ArcLengthUtils
      Bezier2D
      Curve
      Segments
      TableMapping)
    (top.kzre.krro.canvas.vector CurveClipper)))

(def ^:private default-stroke-width 1.0)

(def ^:private valid-path-types #{:bezier :catmull-rom})

(defn valid-path?
  "path 是否为合法路径 map：
   - 是 map
   - :path-type 是已知类型
   - :curve 字段存在
   - :style 字段存在"
  [p]
  (and (map? p)
       (contains? valid-path-types (:path-type p))
       (contains? p :curve)
       (contains? p :style)))

;; ═══════════════════════════════════════
;; 结构访问
;; ═══════════════════════════════════════

(defn path-curve
  "返回路径的曲线 EDN。:path-type 缺失或未知时抛异常。"
  [path]
  {:pre [(valid-path? path)]}
  (case (:path-type path)
    (:bezier :catmull-rom) (:curve path)
    (throw (ex-info "Unknown or missing :path-type"
                    {:path-type (:path-type path) :path path}))))

(defn path-points
  "返回路径的控制点序列（vector of {:x :y}）。
   统一 :bezier 与 :catmull-rom。:points 缺失时返回 []。"
  [path]
  {:pre [(valid-path? path)]}
  (vec (:points (path-curve path))))

(defn path-point-count
  "返回路径控制点的数量。"
  [path]
  {:pre [(valid-path? path)]}
  (count (path-points path)))

(defn path-closed?
  "路径是否闭合。:path-type 缺失或未知时抛异常。"
  [path]
  {:pre [(valid-path? path)]}
  (case (:path-type path)
    (:bezier :catmull-rom) (boolean (get-in path [:curve :closed]))
    (throw (ex-info "Unknown or missing :path-type"
                    {:path-type (:path-type path) :path path}))))

(defn path->curve
  "从路径描述提取 Java Curve 对象。
   :path-type 缺失或未知时抛异常。"
  ^Curve [path]
  {:pre [(valid-path? path)]}
  (case (:path-type path)
    :bezier      (curve/edn->curve   (:curve path))
    :catmull-rom (crcurve/edn->curve (:curve path))
    (throw (ex-info "Unknown or missing :path-type"
                    {:path-type (:path-type path) :path path}))))

;; ═══════════════════════════════════════
;; 宽度类型
;; ═══════════════════════════════════════

(def ^:private valid-width-types #{:fixed :point-width :t-width :curve})

(defn path-width-type
  "路径宽度控制类型。...（docstring 不变）..."
  [path]
  {:pre [(valid-path? path)]}
  (cond
    (:width-curve path) :curve
    (seq (:width-samples path))
    (if (seq (:t-params path)) :t-width :point-width)
    :else :fixed))

(defn uniform-t-params
  "生成均匀参数 t = i / (n-1)。n <= 1 时返回 nil。"
  [num-points]
  {:pre [(int? num-points) (not (neg? num-points))]}
  (when (> num-points 1)
    (mapv #(/ % (dec num-points)) (range num-points))))

;; ═══════════════════════════════════════
;; 宽度类型标准化
;; ═══════════════════════════════════════

(defn- compute-arc-params
  "从 path 构造 Curve 并计算均匀弧长参数。"
  [path t-params]
  (let [^Curve c   (path->curve path)
        arc-lengths (ArcLengthUtils/buildArcLengthParams c (double-array t-params))]
    (vec (TableMapping/uniformSParams arc-lengths))))

(defn ensure-width-type*
  "确保路径具有指定的宽度类型。..."
  [path width-type & {:keys [t-params width-samples arc-params _width-curve compute-arc?]
                      :or {compute-arc? false}}]
  {:pre [(valid-path? path)
         (contains? valid-width-types width-type)]}
  (let [num-points    (path-point-count path)
        default-width (get-in path [:style :stroke :width] default-stroke-width)]
    (case width-type
      :fixed
      (if (= :fixed (path-width-type path))
        path
        (-> path
            (dissoc :width-samples :t-params :arc-params :width-curve)
            (assoc-in [:style :stroke :width]
                      (or (get-in path [:style :stroke :width])
                          default-width))))

      :point-width
      (let [samples (or (:width-samples path)
                        (if (delay? width-samples) @width-samples width-samples)
                        (vec (repeat num-points default-width)))
            _ (when (not= (count samples) num-points)
                (throw (ex-info "width-samples length must equal control points"
                                {:expected num-points :actual (count samples)})))
            arc (or (:arc-params path)
                    (if (delay? arc-params) @arc-params arc-params)
                    (when compute-arc?
                      (compute-arc-params
                        path
                        (or (if (delay? t-params) @t-params t-params)
                            (uniform-t-params num-points)))))]
        (-> path
            (dissoc :t-params :width-curve)
            (assoc :width-samples samples)
            (assoc :arc-params arc)))

      :t-width
      (let [t-params (or (:t-params path)
                         (if (delay? t-params) @t-params t-params)
                         (uniform-t-params num-points))
            _ (when (not= (count t-params) num-points)
                (throw (ex-info "t-params length must equal control points"
                                {:expected num-points :actual (count t-params)})))
            samples (or (:width-samples path)
                        (if (delay? width-samples) @width-samples width-samples)
                        (vec (repeat num-points default-width)))
            _ (when (not= (count samples) num-points)
                (throw (ex-info "width-samples length must equal t-params"
                                {:expected (count t-params) :actual (count samples)})))
            arc (or (:arc-params path)
                    (if (delay? arc-params) @arc-params arc-params)
                    (when compute-arc?
                      (compute-arc-params path t-params)))]
        (-> path
            (dissoc :width-curve)
            (assoc :t-params t-params)
            (assoc :width-samples samples)
            (assoc :arc-params arc)))

      :curve
      (throw (ex-info "Curve width type not yet supported" {:path path})))))

;; ═══════════════════════════════════════
;; 宽度查询
;; ═══════════════════════════════════════

(defn max-path-width
  "返回路径的有效最大宽度。..."
  [path]
  {:pre [(valid-path? path)]}
  (let [stroke-width (get-in path [:style :stroke :width] default-stroke-width)
        samples      (:width-samples path)]
    (case (path-width-type path)
      :fixed       stroke-width
      :point-width (if (seq samples) (apply max samples) stroke-width)
      :t-width     (if (seq samples) (apply max samples) stroke-width)
      :curve       stroke-width)))

(defn max-path-half-width
  "路径描边半宽，用于瓦片扩展 / 裁剪可见性判断。"
  [path]
  {:pre [(valid-path? path)]}
  (/ (max-path-width path) 2.0))

;; ═══════════════════════════════════════
;; 纯 EDN 平移
;; ═══════════════════════════════════════

(defn translate-points
  "平移曲线 EDN 中指定索引的控制点。
   纯 EDN 操作，不构造 Java 对象。"
  [curve-edn dx dy idxs]
  {:pre [(map? curve-edn)
         (number? dx) (number? dy)
         (sequential? idxs)
         (every? int? idxs)]}
  (let [idx-set (set idxs)]
    (update curve-edn :points
            (fn [points]
              (mapv (fn [i p]
                      (if (contains? idx-set i)
                        (assoc p :x (+ (:x p) dx) :y (+ (:y p) dy))
                        p))
                    (range)
                    points)))))

;; ═══════════════════════════════════════
;; Tiles
;; ═══════════════════════════════════════

(defn seg-tiles
  "第 idx 段经过的瓦片集合（含描边宽度扩展）。
   idx 越界由 Java 抛异常。"
  [path idx tile-size]
  {:pre [(valid-path? path)
         (int? idx)
         (pos-int? tile-size)]}
  (let [^Curve c (path->curve path)]
    (set (CurveClipper/segTiles (.getSegment c (int idx))
                                (int tile-size)
                                (max-path-half-width path)))))

(defn path-tiles
  "整条路径经过的瓦片集合（含描边宽度扩展）。"
  [path tile-size]
  {:pre [(valid-path? path)
         (pos-int? tile-size)]}
  (set (CurveClipper/curveTiles (path->curve path)
                                (int tile-size)
                                (max-path-half-width path))))

;; ═══════════════════════════════════════
;; AABB（不含描边宽度）
;; ═══════════════════════════════════════

(defn path-aabb
  "整条路径的包围盒。"
  [path]
  {:pre [(valid-path? path)]}
  (curve/aabb->edn (Bezier2D/aabb (path->curve path))))

(defn seg-aabb
  "第 idx 段的包围盒（段 = 控制点 idx 到 idx+1）。
   idx 越界由 Java 抛异常。"
  [path idx]
  {:pre [(valid-path? path) (int? idx)]}
  (curve/aabb->edn (Segments/aabb (.getSegment (path->curve path) (int idx)))))
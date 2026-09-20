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

;; ═══════════════════════════════════════
;; 结构访问
;; ═══════════════════════════════════════

(defn path-curve
  "返回路径的曲线 EDN。统一 :bezier / :catmull-rom。"
  [path]
  (case (:path-type path)
    :bezier      (:bezier-curve path)
    :catmull-rom (:cr-curve path)
    (throw (ex-info "Unknown or missing :path-type"
                    {:path-type (:path-type path) :path path}))))

(defn path-points
  "返回路径的控制点序列（vector of {:x :y}）。
   统一 :bezier 与 :catmull-rom。:points 缺失时返回 []。
   :path-type 未知时抛异常。"
  [path]
  (vec (:points (path-curve path))))

(defn path-point-count
  "返回路径控制点的数量。"
  [path]
  (count (path-points path)))

(defn path-closed?
  "路径是否闭合。:path-type 缺失或未知时抛异常。"
  [path]
  (case (:path-type path)
    :bezier      (boolean (get-in path [:bezier-curve :closed]))
    :catmull-rom (boolean (get-in path [:cr-curve :closed]))
    (throw (ex-info "Unknown or missing :path-type"
                    {:path-type (:path-type path) :path path}))))

(defn path->curve
  "从路径描述提取 Java Curve 对象。
   :path-type 缺失或未知时抛异常。"
  ^Curve [path]
  (case (:path-type path)
    :bezier      (curve/edn->curve (:bezier-curve path))
    :catmull-rom (crcurve/edn->curve (:cr-curve path))
    (throw (ex-info "Unknown or missing :path-type"
                    {:path-type (:path-type path) :path path}))))

;; ═══════════════════════════════════════
;; 宽度类型
;; ═══════════════════════════════════════

(defn path-width-type
  "路径宽度控制类型。根据路径中的宽度控制字段确定宽度控制方式。

  返回值为以下四种类型之一：

  - :fixed     固定宽度，整个路径宽度恒定，使用 :width 或默认值。
                对应数据：无 :width-samples，无 :t-params，无 :width-curve。

  - :point-width 控制点宽度，宽度值存储在每个控制点上（宽度采样数量等于控制点数量）。
                对应数据：有 :width-samples，无 :t-params，无 :width-curve。
                宽度通过控制点索引直接索引。

  - :t-width   参数化宽度，宽度采样与参数 t（0~1）关联，采样点数独立于控制点数量。
                对应数据：有 :width-samples，有 :t-params，无 :width-curve。
                宽度通过 t 参数线性插值，适用于宽度变化复杂但路径简单的场景。

  - :curve     曲线宽度控制，使用显式的宽度曲线函数（如样条或高阶插值）。
                对应数据：有 :width-curve。
                提供最灵活的宽度控制，但需要额外计算开销。

  判断优先级：:width-curve > :width-samples > 默认 :fixed。

  示例：
    (path-width-type path) ; => :fixed | :point-width | :t-width | :curve"
  [path]
  (cond
    (:width-curve path) :curve
    (seq (:width-samples path))
    (if (seq (:t-params path))
      :t-width
      :point-width)
    :else :fixed))

(defn uniform-t-params
  "生成均匀参数 t = i / (n-1)。n <= 1 时返回 nil。"
  [num-points]
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
  "确保路径具有指定的宽度类型。可选参数可为 delay 或其他值。
   如果参数是 delay，则只在需要时 deref。"
  [path width-type & {:keys [t-params width-samples arc-params _width-curve compute-arc?]
                      :or {compute-arc? false}}]
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
                          default-width))
            (assoc :width-type :fixed)))

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
            (assoc :arc-params arc)
            (assoc :width-type :point-width)))

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
            (assoc :arc-params arc)
            (assoc :width-type :t-width)))

      :curve
      (throw (ex-info "Curve width type not yet supported" {:path path})))))

;; ═══════════════════════════════════════
;; 宽度查询
;; ═══════════════════════════════════════

(defn max-path-width
  "返回路径的有效最大宽度。
   - :fixed         → stroke 样式宽度
   - :point-width   → width-samples 的最大值
   - :t-width       → width-samples 的最大值
   - :curve         → 待实现（当前退化为 stroke 宽度）"
  [path]
  (let [stroke-width (get-in path [:style :stroke :width] default-stroke-width)
        samples      (:width-samples path)]
    (case (path-width-type path)
      :fixed       stroke-width
      :point-width (if (seq samples) (apply max samples) stroke-width)
      :t-width     (if (seq samples) (apply max samples) stroke-width)
      :curve       stroke-width)))   ; TODO: 宽度曲线求最大

(defn max-path-half-width
  "路径描边半宽，用于瓦片扩展 / 裁剪可见性判断。"
  [path]
  (/ (max-path-width path) 2.0))

;; ═══════════════════════════════════════
;; 纯 EDN 平移（供 anchor/translate-path 使用）
;; ═══════════════════════════════════════

(defn translate-points
  "平移曲线 EDN 中指定索引的控制点。
   纯 EDN 操作，不构造 Java 对象。"
  [curve-edn dx dy idxs]
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
   返回 Set<Long>（TiledCanvas.pack 格式）。
   idx 越界由 Java 抛异常。"
  [path idx tile-size]
  (let [^Curve c (path->curve path)]
    (CurveClipper/segTiles (.getSegment c (int idx))
                           (int tile-size)
                           (max-path-half-width path))))

(defn path-tiles
  "整条路径经过的瓦片集合（含描边宽度扩展）。"
  [path tile-size]
  (CurveClipper/curveTiles (path->curve path)
                           (int tile-size)
                           (max-path-half-width path)))

;; ═══════════════════════════════════════
;; AABB（不含描边宽度）
;; ═══════════════════════════════════════

(defn path-aabb
  "整条路径的包围盒。"
  [path]
  (curve/aabb->edn (Bezier2D/aabb (path->curve path))))

(defn seg-aabb
  "第 idx 段的包围盒（段 = 控制点 idx 到 idx+1）。
   idx 越界由 Java 抛异常。"
  [path idx]
  (curve/aabb->edn (Segments/aabb (.getSegment (path->curve path) (int idx)))))
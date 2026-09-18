(ns top.kzre.krro.canvas.vector.core
  "矢量渲染核心：提供独立于图层的曲线渲染函数，以及矢量图层的渲染入口。"
  (:require
   [taoensso.tufte :refer [p profile]]
   [top.kzre.krro.canvas.core.layer.render.composite :as composite]
   [top.kzre.krro.canvas.core.layer.util :as lu]
   [top.kzre.krro.core.util.promise :as promise]
   [top.kzre.krro.curve.bezier2d.core :as bezier]
   [top.kzre.krro.curve.catmullrom2d.core :as cr])
  (:import
   (java.util Collection UUID)
   (top.kzre.curve.bezier2d Bezier2D)
   (top.kzre.krro.canvas.core.layer LayerUtils PixelBlitter PixelBlitter$BlitterRequest)
   (top.kzre.krro.canvas.vector
    AntiAlias
    ArcLengthSampleWidthFunc
    Cap
    FillRule
    FixedWidthFunction
    Join
    RenderCurveTaskBuilder)
   (top.kzre.krro.util.math KMath)
   (top.kzre.krro.util.tile TiledCanvas)))

(defn make-vector-layer
  "创建矢量图层数据结构。"
  [& {:keys [id name opacity blend-mode visible backend antialias]
      :or   {id         (keyword (str "layer-" (UUID/randomUUID)))
             name       "Vector Layer"
             opacity    1.0
             blend-mode :normal
             visible    true
             antialias  true
             backend    :default}
      :as   opts}]
  (merge
    {:id           id
     :type         :vector
     :name         name
     :opacity      opacity
     :blend-mode   blend-mode
     :visible      visible
     :backend      backend
     :paths    {}
     :path-order   []
     :antialias   antialias}
    (select-keys opts [:x :y :scale-x :scale-y :rotation :transform])))

;; ============================================================================
;; 辅助转换函数
;; ============================================================================

(defn keyword->antialias
  "将 Clojure 关键字转换为 AntiAlias 枚举。"
  [kw]
  (if kw AntiAlias/ANALYTIC AntiAlias/NONE))

(defn keyword->cap
  "将 Clojure 关键字转换为 Cap 枚举。"
  [kw]
  (case kw
    :butt Cap/BUTT
    :round Cap/ROUND
    Cap/SQUARE))

(defn keyword->join
  "将 Clojure 关键字转换为 Join 枚举。"
  [kw]
  (case kw
    :miter Join/MITER
    :round Join/ROUND
    Join/BEVEL))

(defn keyword->fill-rule
  "将 Clojure 关键字转换为 FillRule 枚举。"
  [kw]
  (case kw
    :even-odd FillRule/EVEN_ODD
    :non-zero FillRule/NON_ZERO
    (throw (ex-info "Unknown fill rule" {:mode kw}))))


(defn- configure-fill!
  "在曲线配置上设置填充。无填充则跳过。"
  [curve-config fill]
  (when-let [fill fill]
    (let [fill-config (.fill curve-config)
          color (float-array (:color fill))
          rule  (keyword->fill-rule (:rule fill :non-zero))]
      (.color fill-config color)
      (.fillRule fill-config rule))))

(defn- configure-stroke-width!
  "设置描边宽度函数——优先弧长采样宽度，否则固定宽度。"
  [stroke-config stroke arc-params width-samples]
  (if (and (seq arc-params) (seq width-samples))
    (.widthFunc stroke-config
                (ArcLengthSampleWidthFunc.
                  (double-array arc-params)
                  (double-array width-samples)))
    (.widthFunc stroke-config
                (FixedWidthFunction. (:width stroke 1.0)))))

(defn- configure-stroke!
  "在曲线配置上设置描边。无描边则跳过。"
  [curve-config stroke arc-params width-samples]
  (when-let [stroke stroke]
    (let [stroke-config (.stroke curve-config)
          color (float-array (:color stroke [1.0 1.0 1.0 1.0]))]
      (.color stroke-config color)
      (.cap  stroke-config (keyword->cap (:cap stroke :butt)))
      (.join stroke-config (keyword->join (:join stroke :miter)))
      (configure-stroke-width! stroke-config stroke arc-params width-samples)
      (when-let [ml (:miter-limit stroke)]
        (.miterLimit stroke-config (float ml))))))

(defn- configure-curve!
  "配置单条曲线的 flatness、填充、描边。"
  [builder {:keys [bezier-curve style width-samples arc-params width-tolerance]} flatness]
  (let [curve-config (.curve builder bezier-curve)]
    (when width-tolerance
      (.widthTolerance curve-config (float width-tolerance)))
    (configure-fill! curve-config (:fill style))
    (configure-stroke! curve-config (:stroke style) arc-params width-samples)))

(defn- configure-builder!
  "设置 builder 的全局配置：画布、尺寸、脏瓦片、缩放、抗锯齿。"
  [builder canvas view-width view-height
   {:keys [scale-x scale-y flatness antialias dirty-tiles]}]
  (doto (.config builder)
    (.canvas canvas)
    (.flatness flatness)
    (.viewSize view-width view-height)
    (.dirtyTiles dirty-tiles)
    (.scale scale-x scale-y)
    (.antiAlias (keyword->antialias antialias))))

(defn build-render-task
  [canvas view-width view-height
   {:keys [scale-x scale-y flatness antialias dirty-tiles]
    :or   {scale-x 1.0
           scale-y 1.0
           flatness 0.25}}
   & paths]
  (let [builder (RenderCurveTaskBuilder/create)]
    (configure-builder! builder canvas view-width view-height
                        {:scale-x     scale-x
                         :scale-y     scale-y
                         :flatness    flatness
                         :antialias   antialias
                         :dirty-tiles dirty-tiles})
    (doseq [path paths]
      (configure-curve! builder path flatness))
    (.build builder)))

(defn render-paths!
  "批量渲染多条曲线到临时画布（同步执行）。
   参数同 render-path!，但 curves 为集合 (Collection<Curve>)。
   所有曲线共用同一配置。"
  [^TiledCanvas canvas canvas-w canvas-h ^Collection curves
   & {:keys [antialias flatness dirty-tiles fill stroke]
      :or {antialias true
           flatness 0.25}}]
  (when (seq curves)
    (let [curves-config
          (mapv
            (fn [c]
              {:bezier-curve c
               :flatness flatness
               :fill fill
               :stroke stroke})
            curves)
          task (apply build-render-task
                      canvas canvas-w canvas-h
                      {:antialias antialias
                       :dirty-tiles dirty-tiles}
                      curves-config)]
      (.run task)
      nil)))


(defn- curve-of
  "从路径描述提取 Bezier 曲线。未知类型返回 nil。"
  [path]
  (case (:path-type path)
    :bezier      (bezier/edn->curve (:bezier-curve path))
    :catmull-rom (-> (cr/edn->crcurve (:cr-curve path))
                     (.getBezierCurve))
    nil))

(defn- transform-info
  "解析图层变换矩阵，返回渲染所需的缩放与变换信息。"
  [^floats transform]
  (let [identity? (or (nil? transform)
                      (KMath/mat2dIsIdentity transform))]
    {:identity? identity?
     :scale-x   (if identity? 1.0 (KMath/mat2dScaleX transform))
     :scale-y   (if identity? 1.0 (KMath/mat2dScaleY transform))
     :xform     (when-not identity? (float-array transform))}))

(defn- build-layer-curves
  "遍历 path-order，构造已应用图层变换的曲线集合。"
  [layer xform]
  (into []
        (keep (fn [path-id]
                (when-let [path (get (:paths layer) path-id)]
                  (when-let [curve (curve-of path)]
                    (assoc path :bezier-curve
                                (if xform
                                  (Bezier2D/transform curve xform)
                                  curve))))))
        (:path-order layer)))


(defn render-to-canvas!
  "把矢量图层的曲线渲染到临时画布。
   前置条件：调用方负责 tmp 的创建与清理。"
  [layer ^TiledCanvas tmp {:keys [view-width view-height dirty-tiles]}]
  (let [antialias (:antialias layer true)
        flatness  (:flatness layer 0.25)
        {:keys [scale-x scale-y xform]}
        (transform-info (:transform layer))

        transformed-paths
        (p :build-transformed-curves
           (build-layer-curves layer xform))

        task
        (p :build-render-task
           (apply build-render-task
                  tmp view-width view-height
                  {:scale-x     scale-x
                   :scale-y     scale-y
                   :flatness    flatness
                   :dirty-tiles dirty-tiles
                   :antialias   antialias}
                  transformed-paths))]

    (p :run-render-task (.run task))
    nil))

(defmethod composite/composite-layer :vector
  [layer ^TiledCanvas canvas
   {:keys [view-width view-height dirty-tiles subpixel?]
    :or   {subpixel? false}}]
  (profile
    {:id :vector/render}
    (let [tmp-canvas (TiledCanvas. (.getTileSize canvas))]
      (try
        (render-to-canvas! layer tmp-canvas
                              {:view-width  view-width
                               :view-height view-height
                               :dirty-tiles dirty-tiles})
        ;; TODO image-size
        (p :blit-canvas
           (PixelBlitter/blit
             (.build
               (doto (PixelBlitter$BlitterRequest/builder)
                 (.dst canvas)
                 (.src tmp-canvas)
                 (.viewSize view-width view-height)
                 (.blendMode (lu/blend-mode-str (:blend-mode layer) :normal))
                 (.opacity (:opacity layer 1.0))
                 (.dirtyTiles dirty-tiles)
                 (.subpixel subpixel?)))))
        (promise/resolved canvas)
        (finally
          (.clear tmp-canvas))))))
(ns top.kzre.krro.canvas.vector.crcurve
  "Catmull-Rom 曲线的 EDN ↔ Java CRCurve 转换层。
   只做转换，不做几何运算。
   ⚠️ Java CRCurve 只在函数体内部存活，不跨函数边界。"
  (:require
   [clojure.spec.alpha :as s]
   [top.kzre.krro.canvas.vector.curve :as vc])
  (:import
   [java.util ArrayList Collection List]
   [top.kzre.curve.bezier2d Curve]
   [top.kzre.curve.catmullrom2d CRCurve CatmullRom2D ParamType]))

;; ═══════════════════════════════════════
;; param-type 双向映射
;; ═══════════════════════════════════════

(def ^:private param-type->kw
  {ParamType/UNIFORM     :uniform
   ParamType/CHORDAL     :chordal
   ParamType/CENTRIPETAL :centripetal})

(def ^:private kw->param-type
  {:uniform     ParamType/UNIFORM
   :chordal     ParamType/CHORDAL
   :centripetal ParamType/CENTRIPETAL})

;; ═══════════════════════════════════════
;; Java → EDN
;; ═══════════════════════════════════════

(defn crcurve->edn
  "Java CRCurve → EDN。"
  [^CRCurve c]
  {:points     (mapv vc/pair->edn (.getPoints c))
   :closed     (.isClosed c)
   :tension    (.getTension c)
   :param-type (or (param-type->kw (.getParamType c))
                   (throw (IllegalStateException.
                            (str "Unknown ParamType: " (.getParamType c)))))})

;; ═══════════════════════════════════════
;; EDN → Java
;; ═══════════════════════════════════════

(defn edn->crcurve
  "EDN → Java CRCurve（新分配）。"
  ^CRCurve [m]
  (let [pts     (mapv vc/edn->pair (:points m))
        ^List lst (ArrayList. ^Collection pts)
        closed  (boolean (:closed m))
        tension (double (:tension m))
        pt      (or (kw->param-type (:param-type m))
                    (throw (IllegalArgumentException.
                             (str "Unknown ParamType: " (:param-type m)))))]
    (CRCurve. lst closed tension pt)))

;; ═══════════════════════════════════════
;; 曲线类型转换（CR EDN → Bézier EDN）
;; ═══════════════════════════════════════

(defn crcurve->bezier-edn
  "将 Catmull-Rom 曲线 EDN 转换为等价 Bézier 曲线 EDN。"
  [cr-edn]
  (let [c (edn->crcurve cr-edn)]
    (vc/curve->edn (CatmullRom2D/toBezierCurve c))))

(defn edn->curve
  "CR 曲线 EDN → Java Curve。"
  ^Curve [cr-edn]
  (CatmullRom2D/toBezierCurve (edn->crcurve cr-edn)))

;; ═══════════════════════════════════════
;; spec
;; ═══════════════════════════════════════

(s/def ::point ::vc/point)
(s/def ::param-type #{:uniform :chordal :centripetal})
(s/def ::tension (s/and number? #(> % 0) #(<= % 1.0)))
(s/def ::points (s/coll-of ::point :kind vector? :min-count 2))
(s/def ::closed boolean?)
(s/def ::curve (s/keys :req-un [::points ::closed ::tension ::param-type]))
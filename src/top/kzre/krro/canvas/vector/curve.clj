(ns top.kzre.krro.canvas.vector.curve
  "EDN ↔ Java Curve 转换层。
   只做转换，不做几何运算。
   ⚠️ 所有函数的输入输出都是 EDN；Java 对象只在函数体内部存活。"
  (:require [clojure.spec.alpha :as s])
  (:import (java.util ArrayList Collection)
           [top.kzre.curve.bezier2d
            AABB ClosestPointResult Continuity ControlPoint Curve Pair]))

;; ═══════════════════════════════════════
;; Continuity 双向映射
;; ═══════════════════════════════════════

(def ^:private kw->continuity
  {:none Continuity/NONE
   :g1   Continuity/G1
   :c1   Continuity/C1
   :g2   Continuity/G2
   :c2   Continuity/C2})

(def ^:private continuity->kw
  {Continuity/NONE :none
   Continuity/G1   :g1
   Continuity/C1   :c1
   Continuity/G2   :g2
   Continuity/C2   :c2})

(defn- edn->continuity
  "EDN keyword → Java Continuity。
   缺省或未知时返回 NONE。"
  ^Continuity [kw]
  (or (kw->continuity kw) Continuity/NONE))

(defn- continuity->edn
  "Java Continuity → EDN keyword。"
  [^Continuity c]
  (get continuity->kw c :none))

;; ═══════════════════════════════════════
;; EDN → Java
;; ═══════════════════════════════════════

(defn edn->point
  "EDN 控制点 → Java ControlPoint。"
  ^ControlPoint [p]
  (ControlPoint.
    (double (:x p))   (double (:y p))
    (double (:dx1 p)) (double (:dy1 p))
    (double (:dx2 p)) (double (:dy2 p))
    (edn->continuity (:continuity p))))

(defn edn->curve
  "EDN 曲线 → Java Curve（新分配）。"
  ^Curve [m]
  (let [pts (mapv edn->point (:points m))]
    (Curve. (ArrayList. ^Collection pts)
            (boolean (:closed m)))))

(defn edn->pair
  "EDN {:x :y} → Java Pair。"
  ^Pair [m]
  (Pair. (:x m) (:y m)))

;; ═══════════════════════════════════════
;; Java → EDN
;; ═══════════════════════════════════════

(defn point->edn
  "Java ControlPoint → EDN 控制点。"
  [^ControlPoint p]
  {:x          (.getX p)   :y   (.getY p)
   :dx1        (.getDx1 p) :dy1 (.getDy1 p)
   :dx2        (.getDx2 p) :dy2 (.getDy2 p)
   :continuity (continuity->edn (.getContinuity p))})

(defn curve->edn
  "Java Curve → EDN 曲线。"
  [^Curve c]
  {:closed (.isClosed c)
   :points (mapv point->edn (.getPoints c))})

(defn pair->edn
  "Java Pair → EDN {:x :y}。"
  [^Pair p]
  {:x (.getX p) :y (.getY p)})

(defn aabb->edn
  "Java AABB → EDN 包围盒。"
  [^AABB aabb]
  {:min-x (.getMinX aabb) :min-y (.getMinY aabb)
   :max-x (.getMaxX aabb) :max-y (.getMaxY aabb)})

(defn closest->edn
  "Java ClosestPointResult → EDN。"
  [^ClosestPointResult r]
  {:point    (pair->edn (.getPoint r))
   :t        (.getT r)
   :distance (.getDistance r)})

;; ═══════════════════════════════════════
;; spec
;; ═══════════════════════════════════════

(s/def ::x number?)
(s/def ::y number?)
(s/def ::dx1 number?)
(s/def ::dy1 number?)
(s/def ::dx2 number?)
(s/def ::dy2 number?)

(s/def ::continuity #{:none :g1 :c1 :g2 :c2})

(s/def ::point (s/keys :req-un [::x ::y]))
(s/def ::control-point
  (s/keys :req-un [::x ::y ::dx1 ::dy1 ::dx2 ::dy2]
          :opt-un [::continuity]))   ; 缺省视作 :none

(s/def ::closed boolean?)
(s/def ::points (s/coll-of ::control-point :kind vector? :min-count 2))
(s/def ::curve (s/keys :req-un [::closed ::points]))

(s/def ::min-x number?)
(s/def ::min-y number?)
(s/def ::max-x number?)
(s/def ::max-y number?)
(s/def ::aabb (s/keys :req-un [::min-x ::min-y ::max-x ::max-y]))

(s/def ::t number?)
(s/def ::distance (s/and number? (complement neg?)))
(s/def ::closest-result (s/keys :req-un [::point ::t ::distance]))
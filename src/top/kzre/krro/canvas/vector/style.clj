(ns top.kzre.krro.canvas.vector.style
  "矢量图层光栅化样式规格。支持描边和填充样式，
   描边支持均匀宽度与可变宽度（通过函数采样）。"
  (:require [clojure.spec.alpha :as s]))

(s/def ::color (s/coll-of float? :kind vector? :count 4))
(s/def ::fill-rule #{:even-odd :non-zero})

(s/def ::fill (s/keys :req-un [::color]
                      :opt-un [::fill-rule]))

;; 宽度：固定数值 或 采样函数 (fn [t] width)，t ∈ [0,1]
(s/def ::width (s/or :fixed number? :func ifn?))
(s/def ::cap #{:butt :round :square})
(s/def ::join #{:miter :round :bevel})
(s/def ::stroke (s/keys :req-un [::color ::width]
                        :opt-un [::cap ::join]))

(s/def ::style (s/keys :opt-un [::fill ::stroke]))
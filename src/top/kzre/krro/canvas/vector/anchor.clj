(ns top.kzre.krro.canvas.vector.anchor
  (:require
   [top.kzre.krro.canvas.vector.path :as path])
  (:import
    (top.kzre.curve.bezier2d Curve)
    (top.kzre.krro.canvas.vector CurveClipper)))


(defrecord Anchor [path-id point-idx])
(defrecord AnchorTranslation [^Anchor anchor dx dy])


(defn path-of-anchor
  "从路径映射 paths 中查找包含 anchor 的路径数据。
   若路径存在则返回路径 map，否则返回 nil。"
  [paths ^Anchor anchor]
  (get paths (:path-id anchor)))

(defn anchor-tiles
  [paths ^Anchor anchor tile-size]
  (when-let [path (path-of-anchor paths anchor)]
    (when-let [^Curve c (path/path->curve path)]
      (CurveClipper/anchorTiles c
                                (int (:point-idx anchor))
                                (int tile-size)
                                (path/max-path-half-width path)))))


(defn all-anchors
  "返回路径中所有锚点的列表。"
  [path path-id]
  (mapv #(->Anchor path-id %) (range (path/path-point-count path))))

(defn anchor-point
  "锚点坐标 {:x :y}。索引越界返回 nil。"
  [path idx]
  (get (path/path-points path) idx))


(defn end-anchor?
  "是否为路径的端点。仅支持非闭合曲线。"
  [path anchor]
  (let [n   (path/path-point-count path)
        idx (:point-idx anchor)]
    (boolean
      (and (not (path/path-closed? path))
           (pos? n)
           (or (zero? idx) (= idx (dec n)))))))

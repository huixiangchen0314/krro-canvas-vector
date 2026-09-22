(ns top.kzre.krro.canvas.vector.width-adjust
  (:require
    [top.kzre.krro.canvas.vector.path :as path]))

;; t-params
(defn adjust-widths
  [paths anchors delta
   & {:keys [min-width max-width]
      :or {min-width 0.01
           max-width Double/POSITIVE_INFINITY}}]
  (let [new-paths
        (reduce
          (fn [paths-acc [path-id anchor-group]]
            (let [path (get paths-acc path-id)
                  width-type (path/path-width-type path)]
              (case width-type
                :fixed
                (let [old-width (get-in path [:style :stroke :width] 1.0)
                      new-width (-> (+ old-width delta)
                                    (max min-width)
                                    (min max-width))
                      new-path (-> path
                                   (assoc-in [:style :stroke :width] new-width)
                                   (dissoc :width-samples :arc-params :t-params)
                                   (assoc :width-type :fixed))]
                  (assoc paths-acc path-id new-path))

                ;; TODO t 宽度衰减编辑
                (:point-width :t-width)
                (let [path (path/ensure-width-type* path width-type)
                      samples (:width-samples path)
                      idx-set (set (map :point-idx anchor-group))
                      new-samples (mapv (fn [idx width]
                                          (if (contains? idx-set idx)
                                            (-> (+ width delta)
                                                (max min-width)
                                                (min max-width))
                                            width))
                                        (range (count samples))
                                        samples)
                      new-path (assoc path :width-samples new-samples)]
                  (assoc paths-acc path-id new-path))

                :curve
                (throw (ex-info "Adjusting width on curve-controlled path not yet supported"
                                {:path-id path-id :width-type width-type})))))
          paths
          (group-by :path-id anchors))]
    new-paths))

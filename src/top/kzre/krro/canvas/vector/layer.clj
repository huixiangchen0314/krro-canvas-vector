(ns top.kzre.krro.canvas.vector.layer)


(defn paths
  "返回图层的路径映射 {path-id path-data}。"
  [layer]
  (:paths layer {}))

(defn path-order
  "返回图层的路径顺序（向量）。"
  [layer]
  (:path-order layer []))


(defn- ensure-order-entry [layer path-id]
  (update layer :path-order
          (fn [order]
            (if (some #{path-id} order)
              order
              (conj order path-id)))))


(defn fresh-path-id []
  (keyword (str "path-" (random-uuid))))

(defn save-path
  "新增或更新路径。新增时追加到 :path-order 尾部。
   对同一 path-id 重复调用是幂等的（不会在 :path-order 里产生重复）。"
  ([layer path] (save-path layer (fresh-path-id) path))
  ([layer path-id path]
   {:pre [(some? path-id)]}
   (-> layer
       (update :paths assoc path-id path)
       (ensure-order-entry path-id))))

(defn delete-path [layer path-id]
  (let [excluded #{path-id}]
    (-> layer
        (update :paths dissoc path-id)
        (update :path-order (fn [order] (into [] (remove excluded) order))))))




(defn make-vector-layer
  "创建矢量图层数据结构。"
  [& {:keys [id name opacity blend-mode visible backend antialias]
      :or   {id         (keyword (str "layer-" (random-uuid)))
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
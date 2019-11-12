(ns acc-text.nlg.gf.builder
  (:require [acc-text.nlg.spec.semantic-graph :as sg]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(defmulti build-grammar-fragment ::sg/type)

(defmethod build-grammar-fragment :document-plan [{relations ::sg/relations}]
  (format "Document. S ::= %s;" (str/join " " (map (comp (partial str "x") name ::sg/to) relations))))

(defmethod build-grammar-fragment :segment [{id ::sg/id relations ::sg/relations}]
  (format "Segment. x%s ::= %s;" (name id) (str/join " " (map (comp (partial str "x") name ::sg/to) relations))))

(defmethod build-grammar-fragment :amr [{id ::sg/id value ::sg/value relations ::sg/relations {syntax ::sg/syntax} ::sg/attributes}]
  (let [function (some (fn [{role ::sg/role to ::sg/to}]
                         (when (= :function role) (name to)))
                       relations)
        name->id (reduce (fn [m {to ::sg/to role ::sg/role {attr-name ::sg/name} ::sg/attributes}]
                           (cond-> m (and (not= :function role) (some? attr-name)) (assoc (str/lower-case attr-name) (str "x" (name to)))))
                         {}
                         relations)]
    (for [[i instance] (zipmap (rest (range)) syntax)]
      (format "%sV%s. x%s ::= %s;" (str/capitalize value) i (name id) (str/join " " (for [{pos :pos value :value} instance]
                                                                                      (or (get name->id (when value (str/lower-case value)))
                                                                                          (when value (format "\"%s\"" value))
                                                                                          (str "x" function))))))))

(defmethod build-grammar-fragment :data [{id ::sg/id value ::sg/value}]
  (format "Data. x%s ::= \"{{%s}}\";" (name id) value))

(defmethod build-grammar-fragment :quote [{id ::sg/id value ::sg/value}]
  (format "Quote. x%s ::= \"%s\";" (name id) value))

(defmethod build-grammar-fragment :dictionary-item [{id ::sg/id members ::sg/members {attr-name ::sg/name} ::sg/attributes}]
  (for [v (set (cons attr-name members))]
    (format "Item. x%s ::= \"%s\";" (name id) v)))

(defn build-grammar [{relations ::sg/relations concepts ::sg/concepts :as graph}]
  (let [relation-map (group-by ::sg/from relations)]
    (->> concepts
         (map #(assoc % ::sg/relations (get relation-map (::sg/id %) [])))
         (map build-grammar-fragment)
         (flatten))))

(s/fdef build-grammar
        :args (s/cat :semantic-graph ::sg/graph)
        :ret (s/coll-of string? :min-count 2))

(ns db-quiz-prep.prepare
  (:require [db-quiz-prep.sparql :as sparql]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [stencil.core :refer [render-string]]))

; ----- Public functions -----

(defn get-indegrees
  "Get indegrees of resources"
  [config]
  (let [parse-int (fn [int-like] (Integer/parseInt int-like))
        query (slurp (io/resource "indegrees.mustache"))]
    (map (comp parse-int :indegree)
         (sparql/execute-unlimited-select-query config query))))

(defn materialize-difficulties
  "Split difficulties by the thirds of the area under curve."
  [config]
  (let [indegrees (get-indegrees config)
        third (/ (reduce + indegrees) 3)
        partitions (->> (map vector (reductions + indegrees) indegrees)
                        (partition-by (comp #(quot % third) first))
                        (map (partial map second)))
        minimum-indegree (-> partitions (nth 2) first)
        maximum-indegree (-> partitions first last)
        update-template (slurp (io/resource "materialize_difficulties.mustache"))]
    (sparql/execute-unlimited-update config
                                     update-template
                                     :data {:maximum-indegree maximum-indegree
                                            :minimum-indegree minimum-indegree})))

(defn delete-difficulties
  "Delete materialized difficulties for given selector.
  Used for testing."
  [{:keys [data params]
    :as config}]
  (let [update-template (slurp (io/resource "delete_difficulties.mustache"))]
    (sparql/execute-unlimited-update config
                                     update-template
                                     :data (merge params data))))

(defn materialize-questions
  "Materialize questions based on provided configuration"
  [config]
  (let [update-template (slurp (io/resource "materialize_questions.mustache"))]
    (sparql/execute-unlimited-update config update-template)))

(defn execute
  "Execute `task` given configuration in `config`."
  [{{:keys [page-size]
      :or {page-size 10000}} :sparql-endpoint
     :keys [data start-from]
     :or {start-from 0}
     :as config}
   task]
  (let [config' (merge config {:page-size page-size
                               :params data
                               :start-from start-from})]
    (case task
          "questions" (materialize-questions config')
          "difficulties" (materialize-difficulties config')
          "delete-difficulties" (delete-difficulties config'))))

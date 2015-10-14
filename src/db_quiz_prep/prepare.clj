(ns db-quiz-prep.prepare
  (:require [db-quiz-prep.sparql :as sparql]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defn- sign
  [number]
  (if (neg? number) - +))

(defn bisection
  ([n f a b tolerance nmax]
   (if (<= n nmax)
     (let [c (/ (+ a b) 2)
           fc (f c)]
       (if (< (/ (- b a) 2) tolerance)
         c
         (if (= (sign fc) (sign (f a)))
           (recur (inc n) f c b tolerance nmax)
           (recur (inc n) f a c tolerance nmax))))
     (throw (Exception. "Bisection method failed"))))
  ([f a b tolerance nmax]
   (bisection 1 f a b tolerance nmax)))

(defn estimate-b
  [indegree-histogram b]
  (letfn [(sum-map [f] (reduce + (map f indegree-histogram)))]
    (- (* (sum-map (fn [[x _]] (* x (Math/exp (* 2 b x)))))
          (/ (sum-map (fn [[x y]] (* y (Math/exp (* b x)))))
             (sum-map (fn [[x _]] (Math/exp (* 2 b x))))))
       (sum-map (fn [[x y]] (* y x (Math/exp (* b x))))))))

(defn get-indegree-histogram
  "Compute indegree histogram based on the provided configuration.
  Returns a collection of [frequency indegree]."
  [config]
  (let [parse-int (fn [int-like] (Integer/parseInt int-like))
        query (slurp (io/resource "indegree_histogram.mustache"))]
    (map (fn [{:keys [frequency indegree]}]
           (mapv parse-int [indegree frequency]))
         (sparql/execute-unlimited-select-query config query))))

(defn materialize-difficulty
  [config]
  (let [indegree-histogram (get-indegree-histogram config)]
    indegree-histogram))

(defn materialize-questions
  "Materialize questions based on provided configuration"
  [config]
  (let [update-template (slurp (io/resource "materialize_questions.mustache"))]
    (sparql/execute-unlimited-update config materialize-questions)))

(defn execute
  ""
  [{{:keys [page-size]
      :or {page-size 10000}} :sparql-endpoint
     :keys [data start-from]
     :or {start-from 0}
     :as config}]
  (let [config' (merge config {:page-size page-size
                               :start-from start-from
                               :params data})]
    ;(materialize-questions config')
    (materialize-difficulty config')))

(comment
  (def config (edn/read-string (slurp "config.edn")))
  (def indegrees (execute config))
  (def f (partial estimate-b indegrees))
  (float (bisection f -10 0 0.00001 100))
  )

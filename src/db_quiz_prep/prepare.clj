(ns db-quiz-prep.prepare
  (:require [db-quiz-prep.sparql :as sparql]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [stencil.core :refer [render-string]]))

; ----- Private functions -----

(def ^:private degrees->radians
  "Convert angle in degrees to radians"
  (partial * (/ Math/PI 180)))

(defn- round
  "Round a `number` to a given `precision` decimal places."
  [precision number]
  (let [rounding-factor (Math/pow 10 precision)]
    (/ (Math/round (* number rounding-factor))
       rounding-factor)))

(def ^:private round-2
  "Round to 2 decimal places"
  (partial round 2))

(defn- sign
  "Tell the sign of the `number`."
  [number]
  (if (neg? number) - +))

; ----- Public functions -----

(defn bisection
  "Use bisection method to find the root of function `f` in the interval [a, b].
  `tolerance` the maximum deviation from 0.
  `nmax` is the maximum number of iteration of the bisection before it fails."
  ([n f a b tolerance nmax]
   (if (<= n nmax)
     (let [c (/ (+ a b) 2)
           fc (f c)]
       (if (< (Math/abs (float (/ (- b a) 2))) tolerance)
         c
         (if (= (sign fc) (sign (f a)))
           (recur (inc n) f c b tolerance nmax)
           (recur (inc n) f a c tolerance nmax))))
     (throw (Exception. "Bisection method failed"))))
  ([f a b tolerance nmax]
   (bisection 1 f a b tolerance nmax)))

(defn estimate-b
  "Estimates parameter b of an exponential function based on `indegree-histogram`."
  [indegree-histogram b]
  (letfn [(sum-map [f] (reduce + (map f indegree-histogram)))]
    (- (* (sum-map (fn [[x _]] (* x (Math/exp (* 2 b x)))))
          (/ (sum-map (fn [[x y]] (* y (Math/exp (* b x)))))
             (sum-map (fn [[x _]] (Math/exp (* 2 b x))))))
       (sum-map (fn [[x y]] (* y x (Math/exp (* b x))))))))

(defn compute-a
  "Computes parameter a of an exponential function with parameter `b`
  based on `indegree-histogram`."
  [indegree-histogram b]
  (letfn [(sum-map [f] (reduce + (map f indegree-histogram)))]
    (/ (sum-map (fn [[x y]] (* y (Math/exp (* b x)))))
       (sum-map (fn [[x _]] (Math/exp (* 2 b x)))))))

(defn get-indegree-limit
  "Get limiting indegree for exponential function parameterized with `a` and `b` at `angle` in degrees."
  [a b angle]
  (round-2 (/ (Math/log (/ (Math/tan (degrees->radians angle))
                           (* a b)))
              b)))

(defn get-indegree-histogram
  "Compute indegree histogram based on the provided configuration.
  Returns a collection of [frequency indegree]."
  [config]
  (let [parse-int (fn [int-like] (Integer/parseInt int-like))
        query (slurp (io/resource "indegree_histogram.mustache"))]
    (map (fn [{:keys [frequency indegree]}]
           (mapv parse-int [indegree frequency]))
         (sparql/execute-unlimited-select-query config query))))

(defn materialize-difficulties
  "Precompute difficulties of questions based on their indegree."
  [config]
  (let [{{:keys [easy normal]} :split-angles} config
        indegree-histogram (get-indegree-histogram config)
        b (float (bisection (partial estimate-b indegree-histogram) -10 0 0.001 100))
        a (float (compute-a indegree-histogram b))
        maximum-indegree (get-indegree-limit a b easy)
        minimum-indegree (get-indegree-limit a b normal)
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
  (let [update-template (slurp (io/resource "delete_difficulties.mustache"))
        update-string (render-string update-template (merge params data))]
    (sparql/execute-update config update-string)))

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

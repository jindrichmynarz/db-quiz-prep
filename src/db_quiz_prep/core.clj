(ns db-quiz-prep.core
  (:gen-class)
  (:require [db-quiz-prep.mustache :as mustache]
            [db-quiz-prep.util :refer [join-lines]]
            [db-quiz-prep.prepare :as prepare]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.edn :as edn]
            [schema.core :as s]
            [schema-contrib.core :as sc]))

; ----- Schemata -----

(def ^:private positive-number (s/both s/Int (s/pred pos? 'pos?)))

(def ^:private Config
  {:sparql-endpoint {:url sc/URI
                     :username s/Str
                     :password s/Str
                     :page-size positive-number}
   :data {:class sc/URI
          :surface-forms [sc/URI]
          :source-graph sc/URI
          :target-graph sc/URI}
   (s/optional-key :start-from) positive-number})

; ----- Private functions -----

(defn- error-msg
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (join-lines errors)))

(defn- exit
  "Exit with @status and message `msg`.
  `status` 0 is OK, `status` 1 indicates error."
  [^Integer status
   ^String msg]
  {:pre [(#{0 1} status)]}
  (println msg)
  (System/exit status))

(defn- usage
  "Wrap usage `summary` in a description of the program."
  [summary]
  (join-lines ["Execute unlimited SPARQL!"
               "Options:\n"
               summary]))

(def ^:private validate-config
  "Validate configuration `config` according to its schema."
  (let [expected-structure (s/explain Config)]
    (fn [config]
      (try (s/validate Config config) nil
           (catch RuntimeException e (join-lines ["Invalid configuration:"
                                                  (.getMessage e)
                                                  "The expected structure of configuration is:"
                                                  expected-structure]))))))

; ----- Private vars -----

(def ^:private cli-options
  [["-c" "--config CONFIG" "Path to configuration file in EDN"
    :parse-fn #(edn/read-string (slurp %))]
   ["-h" "--help" "Display help message"]])

; ----- Public functions -----

(defn -main
  [& args]
  (let [{{:keys [config help]} :options
         :keys [errors summary]} (parse-opts args cli-options)]
    (cond help (exit 0 (usage summary)) 
          errors (exit 1 (error-msg errors))
          :else (if-let [error (validate-config config)]
                    (exit 1 error)
                    (prepare/execute config)))))

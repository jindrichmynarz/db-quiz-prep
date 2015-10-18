(ns db-quiz-prep.mustache
  (:require [db-quiz-prep.util :refer [join-lines]]
            [clojure.set :refer [difference]]
            [clojure.string :as string]
            [stencil.parser :refer [parse]]))

(defn get-template-variables
  "Get a set of variables used in `template`."
  [template]
  (->> (parse template)
       (filter (complement string?))
       (mapcat :name)
       distinct
       set))


(defproject db-quiz-prep "0.1.0-SNAPSHOT"
  :description "Data pre-processing for DB-quiz"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.3"]
                 [org.clojure/data.zip "0.1.1"]
                 [stencil "0.5.0"]
                 [clj-http "2.0.0"]
                 [prismatic/schema "1.0.1"]
                 [schema-contrib "0.1.5"]
                 [slingshot "0.12.2"]]
  :main db-quiz-prep.core)

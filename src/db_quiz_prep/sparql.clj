(ns db-quiz-prep.sparql
  (:require [clj-http.client :as client]
            [stencil.core :refer [render-string]]
            [slingshot.slingshot :refer [throw+ try+]]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zip-xml]))

; ----- Private functions -----

(defn- lazy-cat'
  "Lazily concatenates lazy sequence of sequences `colls`.
  Taken from <http://stackoverflow.com/a/26595111/385505>."
  [colls]
  (lazy-seq
    (if (seq colls)
      (concat (first colls) (lazy-cat' (next colls))))))

(defn- xml->zipper
  "Take XML string `s`, parse it, and return XML zipper."
  [s]
  (->> s
       .getBytes
       java.io.ByteArrayInputStream.
       xml/parse
       zip/xml-zip))

(defn- execute-sparql
  "Execute SPARQL from `sparql-string`.
  `url` is the URL of the SPARQL endpoint.
  `username` and `password` are user credentials of a user permitted to execute SPARQL Update operations.
  If `method` is :GET, executes a query. If `method` is :POST, executes an update."
  [{{:keys [password username url]} :sparql-endpoint} method sparql-string]
  (let [[method-fn params-key request-key] (case method
                                                 :GET [client/get :query-params "query"]
                                                 ; Fuseki requires form-encoded params
                                                 :POST [client/post :form-params "update"])]
    (try+ (:body (method-fn url {:digest-auth [username password]
                                 params-key {"timeout" 100000
                                             request-key sparql-string}
                                 :throw-entire-message? true}))
          (catch Exception {:keys [body]}
            (println body)
            (throw+)))))

(defn- execute-query
  [config sparql-string]
  (execute-sparql config :GET sparql-string))

(defn- execute-update
  "Execute a SPARQL Update operation."
  [config sparql-string]
  (execute-sparql config :POST sparql-string))

(defn- select
  "Execute SPARQL SELECT query. 
  Returns empty sequence when query has no results."
  [config sparql-string]
  (let [results (xml->zipper (execute-query config sparql-string))
        sparql-variables (map keyword (zip-xml/xml-> results :head :variable (zip-xml/attr :name)))
        sparql-results (zip-xml/xml-> results :results :result)
        get-bindings (comp (partial zipmap sparql-variables) #(zip-xml/xml-> % :binding zip-xml/text))]
    (map get-bindings sparql-results)))

; ----- Public functions -----

(defn execute-unlimited-select-query
  "Lazily stream pages of SPARQL SELECT query results
  by executing paged query from `sparql-template`"
  [{:keys [page-size params start-from] :as config} sparql-template]
  (letfn [(paged-select [offset]
            (println (format "Executing SELECT query with offset %s..." offset))
            (select config (render-string sparql-template
                                          (merge {:limit page-size
                                                  :offset offset} params))))] 
    (->> (iterate (partial + page-size) 0)
         (map paged-select)
         (take-while seq)
         lazy-cat')))

(defn execute-unlimited-update
  [{:keys [page-size params start-from] :as config} sparql-template]
  (let [message-regex (re-pattern #"(\d+)( \(or less\))? triples")
        update-fn (fn [offset]
                    (let [sparql (render-string sparql-template
                                                (merge {:limit page-size
                                                        :offset offset} params))]
                      (println (format "Executing update operation with offset %s..." offset))
                      (execute-update config sparql)))
        triples-changed (comp (fn [number-like]
                                (Integer/parseInt number-like))
                              second
                              (fn [message]
                                (re-find message-regex message))
                              first
                              (fn [zipper]
                                (zip-xml/xml-> zipper :results :result :binding :literal zip-xml/text))
                              xml->zipper)
        continue? (comp not zero? triples-changed)]
    (dorun (->> (iterate (partial + page-size) start-from)
                (map update-fn)
                (take-while continue?)))))

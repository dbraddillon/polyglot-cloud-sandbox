(ns dev.sandbox.lab.clojure-datomic-api.core
  (:require [clojure.string :as str]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [datomic.client.api :as d]
            [org.httpkit.server :as http]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]))

;; No classes here at all - unlike every other sample in this repo (even the Node/Python ones
;; have an implicit "this module/function is the unit"), Clojure has no class/object concept.
;; A "Provider" is never reified as a type: it's just a plain map shaped {:id .. :name ..
;; :specialty ..} passed around and returned as-is. There's no compiler holding any caller
;; accountable for that shape - the closest analogue to a Java/C# compile-time contract is a
;; spec/schema library layered on top, deliberately skipped here to keep this sample small.

(def schema
  [{:db/ident :provider/external-id
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity}
   {:db/ident :provider/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :provider/specialty
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}])

;; :storage-dir :mem is dev-local's fully in-memory mode - no files written to disk at all,
;; the whole database vanishes when the JVM process exits. Matches this repo's disposable-state
;; ethos (same spirit as Pulumi's local, throwaway state) more directly than dev-local's other
;; mode (a real directory under ~/.datomic persisting between runs).
(defonce client (d/client {:server-type :dev-local :system "sandbox" :storage-dir :mem}))
(defonce conn-atom (atom nil))

(defn init-db! []
  (d/create-database client {:db-name "providers"})
  (let [conn (d/connect client {:db-name "providers"})]
    (d/transact conn {:tx-data schema})
    (reset! conn-atom conn)))

;; create-database is idempotent (a no-op if "providers" already exists) and connect just
;; reconnects to whatever's already there - neither one gives you a fresh database the way you
;; might assume from how similar-sounding init code looks in the other samples' @BeforeEach/
;; use-fixtures blocks. Even with :storage-dir :mem, the database persists for as long as
;; `client` (a defonce, so the whole JVM process's lifetime) unless explicitly deleted first.
;; Found this the hard way: an early version of this sample's test suite passed each test
;; against an ever-growing shared database instead of an isolated one per test.
(defn reset-db! []
  (d/delete-database client {:db-name "providers"})
  (init-db!))

(defn- ->provider [pulled]
  {:id (:provider/external-id pulled)
   :name (:provider/name pulled)
   :specialty (:provider/specialty pulled)})

(defn create-provider! [{:keys [name specialty]}]
  (let [external-id (str (random-uuid))]
    (d/transact @conn-atom
                {:tx-data [{:provider/external-id external-id
                            :provider/name name
                            :provider/specialty specialty}]})
    {:id external-id :name name :specialty specialty}))

;; Datalog, Datomic's query language, is a genuinely different shape from SQL/JPQL - :find/
;; :where clauses over facts (entity/attribute/value triples) rather than tables and joins.
;; `pull` here is doing roughly what a JPA @Entity's field access or an EF Core projection
;; would: pick which attributes come back, rather than the whole entity.
(defn find-by-external-id [external-id]
  (let [db (d/db @conn-atom)
        results (d/q '[:find (pull ?e [:provider/external-id :provider/name :provider/specialty])
                       :in $ ?eid
                       :where [?e :provider/external-id ?eid]]
                     db external-id)]
    (some-> results ffirst ->provider)))

(defn list-all []
  (let [db (d/db @conn-atom)]
    (->> (d/q '[:find (pull ?e [:provider/external-id :provider/name :provider/specialty])
                :where [?e :provider/name]]
              db)
         (map first)
         (map ->provider))))

(defn search-by-specialty [specialty]
  (let [db (d/db @conn-atom)]
    (->> (d/q '[:find (pull ?e [:provider/external-id :provider/name :provider/specialty])
                :in $ ?specialty
                :where [?e :provider/specialty ?specialty]]
              db specialty)
         (map first)
         (map ->provider))))

;; Route order matters: /providers/search has to come before /providers/:id, or Compojure's
;; wildcard :id segment matcher would swallow "search" as if it were an id - the same class of
;; routing-order gotcha that shows up in most web frameworks, Ring/Compojure included.
(defroutes app-routes
  (GET "/providers" [] {:status 200 :body (list-all)})
  (GET "/providers/search" [specialty]
    {:status 200 :body (search-by-specialty specialty)})
  (GET "/providers/:id" [id]
    (if-let [provider (find-by-external-id id)]
      {:status 200 :body provider}
      {:status 404 :body {:error (str "Provider not found: " id)}}))
  (POST "/providers" {:keys [body]}
    (let [name (str/trim (or (:name body) ""))
          specialty (:specialty body)]
      (if (str/blank? name)
        {:status 400 :body {:error "name must not be blank"}}
        {:status 201 :body (create-provider! {:name name :specialty specialty})})))
  (route/not-found {:error "not found"}))

(def app
  (-> app-routes
      (wrap-json-body {:keywords? true})
      wrap-json-response
      wrap-keyword-params
      wrap-params))

(defn -main [& _]
  (init-db!)
  (println "clojure-datomic-api listening on port 8087")
  (http/run-server app {:port 8087}))

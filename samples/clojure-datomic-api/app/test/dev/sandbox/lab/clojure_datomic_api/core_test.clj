(ns dev.sandbox.lab.clojure-datomic-api.core-test
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [dev.sandbox.lab.clojure-datomic-api.core :as core]))

;; use-fixtures :each + a fresh in-memory database per test is the Clojure/Datomic equivalent
;; of @DirtiesContext or a @BeforeEach re-creating repository state in the Java samples' tests -
;; :mem storage makes this cheap, but only via reset-db! specifically, not init-db!. init-db!
;; alone reconnects to whatever's already there (create-database is a no-op if it exists) - an
;; earlier version of this fixture called init-db! here and every test silently shared one
;; ever-growing database instead of getting a clean one each time.
(use-fixtures :each
  (fn [test-fn]
    (core/reset-db!)
    (test-fn)))

(deftest create-and-find-provider
  (let [created (core/create-provider! {:name "Dr. Rivera" :specialty "Cardiology"})
        found (core/find-by-external-id (:id created))]
    (is (= "Dr. Rivera" (:name found)))
    (is (= "Cardiology" (:specialty found)))))

(deftest find-by-external-id-returns-nil-when-missing
  (is (nil? (core/find-by-external-id "does-not-exist"))))

(deftest list-all-returns-every-provider
  (core/create-provider! {:name "Dr. Ito" :specialty "Pediatrics"})
  (core/create-provider! {:name "Dr. Okafor" :specialty "Dermatology"})
  (is (= 2 (count (core/list-all)))))

(deftest search-by-specialty-filters-correctly
  (core/create-provider! {:name "Dr. Ito" :specialty "Pediatrics"})
  (core/create-provider! {:name "Dr. Kim" :specialty "Pediatrics"})
  (core/create-provider! {:name "Dr. Okafor" :specialty "Dermatology"})

  (let [results (core/search-by-specialty "Pediatrics")]
    (is (= 2 (count results)))
    (is (every? #(= "Pediatrics" (:specialty %)) results))))

(defn- json-request [method uri body]
  {:request-method method
   :uri uri
   :headers {"content-type" "application/json"}
   :body (java.io.ByteArrayInputStream. (.getBytes (json/generate-string body)))})

;; wrap-json-response hands back :body as a plain JSON string, not a stream - unlike a real
;; HTTP round trip through http-kit, where the client would see bytes on a socket. slurp-ing a
;; plain string tries to open it as a *file path* (clojure.java.io's reader dispatch treats a
;; bare String as something to open, not literal content) and throws FileNotFoundException -
;; caught by actually running this test, not assumed. json/parse-string works directly on the
;; string with no slurp needed.
(deftest app-routes-post-then-get
  (testing "creating via the full Ring/Compojure/middleware stack, not just the plain functions"
    (let [post-response (core/app (json-request :post "/providers"
                                                  {:name "Dr. Nguyen" :specialty "Neurology"}))
          created (json/parse-string (:body post-response) true)]
      (is (= 201 (:status post-response)))
      (is (= "Dr. Nguyen" (:name created)))

      (let [get-response (core/app {:request-method :get :uri (str "/providers/" (:id created))})
            fetched (json/parse-string (:body get-response) true)]
        (is (= 200 (:status get-response)))
        (is (= "Neurology" (:specialty fetched)))))))

(deftest app-routes-post-rejects-blank-name
  (let [response (core/app (json-request :post "/providers" {:name "   "}))]
    (is (= 400 (:status response)))))

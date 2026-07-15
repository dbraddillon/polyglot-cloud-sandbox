(ns dev.sandbox.lab.clojure-datomic-api.runner
  (:require [clojure.test :as test]
            [dev.sandbox.lab.clojure-datomic-api.core-test]))

;; A hand-rolled runner instead of a test-runner library: clojure.test/run-tests returns a
;; summary map ({:test .. :pass .. :fail .. :error ..}), not a boolean or an exception on
;; failure, so something has to translate that into a process exit code for deploy.sh to react
;; to - the same job JUnit's Maven Surefire plugin does automatically for the Java samples.
(defn -main [& _]
  (let [{:keys [fail error]} (test/run-tests 'dev.sandbox.lab.clojure-datomic-api.core-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))

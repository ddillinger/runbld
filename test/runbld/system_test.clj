(ns runbld.system-test
  (:require
   [clojure.data :as data]
   [clojure.test :refer :all]
   [runbld.facts.factory :as facter]
   [runbld.fs.factory :as fs]
   [runbld.system :as system]
   [schema.test])
  (:import
   (java.util TimeZone)))

(use-fixtures :once schema.test/validate-schemas)

(defn oshi-facter []
  (with-redefs [facter/facter-version (constantly nil)]
    (system/inspect-system ".")))

(deftest basic
  ;; schema does most of the work here, just want to see if it returns
  ;; a map that matches it
  (testing "OSHI instead of facter"
    (with-redefs [facter/facter-version (constantly nil)]
      (is (oshi-facter))))
  ;; no sense in testing if facter isn't installed
  (if (facter/facter-version)
    (do
      (is (system/inspect-system ".")
          "facter inspection works (when it's installed)")
      (testing "facter and oshi return comparable data"
        (try
          (let [facter-facts (system/inspect-system ".")
                oshi-facts (oshi-facter)]
            (let [[facter oshi both] (data/diff facter-facts oshi-facts)
                  diffs (into {}
                              (for [k (set (concat (keys oshi) (keys facter)))]
                                [k {:facter (get facter k)
                                    :oshi (get oshi k)}]))
                  diffs-ok-keys #{;; These have diffs that are
                                  ;; expected, and oshi is better
                                  :timezone :os-version :os
                                  ;; These have sporadic differences
                                  :uptime-secs
                                  :fs-bytes-used :fs-bytes-free
                                  ;; These are obviously different
                                  :facter-provider :facter-version}
                  tz (TimeZone/getTimeZone (get-in diffs [:timezone :oshi]))
                  tzs (set (.getDisplayName tz true TimeZone/SHORT)
                           (.getDisplayName tz false TimeZone/SHORT))]
              (is (every? diffs-ok-keys (keys diffs))
                  (str "the only keys allowed to be different are "
                       diffs-ok-keys))
              (when (= "Darwin" (get-in diffs [:os :facter]))
                (is (= "macOS" (get-in diffs [:os :oshi]))))
              (is (tzs (get-in diffs [:timezone :facter]))
                  (str "Oshi should show the same timezone, "
                       "but use the (more accurate) long name."))))
          ;; ignore- the previous tests will have already reported this
          (catch Exception _))))
    (println "facter isn't installed, skipping optional tests")))


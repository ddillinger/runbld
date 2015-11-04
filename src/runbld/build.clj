(ns runbld.build
  (:require [runbld.util.data :refer [deep-merge-with deep-merge]]
            [runbld.util.date :as date]
            [slingshot.slingshot :refer [throw+]]))

(defn make-rand-uuid []
  (.replaceAll (str (java.util.UUID/randomUUID)) "-" ""))

(defn make-id []
  (format "%s-%s"
          (date/yyyymmdd-hhmmss)
          (make-rand-uuid)))

(defn split-job-name
  "Why commas? Ask Jenkins..."
  [s]
  (when s
    (let [[job-name org project branch job-name-extra]
          (re-find #"^([^,]+),([^,]+),([^,]+),?([^,]*)?$" s)]
      {:org org
       :project project
       :branch branch
       :job-name-extra job-name-extra
       :job-name job-name
       :profile-name (format "%s-%s-%s" org project branch)})))

(defn inherited-build-info [raw-name]
  (let [{:keys [job-name] :as info} (split-job-name raw-name)]
    (if job-name
      info
      (throw+ {:error ::invalid-job-name
               :msg "please set $JOB_NAME in the format 'org,repo,branch'"}))))


(defn wrap-merge-profile [proc]
  (fn [opts]
    (let [profile (keyword (get-in opts [:build :profile-name]))]
      (proc
       (assoc opts
              :opts (deep-merge-with
                     deep-merge
                     (:opts opts)
                     (get-in opts [:opts :profiles profile])))))))

(defn wrap-build-meta [proc]
  (fn [opts]
    (proc
     (assoc opts
            :build (merge
                    {:id (make-id)
                     :url (get-in opts [:env "BUILD_URL"])
                     :jenkins-number (get-in opts [:env "BUILD_NUMBER"])
                     :node-executor (get-in opts [:env "EXECUTOR_NUMBER"])
                     :host (get-in opts [:env "NODE_NAME"])
                     :labels (get-in opts [:env "NODE_LABELS"])
                     :workspace (get-in opts [:env "WORKSPACE"])}
                    (inherited-build-info
                     (or (get-in opts [:env "JOB_NAME"])
                         (get-in opts [:opts :default-job-name]))))))))

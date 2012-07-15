(ns trapperkeeper.core
  (:use [compojure.core]
        [cheshire.core]
        [clojure.tools.logging :only [info error]]
        [clojure.string :only (join split)]
        [clojure.java.shell :only [sh]])
  (:require [clojure.java.io :as io]
            [clojure.test :as test]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [cheshire.core :as json]
            [cheshire.custom :as custom]
            [trapperkeeper.filters :as filter])
  (:import (java.security MessageDigest)
           (java.util.zip CRC32)
           (java.util Date)
           (java.text SimpleDateFormat)))

(def base_path ".")
(def data_path "./data")
(def cache_path "./cache")
(def cache_ttl (* 2592000 1000)) ; 30 days of seconds * 1000 because Java is weird.
(def expires_format (new SimpleDateFormat "EEE, d MMM yyyy HH:mm:ss"))

(defn make-cache-path [params]
  (let [
    dir (join "/" [cache_path (:bucket params) (:dir params)])
    filename (apply str (first (split (:filename params) #"\.")))
    filter-params (apply str (map (fn [[k v]] (str "_" (name k) "_" v)) (dissoc params :bucket :dir :filename :no-cache)))
    extension (apply str (take-last 1 (split (:filename params) #"\.")))]
    (str dir "/" filename filter-params "." extension)))

(defn make-path [params]
  (join "/" [data_path (:bucket params) (:dir params) (:filename params)]))

(defn make-dir [filepath]
"Take path and create all the necessary directories."
  (let [dir (join "/" (drop-last (split filepath #"/")))]
    (if (= 0 (:exit (sh "mkdir" "-p" dir)))
      (boolean true)
      (boolean false))))

(defn content-type [filepath]
  (let [
    extension (apply str (take-last 1 (split filepath #"\.")))
    types {:jpg "image/jpeg", :jpeg "image/jpeg", :gif "image/gif", :png "image/png"}]
    (if (contains? types (keyword extension)) ((keyword extension) types))))

(defn json-output [data errors]
  (let [body (generate-string {:errors errors, :data data})]
    {:status 200
    :headers {"Content-Type" "application/json"}
    :body body}))

(defn image-output [filepath]
  (try
    (let [fh (io/file filepath)]
      (if (.exists fh)
        {
          :status 200
          :headers 
          {
            "Content-Type" (content-type filepath),
            "Content-Length" (str (.length fh)),
            "Cache-Control" (str "max-age=\"" cache_ttl "\""),
            "Expires" (str (.format expires_format (new Date (+ (.getTime (new Date)) cache_ttl))) " GMT")
          }
          :body fh
        }))
    (catch Exception e {:status 404})))

(defn crc32 [byte-seq]
  (.getValue (doto (CRC32.) (.update (byte-array byte-seq)))))

(defn sha1 [s]
"https://gist.github.com/1472865"
  (->> (-> "sha1"
           java.security.MessageDigest/getInstance
           (.digest (.getBytes s)))
       (map #(.substring
              (Integer/toString
               (+ (bit-and % 0xff) 0x100) 16) 1))
       (apply str)))

(defn gen-delete-key [filepath]
  (sha1 (str filepath (* (.length (io/file filepath)) (count filepath)))))

(defn run-filter [filtername params inpath outpath]
; 1: Check for cache, 2: Check for filter 3: Run filter 4: Return success
;  (try
    (if (and (not (contains? params :no-cache)) (.exists (io/file outpath)))
      (do
        (prn "Returning cached filter output.")
        (boolean true))
      (if (nil? (resolve (symbol (str "trapperkeeper.filters/" filtername))))
        (do
          (prn (str "No filter function found for " filtername))
          (boolean false))
        (do
          (prn (str "Found filter " filtername))
          (make-dir outpath)
          ((resolve (symbol (str "trapperkeeper.filters/" filtername))) params inpath outpath)))))
;  (catch Exception e (do (prn (str "Caught exception: " (.getMessage e))) (boolean false)))))

(defn endpoint_view [params]
  (if (contains? params :filter)
    (let [
      cache-filepath (make-cache-path params)
      image-filepath (make-path params)]
      (if (run-filter (:filter params) params image-filepath cache-filepath)
        (image-output cache-filepath)
        (image-output image-filepath)))
    (image-output (make-path params))))

(defn endpoint_upload [params]
  (json-output {
    (keyword "file.jpg") {
      :url "/testing/12864d5b/6d69f865ce73aee58e922499e2a45723c423ce8d.jpg"
      }} nil))

(defn endpoint_info [params]
  (try
    (let [
      filepath (make-path params)
      fh (with-open [rdr (java.io.FileInputStream. filepath)] (javax.imageio.ImageIO/read rdr))]
      (json-output {
        :type (content-type filepath)
        :size (str (.length (io/file filepath))),
        :width (str (.getWidth fh)),
        :height (str (.getHeight fh)),
        :thumbmail {
          :url (str "/view:thumb/" (:bucket params) "/" (:dir params) "/" (:filename params)),
          :width "250",
          :height "250"
        },
        :delete_key (gen-delete-key filepath)
      } nil))
    (catch Exception e (json-output nil "File not found."))))

(defn endpoint_delete [params]
  (try
    (let [
      filepath (make-path params)
      fh (io/file filepath)]
      (if (= (gen-delete-key) (:key params))
        (if (io/delete-file fh)
          (json-output {:key (:key params), :success true} nil)
          (json-output {:key (:key params), :success false} nil))
        (json-output nil "Invalid deletion key.")))
  (catch Exception e (json-output nil "File not found."))))

(defroutes main-routes
  (GET "/view:filter/:bucket/:dir/:filename" {params :params} (endpoint_view (merge params {:filter (apply str (drop 1 (:filter params)))})))
  (GET "/view/:bucket/:dir/:filename" {params :params} (endpoint_view params))
  (GET "/upload" {params :params} (endpoint_upload params))
  (GET "/info/:bucket/:dir/:filename" {params :params} (endpoint_info params))
  (GET "/delete/:bucket/:dir/:filename" {params :params} (endpoint_delete params))
  (GET "/:bucket/:dir/:filename" {params :params} (endpoint_view params))
  (route/files "/")
  (route/resources "/s" {:root "./public/s"})
  (route/not-found "Page not found"))

(def app
  (handler/api main-routes))
(ns trapperkeeper.core
  (:use [compojure.core]
        [cheshire.core]
        [clojure.tools.logging :only [info error]]
        [clojure.string :only (join split)])
  (:require [clojure.java.io :as io]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [cheshire.core :as json]
            [cheshire.custom :as custom]
            [trapperkeeper.filters :as filters])
  (:import (java.security MessageDigest)
           (java.util.zip CRC32)
           (java.util Date)
           (java.text SimpleDateFormat)))

(def base_path "/var/www/html/announcemedia/fileserver")
(def data_path "/var/www/html/announcemedia/fileserver/data")
(def cache_path "/var/www/html/announcemedia/fileserver/cache")
(def cache_ttl (* 2592000 1000)) ; 30 days of seconds * 1000 because Java is weird.
(def expires_format (new SimpleDateFormat "EEE, d MMM yyyy HH:mm:ss"))

(defn content-type [file]
  (let [
    extension (take-last 1 (split (.toString file) #"\."))
    types [:jpg "image/jpeg", :jpeg "image/jpeg", :gif "image/gif", :png "image/png"]]
    (if (contains? types extension) ((keyword extension) types))))

(defn json-output [data]
  (let [body (generate-string {:errors false, :data data})]
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
            "Content-Type" (content-type fh),
            "Content-Length" (str (.length fh)),
            "Cache-Control" (str "max-age=\"" cache_ttl "\""),
            "Expires" (str (.format expires_format (new Date (+ (.getTime (new Date)) cache_ttl))) " GMT")
          }
          :body fh
        }))
    (catch Exception e {:status 404})))

(defn crc32 [byte-seq]
  (.getValue (doto (CRC32.) (.update (byte-array byte-seq)))))

(defn sha1 [obj] 
  (let [bytes (.getBytes (with-out-str (pr obj)))] 
    (apply vector (.digest (MessageDigest/getInstance "SHA-1") bytes))))

(defn endpoint_view [params]
  (prn params)
  (if (contains? params :filter)
  	(let [filter (apply str (drop 1 (:filter params)))]
  		(str "This is a filtered image with " filter "!"))
    (image-output (join "/" [data_path (:bucket params) (:dir params) (:filename params)]))))

(defn endpoint_upload [params]
  (json-output {
    (keyword "file.jpg") {
      :url "/testing/12864d5b/6d69f865ce73aee58e922499e2a45723c423ce8d.jpg"
      }}))

(defn endpoint_info [params]
  (json-output {
    :type "image/jpeg",
    :size "000",
    :width "250",
    :height "250",
    :thumbmail {
      :url "/testing/12864d5b/6d69f865ce73aee58e922499e2a45723c423ce8d.jpg",
      :width "250",
      :height "250"
    },
    :delete_key "abcdegf123456789"
  }))

(defn endpoint_delete [params]
  (json-output {:key (:key params), :success true}))

(defroutes main-routes
  (GET "/view:filter/:bucket/:dir/:filename" {params :params} (endpoint_view params))	
  (GET "/view/:bucket/:dir/:filename" {params :params} (endpoint_view params))
  (GET "/upload" {params :params} (endpoint_upload params))
  (GET "/info/:bucket/:dir/:filename" {params :params} (endpoint_info params))
  (GET "/delete/:bucket/:dir/:filename" {params :params} (endpoint_delete params))
  (GET "/" {params :params} (endpoint_view params))
  (route/files "/")
  (route/resources "/s" {:root "./public/s"})
  (route/not-found "Page not found"))

(def app
  (handler/api main-routes))
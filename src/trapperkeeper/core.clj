(ns trapperkeeper.core
  (:use compojure.core
  		[cheshire.core]
        [clojure.tools.logging :only [info error]])
  (:require [clojure.java.io :as io]
  			[compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [cheshire.core :as json]
            [cheshire.custom :as custom])
  (:import (java.security MessageDigest)
  		   (java.util.zip CRC32)))

(def base_path "/var/www/html/announcemedia/fileserver")
(def data_path "/var/www/html/announcemedia/fileserver/data")
(def cache_path "/var/www/html/announcemedia/fileserver/cache")

(defn json-output [data]
  (def body (generate-string {:errors false, :data data}))
  {:status 200
  :headers {"Content-Type" "application/json"}
  :body body})

(defn image-output [filename]
  {:status 200
  :headers {"Content-Type" "image/jpeg"}
  :body (clojure.java.io/file filename)})

(defn crc32 [byte-seq]
  (.getValue (doto (CRC32.) (.update (byte-array byte-seq)))))

(defn sha1 [obj] 
  (let [bytes (.getBytes (with-out-str (pr obj)))] 
    (apply vector (.digest (MessageDigest/getInstance "SHA-1") bytes))))

(defn endpoint_view [params]
	(prn params)
	"View endpoint.")

(defn endpoint_upload [params]
	(json-output {
		(keyword "file.jpg") {
			:url "/testing/12864d5b/6d69f865ce73aee58e922499e2a45723c423ce8d.jpg"
		}
		}))

(defn endpoint_info []
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
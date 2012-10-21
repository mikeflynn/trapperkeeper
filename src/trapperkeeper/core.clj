(ns trapperkeeper.core
  (:use [compojure.core]
        [cheshire.core]
        [clojure.set :only [rename-keys]]
        [clojure.walk :only [keywordize-keys]]
        [clojure.tools.logging :only [info error]]
        [clojure.java.shell :only [sh]])
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [ring.middleware [multipart-params :as mp]]
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
(def allowed-types {:jpg "image/jpeg", :jpeg "image/jpeg", :gif "image/gif", :png "image/png"})
(def max-filesize 5000000)

(defn crc32 [string]
  (.getValue (doto (CRC32.) (.update (byte-array (.getBytes string))))))

(defn sha1 [s]
"https://gist.github.com/1472865"
  (->> (-> "sha1"
           java.security.MessageDigest/getInstance
           (.digest (.getBytes s)))
       (map #(.substring
              (Integer/toString
               (+ (bit-and % 0xff) 0x100) 16) 1))
       (apply str)))

(defn make-cache-path [params]
  (let [
    dir (string/join "/" [cache_path (:bucket params) (:dir params)])
    filename (apply str (first (string/split (:filename params) #"\.")))
    filter-params (apply str (map (fn [[k v]] (str "_" (name k) "_" v)) (dissoc params :bucket :dir :filename :no-cache)))
    extension (apply str (take-last 1 (string/split (:filename params) #"\.")))]
    (str dir "/" filename filter-params "." extension)))

(defn make-path [params]
  (string/join "/" [data_path (:bucket params) (:dir params) (:filename params)]))

(defn make-upload-path [params file-name file-contents file-ext]
  (str
    (get params :bucket)
    "/"
    (if (contains? params :custom_path)
      (get params :custom_path)
      (crc32 file-name))
    "/"
    (if (contains? params :custom_filename)
      (get params :custom_filename)
      (sha1 file-contents))
    "."
    file-ext))

(defn make-dir [filepath]
"Take path and create all the necessary directories."
  (let [dir (string/join "/" (drop-last (string/split filepath #"/")))]
    (if (= 0 (:exit (sh "mkdir" "-p" dir)))
      (boolean true)
      (boolean false))))

(defn dl-to-temp [url]
  (let [
    temp-dir (io/file (System/getProperty "java.io.tmpdir"))
    temp-file (str "trapperkeeper-tmp-file-" (System/currentTimeMillis) "-" (long (rand 1000000000)))
    temp-path (str temp-dir "/" temp-file)]
    (try
      (with-open [
        in (io/input-stream url)
        out (io/output-stream temp-path)]
        (io/copy in out))
      temp-path
    (catch Exception e false))))

(defn content-type [filepath]
  (let [extension (string/lower-case (apply str (take-last 1 (string/split filepath #"\."))))]
    (if (contains? allowed-types (keyword extension))
      ((keyword extension) allowed-types)
      (boolean false))))

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

(defn gen-delete-key [filepath]
  (sha1 (str filepath (* (.length (io/file filepath)) (count filepath)))))

(defn run-filter [filtername params inpath outpath]
  (try
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
          ((resolve (symbol (str "trapperkeeper.filters/" filtername))) params inpath outpath))))
  (catch Exception e (do (prn (str "Caught exception: " (.getMessage e))) (boolean false)))))

(defn checkfile [file filename]
"Ensure the uploaded file is an image. Return boolean."
  (if (content-type filename)
    (let [fh (javax.imageio.ImageIO/read file)]
      (if (nil? (.getHeight fh))
        (boolean false)
        (boolean true)))
    (boolean false)))

(defn endpoint_view [params]
  (let [image-filepath (make-path params)]
    (if (.exists (io/file image-filepath))
      (if (contains? params :filter)
        (let [
          cache-filepath (make-cache-path params)]
          (if (run-filter (:filter params) params image-filepath cache-filepath)
            (image-output cache-filepath)
            (image-output image-filepath)))
        (image-output image-filepath))
    {:status 404})))

(defn endpoint_get_upload [params]
"Upload via GET params"
  (try
    (if (and (contains? params :url) (contains? params :bucket))
      (let [
        url (get params :url)
        file-name (apply str (take-last 1 (string/split url #"/")))
        file-ext (apply str (take-last 1 (string/split file-name #"\.")))
        dl-file (io/file (dl-to-temp url))
        new-path (make-upload-path params file-name (slurp url) file-ext)]
        (if (and (<= (.length dl-file) max-filesize) (checkfile dl-file url)) ; filesize of input isn't over the limit and the filetype is allowed... 
          (do
            (make-dir (str data_path "/" new-path))
            (io/copy dl-file (io/file (str data_path "/" new-path)))
            (json-output {
              (keyword file-name) {
                :url (str "/" new-path)
              }} nil))
          (json-output nil "This isn't a valid filetype or is too large for upload.")))
      (json-output nil "No file to process or bucket selected."))
    (catch Exception e (json-output nil "Couldn't process file."))))

(defn endpoint_post_upload [params]
"Upload via POST params"
  (try
    (if (and (contains? params :filedata) (contains? params :bucket))
      (let [
        fileobj (get params :filedata)
        temp-file (fileobj :tempfile)
        file-name (fileobj :filename)
        file-ext (apply str (take-last 1 (string/split file-name #"\.")))
        new-path (make-upload-path params file-name (slurp temp-file) file-ext)]
        (if (and (<= (fileobj :size) max-filesize) (checkfile temp-file file-name))
          (do
            (make-dir (str data_path "/" new-path))
            (io/copy temp-file (io/file (str data_path "/" new-path)))
            (json-output {
              (keyword file-name) {
                :url (str "/" new-path)
              }} nil))
          (do
            (json-output nil "This isn't a valid filetype or is too large for upload."))))
      (json-output nil "No file to process or bucket selected."))
    (catch Exception e (json-output nil "Couldn't process file."))))

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
        :thumbnail {
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

(defn endpoint_page [params]
  (try
    (let [
      url (str "/" (:bucket params) "/" (:dir params) "/" (:filename params))
      url475 (str "/view:resize" url "?w=475")
      html (slurp "resources/page.html")]
      {
        :status 200
        :body (string/replace html "##URL475##" url475)
      })
  (catch Exception e {:status 404})))

(defroutes main-routes
  (route/files "/")
  (route/resources "/")
  (GET "/view:filter/:bucket/*/:filename" {params :params} (endpoint_view (merge params {:filter (apply str (drop 1 (:filter (rename-keys params {:* :dir}))))})))
  (GET "/view/:bucket/*/:filename" {params :params} (endpoint_view (rename-keys params {:* :dir})))
  (GET "/page/:bucket/*/:filename" {params :params} (endpoint_page (rename-keys params {:* :dir})))
  (GET "/upload" {params :params} (endpoint_get_upload params))
  (mp/wrap-multipart-params
    (POST "/upload" {params :params} (endpoint_post_upload (keywordize-keys params))))
  (GET "/info/:bucket/*/:filename" {params :params} (endpoint_info (rename-keys params {:* :dir})))
  (GET "/delete/:bucket/*/:filename" {params :params} (endpoint_delete (rename-keys params {:* :dir})))
  (GET "/:bucket/*/:filename" {params :params} (endpoint_view (rename-keys params {:* :dir})))
  (route/not-found "Page not found"))

(def app
  (handler/api main-routes))

(defn init-app []
  (let [args *command-line-args*]
    (prn args)))

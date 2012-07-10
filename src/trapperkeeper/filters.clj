(ns trapperkeeper.filters
  (:use [clojure.java.shell :only [sh]]))

(defn resize [params outfile infile]
  (if (= 0 (:exit (sh "convert" (str tmp-img-dir "/*") "-append" outfile)))
    true
    false))

(defn banner [params outfile infile]
  (if (= 0 (:exit (sh "convert" (str tmp-img-dir "/*") "-append" outfile)))
    true
    false))

(defn newsprint [params outfile infile]
  (if (= 0 (:exit (sh "convert" (str tmp-img-dir "/*") "-append" outfile)))
    true
    false))

(defn polaroid [params outfile infile]
  (if (= 0 (:exit (sh "convert" (str tmp-img-dir "/*") "-append" outfile)))
    true
    false))

(defn thumb [params outfile infile]
  (if (= 0 (:exit (sh "convert" (str tmp-img-dir "/*") "-append" outfile)))
    true
    false))
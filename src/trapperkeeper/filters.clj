(ns trapperkeeper.filters
  (:use [clojure.java.shell :only [sh]]))

(defn resize [params infile outfile]
; convert -resize '250'x -quality '75' -format jpg '$infile' '$outfile'
  (let [
    defaults {:w "250", :q "75"}
    args (merge defaults params)
    command (sh "convert" "-resize" (str (:w args) "x") "-quality" (:q args) "-format" "jpg" infile outfile)]
    (if (= 0 (:exit command))
        true
        (do (prn command) (boolean true)))))

(defn banner [params outfile infile]
; convert '$infile' -resize '100'x -gravity Center -crop '100'x'25'+0+0 +repage -format jpg -quality 91 '$outfile'	
  (let [
    defaults {:w "100", :h "25", :q "75"}
    args (merge defaults params)
    command (sh "convert" infile "-resize" (str (:w args) "x") "-gravity" "Center" "-crop" (str (:w args) "x" (:h args) "x+0+0") "+repage" "-quality" (:q args) "-format" "jpg" outfile)]
    (if (= 0 (:exit command))
        true
        (do (prn command) (boolean true)))))

(defn newsprint [params outfile infile]
; convert -colorspace Gray '$infile' -thumbnail x'500' -resize ''500'x<' -resize 50% -gravity center -crop '250'x'250'+0+0 +repage -format jpg -quality 91 -ordered-dither h4x4a '$outfile'	
  (let [
    defaults {:w "250", :q "75"}
    args (merge defaults params)
    command (sh "convert" "-colorspace" "Gray" infile "-thumbnail" (str "x" (* 2 (:w args))) "-resize" (str (* 2 (:w args)) "x<") "-resize" "50%" "-gravity" "center" "-crop" (str (:w args) "x" (:w args) "+0+0") "+repage" "-format" "jpg" "-quality" (:q args) "-ordered-dither h4x4a" outfile)]
    (if (= 0 (:exit command))
        true
        (do (prn command) (boolean true)))))

(defn polaroid [params outfile infile]
; convert '$infile' -thumbnail '250'x -quality 75 -bordercolor snow +polaroid -background black \( +clone -shadow 60x4+4+4 \) +swap -background white -flatten '$outfile'	
  (let [
    defaults {:w "250", :q "75"}
    args (merge defaults params)
    command (sh "convert" infile "-thumbnail" (str (:w args) "x") "-quality" (:q args) "-bordercolor" "snow" "+polaroid" "-background black" "\\( +clone -shadow 60x4+4+4 \\)" "+swap" "-background" "white" "-flatten" outfile)]
    (if (= 0 (:exit command))
        true
        (do (prn command) (boolean true)))))

(defn thumb [params outfile infile]
; convert '$infile' -thumbnail x'500' -resize ''500'x<' -resize 50% -gravity center -crop '250'x'250'+0+0 +repage -format jpg -quality 91 '$outfile'
  (let [
    defaults {:w "250", :q "75"}
    args (merge defaults params)
    command (sh "convert" infile "-thumbnail" (str "x" (* 2 (:w args))) "-resize" "50%" "-gravity" "center" "-crop" (str (:w args) "x" (:w args) "+0+0") "+repage" "-quality" (:q args) "-format" "jpg" outfile)]
    (if (= 0 (:exit command))
        true
        (do (prn command) (boolean true)))))
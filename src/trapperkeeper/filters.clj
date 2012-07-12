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
        false)))

(defn banner [params outfile infile]
; convert '$infile' -resize '100'x -gravity Center -crop '100'x'25'+0+0 +repage -format jpg -quality 91 '$outfile'	
  (let [
    defaults {:w 250, :q 75}
    args (merge defaults params)]
    (if (= 0 (:exit (sh "convert" "-resize" (str (:w args) "x") (str "-quality '" (:q args) "'") "-format jpg" infile outfile)))
      true
      false)))

(defn newsprint [params outfile infile]
; convert -colorspace Gray '$infile' -thumbnail x'500' -resize ''500'x<' -resize 50% -gravity center -crop '250'x'250'+0+0 +repage -format jpg -quality 91 -ordered-dither h4x4a '$outfile'	
  (let [
    defaults {:w 250, :q 75}
    args (merge defaults params)]
    (if (= 0 (:exit (sh "convert" "-resize" (str (:w args) "x") (str "-quality '" (:q args) "'") "-format jpg" infile outfile)))
      true
      false)))

(defn polaroid [params outfile infile]
; convert '$infile' -thumbnail '250'x -quality 75 -bordercolor snow +polaroid -background black \( +clone -shadow 60x4+4+4 \) +swap -background white -flatten '$outfile'	
  (let [
    defaults {:w 250, :q 75}
    args (merge defaults params)]
    (if (= 0 (:exit (sh "convert" "-resize" (str (:w args) "x") (str "-quality '" (:q args) "'") "-format jpg" infile outfile)))
      true
      false)))

(defn thumb [params outfile infile]
; convert '$infile' -thumbnail x'500' -resize ''500'x<' -resize 50% -gravity center -crop '250'x'250'+0+0 +repage -format jpg -quality 91 '$outfile'
  (let [
    defaults {:w 250, :q 75}
    args (merge defaults params)]
    (if (= 0 (:exit (sh "convert" "-resize" (str (:w args) "x") (str "-quality '" (:q args) "'") "-format jpg" infile outfile)))
      true
      false)))
(ns status-im.utils.colors
  (:require [clojure.string :as string]))

(defn hex-to-rgb
  [hex]
  ; Expand shorthand form (e.g. "03F") to full form (e.g. "0033FF")
  ; https://stackoverflow.com/questions/59193701/how-to-change-font-color-based-on-background-color-using-reactjs
  (let [shorthandRegex #"(?i)^#?([a-f\d])([a-f\d])([a-f\d])$"
        rgbColor
        (string/replace
         hex
         shorthandRegex
         (fn [r g b] (+ (+ (+ (+ (+ r r) g) g) b) b)))
        result (.exec #"(?i)^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$" rgbColor)]
    (if result [(js/parseInt (aget result 1) 16) ;16 is default value 
                (js/parseInt (aget result 2) 16)
                (js/parseInt (aget result 3) 16)] nil)))

;The first step to convert a red, green, and blue triple (r,g,b) 
;to YCbCr space is to divide each intensity by 255 so that the 
;resulting triple has values in the interval [0,1]. Let's define (r',g',b') = (r/255,g/255,b/255).
;The Y channel is luminance while Cb and Cr are chrominance channels
;We obtain the luminance value y using the formula
;y = .299r' + .587g' + .114b'
;For more detail can visite below link 
;https://www.whydomath.org/node/wavlets/imagebasics.html

(defn color-brightness
  [color]
  (let [[r g b] (hex-to-rgb color)
        targetColor (str "rgb(" r "," g "," b ")")
        colors (.match targetColor #"^rgb\((\d+),\s*(\d+),\s*(\d+)\)$")]
    (.round
     js/Math
     (/
      (+
       (+
        (* (js/parseInt (aget colors 1)) 299)
        (* (js/parseInt (aget colors 2)) 587))
       (* (js/parseInt (aget colors 3)) 114))
      1000))))
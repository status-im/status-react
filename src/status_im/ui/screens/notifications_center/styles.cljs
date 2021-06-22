(ns status-im.ui.screens.notifications-center.styles
  (:require [status-im.ui.components.colors :as colors]
            [quo.design-system.spacing :as spacing]))

(def notification-message-text
  {:flex        1
   :align-self  :stretch
   :line-height 22
   :font-size   15})

(def notification-reply-text
  {:line-height 20
   :font-size   13
   :color       colors/text-gray})

(def mention-text
  {:color colors/blue})

(def datetime-text
  {:color          colors/text-gray
   :font-size      10
   :text-align     :right
   :letter-spacing 0.4
   :align-items    :center
   :line-height    12
   :position       :absolute
   :top            17
   :right          16})

(def group-info-container
  (merge
   {:height          22
    :align-self      :baseline
    :align-items     :center
    :justify-content :center
    :border-radius   11
    :border-color    colors/gray-transparent-40
    :border-width    1
    :margin-top      6
    :margin-bottom   10
    :flex-direction  :row}
   (:x-tiny spacing/padding-horizontal)))

(def reply-message-container
  (merge
   {:height          22
    :align-self      :baseline
    :align-items :center
    :border-radius   11
    :border-color    colors/gray-transparent-40
    :border-width    1
    :margin-top      6
    :margin-bottom   10
    :margin-right    15
    :flex-direction  :row}
   (:x-tiny spacing/padding-horizontal)))

(defn notification-container [read]
  {:min-height       64
   :background-color (when-not read colors/blue-light)})

(def notification-content-container
  {:flex 1})

(def photo-container
  {:position :absolute
   :top      12
   :left     16})

(def title-text
  {:margin-left  72
   :margin-top   12
   :margin-right 50})

(def notification-message-container
  {:margin-left  72
   :margin-right 16})

(def group-icon
  {:margin-right 4})

(def reply-icon
  {:margin-right 1})

(def community-info-container
  {:flex-direction :row
   :align-items    :center})
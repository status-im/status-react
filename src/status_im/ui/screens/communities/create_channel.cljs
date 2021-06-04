(ns status-im.ui.screens.communities.create-channel
  (:require [clojure.string :as str]
            [reagent.core :as reagent]
            [quo.react-native :as rn]
            [quo.core :as quo]
            [status-im.i18n.i18n :as i18n]
            [status-im.ui.components.toolbar :as toolbar]
            [status-im.communities.core :as communities]
            [status-im.ui.components.topbar :as topbar]
            [status-im.utils.debounce :as debounce]))

(def max-name-length 30)

(defn countable-label [{:keys [label value max-length]}]
  [rn/view {:style {:padding-bottom  10
                    :justify-content :space-between
                    :align-items     :flex-end
                    :flex-direction  :row
                    :flex-wrap       :nowrap}}
   [quo/text label]
   [quo/text {:size  :small
              :color (if (> (count value) max-length)
                       :negative
                       :secondary)}

    (str (count value) "/" max-length)]])
(defn valid? [community-name]
  (and
   (<= (count community-name) max-name-length)
   (= (communities/sanitize-name community-name) community-name)
   (not (str/blank? community-name))))

(defn input-text []
  (fn [value reset-fn error]
    [quo/text-input
     {:placeholder    (i18n/label :t/name-your-channel-placeholder)
      :on-change-text reset-fn
      :default-value  value
      :error          error
      :auto-correct   false
      :auto-capitalize :none
      :auto-focus     true}]))

(defn create-channel []
  (let [channel-name (reagent/atom "")]
    (fn []
      (let [valid-channel-name (valid? @channel-name)]
        [:<>
         [topbar/topbar {:title (i18n/label :t/create-channel-title)}]
         [rn/scroll-view {:style                   {:flex 1}
                          :content-container-style {:padding-vertical 16}}
          [rn/view {:style {:padding-bottom     16
                            :padding-top        10
                            :padding-horizontal 16}}
           [countable-label {:label (i18n/label :t/name-your-channel)
                             :value @channel-name
                             :max-length max-name-length}]
           [input-text @channel-name #(reset! channel-name %) (when
                                                               (and (not valid-channel-name)
                                                                    (seq @channel-name))
                                                                (i18n/label :t/community-channel-name-error))]]]

         [toolbar/toolbar
          {:show-border? true
           :center
           [quo/button {:disabled (not valid-channel-name)
                        :type     :secondary
                        :on-press #(debounce/dispatch-and-chill
                                    [::communities/create-channel-confirmation-pressed @channel-name]
                                    3000)}
            (i18n/label :t/create)]}]]))))

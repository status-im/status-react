(ns status-im.ui.screens.popover.views
  (:require-macros [status-im.utils.views :as views])
  (:require [status-im.ui.components.animation :as anim]
            [reagent.core :as reagent]
            [status-im.ui.components.react :as react]
            [re-frame.core :as re-frame]
            [status-im.utils.platform :as platform]
            [status-im.ui.screens.wallet.signing-phrase.views :as signing-phrase]
            [status-im.ui.screens.communities.views :as communities]
            [status-im.ui.screens.wallet.request.views :as request]
            [status-im.ui.screens.profile.user.views :as profile.user]
            ["react-native" :refer (BackHandler)]
            [status-im.ui.components.invite.advertiser :as advertiser.invite]
            [status-im.ui.screens.reset-password.views :as reset-password.views]
            [status-im.ui.components.invite.dapp :as dapp.invite]
            [status-im.ui.screens.multiaccounts.recover.views :as multiaccounts.recover]
            [status-im.ui.screens.multiaccounts.key-storage.views :as multiaccounts.key-storage]
            [status-im.ui.screens.signing.views :as signing]
            [status-im.ui.screens.biometric.views :as biometric]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.screens.keycard.views :as keycard.views]
            [status-im.ui.screens.keycard.frozen-card.view :as frozen-card]
            [status-im.ui.screens.chat.message.pinned-message :as pinned-message]
            [status-im.ui.screens.signing.sheets :as signing-sheets]))

(defn hide-panel-anim
  [bottom-anim-value alpha-value window-height]
  (anim/start
   (anim/parallel
    [(anim/spring bottom-anim-value {:toValue         (- window-height)
                                     :useNativeDriver true})
     (anim/timing alpha-value {:toValue         0
                               :duration        500
                               :useNativeDriver true})])))

(defn show-panel-anim
  [bottom-anim-value alpha-value]
  (anim/start
   (anim/parallel
    [(anim/spring bottom-anim-value {:toValue         0
                                     :useNativeDriver true})
     (anim/timing alpha-value {:toValue         0.4
                               :duration        500
                               :useNativeDriver true})])))

(defn popover-view [_ window-height]
  (let [bottom-anim-value (anim/create-value window-height)
        alpha-value       (anim/create-value 0)
        clear-timeout     (atom nil)
        current-popover   (reagent/atom nil)
        update?           (reagent/atom nil)
        request-close     (fn []
                            (when-not (:prevent-closing? @current-popover)
                              (reset! clear-timeout
                                      (js/setTimeout
                                       #(do (reset! current-popover nil)
                                            (re-frame/dispatch [:hide-popover])) 200))
                              (hide-panel-anim
                               bottom-anim-value alpha-value (- window-height)))
                            true)
        on-show           (fn []
                            (show-panel-anim bottom-anim-value alpha-value)
                            (when platform/android?
                              (.removeEventListener BackHandler
                                                    "hardwareBackPress"
                                                    request-close)
                              (.addEventListener BackHandler
                                                 "hardwareBackPress"
                                                 request-close)))
        on-hide           (fn []
                            (when platform/android?
                              (.removeEventListener BackHandler
                                                    "hardwareBackPress"
                                                    request-close)))]
    (reagent/create-class
     {:UNSAFE_componentWillUpdate
      (fn [_ [_ popover _]]
        (when @clear-timeout (js/clearTimeout @clear-timeout))
        (cond
          @update?
          (do (reset! update? false)
              (on-show))

          (and @current-popover popover)
          (do (reset! update? true)
              (js/setTimeout #(reset! current-popover popover) 600)
              (hide-panel-anim bottom-anim-value alpha-value (- window-height)))

          popover
          (do (reset! current-popover popover)
              (on-show))

          :else
          (do (reset! current-popover nil)
              (on-hide))))
      :component-will-unmount on-hide
      :reagent-render
      (fn []
        (when @current-popover
          (let [{:keys [view style]} @current-popover]
            [react/view {:position :absolute :top 0 :bottom 0 :left 0 :right 0}
             (when platform/ios?
               [react/animated-view
                {:style {:flex 1 :background-color colors/black-persist :opacity alpha-value}}])
             [react/animated-view {:style
                                   {:position  :absolute
                                    :height    window-height
                                    :left      0
                                    :right     0
                                    :transform [{:translateY bottom-anim-value}]}}
              [react/touchable-highlight
               {:style    {:flex 1 :align-items :center :justify-content :center}
                :on-press request-close}
               [react/view (merge {:background-color colors/white
                                   :border-radius    16
                                   :margin           32
                                   :shadow-offset    {:width 0 :height 2}
                                   :shadow-radius    8
                                   :shadow-opacity   1
                                   :shadow-color     "rgba(0, 9, 26, 0.12)"}
                                  style)
                [react/touchable-opacity {:active-opacity 1}
                 (cond
                   (vector? view)
                   view

                   (= :signing-phrase view)
                   [signing-phrase/signing-phrase]

                   (= :share-account view)
                   [request/share-address]

                   (= :share-chat-key view)
                   [profile.user/share-chat-key]

                   (= :custom-seed-phrase view)
                   [multiaccounts.recover/custom-seed-phrase]

                   (= :enable-biometric view)
                   [biometric/enable-biometric-popover]

                   (= :secure-with-biometric view)
                   [biometric/secure-with-biometric-popover]

                   (= :disable-password-saving view)
                   [biometric/disable-password-saving-popover]

                   (= :transaction-data view)
                   [signing/transaction-data]

                   (= :frozen-card view)
                   [frozen-card/frozen-card]

                   (= :blocked-card view)
                   [keycard.views/blocked-card-popover]

                   (= :advertiser-invite view)
                   [advertiser.invite/accept-popover]

                   (= :export-community view)
                   [communities/export-community]

                   (= :dapp-invite view)
                   [dapp.invite/accept-popover]

                   (= :seed-key-uid-mismatch view)
                   [multiaccounts.key-storage/seed-key-uid-mismatch-popover]

                   (= :transfer-multiaccount-to-keycard-warning view)
                   [multiaccounts.key-storage/transfer-multiaccount-warning-popover]

                   (= :transfer-multiaccount-unknown-error view)
                   [multiaccounts.key-storage/unknown-error-popover]

                   (= :password-reset-popover view)
                   [reset-password.views/reset-password-popover]

                   (= :pin-limit view)
                   [pinned-message/pin-limit-popover]

                   (= :fees-warning view)
                   [signing-sheets/fees-warning]

                   :else
                   [view])]]]]])))})))

(views/defview popover []
  (views/letsubs [popover [:popover/popover]
                  {window-height :height} [:dimensions/window]]
    [popover-view popover window-height]))

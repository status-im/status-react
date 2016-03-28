(ns syng-im.components.login
  (:require [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [syng-im.components.react :refer [view text image touchable-highlight
                                              toolbar-android text-input]]
            [syng-im.components.resources :as res]
            [syng-im.components.spinner :refer [spinner]]
            [syng-im.components.nav :as nav]
            [syng-im.utils.utils :refer [log toast http-post]]
            [syng-im.utils.phone-number :refer [format-phone-number]]
            ;; [messenger.android.sign-up-confirm :refer [sign-up-confirm]]
))

(def nav-atom (atom nil))

(defn show-confirm-view []
  (dispatch [:set-loading false])
  ;; TODO 'nav-replace
  (nav/nav-push @nav-atom {:view-id :chat}))

(defn sign-up [user-phone-number user-identity]
  (dispatch [:set-loading true])
  (dispatch [:sign-up user-phone-number user-identity show-confirm-view]))

(defn update-phone-number [value]
  (let [formatted (format-phone-number value)]
    (dispatch [:set-user-phone-number formatted])))

(defn login-view [{:keys [navigator]}]
  (let [loading (subscribe [:get-loading])
        user-phone-number (subscribe [:get-user-phone-number])
        user-identity (subscribe [:get-user-identity])]
    (fn []
      (reset! nav-atom navigator)
      [view {:style {:flex 1}}
       [view {:style {:flex 1
                      :backgroundColor "white"}}
        [toolbar-android {:logo res/logo-icon
                          :title "Login"
                          :titleColor "#4A5258"
                          :style {:backgroundColor "white"
                                  :height 56
                                  :elevation 2}}]
        [view {}
         [text-input {:underlineColorAndroid "#9CBFC0"
                      :placeholder "Enter your phone number"
                      :keyboardType "phone-pad"
                      :onChangeText (fn [value]
                                      (update-phone-number value))
                      :style {:flex 1
                              :marginHorizontal 18
                              :lineHeight 42
                              :fontSize 14
                              :fontFamily "Avenir-Roman"
                              :color "#9CBFC0"}}
          @user-phone-number]
         [touchable-highlight {:onPress #(sign-up @user-phone-number @user-identity)
                               :style {:alignSelf "center"
                                       :borderRadius 7
                                       :backgroundColor "#E5F5F6"
                                       :width 100}}
          [text {:style {:marginVertical 10
                         :textAlign "center"}}
           "Sign up"]]]]
       ;; (when (or loading (not user-identity))
       ;;   [spinner {:visible true}])
       ])))

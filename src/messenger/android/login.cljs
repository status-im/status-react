(ns messenger.android.login
  (:require-macros
   [natal-shell.components :refer [view text image touchable-highlight list-view
                                   toolbar-android text-input]]
   [natal-shell.async-storage :refer [get-item set-item]]
   [natal-shell.core :refer [with-error-view]]
   [natal-shell.alert :refer [alert]])
  (:require [om.next :as om :refer-macros [defui]]
            [re-natal.support :as sup]
            [syng-im.protocol.web3 :as whisper]
            [messenger.state :as state]
            [messenger.android.utils :refer [log toast http-post]]
            [messenger.android.resources :as res]
            [messenger.android.sign-up-confirm :refer [sign-up-confirm]]))

(def nav-atom (atom nil))

(set! js/PhoneNumber (js/require "awesome-phonenumber"))
(def country-code "US")
(def ethereum-rpc-url "http://localhost:8545")

(defn show-confirm-view []
  (binding [state/*nav-render* false]
    (.replace @nav-atom (clj->js {:component sign-up-confirm
                                  :name "sign-up-confirm"}))))

(defn sign-in [phone-number whisper-identity]
  ;; (toast (str "TODO: send number: " phone-number ", "
  ;;             (subs whisper-identity 0 2) ".."
  ;;             (subs whisper-identity (- (count whisper-identity) 2)
  ;;                   (count whisper-identity))))
  (http-post "sign-up"
             {:phone-number phone-number
              :whisper-identity whisper-identity}
             (fn [body]
               (log body)
               (show-confirm-view))))

(defn identity-handler [error result]
  (if error
    (do (toast (str error))
        (.log js/console "error")
        (.log js/console error))
    (toast (str result))))

(defn get-identity [handler]
  (let [web3 (whisper/make-web3 ethereum-rpc-url)]
    (str (.newIdentity (whisper/whisper web3) handler))))


(defn get-whisper-identity-handler [phone-number]
  (fn [identity]
    ;; TODO to test newIdentity. Change to 'identity' to use saved identity.
    (if false ;; identity
      (sign-in phone-number identity)
      (get-identity (fn [error identity]
                      (if (not error)
                        (do (set-item "user-whisper-identity" identity)
                            (swap! state/app-state assoc :user-whisper-identity identity)
                            (sign-in phone-number identity))
                        (toast (str "error" error))))))))

(defn load-user-whisper-identity [handler]
  (get-item "user-whisper-identity"
            (fn [error value]
              (if (not error)
                (let [whisper-identity (when value (str value))]
                  (swap! state/app-state assoc :user-whisper-identity whisper-identity)
                  (handler whisper-identity))
                (toast (str "error" error))))))

(defn handle-phone-number [phone-number]
  (when phone-number
    (load-user-whisper-identity (get-whisper-identity-handler phone-number))))

(defn save-phone-number []
  (let [phone-number (:user-phone-number @state/app-state)]
    (set-item "user-phone-number" phone-number)
    (handle-phone-number phone-number)))

(defn update-phone-number [value]
  (let [formatted (str (.getNumber (js/PhoneNumber. value country-code "international")))]
    (swap! state/app-state assoc :user-phone-number
           formatted)))

(defui Login
  static om/IQuery
  (query [this]
         '[:user-phone-number])
  (render [this]
          (let [{:keys [user-phone-number]} (om/props this)
                {:keys [nav]} (om/get-computed this)]
            (reset! nav-atom nav)
            (view
             {:style {:flex 1
                      :backgroundColor "white"}}
             (toolbar-android {:logo res/logo-icon
                               :title "Login"
                               :titleColor "#4A5258"
                               :style {:backgroundColor "white"
                                       :height 56
                                       :elevation 2}})
             (view {:style {;; :alignItems "center"
                            }}
                   (text-input {:underlineColorAndroid "#9CBFC0"
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
                               user-phone-number)
                   (touchable-highlight {:onPress #(save-phone-number)
                                         :style {:alignSelf "center"
                                                 :borderRadius 7
                                                 :backgroundColor "#E5F5F6"
                                                 :width 100}}
                                        (text {:style {:marginVertical 10
                                                       :textAlign "center"}}
                                              "Sign in")))))))

(def login (om/factory Login))

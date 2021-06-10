(ns status-im.ui.screens.views
  (:require [status-im.utils.universal-links.core :as utils.universal-links]
            [re-frame.core :as re-frame]
            [cljs-bean.core :refer [bean]]
            [status-im.ui.components.react :as react]
            [status-im.ui.screens.routing.core :as navigation]
            [reagent.core :as reagent]
            [status-im.ui.screens.routing.main :as routing]
            [status-im.ui.screens.signing.views :as signing]
            [status-im.ui.screens.popover.views :as popover]
            [status-im.ui.screens.wallet.send.views :as wallet]
            [status-im.ui.components.status-bar.view :as statusbar]
            [status-im.ui.components.colors :as colors]
            [status-im.keycard.test-menu :as keycard.test-menu]
            [status-im.ui.screens.bottom-sheets.views :as bottom-sheets]
            [status-im.utils.config :as config]
            [status-im.reloader :as reloader]
            [status-im.i18n.i18n :as i18n]
            [status-im.wallet.background-check :as background-check]
            ["react-native" :as rn]
            ["react-native-languages" :default react-native-languages]
            ["react-native-shake" :as react-native-shake]))

(def splash-screen (-> rn .-NativeModules .-SplashScreen))

(defn on-languages-change [^js event]
  (i18n/set-language (.-language event)))

(defn on-shake []
  (re-frame/dispatch [:shake-event]))

(defn app-state-change-handler [state]
  (re-frame/dispatch [:app-state-change state]))

(def debug? ^boolean js/goog.DEBUG)

;; Persist navigation state in dev mode
(when debug?
  (defonce state (atom nil))
  (defn- persist-state! [state-obj]
    (js/Promise.
     (fn [resolve _]
       (reset! state state-obj)
       (resolve true)))))

(defn on-state-change [state]
  (let [route-name (navigation/get-active-route-name (bean state))]
    ;; NOTE(Ferossgp): Keycard did-load events backward compatibility
    (re-frame/dispatch [:screens/on-will-focus route-name])

    ;; NOTE(Ferossgp): Both calls are for backward compatibility, should be reworked in future
    (statusbar/set-status-bar route-name)
    (re-frame/dispatch [:set :view-id route-name]))
  (when debug?
    (persist-state! state)))

(defn root [_]
  (reagent/create-class
   {:component-did-mount
    (fn [_]
      (background-check/start-background-fetch)
      (.addEventListener ^js react/app-state "change" app-state-change-handler)
      (.addEventListener react-native-languages "change" on-languages-change)
      (.addEventListener react-native-shake "ShakeEvent" on-shake)
      (.hide ^js splash-screen)
      (utils.universal-links/initialize))
    :component-will-unmount
    (fn []
      (.removeEventListener ^js react/app-state "change" app-state-change-handler)
      (.removeEventListener react-native-languages "change" on-languages-change)
      (utils.universal-links/finalize))
    :display-name   "root"
    :reagent-render (fn []
                      [react/safe-area-provider
                       ^{:key (str @colors/theme @reloader/cnt)}
                       [react/view {:flex             1
                                    :background-color colors/black-persist}
                        [navigation/navigation-container
                         (merge {:ref               (fn [r]
                                                      (navigation/set-navigator-ref r))
                                 :onStateChange     on-state-change
                                 :enableURLHandling false}
                                (when debug?
                                  {:enableURLHandling true
                                   :initialState      @state}))
                         [routing/main-nav-component]]
                        [wallet/select-account]
                        [signing/signing]
                        [bottom-sheets/bottom-sheet]
                        [popover/popover]
                        (when debug?
                          [reloader/reload-view @reloader/cnt])
                        (when config/keycard-test-menu-enabled?
                          [keycard.test-menu/test-menu])]])}))

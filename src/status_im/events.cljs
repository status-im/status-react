(ns status-im.events
  (:require [re-frame.core :as re-frame]
            [status-im.chat.models :as chat]
            [status-im.i18n.i18n :as i18n]
            [status-im.mailserver.core :as mailserver]
            [status-im.multiaccounts.core :as multiaccounts]
            [status-im.ui.components.react :as react]
            [status-im.utils.fx :as fx]
            status-im.utils.logging.core
            [status-im.wallet.core :as wallet]
            [status-im.keycard.core :as keycard]
            [status-im.utils.dimensions :as dimensions]
            [status-im.multiaccounts.biometric.core :as biometric]
            [status-im.constants :as constants]
            [status-im.native-module.core :as status]
            [status-im.ui.components.permissions :as permissions]
            [status-im.utils.utils :as utils]
            [status-im.ethereum.json-rpc :as json-rpc]
            [status-im.anon-metrics.core :as anon-metrics]
            clojure.set
            status-im.currency.core
            status-im.utils.universal-links.core
            status-im.wallet.custom-tokens.core
            status-im.waku.core
            status-im.wallet.choose-recipient.core
            status-im.wallet.accounts.core
            status-im.popover.core
            status-im.bottom-sheet.core
            status-im.add-new.core
            status-im.search.core
            status-im.http.core
            status-im.profile.core
            status-im.chat.models.images
            status-im.ui.screens.privacy-and-security-settings.events
            status-im.multiaccounts.login.core
            status-im.multiaccounts.logout.core
            status-im.multiaccounts.update.core
            status-im.pairing.core
            status-im.privacy-policy.core
            status-im.signals.core
            status-im.stickers.core
            status-im.transport.core
            status-im.init.core
            status-im.log-level.core
            status-im.mailserver.constants
            status-im.ethereum.subscriptions
            status-im.fleet.core
            status-im.contact.block
            status-im.contact.core
            status-im.contact.chat
            status-im.chat.models.input
            status-im.chat.models.loading
            status-im.bootnodes.core
            status-im.browser.core
            status-im.browser.permissions
            status-im.chat.models.transport
            status-im.notifications-center.core
            [status-im.navigation :as navigation]
            [status-im.wallet.background-check :as background-check]))

(re-frame/reg-fx
 :dismiss-keyboard
 (fn []
   (react/dismiss-keyboard!)))

(re-frame/reg-fx
 :request-permissions-fx
 (fn [options]
   (permissions/request-permissions options)))

(re-frame/reg-fx
 :ui/show-error
 (fn [content]
   (utils/show-popup "Error" content)))

(re-frame/reg-fx
 :ui/show-confirmation
 (fn [options]
   (utils/show-confirmation options)))

(re-frame/reg-fx
 :ui/close-application
 (fn [_]
   (status/close-application)))

(re-frame/reg-fx
 ::app-state-change-fx
 (fn [state]
   (status/app-state-change state)))

(re-frame/reg-fx
 :ui/listen-to-window-dimensions-change
 (fn []
   (dimensions/add-event-listener)))

(fx/defn dismiss-keyboard
  {:events [:dismiss-keyboard]}
  [_]
  {:dismiss-keyboard nil})

(fx/defn identicon-generated
  {:events [:identicon-generated]}
  [{:keys [db]} path identicon]
  {:db (assoc-in db path identicon)})

(fx/defn gfycat-generated
  {:events [:gfycat-generated]}
  [{:keys [db]} path gfycat]
  {:db (assoc-in db path gfycat)})

(fx/defn system-theme-mode-changed
  {:events [:system-theme-mode-changed]}
  [{:keys [db]} theme]
  (let [cur-theme (get-in db [:multiaccount :appearance])]
    (when (or (nil? cur-theme) (zero? cur-theme))
      {::multiaccounts/switch-theme (if (= :dark theme) 2 1)})))

(def authentication-options
  {:reason (i18n/label :t/biometric-auth-reason-login)})

(defn- on-biometric-auth-result [{:keys [bioauth-success bioauth-code bioauth-message]}]
  (when-not bioauth-success
    (if (= bioauth-code "USER_FALLBACK")
      (re-frame/dispatch [:multiaccounts.logout.ui/logout-confirmed])
      (utils/show-confirmation {:title               (i18n/label :t/biometric-auth-confirm-title)
                                :content             (or bioauth-message (i18n/label :t/biometric-auth-confirm-message))
                                :confirm-button-text (i18n/label :t/biometric-auth-confirm-try-again)
                                :cancel-button-text  (i18n/label :t/biometric-auth-confirm-logout)
                                :on-accept           #(biometric/authenticate nil on-biometric-auth-result authentication-options)
                                :on-cancel           #(re-frame/dispatch [:multiaccounts.logout.ui/logout-confirmed])}))))

(fx/defn on-return-from-background [{:keys [db now] :as cofx}]
  (let [app-in-background-since (get db :app-in-background-since)
        signed-up? (get-in db [:multiaccount :signed-up?])
        biometric-auth? (= (:auth-method db) "biometric")
        requires-bio-auth (and
                           signed-up?
                           biometric-auth?
                           (some? app-in-background-since)
                           (>= (- now app-in-background-since)
                               constants/ms-in-bg-for-require-bioauth))]
    (fx/merge cofx
              {:db (-> db
                       (dissoc :app-in-background-since)
                       (assoc :app-active-since now))}
              (mailserver/process-next-messages-request)
              (wallet/restart-wallet-service-after-background app-in-background-since)
              #(when requires-bio-auth
                 (biometric/authenticate % on-biometric-auth-result authentication-options)))))

(fx/defn on-going-in-background
  [{:keys [db now] :as cofx}]
  {:db         (-> db
                   (dissoc :app-active-since)
                   (assoc :app-in-background-since now))
   :dispatch-n [[:audio-recorder/on-background] [:audio-message/on-background]]})

(fx/defn app-state-change
  {:events [:app-state-change]}
  [{:keys [db] :as cofx} state]
  (let [app-coming-from-background? (= state "active")
        app-going-in-background? (= state "background")]
    (fx/merge cofx
              {::app-state-change-fx state
               :db                   (assoc db :app-state state)}
              #(when app-coming-from-background?
                 (on-return-from-background %))
              #(when app-going-in-background?
                 (on-going-in-background %))
              (when app-going-in-background?
                (background-check/configure)))))

(fx/defn request-permissions
  {:events [:request-permissions]}
  [_ options]
  {:request-permissions-fx options})

(fx/defn update-window-dimensions
  {:events [:update-window-dimensions]}
  [{:keys [db]} dimensions]
  {:db (assoc db :dimensions/window (dimensions/window dimensions))})

(fx/defn init-timeline-chat
  {:events [:init-timeline-chat]}
  [{:keys [db] :as cofx}]
  (when-not (get-in db [:pagination-info constants/timeline-chat-id :messages-initialized?])
    (chat/preload-chat-data cofx constants/timeline-chat-id)))

(fx/defn on-will-focus
  {:events [:screens/on-will-focus]
   :interceptors [anon-metrics/interceptor]}
  [cofx view-id]
  (fx/merge cofx
            #(case view-id
               :keycard-settings (keycard/settings-screen-did-load %)
               :reset-card (keycard/reset-card-screen-did-load %)
               :enter-pin-settings (keycard/enter-pin-screen-did-load %)
               :keycard-login-pin (keycard/enter-pin-screen-did-load %)
               :add-new-account-pin (keycard/enter-pin-screen-did-load %)
               :keycard-authentication-method (keycard/authentication-method-screen-did-load %)
               :multiaccounts (keycard/multiaccounts-screen-did-load %)
               (:wallet-stack :wallet) (wallet/wallet-will-focus %)
               nil)))

;;TODO :replace by named events
(fx/defn set-event
  {:events [:set]}
  [{:keys [db]} k v]
  {:db (assoc db k v)})

;;TODO :replace by named events
(fx/defn set-once-event
  {:events [:set-once]}
  [{:keys [db]} k v]
  (when-not (get db k)
    {:db (assoc db k v)}))

;;TODO :replace by named events
(fx/defn set-in-event
  {:events [:set-in]}
  [{:keys [db]} path v]
  {:db (assoc-in db path v)})

(defn on-ramp<-rpc [on-ramp]
  (clojure.set/rename-keys on-ramp {:logoUrl :logo-url
                                    :siteUrl :site-url}))

(fx/defn crypto-loaded-event
  {:events [::crypto-loaded]}
  [{:keys [db]} on-ramps]
  {:db (assoc
        db
        :buy-crypto/on-ramps
        (map on-ramp<-rpc on-ramps))})

(fx/defn buy-crypto-ui-loaded
  {:events [:buy-crypto.ui/loaded]}
  [_]
  {::json-rpc/call [{:method     "wallet_getCryptoOnRamps"
                     :params     []
                     :on-success (fn [on-ramps]
                                   (re-frame/dispatch [::crypto-loaded on-ramps]))}]})

(fx/defn open-buy-crypto-screen
  {:events [:buy-crypto.ui/open-screen]}
  [cofx]
  (fx/merge
   cofx
   (navigation/navigate-to :buy-crypto nil)
   (wallet/keep-watching-history)))

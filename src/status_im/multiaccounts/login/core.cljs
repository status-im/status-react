(ns status-im.multiaccounts.login.core
  (:require [re-frame.core :as re-frame]
            [status-im.anon-metrics.core :as anon-metrics]
            [status-im.chat.models.loading :as chat.loading]
            [status-im.contact.core :as contact]
            [status-im.utils.config :as config]
            [status-im.data-store.settings :as data-store.settings]
            [status-im.ethereum.core :as ethereum]
            [status-im.ethereum.transactions.core :as transactions]
            [status-im.ethereum.eip55 :as eip55]
            [status-im.ethereum.json-rpc :as json-rpc]
            [status-im.keycard.common :as keycard.common]
            [status-im.fleet.core :as fleet]
            [status-im.i18n.i18n :as i18n]
            [status-im.multiaccounts.biometric.core :as biometric]
            [status-im.multiaccounts.core :as multiaccounts]
            [status-im.native-module.core :as status]
            [status-im.notifications.core :as notifications]
            [status-im.popover.core :as popover]
            [status-im.communities.core :as communities]
            [status-im.transport.core :as transport]
            [status-im.stickers.core :as stickers]
            [status-im.mobile-sync-settings.core :as mobile-network]
            [status-im.navigation :as navigation]
            [status-im.utils.fx :as fx]
            [status-im.utils.keychain.core :as keychain]
            [status-im.utils.logging.core :as logging]
            [status-im.utils.security :as security]
            [status-im.utils.types :as types]
            [status-im.utils.utils :as utils]
            [status-im.wallet.core :as wallet]
            [status-im.wallet.prices :as prices]
            [status-im.acquisition.core :as acquisition]
            [taoensso.timbre :as log]
            [status-im.data-store.invitations :as data-store.invitations]
            [status-im.chat.models.link-preview :as link-preview]
            [status-im.utils.mobile-sync :as utils.mobile-sync]
            [status-im.async-storage.core :as async-storage]
            [status-im.notifications-center.core :as notifications-center]
            [status-im.wallet.background-check :as background-check]))

(re-frame/reg-fx
 ::initialize-communities-enabled
 (fn []
   (let [callback #(re-frame/dispatch [:multiaccounts.ui/switch-communities-enabled %])]
     (if config/communities-enabled?
       (callback true)
       (async-storage/get-item
        :communities-enabled?
        callback)))))

(re-frame/reg-fx
 ::login
 (fn [[key-uid account-data hashed-password]]
   (status/login key-uid account-data hashed-password)))

(re-frame/reg-fx
 ::export-db
 (fn [[key-uid account-data hashed-password]]
   (status/export-db key-uid account-data hashed-password)))

(re-frame/reg-fx
 ::import-db
 (fn [[key-uid account-data hashed-password]]
   (status/import-db key-uid account-data hashed-password)))

(re-frame/reg-fx
 ::enable-local-notifications
 (fn []
   (status/start-local-notifications)))

(defn rpc->accounts [accounts]
  (reduce (fn [acc {:keys [chat type wallet] :as account}]
            (if chat
              acc
              (let [account (cond->
                             (update account :address
                                     eip55/address->checksum)
                              type
                              (update :type keyword))]
                ;; if the account is the default wallet we
                ;; put it first in the list
                (if wallet
                  (into [account] acc)
                  (conj acc account)))))
          []
          accounts))

(fx/defn initialize-wallet
  {:events [::initialize-wallet]}
  [{:keys [db] :as cofx} accounts custom-tokens
   favourites scan-all-tokens? new-account?]
  (fx/merge
   cofx
   {:db                          (assoc db :multiaccount/accounts
                                        (rpc->accounts accounts))
    ;; NOTE: Local notifications should be enabled only after wallet was started
    ::enable-local-notifications nil}
   (wallet/initialize-tokens custom-tokens)
   (wallet/initialize-favourites favourites)
   (wallet/get-pending-transactions)
   (cond (and new-account?
              (not scan-all-tokens?))
         (wallet/set-zero-balances (first accounts))

         (and new-account? scan-all-tokens?
              (not (utils.mobile-sync/cellular? (:network/type db))))
         (wallet/set-max-block (get (first accounts) :address) 0)

         :else
         (wallet/get-cached-balances scan-all-tokens?))
   (when-not (get db :wallet/new-account)
     (wallet/restart-wallet-service nil))
   (when-not (utils.mobile-sync/syncing-allowed? cofx)
     (transactions/get-fetched-transfers))
   (prices/update-prices)))

(fx/defn login
  {:events [:multiaccounts.login.ui/password-input-submitted]}
  [{:keys [db]}]
  (let [{:keys [key-uid password name identicon]} (:multiaccounts/login db)]
    {:db (-> db
             (assoc-in [:multiaccounts/login :processing] true)
             (dissoc :intro-wizard)
             (update :keycard dissoc :flow))
     ::login [key-uid
              (types/clj->json {:name       name
                                :key-uid    key-uid
                                :identicon  identicon})
              (ethereum/sha3 (security/safe-unmask-data password))]}))

(fx/defn export-db-submitted
  {:events [:multiaccounts.login.ui/export-db-submitted]}
  [{:keys [db]}]
  (let [{:keys [key-uid password name identicon]} (:multiaccounts/login db)]
    {::export-db [key-uid
                  (types/clj->json {:name       name
                                    :key-uid    key-uid
                                    :identicon  identicon})
                  (ethereum/sha3 (security/safe-unmask-data password))]}))

(fx/defn import-db-submitted
  {:events [:multiaccounts.login.ui/import-db-submitted]}
  [{:keys [db]}]
  (let [{:keys [key-uid password name identicon]} (:multiaccounts/login db)]
    {::import-db [key-uid
                  (types/clj->json {:name       name
                                    :key-uid    key-uid
                                    :identicon  identicon})
                  (ethereum/sha3 (security/safe-unmask-data password))]}))

(fx/defn finish-keycard-setup
  [{:keys [db] :as cofx}]
  {:db (update db :keycard dissoc :flow)})

(fx/defn  initialize-dapp-permissions
  {:events [::initialize-dapp-permissions]}
  [{:keys [db]} all-dapp-permissions]
  (let [dapp-permissions (reduce (fn [acc {:keys [dapp] :as dapp-permissions}]
                                   (assoc acc dapp dapp-permissions))
                                 {}
                                 all-dapp-permissions)]
    {:db (assoc db :dapps/permissions dapp-permissions)}))

(fx/defn initialize-browsers
  {:events [::initialize-browsers]}
  [{:keys [db]} all-stored-browsers]
  (let [browsers (reduce (fn [acc {:keys [browser-id] :as browser}]
                           (assoc acc browser-id browser))
                         {}
                         all-stored-browsers)]
    {:db (assoc db :browser/browsers browsers)}))

(fx/defn initialize-bookmarks
  {:events [::initialize-bookmarks]}
  [{:keys [db]} stored-bookmarks]
  (let [bookmarks (reduce (fn [acc {:keys [url] :as bookmark}]
                            (assoc acc url bookmark))
                          {}
                          stored-bookmarks)]
    {:db (assoc db :bookmarks/bookmarks bookmarks)}))

(fx/defn initialize-invitations
  {:events [::initialize-invitations]}
  [{:keys [db]} invitations]
  {:db (assoc db :group-chat/invitations (reduce (fn [acc {:keys [id] :as inv}]
                                                   (assoc acc id (data-store.invitations/<-rpc inv)))
                                                 {}
                                                 invitations))})

(fx/defn initialize-web3-client-version
  {:events [::initialize-web3-client-version]}
  [{:keys [db]} node-version]
  {:db (assoc db :web3-node-version node-version)})

(fx/defn handle-close-app-confirmed
  {:events [::close-app-confirmed]}
  [_]
  {:ui/close-application nil})

(fx/defn check-network-version
  [_ network-id]
  {::json-rpc/call
   [{:method "net_version"
     :on-success
     (fn [fetched-network-id]
       (when (not= network-id fetched-network-id)
         ;;TODO: this shouldn't happen but in case it does
         ;;we probably want a better error message
         (utils/show-popup
          (i18n/label :t/ethereum-node-started-incorrectly-title)
          (i18n/label :t/ethereum-node-started-incorrectly-description
                      {:network-id         network-id
                       :fetched-network-id fetched-network-id})
          #(re-frame/dispatch [::close-app-confirmed]))))}]})

(re-frame/reg-fx
 ;;TODO: this could be replaced by a single API call on status-go side
 ::initialize-wallet
 (fn [callback]
   (-> (js/Promise.all
        (clj->js
         [(js/Promise.
           (fn [resolve reject]
             (json-rpc/call {:method "accounts_getAccounts"
                             :on-success resolve
                             :on-error reject})))
          (js/Promise.
           (fn [resolve reject]
             (json-rpc/call {:method "wallet_getCustomTokens"
                             :on-success resolve
                             :on-error reject})))
          (js/Promise.
           (fn [resolve reject]
             (json-rpc/call {:method "wallet_getFavourites"
                             :on-success resolve
                             :on-error reject})))]))
       (.then (fn [[accounts custom-tokens favourites]]
                (callback accounts
                          (mapv #(update % :symbol keyword) custom-tokens)
                          favourites)))
       (.catch (fn [_]
                 (log/error "Failed to initialize wallet"))))))

(fx/defn initialize-appearance [cofx]
  {::multiaccounts/switch-theme (get-in cofx [:db :multiaccount :appearance])})

(fx/defn get-group-chat-invitations [cofx]
  {::json-rpc/call
   [{:method     (json-rpc/call-ext-method "getGroupChatInvitations")
     :on-success #(re-frame/dispatch [::initialize-invitations %])}]})

(fx/defn initialize-communities-enabled
  [cofx]
  {::initialize-communities-enabled nil})

(fx/defn get-settings-callback
  {:events [::get-settings-callback]}
  [{:keys [db] :as cofx} settings]
  (let [{:keys          [notifications-enabled?]
         :networks/keys [current-network networks]
         :as            settings}
        (data-store.settings/rpc->settings settings)
        multiaccount (dissoc settings :networks/current-network :networks/networks)
        network-id   (str (get-in networks [current-network :config :NetworkId]))]
    (fx/merge cofx
              (cond-> {:db (-> db
                               (dissoc :multiaccounts/login)
                               (assoc :networks/current-network current-network
                                      :networks/networks networks
                                      :multiaccount multiaccount))
                       ::initialize-wallet
                       (fn [accounts custom-tokens favourites]
                         (re-frame/dispatch [::initialize-wallet
                                             accounts custom-tokens favourites]))}
                notifications-enabled?
                (assoc ::notifications/enable nil))
              (acquisition/login)
              (initialize-appearance)
              (transport/start-messenger)
              (initialize-communities-enabled)
              (check-network-version network-id)
              (chat.loading/initialize-chats)
              (communities/fetch)
              (contact/initialize-contacts)
              (stickers/init-stickers-packs)
              (mobile-network/on-network-status-change)
              (get-group-chat-invitations)
              (logging/set-log-level (:log-level multiaccount))
              (multiaccounts/get-profile-picture)
              (multiaccounts/switch-preview-privacy-mode-flag)
              (link-preview/request-link-preview-whitelist)
              (notifications-center/get-activity-center-notifications-count))))

(defn get-new-auth-method [auth-method save-password?]
  (when save-password?
    (when-not (or (= keychain/auth-method-biometric auth-method)
                  (= keychain/auth-method-password auth-method))
      (if (= auth-method keychain/auth-method-biometric-prepare)
        keychain/auth-method-biometric
        keychain/auth-method-password))))

(fx/defn login-only-events
  [{:keys [db] :as cofx} key-uid password save-password?]
  (let [auth-method     (:auth-method db)
        new-auth-method (get-new-auth-method auth-method save-password?)]
    (log/debug "[login] login-only-events"
               "auth-method" auth-method
               "new-auth-method" new-auth-method)
    (fx/merge cofx
              {:db (assoc db :chats/loading? true)
               ::json-rpc/call
               [{:method     "browsers_getBrowsers"
                 :on-success #(re-frame/dispatch [::initialize-browsers %])}
                {:method     "browsers_getBookmarks"
                 :on-success #(re-frame/dispatch [::initialize-bookmarks %])}
                {:method     "permissions_getDappPermissions"
                 :on-success #(re-frame/dispatch [::initialize-dapp-permissions %])}
                {:method     "settings_getSettings"
                 :on-success #(re-frame/dispatch [::get-settings-callback %])}]}
              (notifications/load-notification-preferences)
              (when save-password?
                (keychain/save-user-password key-uid password))
              (keychain/save-auth-method key-uid (or new-auth-method auth-method keychain/auth-method-none)))))

(fx/defn create-only-events
  [{:keys [db] :as cofx}]
  (let [{:keys [multiaccount :multiaccounts/multiaccounts :multiaccount/accounts]} db
        {:keys [creating?]} (:multiaccounts/login db)
        first-account?      (and creating?
                                 (empty? multiaccounts))]
    (fx/merge cofx
              {:db                   (-> db
                                         (dissoc :multiaccounts/login)
                                         (assoc-in [:multiaccount :multiaccounts/first-account] first-account?))
               :dispatch-later       [{:ms 2000 :dispatch [::initialize-wallet accounts nil nil (:recovered multiaccount) true]}]}
              (finish-keycard-setup)
              (transport/start-messenger)
              (chat.loading/initialize-chats)
              (communities/fetch)
              (initialize-communities-enabled)
              (multiaccounts/switch-preview-privacy-mode-flag)
              (link-preview/request-link-preview-whitelist)
              (logging/set-log-level (:log-level multiaccount)))))

(defn- keycard-setup? [cofx]
  (boolean (get-in cofx [:db :keycard :flow])))

(fx/defn multiaccount-login-success
  [{:keys [db now] :as cofx}]
  (let [{:keys [key-uid password save-password? creating?]}
        (:multiaccounts/login db)

        multiaccounts            (:multiaccounts/multiaccounts db)
        recovered-account?       (get db :recovered-account?)
        login-only?              (not (or creating?
                                          recovered-account?
                                          (keycard-setup? cofx)))
        nodes                    nil
        should-send-metrics?     (get-in db [:multiaccount :anon-metrics/should-send?])
        opt-in-screen-displayed? (get db :anon-metrics/opt-in-screen-displayed?)]
    (log/debug "[multiaccount] multiaccount-login-success"
               "login-only?" login-only?
               "recovered-account?" recovered-account?)
    (fx/merge cofx
              {:db (-> db
                       (dissoc :connectivity/ui-status-properties)
                       (update :keycard dissoc
                               :on-card-read
                               :card-read-in-progress?
                               :pin
                               :multiaccount)
                       (assoc :logged-in-since now)
                       (assoc :view-id :home))
               ::json-rpc/call
               [{:method     "web3_clientVersion"
                 :on-success #(re-frame/dispatch [::initialize-web3-client-version %])}]}
              ;; Start tasks to save usage data locally
              (when should-send-metrics?
                (anon-metrics/start-transferring))
              ;;FIXME
              (when nodes
                (fleet/set-nodes :eth.contract nodes))
              (when (and (not login-only?)
                         (not recovered-account?))
                (wallet/set-initial-blocks-range))
              (background-check/start-background-task)
              (if login-only?
                (login-only-events key-uid password save-password?)
                (create-only-events))
              (when (and login-only?
                         (not opt-in-screen-displayed?)
                         config/metrics-enabled?)
                (navigation/navigate-to :anon-metrics-opt-in {})))))

;; FIXME(Ferossgp): We should not copy keys as we denormalize the database,
;; this create desync between actual accounts and the one on login causing broken state
;; UPDATE(cammellos): This code is copying over some fields explicitly as some values
;; are alreayd in `multiaccounts/login` and should not be overriden, as they come from
;; the keychain (save-password), this is not very explicit and we should probably
;; make it clearer
(fx/defn open-login
  [{:keys [db] :as cofx} override-multiaccount]
  (fx/merge cofx
            {:db (-> db
                     (update :multiaccounts/login
                             merge
                             override-multiaccount)
                     (update :multiaccounts/login
                             dissoc
                             :error
                             :password))}
            (keychain/get-auth-method (:key-uid override-multiaccount))))

(fx/defn open-login-callback
  {:events [:multiaccounts.login.callback/get-user-password-success]}
  [{:keys [db] :as cofx} password]
  (let [key-uid          (get-in db [:multiaccounts/login :key-uid])
        keycard-account? (boolean (get-in db [:multiaccounts/multiaccounts
                                              key-uid
                                              :keycard-pairing]))]
    (if password
      (fx/merge
       cofx
       {:db (update-in db [:multiaccounts/login] assoc
                       :password password
                       :save-password? true)}
       (navigation/navigate-to-cofx :intro-stack {:screen :progress})
       login)
      (fx/merge
       cofx
       (when keycard-account?
         {:db (-> db
                  (assoc-in [:keycard :pin :enter-step] :login)
                  (assoc-in [:keycard :pin :status] nil)
                  (assoc-in [:keycard :pin :login] []))})
       (if keycard-account?
         (navigation/navigate-to-cofx :intro-stack {:screen :keycard-login-pin})
         (navigation/navigate-to-cofx :intro-stack {:screen :login}))))))

(fx/defn get-credentials
  [{:keys [db] :as cofx} key-uid]
  (let [keycard-multiaccount? (boolean (get-in db [:multiaccounts/multiaccounts key-uid :keycard-pairing]))]
    (log/debug "[login] get-credentials"
               "keycard-multiacc?" keycard-multiaccount?)
    (if keycard-multiaccount?
      (keychain/get-keycard-keys cofx key-uid)
      (keychain/get-user-password cofx key-uid))))

(fx/defn get-auth-method-success
  "Auth method: nil - not supported, \"none\" - not selected, \"password\", \"biometric\", \"biometric-prepare\""
  {:events [:multiaccounts.login/get-auth-method-success]}
  [{:keys [db] :as cofx} auth-method]
  (let [key-uid               (get-in db [:multiaccounts/login :key-uid])
        keycard-multiaccount? (boolean (get-in db [:multiaccounts/multiaccounts key-uid :keycard-pairing]))]
    (log/debug "[login] get-auth-method-success"
               "auth-method" auth-method
               "keycard-multiacc?" keycard-multiaccount?)
    (fx/merge
     cofx
     {:db (assoc db :auth-method auth-method)}
     #(cond
        (= auth-method keychain/auth-method-biometric)
        (biometric/biometric-auth %)
        (= auth-method keychain/auth-method-password)
        (get-credentials % key-uid)
        (and keycard-multiaccount?
             (get-in db [:keycard :card-connected?]))
        (keycard.common/get-application-info % nil))
     (open-login-callback nil))))

(fx/defn biometric-auth-done
  {:events [:biometric-auth-done]}
  [{:keys [db] :as cofx} {:keys [bioauth-success bioauth-message bioauth-code]}]
  (let [key-uid     (get-in db [:multiaccounts/login :key-uid])
        auth-method (get db :auth-method)]
    (log/debug "[biometric] biometric-auth-done"
               "bioauth-success" bioauth-success
               "bioauth-message" bioauth-message
               "bioauth-code" bioauth-code)
    (if bioauth-success
      (get-credentials cofx key-uid)
      (fx/merge cofx
                {:db (assoc-in db
                               [:multiaccounts/login :save-password?]
                               (= auth-method keychain/auth-method-biometric))}
                (when-not (= auth-method keychain/auth-method-biometric)
                  (keychain/save-auth-method key-uid keychain/auth-method-none))
                (biometric/show-message bioauth-message bioauth-code)
                (open-login-callback nil)))))

(fx/defn save-password
  {:events [:multiaccounts/save-password]}
  [{:keys [db] :as cofx} save-password?]
  (let [bioauth-supported?   (boolean (get db :supported-biometric-auth))
        previous-auth-method (get db :auth-method)]
    (log/debug "[login] save-password"
               "save-password?" save-password?
               "bioauth-supported?" bioauth-supported?
               "previous-auth-method" previous-auth-method)
    (fx/merge
     cofx
     {:db (cond-> db
            (not= previous-auth-method
                  keychain/auth-method-biometric-prepare)
            (assoc :auth-method keychain/auth-method-none)
            (or save-password?
                (not bioauth-supported?)
                (and (not save-password?)
                     bioauth-supported?
                     (= previous-auth-method keychain/auth-method-none)))
            (assoc-in [:multiaccounts/login :save-password?] save-password?))}
     (when bioauth-supported?
       (if save-password?
         (popover/show-popover {:view :secure-with-biometric})
         (when-not (= previous-auth-method keychain/auth-method-none)
           (popover/show-popover {:view :disable-password-saving})))))))

(fx/defn welcome-lets-go
  {:events [::welcome-lets-go]}
  [cofx]
  (let [first-account? (get-in cofx [:db :multiaccount :multiaccounts/first-account])]
    (fx/merge cofx
              (when first-account?
                (acquisition/create))
              (if config/metrics-enabled?
                (navigation/navigate-to :anon-metrics-opt-in {})
                (navigation/navigate-reset {:index  0
                                            :routes [{:name :tabs}]})))))

(fx/defn multiaccount-selected
  {:events [:multiaccounts.login.ui/multiaccount-selected]}
  [{:keys [db] :as cofx} key-uid]
  ;; We specifically pass a bunch of fields instead of the whole multiaccount
  ;; as we want store some fields in multiaccount that are not here
  (let [multiaccount (get-in db [:multiaccounts/multiaccounts key-uid])]
    (fx/merge
     cofx
     {:db (-> db
              (dissoc :intro-wizard :recovered-account?)
              (update :keycard dissoc :application-info))}
     (open-login (select-keys multiaccount [:key-uid :name :public-key :identicon :images])))))

(fx/defn hide-keycard-banner
  {:events [:hide-keycard-banner]}
  [{:keys [db]}]
  {:db                  (assoc db :keycard/banner-hidden true)
   ::async-storage/set! {:keycard-banner-hidden true}})

(fx/defn get-keycard-banner-preference-cb
  {:events [:get-keycard-banner-preference-cb]}
  [{:keys [db]} {:keys [keycard-banner-hidden]}]
  {:db (assoc db :keycard/banner-hidden keycard-banner-hidden)})

(fx/defn get-keycard-banner-preference
  {:events [:get-keycard-banner-preference]}
  [_]
  {::async-storage/get {:keys [:keycard-banner-hidden]
                        :cb   #(re-frame/dispatch [:get-keycard-banner-preference-cb %])}})

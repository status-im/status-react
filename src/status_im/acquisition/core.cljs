(ns status-im.acquisition.core
  (:require [re-frame.core :as re-frame]
            [clojure.string :as string]
            [status-im.utils.fx :as fx]
            [status-im.ethereum.core :as ethereum]
            [status-im.ethereum.ens :as ens]
            [status-im.ethereum.contracts :as contracts]
            [status-im.acquisition.notifications :as notifications]
            [status-im.acquisition.chat :as chat]
            [status-im.acquisition.dapp :as dapp]
            [status-im.acquisition.claim :as claim]
            [status-im.acquisition.advertiser :as advertiser]
            [status-im.acquisition.persistance :as persistence]
            [status-im.acquisition.gateway :as gateway]
            [status-im.utils.config :as config]
            [status-im.acquisition.install-referrer :as install-referrer]
            [status-im.utils.universal-links.core :as links]))

(def not-found-code "notfound.click_id")
(def advertiser-type "advertiser")
(def chat-type "chat")
(def chat-public-type "public-chat")
(def dapp-type "dapp")

(fx/defn handle-registration
  [cofx {:keys [message on-success]}]
  (gateway/handle-acquisition cofx
                              {:message    message
                               :on-success on-success
                               :method     "POST"
                               :url        [:registrations nil]}))

(re-frame/reg-fx
 ::get-referrer
 (fn []
   (persistence/get-referrer-flow-state
    (fn [^js data]
      (install-referrer/get-referrer
       (fn [install-referrer]
         (persistence/set-referrer install-referrer)
         (when (not= install-referrer "unknown")
           (when-let [referrer (install-referrer/parse-referrer install-referrer)]
             (re-frame/dispatch [::has-referrer data referrer])))))))))

(re-frame/reg-fx
 ::check-referrer
 (fn [external-referrer]
   (persistence/get-referrer-flow-state
    (fn [^js data]
      (if external-referrer
        (re-frame/dispatch [::has-referrer data external-referrer])
        (persistence/get-referrer
         (fn [install-referrer]
           (when (not= install-referrer "unknown")
             (when-let [referrer (install-referrer/parse-referrer install-referrer)]
               (re-frame/dispatch [::has-referrer data referrer]))))))))))

(fx/defn referrer-registered
  {:events [::referrer-registered]}
  [{:keys [db] :as cofx} referrer {:keys [type attributed] :as referrer-meta}]
  (when-not attributed
    (fx/merge cofx
              {:db (assoc-in db [:acquisition :metadata] referrer-meta)
               ::notifications/create-channel nil}
              (cond
                (= type advertiser-type)
                (advertiser/start-acquisition referrer-meta)

                (#{chat-type chat-public-type} type)
                (chat/start-acquisition referrer-meta (= type chat-public-type))

                (= type dapp-type)
                (dapp/start-acquisition referrer-meta)))))

(fx/defn outdated-referrer
  {:events [::outdated-referrer]}
  [_ _]
  {::persistence/set-referrer-state :outdated})

(fx/defn has-referrer
  {:events [::has-referrer]}
  [{:keys [db] :as cofx} flow-state referrer]
  (when referrer
    (fx/merge cofx
              {:db (-> db
                       (assoc-in [:acquisition :referrer] referrer)
                       (assoc-in [:acquisition :flow-state] flow-state))}
              #(if (links/universal-link? referrer)
                 {:dispatch [:universal-links/handle-url referrer]}
                 (cond
                   (nil? flow-state)
                   (gateway/get-referrer
                    referrer
                    (fn [resp] [::referrer-registered referrer resp])
                    (fn [{:keys [code]}] (= code not-found-code))
                    (fn [resp]
                      (re-frame/dispatch [::outdated-referrer resp])))

                   (= flow-state (:accepted persistence/referrer-state))
                   (fn [_]
                     {::persistence/check-tx-state (fn [tx]
                                                     (when-not (nil? tx)
                                                       (re-frame/dispatch [::claim/check-transaction-receipt tx])))}))))))

(re-frame/reg-fx
 ::resolve-contract
 (fn [{:keys [chain contract on-success]}]
   (let [register (get ens/ens-registries chain)]
     (when contract
       (if (string/starts-with? contract "0x")
         (on-success contract)
         (ens/get-addr register contract on-success))))))

(fx/defn create [{:keys [db]}]
  (when-not config/google-free
    {::resolve-contract {:chain      (ethereum/chain-keyword db)
                         :contract   (contracts/get-address db :status/acquisition)
                         :on-success #(re-frame/dispatch [:set-in [:acquisition :contract] %])}
     ::get-referrer     nil}))

(fx/defn login [{:keys [db]}]
  (when-not config/google-free
    {::resolve-contract {:chain      (ethereum/chain-keyword db)
                         :contract   (contracts/get-address db :status/acquisition)
                         :on-success #(re-frame/dispatch [:set-in [:acquisition :contract] %])}
     ::check-referrer   nil}))

(re-frame/reg-sub
 ::metadata
 (fn [db]
   (get-in db [:acquisition :metadata])))

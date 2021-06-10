(ns status-im.wallet.background-check
  (:require
   [clojure.string :as clojure.string]
   [re-frame.core :as re-frame]
   [status-im.async-storage.core :as async]
   [status-im.ethereum.json-rpc :as json-rpc]
   [status-im.utils.config :as config]
   [status-im.utils.fx :as fx]
   [status-im.utils.money :as money]
   [status-im.utils.platform :as platform]
   [status-im.utils.types :as types]
   [status-im.notifications.local :as local]
   ["react-native-background-fetch" :default background-fetch]
   [taoensso.timbre :as log]))

(defn finish-task [id]
  (.finish ^js background-fetch id))

(re-frame/reg-fx ::finish-task finish-task)

(fx/defn finish
  {:events [::finish]}
  [{:keys [db]} message]
  (let [task-id (get db :wallet/background-fetch-task-id)]
    {:db           (dissoc db :wallet/background-fetch-task-id)
     :local/local-pushes-ios [{:title   "FINISH"
                               :message (str task-id " " message)}]
     ::finish-task task-id}))

(fx/defn configure
  {:events [::configure]}
  [{:keys [db] :as cofx}]
  (when (and platform/ios? (get db :multiaccount))
    {:local/local-pushes-ios [{:title   "GET CACHED BALANCES"
                               :message "nothing here"}]
     ::json-rpc/call
     [{:method     "wallet_getCachedBalances"
       :params     [(mapv :address (get-in cofx [:db :multiaccount/accounts]))]
       :on-success #(re-frame/dispatch [::retrieved-balances %])
       :on-error   #(re-frame/dispatch [::clean-async-storage])}]}))

(fx/defn retrieved-balances
  {:events [::retrieved-balances]}
  [{:keys [db]} balances]
  (log/debug "Cached balances retrieved" balances)
  {:local/local-pushes-ios [{:title   "STORE BALANCES AND URL"
                             :message (str "balances: " (count balances))}]
   ::async/set!
   {:rpc-url         (config/get-rpc-url db)
    :cached-balances balances}})

(fx/defn clean-async-storage
  {:events [::clean-async-storage]}
  [cofx]
  {::async/set!
   {:rpc-url         nil
    :cached-balances nil}})

(fx/defn perform-check
  {:events [::perform-check]}
  [{:keys [db]} task-id]
  (when platform/ios?
    {:db                     (assoc db :wallet/background-fetch-task-id task-id)
     :local/local-pushes-ios [{:title   "PERFORM CHECK"
                               :message task-id}]
     ::async/get             {:keys [:rpc-url :cached-balances]
                              :cb   #(re-frame/dispatch [::retrieve-latest-balances %])}}))

(defn- prepare-batch-request [method addresses]
  (str
   "["
   (clojure.string/join
    ","
    (mapcat
     (fn [address]
       (map
        types/clj->json
        [{:jsonrpc "2.0"
          :id      address
          :method  method
          :params  [address "latest"]}]))
     addresses))
   "]"))

(defn parse-rpc-response [raw-response]
  (-> raw-response
      :response-body
      types/json->clj))

(fx/defn retrieve-latest-balances
  {:events [::retrieve-latest-balances]}
  [cofx {:keys [rpc-url cached-balances] :as configs}]
  (log/debug "Configuration retrieved" configs)
  (let [addresses (mapv :address cached-balances)]
    {:local/local-pushes-ios (mapv
                              (fn [{:keys [address nonce balance]}]
                                {:title   "CACHED DATA"
                                 :message (clojure.string/join
                                           " "
                                           ["address:" address
                                            "nonce:" nonce
                                            "balance:" balance])})
                              cached-balances)
     :http-post
     {:url      rpc-url
      :data     (prepare-batch-request "eth_getBalance" addresses)
      :on-success
      (fn [result]
        (re-frame/dispatch [::retrieve-latest-nonces
                            addresses
                            configs
                            (parse-rpc-response result)]))
      :on-error #(re-frame/dispatch [::finish %])}}))

(fx/defn retrieve-latest-nonces
  {:events [::retrieve-latest-nonces]}
  [cofx addresses {:keys [rpc-url] :as configs} balances]
  {:local/local-pushes-ios [{:title   "RETRIEVED BALANCES"
                             :message (str "latest balances: " (count balances))}]
   :http-post
   {:url        rpc-url
    :data       (prepare-batch-request "eth_getTransactionCount" addresses)
    :on-success (fn [result]
                  (re-frame/dispatch [::check-results
                                      addresses
                                      configs
                                      balances
                                      (parse-rpc-response result)]))
    :on-error   #(re-frame/dispatch [::finish %])}})

(defn prepare-results
  ([k data] (prepare-results {} k data))
  ([acc k data]
   (reduce
    (fn [acc {:keys [id result]}]
      (assoc-in acc [id k] (money/bignumber result)))
    acc
    data)))

(fx/defn update-cache
  [_ cached-balances addresses latest]
  (let [balances (mapv (fn [{:keys [address] :as cache}]
                         (assoc cache
                                :balance (money/to-string
                                          (get-in latest [address :balance]))
                                :nonce (money/to-string
                                        (get-in latest [address :nonce]))))
                       cached-balances)]
    {:local/local-pushes-ios
     [{:title   "UPDATE-CACHE"
       :message (clojure.string/join
                 "; "
                 (mapv
                  (fn [{:keys [address balance] :as cache}]
                    (str address " " balance))
                  balances))}]

     ::async/set-with-callback!
     {:data     {:cached-balances balances}
      :callback #(re-frame/dispatch [::finish "successfully finished"])}}))

(fx/defn notify
  [cofx addresses latest]
  {:local/local-pushes-ios
   (mapv (fn [address]
           {:title   "TRANSACTION DETECTED"
            :message (clojure.string/join
                      " "
                      [address
                       "nonce:"
                       (money/to-string (get-in latest [address :nonce]))
                       "balance:"
                       (money/to-string (get-in latest [address :balance]))])})
         addresses)})

(fx/defn check-results
  {:events [::check-results]}
  [cofx addresses {:keys [cached-balances]} balances nonces]
  (let [latest (->
                (prepare-results :balance balances)
                (prepare-results :nonce nonces))
        addresses-with-changes
        (keep
         (fn [{:keys [address balance nonce]}]
           (when (or (and
                      (get-in latest [address :balance])
                      (not (money/equals
                            (money/bignumber balance)
                            (get-in latest [address :balance]))))
                     (and
                      (get-in latest [address :nonce])
                      (not (money/equals
                            (money/bignumber nonce)
                            (get-in latest [address :nonce])))))
             address))
         cached-balances)]
    (fx/merge
     cofx
     {:local/local-pushes-ios [{:title   "TASK FINISHED"
                                :message (str addresses-with-changes)}]}
     (update-cache cached-balances addresses-with-changes latest)
     (notify addresses-with-changes latest))))

(defn on-event [task-id]
  (re-frame.core/dispatch [::perform-check task-id]))

(defn on-timeout [task-id]
  (local/local-push-ios
   {:title "ON TIMEOUT"
    :message (str task-id)})
  (finish-task task-id))

(defn start-background-fetch []
  (when platform/ios?
    (.then
     (.configure ^js background-fetch
                 #js {:minimumFetchInterval 20}
                 on-event
                 on-timeout)
     (fn [status]
       (local/local-push-ios
        {:title   "CONFIGURE BG FETCHING"
         :message (str status)})))))

(defn start []
  (when platform/ios?
    (.then
     (.start ^js background-fetch)
     (fn [status]
       (local/local-push-ios
        {:title   "START FETCHING ON LOGIN"
         :message (str status)})))))

(re-frame/reg-fx ::start start)

(fx/defn start-background-task [_]
  {::start nil})

(defn stop []
  (when platform/ios?
    (.then
     (.stop ^js background-fetch "react-native-background-fetch")
     (fn [status]
       (local/local-push-ios
        {:title   "STOP FETCHING ON LOGOUT"
         :message (str status)})))))

(re-frame/reg-fx ::stop stop)

(fx/defn stop-background-task [_]
  {::stop nil})

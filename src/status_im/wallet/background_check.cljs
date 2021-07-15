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
   [taoensso.timbre :as log]))

(fx/defn configure
  {:events [::configure]}
  [cofx]
  (when platform/ios?
    (json-rpc/call
     {:method     "wallet_getCachedBalances"
      :params     [(mapv :address (get-in cofx [:db :multiaccount/accounts]))]
      :on-success #(re-frame/dispatch [::retrieved-balances %])
      :on-error   #(re-frame/dispatch [::clean-async-storage])})))

(fx/defn retrieved-balances
  {:events [::retrieved-balances]}
  [{:keys [db]} balances]
  (log/debug "Cached balances retrieved" balances)
  {::async/set!
   {:rpc-url         (config/get-rpc-url db)
    :cached-balances balances}})

(fx/defn clean-async-storage
  {:events [::clean-async-storage]}
  [_]
  {::async/set!
   {:rpc-url         nil
    :cached-balances nil}})

(fx/defn perform-check
  {:events [::perform-check]}
  [_]
  (when platform/ios?
    {::async/get {:keys [:rpc-url :cached-balances]
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
    {:http-post
     {:url      rpc-url
      :data     (prepare-batch-request "eth_getBalance" addresses)
      :on-success
      (fn [result]
        (re-frame/dispatch [::retrieve-latest-nonces
                            addresses
                            configs
                            (parse-rpc-response result)]))
      :on-error (fn [err] (log/info "Failed to fetch balances" err))}}))

(fx/defn retrieve-latest-nonces
  {:events [::retrieve-latest-nonces]}
  [cofx addresses {:keys [rpc-url] :as configs} balances]
  {:http-post
   {:url        rpc-url
    :data       (prepare-batch-request "eth_getTransactionCount" addresses)
    :on-success (fn [result]
                  (re-frame/dispatch [::check-results
                                      addresses
                                      configs
                                      balances
                                      (parse-rpc-response result)]))
    :on-error   (fn [err] (log/info "Failed to fetch nonces" err))}})

(defn prepare-results
  ([k data] (prepare-results {} k data))
  ([acc k data]
   (reduce
    (fn [acc {:keys [id result]}]
      (assoc-in acc [id k] (money/bignumber result)))
    acc
    data)))

(fx/defn check-results
  {:events [::check-results]}
  [_ addresses {:keys [cached-balances]} balances nonces]
  (let [latest (->
                (prepare-results :balance balances)
                (prepare-results :nonce nonces))]
    (doseq [{:keys [address balance nonce]} cached-balances]
      (println :WUT address balance nonce
               (get-in latest [address :balance])
               (get-in latest [address :nonce]))
      (if (or (not (money/equals
                    (money/bignumber balance)
                    (get-in latest [address :balance])))
              (not (money/equals
                    (money/bignumber nonce)
                    (get-in latest [address :nonce]))))
        (log/info "transaction detected")
        (log/info "balance was not changed")))))

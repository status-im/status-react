(ns status-im.wallet.eip1559
  (:require [re-frame.core :as re-frame]
            [status-im.ethereum.core :as ethereum]
            [status-im.ethereum.json-rpc :as json-rpc]
            [status-im.utils.fx :as fx]
            [status-im.utils.money :as money]))

(defn get-london-block [_]
  "100000000000000")

(fx/defn on-latest-block
  {:events [::on-latest-block]}
  [{:keys [db]} callback-event block]
  (let [network-id   (get-in
                      (ethereum/current-network db)
                      [:config :NetworkId])
        london-block (get-london-block network-id)]
    {:dispatch [callback-event (money/greater-or-equal
                                (money/bignumber (:number block))
                                london-block)]}))

(fx/defn check-london-launch
  {:events [::check-london]}
  [{:keys [db]} callback-event]
  (let [network-id (get-in
                    [:config :NetworkId]
                    (ethereum/current-network db))]
    {::json-rpc/call
     [{:method     "eth_getBlockByNumber"
       :params     ["latest" false]
       :on-success #(re-frame/dispatch [::on-latest-block callback-event %])}]}))

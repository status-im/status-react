(ns status-im.signing.eip1559
  (:require [re-frame.core :as re-frame]
            [status-im.ethereum.json-rpc :as json-rpc]
            [status-im.utils.config :as config]
            [status-im.utils.money :as money]))

(def activation-blocks
  {"123" (money/bignumber 0)})

(defonce activated? (atom {}))

(defonce activated-on-current-network? (atom nil))

(defn get-activation-block [network-id]
  (get activation-blocks (str network-id)))

(defn on-block [network-id callback header]
  (let [london-activated?
        (boolean
         (and
          (get-activation-block network-id)
          (money/greater-than-or-equals
           (money/bignumber (:number header))
           (get-activation-block network-id))))]
    (swap! activated? assoc network-id london-activated?)
    (reset! activated-on-current-network? london-activated?)
    (callback london-activated?)))

(defn check-activation [network-id callback]
  (json-rpc/call
   {:method     "eth_getBlockByNumber"
    :params     ["latest" false]
    :on-success (partial on-block network-id callback)
    :on-error   #(callback nil)}))

(defn sync-enabled? []
  (and config/eip1559-enabled?
       @activated-on-current-network?))

(defn enabled? [network-id enabled-callback disabled-callback]
  (let [london-activated? (get @activated? network-id)]
    (cond
      (not config/eip1559-enabled?)
      (disabled-callback)

      (nil? london-activated?)
      (check-activation
       network-id
       (fn [activated?]
         (if activated?
           (enabled-callback)
           (disabled-callback))))

      london-activated?
      (enabled-callback)

      :else
      (disabled-callback))))

(re-frame/reg-fx
 ::check-eip1559-activation
 (fn [{:keys [network-id on-enabled on-disabled]}]
   (enabled? network-id on-enabled on-disabled)))


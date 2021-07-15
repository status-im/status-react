(ns status-im.utils.fx
  (:require-macros status-im.utils.fx)
  (:require [status-im.ethereum.json-rpc :as json-rpc]
            [taoensso.timbre :as log]
            [status-im.utils.handlers :as handlers])
  (:refer-clojure :exclude [merge reduce]))

(defn- update-db [cofx fx]
  (if-let [db (:db fx)]
    (assoc cofx :db db)
    cofx))

(def ^:private mergeable-keys
  #{:filters/load-filters
    :pairing/set-installation-metadata
    :dispatch-n
    :status-im.ens.core/verify-names
    :shh/send-direct-message
    :shh/remove-filter
    :transport/confirm-messages-processed
    :group-chats/extract-membership-signature
    :utils/dispatch-later
    ::json-rpc/call})

(defn- safe-merge [fx new-fx]
  (if (:merging-fx-with-common-keys fx)
    fx
    (clojure.core/reduce (fn [merged-fx [k v]]
                           (if (= :db k)
                             (assoc merged-fx :db v)
                             (if (get merged-fx k)
                               (if (mergeable-keys k)
                                 (update merged-fx k into v)
                                 (do (log/error "Merging fx with common-key: " k v (get merged-fx k))
                                     (reduced {:merging-fx-with-common-keys k})))
                               (assoc merged-fx k v))))
                         fx
                         new-fx)))

(defn merge
  "Takes a map of co-effects and forms as argument.
  The first optional form can be map of effects
  The next forms are functions applying effects and returning a map of effects.
  The fn ensures that updates to db are passed from function to function within the cofx :db key and
  that only a :merging-fx-with-common-keys effect is returned if some functions are trying
  to produce the same effects (excepted :db, :data-source/tx effects).
  :data-source/tx and effects are handled specially and their results
  (list of transactions) are compacted to one transactions list (for each effect). "
  [{:keys [db] :as cofx} & args]
  (when js/goog.DEBUG
    (swap! handlers/handler-nesting-level inc))
  (let [[first-arg & rest-args] args
        initial-fxs? (map? first-arg)
        fx-fns (if initial-fxs? rest-args args)
        res
        (clojure.core/reduce (fn [fxs fx-fn]
                               (let [updated-cofx (update-db cofx fxs)]
                                 (if fx-fn
                                   (safe-merge fxs (fx-fn updated-cofx))
                                   fxs)))
                             (if initial-fxs? first-arg {:db db})
                             fx-fns)]
    (swap! handlers/handler-nesting-level dec)
    res))

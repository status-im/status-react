(ns status-im.signing.gas
  (:require [re-frame.core :as re-frame]
            [status-im.ethereum.json-rpc :as json-rpc]
            [status-im.i18n.i18n :as i18n]
            [status-im.bottom-sheet.core :as bottom-sheet]
            [status-im.utils.fx :as fx]
            [status-im.utils.money :as money]
            [status-im.signing.eip1559 :as eip1559]
            [taoensso.timbre :as log]
            [status-im.popover.core :as popover]))

(def min-gas-price-wei ^js (money/bignumber 1))

(def min-gas-units ^js (money/bignumber 21000))

(defmulti get-error-label-key (fn [type _] type))

(defmethod get-error-label-key :gasPrice [_ value]
  (cond
    (not value) :t/invalid-number
    (.lt ^js (money/->wei :gwei value) min-gas-price-wei) :t/wallet-send-min-wei
    (-> (money/->wei :gwei value) .decimalPlaces pos?) :t/invalid-number))

(defmethod get-error-label-key :gas [_ ^js value]
  (cond
    (not value) :t/invalid-number
    (.lt value min-gas-units) :t/wallet-send-min-units
    (-> value .decimalPlaces pos?) :t/invalid-number))

(defmethod get-error-label-key :default [_ value]
  (when (or (not value)
            (<= value 0))
    :t/invalid-number))

(defn calculate-max-fee
  [^js gas ^js gasPrice]
  (if (and gas gasPrice)
    (money/to-fixed (money/wei->ether (.times gas gasPrice)))
    "0"))

(defn edit-max-fee [edit]
  (let [gasPrice (get-in edit [:gasPrice :value-number])
        gas      (get-in edit [:gas :value-number])]
    (assoc edit :max-fee (calculate-max-fee gas gasPrice))))

(defn build-edit
  "Takes the previous edit, either :gas or :gas-price and a value as string.
  Wei for gas, and gwei for gas price.
  Validates them and sets max fee"
  [edit-value key value]
  (let [^js bn-value        (money/bignumber value)
        error-label-key (get-error-label-key key bn-value)
        data            (if error-label-key
                          {:value   value
                           :max-fee 0
                           :error   (i18n/label error-label-key)}
                          {:value        value
                           :value-number (if (= :gasPrice key)
                                           (money/->wei :gwei bn-value)
                                           bn-value)})]
    (-> edit-value
        (assoc key data)
        edit-max-fee)))

(def minimum-priority-fee
  (money/wei-> :gwei (money/->wei :gwei 1)))

(def average-priority-fee
  (money/wei-> :gwei (money/->wei :gwei 1.5)))

(defn validate-max-fee [db]
  (let [{:keys [maxFeePerGas maxPriorityFeePerGas]} (get db :signing/edit-fee)
        latest-base-fee (money/wei-> :gwei
                                     (money/bignumber
                                      (get db :wallet/latest-base-fee)))
        fee-error (cond
                    (or (:error maxFeePerGas)
                        (:error maxPriorityFeePerGas))
                    nil

                    (money/greater-than latest-base-fee
                                        (:value-number maxFeePerGas))
                    {:label (i18n/label :t/below-base-fee)
                     :severity :error}

                    (money/greater-than (:value-number maxPriorityFeePerGas)
                                        (money/sub (:value-number maxFeePerGas)
                                                   latest-base-fee))
                    {:label (i18n/label :t/reduced-tip)
                     :severity :error})]
    (if fee-error
      (assoc-in db [:signing/edit-fee :maxFeePerGas :fee-error] fee-error)
      (update-in db [:signing/edit-fee :maxFeePerGas] dissoc :fee-error))))

(defn validate-max-priority-fee [db]
  (let [{:keys [maxPriorityFeePerGas]} (get db :signing/edit-fee)
        fee-error (cond
                    (:error maxPriorityFeePerGas)
                    nil

                    (money/greater-than minimum-priority-fee
                                        (:value-number maxPriorityFeePerGas))
                    {:label (i18n/label :t/low-tip)
                     :severity :error}

                    (money/greater-than average-priority-fee
                                        (:value-number maxPriorityFeePerGas))
                    {:label (i18n/label :t/lower-than-average-tip)
                     :severity :error})]
    (if fee-error
      (assoc-in db [:signing/edit-fee :maxPriorityFeePerGas :fee-error] fee-error)
      (update-in db [:signing/edit-fee :maxPriorityFeePerGas] dissoc :fee-error))))

(defn validate-eip1559-fees [db]
  (if (eip1559/sync-enabled?)
    (reduce
     (fn [acc f]
       (f acc))
     db
     [validate-max-fee
      validate-max-priority-fee])
    db))

(fx/defn edit-value
  {:events [:signing.edit-fee.ui/edit-value]}
  [{:keys [db]} key value]
  {:db (-> db
           (update :signing/edit-fee build-edit key value)
           validate-eip1559-fees)})

(fx/defn set-priority-fee
  {:events [:signing.edit-fee.ui/set-priority-fee]}
  [{:keys [db]} value]
  (let [{:keys [maxFeePerGas maxPriorityFeePerGas]}
        (get db :signing/edit-fee)
        latest-base-fee (get db :wallet/latest-base-fee)
        max-fee-value (:value-number maxFeePerGas)
        max-priority-fee-value (:value-number maxPriorityFeePerGas)
        new-value (money/bignumber value)
        fee-without-tip (money/sub max-fee-value max-priority-fee-value)
        base-fee (money/wei-> :gwei (money/bignumber latest-base-fee))
        new-max-fee-value
        (money/to-fixed
         (if (money/greater-than base-fee fee-without-tip)
           (money/add new-value base-fee)
           (money/add new-value fee-without-tip)))]
    {:db (-> db
             (update :signing/edit-fee build-edit :maxPriorityFeePerGas value)
             (update :signing/edit-fee build-edit :maxFeePerGas new-max-fee-value)
             validate-eip1559-fees)}))

(fx/defn update-estimated-gas-success
  {:events [:signing/update-estimated-gas-success]}
  [{db :db} gas]
  {:db (-> db
           (assoc-in [:signing/tx :gas] gas)
           (assoc-in [:signing/edit-fee :gas-loading?] false))})

(fx/defn update-gas-price-success
  {:events [:signing/update-gas-price-success]}
  [{db :db} price]
  {:db (-> db
           (assoc-in [:signing/tx :gasPrice] price)
           (assoc-in [:signing/edit-fee :gas-price-loading?] false))})

(fx/defn update-estimated-gas-error
  {:events [:signing/update-estimated-gas-error]}
  [{db :db} {:keys [message]}]
  {:db (-> db
           (assoc-in [:signing/edit-fee :gas-loading?] false)
           (assoc-in [:signing/tx :gas-error-message] message))})

(fx/defn update-gas-price-error
  {:events [:signing/update-gas-price-error]}
  [{db :db}]
  {:db (assoc-in db [:signing/edit-fee :gas-price-loading?] false)})

(fx/defn open-fee-sheet
  {:events [:signing.ui/open-fee-sheet]}
  [{{:signing/keys [tx] :as db} :db :as cofx} sheet-opts]
  (let [{:keys [gas gasPrice maxFeePerGas maxPriorityFeePerGas]} tx
        max-fee          (money/to-fixed (money/wei-> :gwei maxFeePerGas))
        max-priority-fee (money/to-fixed (money/wei-> :gwei maxPriorityFeePerGas))
        edit-fee         (reduce (partial apply build-edit)
                                 {}
                                 {:gas                  (money/to-fixed gas)
                                  :gasPrice             (money/to-fixed (money/wei-> :gwei gasPrice))
                                  :maxFeePerGas         max-fee
                                  :maxPriorityFeePerGas max-priority-fee})]
    (fx/merge cofx
              {:db (assoc db :signing/edit-fee edit-fee)}
              (bottom-sheet/show-bottom-sheet {:view sheet-opts}))))

(fx/defn submit-fee
  {:events [:signing.edit-fee.ui/submit]}
  [{{:signing/keys [edit-fee] :as db} :db :as cofx} force?]
  (let [{:keys [gas gasPrice maxFeePerGas maxPriorityFeePerGas]} edit-fee
        errors?
        (keep
         (fn [[k {:keys [fee-error]}]]
           (when (= :error (:severity fee-error))
             [k fee-error]))
         edit-fee)]
    (if (and (seq errors?)
             (not force?))
      (popover/show-popover cofx {:view :fees-warning})
      (fx/merge cofx
                {:db (update db :signing/tx assoc
                             :gas (:value-number gas)
                             :gasPrice (:value-number gasPrice)
                             :maxFeePerGas (money/->wei :gwei (:value-number maxFeePerGas))
                             :maxPriorityFeePerGas (money/->wei :gwei (:value-number maxPriorityFeePerGas)))}
                (bottom-sheet/hide-bottom-sheet)))))

(re-frame/reg-fx
 :signing/update-gas-price
 (fn [{:keys [success-event error-event network-id] :as params}]
   (eip1559/enabled?
    network-id
    (fn []
      (json-rpc/call
       {:method     "eth_getBlockByNumber"
        :params     ["latest" false]
        :on-success #(re-frame/dispatch [::header-fetched
                                         (assoc params :header %)])
        :on-error   #(re-frame/dispatch [error-event %])}))
    (fn []
      (json-rpc/call
       {:method     "eth_gasPrice"
        :on-success #(re-frame/dispatch [success-event %])
        :on-error   #(re-frame/dispatch [error-event %])})))))

(fx/defn header-fetched
  {:events [::header-fetched]}
  [_ {:keys [error-event] :as params}]
  {::json-rpc/call
   [{:method     "eth_maxPriorityFeePerGas"
     :on-success #(re-frame/dispatch [::max-priority-fee-per-gas-fetched
                                      (assoc params :max-priority-fee %)])
     :on-error (if error-event
                 #(re-frame/dispatch [error-event %])
                 #(log/error "Can't fetch header" %))}]})

(def london-block-gas-limit (money/bignumber 30000000))

(defn check-base-fee [{:keys [gasUsed baseFeePerGas]}]
  {:base-fee baseFeePerGas
   :spike?   (or (money/greater-than-or-equals
                  (money/bignumber 0)
                  (money/bignumber gasUsed))
                 (money/greater-than-or-equals
                  (money/bignumber gasUsed)
                  (money/bignumber london-block-gas-limit)))})

(fx/defn max-priority-fee-per-gas-fetched
  {:events [::max-priority-fee-per-gas-fetched]}
  [_ {:keys [success-event header max-priority-fee]}]
  (let [{:keys [base-fee spike?]} (check-base-fee header)]
    {:dispatch [success-event {:base-fee         base-fee
                               :max-priority-fee max-priority-fee
                               :spike?           spike?}]}))

(re-frame/reg-fx
 :signing/update-estimated-gas
 (fn [{:keys [obj success-event error-event]}]
   (json-rpc/call
    {:method     "eth_estimateGas"
     :params     [obj]
     :on-success #(re-frame/dispatch [success-event %])
     :on-error #(re-frame/dispatch [error-event %])})))

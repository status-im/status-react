(ns status-im.signing.core
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame]
            [status-im.constants :as constants]
            [status-im.ethereum.abi-spec :as abi-spec]
            [status-im.ethereum.core :as ethereum]
            [status-im.ethereum.eip55 :as eip55]
            [status-im.ethereum.json-rpc :as json-rpc]
            [status-im.ethereum.tokens :as tokens]
            [status-im.keycard.common :as keycard.common]
            [status-im.i18n.i18n :as i18n]
            [status-im.keycard.card :as keycard.card]
            [status-im.native-module.core :as status]
            [status-im.signing.keycard :as signing.keycard]
            [status-im.utils.fx :as fx]
            [status-im.utils.hex :as utils.hex]
            [status-im.utils.money :as money]
            [status-im.utils.security :as security]
            [status-im.utils.types :as types]
            [status-im.utils.utils :as utils]
            [status-im.wallet.prices :as prices]
            [status-im.wallet.core :as wallet]
            [taoensso.timbre :as log]
            [clojure.set :as clojure.set]
            [status-im.signing.eip1559 :as eip1559]))

(re-frame/reg-fx
 :signing/send-transaction-fx
 (fn [{:keys [tx-obj hashed-password cb]}]
   (status/send-transaction (types/clj->json tx-obj)
                            hashed-password
                            cb)))

(re-frame/reg-fx
 :signing/show-transaction-error
 (fn [message]
   (utils/show-popup (i18n/label :t/transaction-failed) message)))

(re-frame/reg-fx
 :signing/show-transaction-result
 (fn []
   (utils/show-popup (i18n/label :t/transaction-sent) (i18n/label :t/transaction-description))))

(re-frame/reg-fx
 :signing.fx/sign-message
 (fn [{:keys [params on-completed]}]
   (status/sign-message (types/clj->json params)
                        on-completed)))

(re-frame/reg-fx
 :signing.fx/sign-typed-data
 (fn [{:keys [v4 data account on-completed hashed-password]}]
   (if v4
     (status/sign-typed-data-v4 data account hashed-password on-completed)
     (status/sign-typed-data data account hashed-password on-completed))))

(defn get-contact [db to]
  (let [to (utils.hex/normalize-hex to)]
    (or
     (get-in db [:contacts/contacts to])
     {:address (ethereum/normalized-hex to)})))

(fx/defn change-password
  {:events [:signing.ui/password-is-changed]}
  [{db :db} password]
  (let [unmasked-pass (security/safe-unmask-data password)]
    {:db (update db :signing/sign assoc
                 :password password
                 :error    nil
                 :enabled? (and unmasked-pass (> (count unmasked-pass) 5)))}))

(fx/defn sign-message
  [{{:signing/keys [sign tx] :as db} :db}]
  (let [{{:keys [data typed? from v4]} :message} tx
        {:keys [in-progress? password]} sign
        from (or from (ethereum/default-address db))
        hashed-password (ethereum/sha3 (security/safe-unmask-data password))]
    (when-not in-progress?
      (merge
       {:db (update db :signing/sign assoc :error nil :in-progress? true)}
       (if typed?
         {:signing.fx/sign-typed-data {:v4           v4
                                       :data         data
                                       :account      from
                                       :hashed-password hashed-password
                                       :on-completed #(re-frame/dispatch [:signing/sign-message-completed %])}}
         {:signing.fx/sign-message {:params       {:data     data
                                                   :password hashed-password
                                                   :account  from}
                                    :on-completed #(re-frame/dispatch [:signing/sign-message-completed %])}})))))

(fx/defn send-transaction
  {:events [:signing.ui/sign-is-pressed]}
  [{{:signing/keys [sign tx] :as db} :db :as cofx}]
  (let [{:keys [in-progress? password]} sign
        {:keys [tx-obj gas gasPrice maxPriorityFeePerGas maxFeePerGas message]} tx
        hashed-password (ethereum/sha3 (security/safe-unmask-data password))]
    (if message
      (sign-message cofx)
      (let [tx-obj-to-send (merge tx-obj
                                  (when gas
                                    {:gas (str "0x" (abi-spec/number-to-hex gas))})
                                  (when gasPrice
                                    {:gasPrice (str "0x" (abi-spec/number-to-hex gasPrice))})
                                  (when maxPriorityFeePerGas
                                    {:maxPriorityFeePerGas (str "0x" (abi-spec/number-to-hex maxPriorityFeePerGas))})
                                  (when maxFeePerGas
                                    {:maxFeePerGas (str "0x" (abi-spec/number-to-hex maxFeePerGas))}))]
        (when-not in-progress?
          {:db                          (update db :signing/sign assoc :error nil :in-progress? true)
           :signing/send-transaction-fx {:tx-obj   tx-obj-to-send
                                         :hashed-password hashed-password
                                         :cb       #(re-frame/dispatch [:signing/transaction-completed % tx-obj-to-send hashed-password])}})))))

(fx/defn prepare-unconfirmed-transaction
  [{:keys [db now]} new-tx-hash {:keys [value gasPrice gas data to from hash]} symbol amount]
  (let [token (tokens/symbol->token (:wallet/all-tokens db) symbol)
        from  (eip55/address->checksum from)
        ;;if there is a hash in the tx object that means we resending transaction
        old-tx-hash hash
        gas-price (money/to-fixed (money/bignumber gasPrice))
        gas-limit (money/to-fixed (money/bignumber gas))
        tx {:timestamp now
            :to        to
            :from      from
            :type      :pending
            :hash      new-tx-hash
            :data      data
            :token     token
            :symbol    symbol
            :value     (if token
                         (money/to-fixed (money/unit->token amount (:decimals token)))
                         (money/to-fixed (money/bignumber value)))
            :gas-price gas-price
            :gas-limit gas-limit}]
    (log/info "[signing] prepare-unconfirmed-transaction" tx)
    {:db (-> db
             ;;remove old transaction, because we replace it with the new one
             (update-in [:wallet :accounts from :transactions] dissoc old-tx-hash)
             (assoc-in [:wallet :accounts from :transactions new-tx-hash] tx))
     ::json-rpc/call [{:method      "wallet_storePendingTransaction"
                       :params      [(-> tx
                                         (dissoc :gas-price :gas-limit)
                                         (assoc :gasPrice
                                                (money/to-fixed (money/bignumber gasPrice))
                                                :gasLimit (money/to-fixed (money/bignumber gas)))
                                         clj->js)]
                       :on-success #(log/info "pending transfer is saved")
                       :on-failure #(log/info "pending transfer was not saved" %)}]}))

(defn get-method-type [data]
  (cond
    (string/starts-with? data constants/method-id-transfer)
    :transfer
    (string/starts-with? data constants/method-id-approve)
    :approve
    (string/starts-with? data constants/method-id-approve-and-call)
    :approve-and-call))

(defn get-transfer-token [db to data]
  (let [{:keys [symbol decimals] :as token} (tokens/address->token (:wallet/all-tokens db) to)]
    (when (and token data (string? data))
      (when-let [type (get-method-type data)]
        (let [[address value _] (abi-spec/decode
                                 (str "0x" (subs data 10))
                                 (if (= type :approve-and-call) ["address" "uint256" "bytes"] ["address" "uint256"]))]
          (when (and address value)
            {:to       address
             :contact  (get-contact db address)
             :contract to
             :approve? (not= type :transfer)
             :value    value
             :amount   (money/to-fixed (money/token->unit value decimals))
             :token    token
             :symbol   symbol}))))))

(defn parse-tx-obj [db {:keys [from to value data cancel? hash]}]
  (merge {:from    {:address from}
          :cancel? cancel?
          :hash    hash}
         (if (nil? to)
           {:contact {:name (i18n/label :t/new-contract)}}
           (let [eth-value  (when value (money/bignumber value))
                 eth-amount (when eth-value (money/to-fixed (money/wei->ether eth-value)))
                 token      (get-transfer-token db to data)]
             (cond
               (and eth-amount (or (not (.equals ^js (money/bignumber 0)  ^js eth-amount)) (nil? data)))
               {:to      to
                :contact (get-contact db to)
                :symbol  :ETH
                :value   value
                :amount  (str eth-amount)
                :token   (tokens/asset-for (:wallet/all-tokens db) (ethereum/chain-keyword db) :ETH)}
               (not (nil? token))
               token
               :else
               {:to      to
                :contact {:address (ethereum/normalized-hex to)}})))))

(defn prepare-tx [db {{:keys [data gas gasPrice maxFeePerGas maxPriorityFeePerGas] :as tx-obj} :tx-obj :as tx}]
  (merge
   tx
   (parse-tx-obj db tx-obj)
   {:data                 data
    :gas                  (when gas (money/bignumber gas))
    :gasPrice             (when gasPrice (money/bignumber gasPrice))
    :maxFeePerGas         (when maxFeePerGas
                            (money/bignumber maxFeePerGas))
    :maxPriorityFeePerGas (when maxPriorityFeePerGas
                            (money/bignumber maxPriorityFeePerGas))}))

(fx/defn show-sign [{:keys [db] :as cofx}]
  (let [{:signing/keys [queue]} db
        {{:keys [gas gasPrice maxFeePerGas] :as tx-obj} :tx-obj {:keys [data typed? pinless?] :as message} :message :as tx} (last queue)
        keycard-multiaccount? (boolean (get-in db [:multiaccount :keycard-pairing]))
        wallet-set-up-passed? (get-in db [:multiaccount :wallet-set-up-passed?])]
    (if message
      (fx/merge
       cofx
       {:db (assoc db
                   :signing/queue (drop-last queue)
                   :signing/tx tx
                   :signing/sign {:type           (cond pinless? :pinless
                                                        keycard-multiaccount? :keycard
                                                        :else :password)
                                  :formatted-data (if typed? (types/json->clj data) (ethereum/hex->text data))
                                  :keycard-step (when pinless? :connect)})
        :show-signing-sheet nil}
       #(when-not wallet-set-up-passed?
          {:dispatch-n [[:show-popover {:view :signing-phrase}]]})
       (when pinless?
         (keycard.card/start-nfc {:on-success #(re-frame/dispatch [:keycard.callback/start-nfc-success])
                                  :on-failure #(re-frame/dispatch [:keycard.callback/start-nfc-failure])})
         (signing.keycard/hash-message {:data data
                                        :typed? true
                                        :on-completed #(re-frame/dispatch [:keycard/store-hash-and-sign-typed %])})))
      (fx/merge
       cofx
       {:db               (assoc db
                                 :signing/queue (drop-last queue)
                                 :signing/tx (prepare-tx db tx))
        :show-signing-sheet nil
        :dismiss-keyboard nil}
       #(when-not wallet-set-up-passed?
          {:dispatch-n [[:show-popover {:view :signing-phrase}]]})
       (prices/update-prices)
       #(when-not gas
          {:db (assoc-in (:db %) [:signing/edit-fee :gas-loading?] true)
           :signing/update-estimated-gas {:obj           (dissoc tx-obj :gasPrice)
                                          :success-event :signing/update-estimated-gas-success
                                          :error-event :signing/update-estimated-gas-error}})
       #(when-not (or maxFeePerGas gasPrice)
          {:db (assoc-in (:db %) [:signing/edit-fee :gas-price-loading?] true)
           :signing/update-gas-price {:success-event :signing/update-gas-price-success
                                      :error-event :signing/update-gas-price-error
                                      :network-id  (get-in (ethereum/current-network db)
                                                           [:config :NetworkId])}})))))

(fx/defn check-queue [{:keys [db] :as cofx}]
  (let [{:signing/keys [tx queue]} db]
    (when (and (not tx) (seq queue))
      (show-sign cofx))))

(fx/defn send-transaction-message
  {:events [:sign/send-transaction-message]}
  [cofx chat-id value contract transaction-hash signature]
  {::json-rpc/call [{:method (json-rpc/call-ext-method "sendTransaction")
                     ;; We make sure `value` is serialized as string, and not
                     ;; as an integer or big-int
                     :params [chat-id (str value) contract transaction-hash
                              (or (:result (types/json->clj signature))
                                  (ethereum/normalized-hex signature))]
                     :js-response true
                     :on-success
                     #(re-frame/dispatch [:transport/message-sent %])}]})

(fx/defn send-accept-request-transaction-message
  {:events [:sign/send-accept-transaction-message]}
  [cofx message-id transaction-hash signature]
  {::json-rpc/call [{:method (json-rpc/call-ext-method "acceptRequestTransaction")
                     :params [transaction-hash message-id
                              (or (:result (types/json->clj signature))
                                  (ethereum/normalized-hex signature))]
                     :js-response true
                     :on-success
                     #(re-frame/dispatch [:transport/message-sent %])}]})

(fx/defn transaction-result
  [{:keys [db] :as cofx} result tx-obj]
  (let [{:keys [on-result symbol amount]} (get db :signing/tx)]
    (fx/merge cofx
              {:db (dissoc db :signing/tx :signing/sign)
               :signing/show-transaction-result nil}
              (prepare-unconfirmed-transaction result tx-obj symbol amount)
              (check-queue)
              (wallet/watch-tx result)
              #(when on-result
                 {:dispatch (conj on-result result)}))))

(fx/defn command-transaction-result
  [{:keys [db] :as cofx} transaction-hash hashed-password
   {:keys [message-id chat-id from] :as tx-obj}]
  (let [{:keys [on-result symbol amount contract value]} (get db :signing/tx)
        data (str (get-in db [:multiaccount :public-key])
                  (subs transaction-hash 2))]
    (fx/merge
     cofx
     {:db (dissoc db :signing/tx :signing/sign)}
     (wallet/watch-tx transaction-hash)
     (if (keycard.common/keycard-multiaccount? db)
       (signing.keycard/hash-message
        {:data data
         :on-completed
         (fn [hash]
           (re-frame/dispatch
            [:keycard/sign-message
             {:tx-hash transaction-hash
              :message-id message-id
              :chat-id chat-id
              :value value
              :contract contract
              :data data}
             hash]))})
       (fn [_]
         {:signing.fx/sign-message
          {:params {:data     data
                    :password hashed-password
                    :account  from}
           :on-completed
           (fn [res]
             (re-frame/dispatch
              (if message-id
                [:sign/send-accept-transaction-message message-id transaction-hash res]
                [:sign/send-transaction-message
                 chat-id value contract transaction-hash res])))}
          :signing/show-transaction-result nil}))
     (prepare-unconfirmed-transaction transaction-hash tx-obj symbol amount)
     (check-queue)
     #(when on-result
        {:dispatch (conj on-result transaction-hash)}))))

(fx/defn transaction-error
  [{:keys [db]} {:keys [code message]}]
  (let [on-error (get-in db [:signing/tx :on-error])]
    (if (= code constants/send-transaction-err-decrypt)
      ;;wrong password
      {:db (assoc-in db [:signing/sign :error] (i18n/label :t/wrong-password))}
      (merge {:db                             (dissoc db :signing/tx :signing/sign)
              :signing/show-transaction-error message}
             (when on-error
               {:dispatch (conj on-error message)})))))

(fx/defn dissoc-signing-db-entries-and-check-queue
  {:events [:signing/dissoc-entries-and-check-queue]}
  [{:keys [db] :as cofx}]
  (fx/merge cofx
            {:db (dissoc db :signing/tx :signing/sign)}
            check-queue))

(fx/defn sign-message-completed
  {:events [:signing/sign-message-completed]}
  [{:keys [db] :as cofx} result]
  (let [{:keys [result error]} (types/json->clj result)
        on-result (get-in db [:signing/tx :on-result])]
    (if error
      {:db (update db :signing/sign
                   assoc :error  (if (= 5 (:code error))
                                   (i18n/label :t/wrong-password)
                                   (:message error))
                   :in-progress? false)}
      (fx/merge cofx
                (when-not (= (-> db :signing/sign :type) :pinless)
                  (dissoc-signing-db-entries-and-check-queue))
                #(when (= (-> db :signing/sign :type) :pinless)
                   {:dispatch-later [{:ms 3000
                                      :dispatch [:signing/dissoc-entries-and-check-queue]}]})
                #(when on-result
                   {:dispatch (conj on-result result)})))))

(fx/defn transaction-completed
  {:events       [:signing/transaction-completed]
   :interceptors [(re-frame/inject-cofx :random-id-generator)]}
  [cofx response tx-obj hashed-password]
  (let [cofx-in-progress-false (assoc-in cofx [:db :signing/sign :in-progress?] false)
        {:keys [result error]} (types/json->clj response)]
    (if error
      (transaction-error cofx-in-progress-false error)
      (if (:command? tx-obj)
        (command-transaction-result cofx-in-progress-false result hashed-password tx-obj)
        (transaction-result cofx-in-progress-false result tx-obj)))))

(fx/defn discard
  "Discrad transaction signing"
  {:events [:signing.ui/cancel-is-pressed]}
  [{:keys [db] :as cofx}]
  (let [{:keys [on-error]} (get-in db [:signing/tx])]
    (fx/merge cofx
              {:db (-> db
                       (assoc-in [:keycard :pin :status] nil)
                       (dissoc :signing/tx :signing/sign))
               :hide-signing-sheet nil}
              (check-queue)
              (keycard.common/hide-connection-sheet)
              (keycard.common/clear-pin)
              #(when on-error
                 {:dispatch (conj on-error "transaction was cancelled by user")}))))

(defn normalize-tx-obj [db tx]
  (update-in tx [:tx-obj :from] #(eip55/address->checksum (or % (ethereum/default-address db)))))

(fx/defn sign
  "Signing transaction or message, shows signing sheet
   tx
   {:tx-obj - transaction object to send https://github.com/ethereum/wiki/wiki/JavaScript-API#parameters-25
    :message {:address  :data  :typed? } - message data to sign
    :on-result - re-frame event vector
    :on-error - re-frame event vector}"
  [{:keys [db] :as cofx} tx]
  (fx/merge cofx
            {:db (update db :signing/queue conj (normalize-tx-obj db tx))}
            (check-queue)))

(fx/defn eth-transaction-call
  "Prepares tx-obj for contract call and show signing sheet"
  [cofx {:keys [contract method params on-result on-error]}]
  (sign cofx {:tx-obj    {:to   contract
                          :data (abi-spec/encode method params)}
              :on-result on-result
              :on-error  on-error}))

(fx/defn sign-transaction-button-clicked-from-chat
  {:events  [:wallet.ui/sign-transaction-button-clicked-from-chat]}
  [{:keys [db] :as cofx} {:keys [to amount from token]}]
  (let [{:keys [symbol address]} token
        amount-hex (str "0x" (abi-spec/number-to-hex amount))
        to-norm (ethereum/normalized-hex (if (string? to) to (:address to)))
        from-address (:address from)
        identity (:current-chat-id db)
        db (dissoc db :wallet/prepare-transaction)]
    (if to-norm
      (fx/merge
       cofx
       {:db db}
       (sign {:tx-obj (if (= symbol :ETH)
                        {:to    to-norm
                         :from  from-address
                         :chat-id  identity
                         :command? true
                         :value amount-hex}
                        {:to       (ethereum/normalized-hex address)
                         :from     from-address
                         :chat-id  identity
                         :command? true
                         :data     (abi-spec/encode
                                    "transfer(address,uint256)"
                                    [to-norm amount-hex])})}))
      {:db db
       ::json-rpc/call
       [{:method (json-rpc/call-ext-method "requestAddressForTransaction")
         :params [(:current-chat-id db)
                  from-address
                  amount
                  (when-not (= symbol :ETH)
                    address)]
         :js-response true
         :on-success #(re-frame/dispatch [:transport/message-sent %])}]})))

(fx/defn sign-transaction-button-clicked-from-request
  {:events  [:wallet.ui/sign-transaction-button-clicked-from-request]}
  [{:keys [db] :as cofx} {:keys [amount from token]}]
  (let [{:keys [request-parameters chat-id]} (:wallet/prepare-transaction db)
        {:keys [symbol address]} token
        amount-hex (str "0x" (abi-spec/number-to-hex amount))
        to-norm (:address request-parameters)
        from-address (:address from)]
    (fx/merge cofx
              {:db (dissoc db :wallet/prepare-transaction)}
              (fn [cofx]
                (sign
                 cofx
                 {:tx-obj (if (= symbol :ETH)
                            {:to    to-norm
                             :from  from-address
                             :message-id (:id request-parameters)
                             :chat-id chat-id
                             :command? true
                             :value amount-hex}
                            {:to       (ethereum/normalized-hex address)
                             :from     from-address
                             :command? true
                             :message-id (:id request-parameters)
                             :chat-id chat-id
                             :data     (abi-spec/encode
                                        "transfer(address,uint256)"
                                        [to-norm amount-hex])})})))))

(fx/defn sign-transaction-button-clicked
  {:events [:wallet.ui/sign-transaction-button-clicked]}
  [{:keys [db] :as cofx} {:keys [to amount from token gas gasPrice maxFeePerGas maxPriorityFeePerGas]}]
  (let [{:keys [symbol address]} token
        amount-hex   (str "0x" (abi-spec/number-to-hex amount))
        to-norm      (ethereum/normalized-hex (if (string? to) to (:address to)))
        from-address (:address from)]
    (fx/merge cofx
              {:db (dissoc db :wallet/prepare-transaction)}
              (sign
               {:tx-obj (merge (if (eip1559/sync-enabled?)
                                 {:from                 from-address
                                  :gas                  gas
                                  ;; per eip1559
                                  :maxFeePerGas         maxFeePerGas
                                  :maxPriorityFeePerGas maxPriorityFeePerGas}
                                 {:from     from-address
                                  ;;gas and gasPrice from qr (eip681)
                                  :gas      gas
                                  :gasPrice gasPrice})
                               (if (= symbol :ETH)
                                 {:to    to-norm
                                  :value amount-hex}
                                 {:to   (ethereum/normalized-hex address)
                                  :data (abi-spec/encode
                                         "transfer(address,uint256)"
                                         [to-norm amount-hex])}))}))))

(re-frame/reg-fx
 :signing/get-transaction-by-hash-fx
 (fn [[hash handler]]
   (json-rpc/call
    {:method     "eth_getTransactionByHash"
     :params     [hash]
     :on-success handler})))

(fx/defn cancel-transaction-pressed
  {:events [:signing.ui/cancel-transaction-pressed]}
  [_ hash]
  {:signing/get-transaction-by-hash-fx [hash #(re-frame/dispatch [:signing/cancel-transaction %])]})

(fx/defn increase-gas-pressed
  {:events [:signing.ui/increase-gas-pressed]}
  [_ hash]
  {:signing/get-transaction-by-hash-fx [hash #(re-frame/dispatch [:signing/increase-gas %])]})

(fx/defn cancel-transaction
  {:events [:signing/cancel-transaction]}
  [cofx {:keys [from nonce hash]}]
  (when (and from nonce hash)
    (sign cofx {:tx-obj {:from    from
                         :to      from
                         :nonce   nonce
                         :value   "0x0"
                         :cancel? true
                         :hash    hash}})))

(fx/defn increase-gas
  {:events [:signing/increase-gas]}
  [cofx {:keys [from nonce] :as tx}]
  (when (and from nonce)
    (sign cofx {:tx-obj (-> tx
                            (select-keys [:from :to :value :input :gas :nonce :hash])
                            (clojure.set/rename-keys {:input :data}))})))

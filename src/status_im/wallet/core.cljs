(ns status-im.wallet.core
  (:require [re-frame.core :as re-frame]
            [status-im.multiaccounts.update.core :as multiaccounts.update]
            [status-im.utils.config :as config]
            [status-im.qr-scanner.core :as qr-scaner]
            [status-im.ethereum.core :as ethereum]
            [status-im.ethereum.eip55 :as eip55]
            [status-im.ethereum.json-rpc :as json-rpc]
            [status-im.ethereum.tokens :as tokens]
            [status-im.i18n.i18n :as i18n]
            [status-im.navigation :as navigation]
            [status-im.utils.core :as utils.core]
            [status-im.utils.fx :as fx]
            [status-im.utils.money :as money]
            [status-im.utils.utils :as utils.utils]
            [taoensso.timbre :as log]
            [status-im.wallet.db :as wallet.db]
            [clojure.string :as string]
            [status-im.contact.db :as contact.db]
            [status-im.ethereum.ens :as ens]
            [status-im.ethereum.stateofus :as stateofus]
            [status-im.bottom-sheet.core :as bottom-sheet]
            [status-im.wallet.prices :as prices]
            [status-im.wallet.utils :as wallet.utils]
            [status-im.utils.mobile-sync :as mobile-network-utils]
            [status-im.utils.datetime :as datetime]
            status-im.wallet.recipient.core
            [status-im.async-storage.core :as async-storage]
            [status-im.popover.core :as popover.core]
            [status-im.signing.eip1559 :as eip1559]
            [clojure.set :as clojure.set]))

(defn get-balance
  [{:keys [address on-success on-error]}]
  (json-rpc/call
   {:method            "eth_getBalance"
    :params            [address "latest"]
    :on-success        on-success
    :number-of-retries 50
    :on-error          on-error}))

(re-frame/reg-fx
 :wallet/get-balances
 (fn [addresses]
   (doseq [address addresses]
     (get-balance
      {:address    address
       :on-success #(re-frame/dispatch [::update-balance-success address %])
       :on-error   #(re-frame/dispatch [::update-balance-fail %])}))))

(defn assoc-error-message [db error-type err]
  (assoc-in db [:wallet :errors error-type] (or err :unknown-error)))

(re-frame/reg-fx
 :wallet/get-cached-balances
 (fn [{:keys [addresses on-success on-error]}]
   (json-rpc/call
    {:method     "wallet_getCachedBalances"
     :params     [addresses]
     :on-success on-success
     :on-error   on-error})))

(fx/defn get-cached-balances
  [{:keys [db]}  scan-all-tokens?]
  (let [addresses (map (comp string/lower-case :address)
                       (get db :multiaccount/accounts))]
    {:wallet/get-cached-balances
     {:addresses  addresses
      :on-success #(re-frame/dispatch [::set-cached-balances addresses % scan-all-tokens?])
      :on-error   #(re-frame/dispatch [::on-get-cached-balance-fail % scan-all-tokens?])}}))

(fx/defn on-update-balance-fail
  {:events [::update-balance-fail]}
  [{:keys [db]} err]
  (log/debug "Unable to get balance: " err)
  {:db (assoc-error-message db :balance-update :error-unable-to-get-balance)})

(fx/defn on-update-token-balance-fail
  {:events [::update-token-balance-fail]}
  [{:keys [db]} err]
  (log/debug "on-update-token-balance-fail: " err)
  {:db (assoc-error-message db :balance-update :error-unable-to-get-token-balance)})

(fx/defn open-transaction-details
  {:events [:wallet.ui/show-transaction-details]}
  [cofx hash address]
  (navigation/navigate-to-cofx cofx :wallet-transaction-details {:hash hash :address address}))

(defn- validate-token-name!
  [{:keys [address symbol name]}]
  (json-rpc/eth-call
   {:contract address
    :method "name()"
    :outputs ["string"]
    :on-success
    (fn [[contract-name]]
      (when (and (seq contract-name)
                 (not= name contract-name))
        (let [message (i18n/label :t/token-auto-validate-name-error
                                  {:symbol   symbol
                                   :expected name
                                   :actual   contract-name
                                   :address  address})]
          (log/warn message)
          (utils.utils/show-popup (i18n/label :t/warning) message))))}))

(defn- validate-token-symbol!
  [{:keys [address symbol]}]
  (when-not (or (= symbol :DCN) (= symbol :SUPRR)) ;; ignore this symbol because it has weird symbol
    (json-rpc/eth-call
     {:contract address
      :method "symbol()"
      :outputs ["string"]
      :on-success
      (fn [[contract-symbol]]
        ;;NOTE(goranjovic): skipping check if field not set in contract
        (when (and (seq contract-symbol)
                   (not= (clojure.core/name symbol) contract-symbol))
          (let [message (i18n/label :t/token-auto-validate-symbol-error
                                    {:symbol   symbol
                                     :expected (clojure.core/name symbol)
                                     :actual   contract-symbol
                                     :address  address})]
            (log/warn message)
            (utils.utils/show-popup (i18n/label :t/warning) message))))})))

(defn- validate-token-decimals!
  [{:keys [address symbol decimals nft?]}]
  (when-not nft?
    (json-rpc/eth-call
     {:contract address
      :method "decimals()"
      :outputs ["uint256"]
      :on-success
      (fn [[contract-decimals]]
        (when (and (not (nil? contract-decimals))
                   (not= decimals contract-decimals))
          (let [message (i18n/label :t/token-auto-validate-decimals-error
                                    {:symbol   symbol
                                     :expected decimals
                                     :actual   contract-decimals
                                     :address  address})]
            (log/warn message)
            (utils.utils/show-popup (i18n/label :t/warning) message))))})))

(defn dups [seq]
  (for [[id freq] (frequencies seq)
        :when (> freq 1)]
    id))

(re-frame/reg-fx
 :wallet/validate-tokens
 (fn [[tokens all-default-tokens]]
   (let [symb-dups (dups (map :symbol all-default-tokens))
         addr-dups (dups (map :address all-default-tokens))]
     (when (seq symb-dups)
       (utils.utils/show-popup (i18n/label :t/warning) (str "Duplicated tokens symbols" symb-dups)))
     (when (seq addr-dups)
       (utils.utils/show-popup (i18n/label :t/warning) (str "Duplicated tokens addresses" addr-dups)))
     (doseq [token (vals tokens)]
       (validate-token-decimals! token)
       (validate-token-symbol! token)
       (validate-token-name! token)))))

(defn- clean-up-results
  "remove empty balances
   if there is no visible assets, returns all positive balances
   otherwise return only the visible assets balances"
  [results tokens assets]
  (let [balances
        (reduce (fn [acc [address balances]]
                  (let [pos-balances
                        (reduce (fn [acc [token-address token-balance]]
                                  (let [token-symbol (or (get tokens (name token-address))
                                                         (get tokens (eip55/address->checksum (name token-address))))]
                                    (if (or (and (empty? assets) (pos? token-balance))
                                            (and (seq assets) (assets token-symbol)))
                                      (assoc acc token-symbol token-balance)
                                      acc)))
                                {}
                                balances)]
                    (if (not-empty pos-balances)
                      (assoc acc (eip55/address->checksum (name address)) pos-balances)
                      acc)))
                {}
                results)]
    (when (not-empty balances)
      balances)))

(defn get-token-balances
  [{:keys [addresses tokens scan-all-tokens? assets]}]
  (json-rpc/call
   {:method            "wallet_getTokensBalances"
    :params            [addresses (keys tokens)]
    :number-of-retries 50
    :on-success
    (fn [results]
      (when-let [balances (clean-up-results
                           results tokens
                           (if scan-all-tokens? nil assets))]
        (re-frame/dispatch (if scan-all-tokens?
                             ;; NOTE: when there it is not a visible
                             ;; assets we make an initialization round
                             [::tokens-found balances]
                             [::update-tokens-balances-success balances]))))
    :on-error
    #(re-frame/dispatch [::update-token-balance-fail %])}))

(re-frame/reg-fx
 :wallet/get-tokens-balances
 get-token-balances)

(defn rpc->token [tokens]
  (reduce (fn [acc {:keys [address] :as token}]
            (assoc acc
                   address
                   (assoc token :custom? true)))
          {}
          tokens))

(fx/defn initialize-tokens
  [{:keys [db]} custom-tokens]
  (let [all-default-tokens (get tokens/all-default-tokens
                                (ethereum/chain-keyword db))
        default-tokens (utils.core/index-by :address all-default-tokens)
        ;;we want to override custom-tokens by default
        all-tokens     (merge (rpc->token custom-tokens) default-tokens)]
    (merge
     {:db (assoc db :wallet/all-tokens all-tokens)}
     (when config/erc20-contract-warnings-enabled?
       {:wallet/validate-tokens [default-tokens all-default-tokens]}))))

(fx/defn initialize-favourites
  [{:keys [db]} favourites]
  {:db (assoc db :wallet/favourites (reduce (fn [acc {:keys [address] :as favourit}]
                                              (assoc acc address favourit))
                                            {}
                                            favourites))})

(fx/defn update-balances
  {:events [:wallet/update-balances]}
  [{{:keys [network-status :wallet/all-tokens
            multiaccount :multiaccount/accounts] :as db} :db
    :as cofx} addresses scan-all-tokens?]
  (log/debug "update-balances"
             "accounts" addresses
             "scan-all-tokens?" scan-all-tokens?)
  (let [addresses (or addresses (map (comp string/lower-case :address) accounts))
        {:keys [:wallet/visible-tokens]} multiaccount
        chain     (ethereum/chain-keyword db)
        assets    (get visible-tokens chain)
        tokens    (->> (vals all-tokens)
                       (remove #(or (:hidden? %)
                                    ;;if not scan-all-tokens? remove not visible tokens
                                    (and (not scan-all-tokens?)
                                         (not (get assets (:symbol %))))))
                       (reduce (fn [acc {:keys [address symbol]}]
                                 (assoc acc address symbol))
                               {}))]
    (when (and (seq addresses)
               (not= network-status :offline))
      (fx/merge
       cofx
       {:wallet/get-balances        addresses
        :wallet/get-tokens-balances {:addresses        addresses
                                     :tokens           tokens
                                     :assets           assets
                                     :scan-all-tokens? scan-all-tokens?}
        :db                         (prices/clear-error-message db :balance-update)}
       (when-not assets
         (multiaccounts.update/multiaccount-update
          :wallet/visible-tokens (assoc visible-tokens chain (or (config/default-visible-tokens chain)
                                                                 #{}))
          {}))))))

(fx/defn on-get-cached-balance-fail
  {:events [::on-get-cached-balance-fail]}
  [{:keys [db] :as cofx} err scan-all-tokens?]
  (log/warn "Can't fetch cached balances" err)
  (update-balances cofx nil scan-all-tokens?))

(defn- set-checked [tokens-id token-id checked?]
  (let [tokens-id (or tokens-id #{})]
    (if checked?
      (conj tokens-id token-id)
      (disj tokens-id token-id))))

(fx/defn update-balance
  {:events [::update-balance-success]}
  [{:keys [db]} address balance]
  {:db (assoc-in db
                 [:wallet :accounts (eip55/address->checksum address) :balance :ETH]
                 (money/bignumber balance))})

(fx/defn set-cached-balances
  {:events [::set-cached-balances]}
  [cofx addresses balances scan-all-tokens?]
  (apply fx/merge
         cofx
         (update-balances nil scan-all-tokens?)
         (map (fn [{:keys [address balance]}]
                (update-balance address balance))
              balances)))

(defn has-empty-balances? [db]
  (some #(nil? (get-in % [:balance :ETH]))
        (get-in db [:wallet :accounts])))

(fx/defn update-toggle-in-settings
  [{{:keys [multiaccount] :as db} :db :as cofx} symbol checked?]
  (let [chain          (ethereum/chain-keyword db)
        visible-tokens (get multiaccount :wallet/visible-tokens)]
    (fx/merge cofx
              (multiaccounts.update/multiaccount-update
               :wallet/visible-tokens (update visible-tokens
                                              chain
                                              #(set-checked % symbol checked?))
               {})
              #(when checked?
                 (update-balances % nil nil)))))

(fx/defn toggle-visible-token
  {:events [:wallet.settings/toggle-visible-token]}
  [cofx symbol checked?]
  (update-toggle-in-settings cofx symbol checked?))

(fx/defn update-tokens-balances
  {:events [::update-tokens-balances-success]}
  [{:keys [db]} balances]
  (let [accounts (get-in db [:wallet :accounts])]
    {:db (assoc-in db
                   [:wallet :accounts]
                   (reduce (fn [acc [address balances]]
                             (assoc-in acc
                                       [address :balance]
                                       (reduce (fn [acc [token-symbol balance]]
                                                 (assoc acc
                                                        token-symbol
                                                        (money/bignumber balance)))
                                               (get-in accounts [address :balance])
                                               balances)))
                           accounts
                           balances))}))

(fx/defn set-zero-balances
  [cofx {:keys [address]}]
  (fx/merge
   cofx
   (update-balance address 0)
   (update-tokens-balances {address {:SNT 0}})))

(fx/defn configure-token-balance-and-visibility
  {:events [::tokens-found]}
  [{:keys [db] :as cofx} balances]
  (let [chain (ethereum/chain-keyword db)
        visible-tokens (get-in db [:multiaccount :wallet/visible-tokens])
        chain-visible-tokens (into (or (config/default-visible-tokens chain)
                                       #{})
                                   (flatten (map keys (vals balances))))]
    (fx/merge cofx
              (multiaccounts.update/multiaccount-update
               :wallet/visible-tokens
               (update visible-tokens chain clojure.set/union chain-visible-tokens)
               {})
              (update-tokens-balances balances)
              (prices/update-prices))))

(fx/defn add-custom-token
  [cofx {:keys [symbol]}]
  (update-toggle-in-settings cofx symbol true))

(fx/defn remove-custom-token
  [cofx {:keys [symbol]}]
  (update-toggle-in-settings cofx symbol false))

(fx/defn set-and-validate-amount
  {:events [:wallet.send/set-amount-text]}
  [{:keys [db]} amount]
  {:db (assoc-in db [:wallet/prepare-transaction :amount-text] amount)})

(fx/defn wallet-send-gas-price-success
  {:events [:wallet.send/update-gas-price-success]}
  [{db :db} price]
  (if (eip1559/sync-enabled?)
    (let [{:keys [base-fee max-priority-fee]} price
          max-priority-fee-bn (money/bignumber max-priority-fee)]
      {:db (-> db
               (update :wallet/prepare-transaction assoc
                       :maxFeePerGas (money/to-hex (money/add max-priority-fee-bn base-fee))
                       :maxPriorityFeePerGas max-priority-fee)
               (assoc :wallet/latest-base-fee base-fee
                      :wallet/latest-priority-fee max-priority-fee))})
    {:db (assoc-in db [:wallet/prepare-transaction :gasPrice] price)}))

(fx/defn set-max-amount
  {:events [:wallet.send/set-max-amount]}
  [{:keys [db]} {:keys [amount decimals symbol]}]
  (let [^js gas (money/bignumber 21000)
        ^js gasPrice (get-in db [:wallet/prepare-transaction :gasPrice])
        ^js fee (when gasPrice (.times gas gasPrice))
        amount-text (if (= :ETH symbol)
                      (when (and fee (money/sufficient-funds? fee amount))
                        (str (wallet.utils/format-amount (.minus amount fee) decimals)))
                      (str (wallet.utils/format-amount amount decimals)))]
    (when amount-text
      {:db (assoc-in db [:wallet/prepare-transaction :amount-text] amount-text)})))

(fx/defn set-and-validate-request-amount
  {:events [:wallet.request/set-amount-text]}
  [{:keys [db]} amount]
  {:db (assoc-in db [:wallet/prepare-transaction :amount-text] amount)})

(fx/defn request-transaction-button-clicked-from-chat
  {:events  [:wallet.ui/request-transaction-button-clicked]}
  [{:keys [db] :as cofx} {:keys [to amount from token]}]
  (let [{:keys [symbol address]} token
        from-address (:address from)
        identity (:current-chat-id db)]
    (fx/merge cofx
              {:db (dissoc db :wallet/prepare-transaction)
               ::json-rpc/call [{:method (json-rpc/call-ext-method "requestTransaction")
                                 :params [(:public-key to)
                                          amount
                                          (when-not (= symbol :ETH)
                                            address)
                                          from-address]
                                 :js-response true
                                 :on-success #(re-frame/dispatch [:transport/message-sent %])}]})))

(fx/defn accept-request-transaction-button-clicked-from-command
  {:events  [:wallet.ui/accept-request-transaction-button-clicked-from-command]}
  [{:keys [db]} chat-id {:keys [value contract] :as request-parameters}]
  (let [identity (:current-chat-id db)
        all-tokens (:wallet/all-tokens db)
        current-network-string  (:networks/current-network db)
        all-networks (:networks/networks db)
        current-network (get all-networks current-network-string)
        chain (ethereum/network->chain-keyword current-network)
        {:keys [symbol decimals]}
        (if (seq contract)
          (get all-tokens contract)
          (tokens/native-currency chain))
        amount-text (str (money/internal->formatted value symbol decimals))]
    {:db (assoc db :wallet/prepare-transaction
                {:from (ethereum/get-default-account (:multiaccount/accounts db))
                 :to   (or (get-in db [:contacts/contacts identity])
                           (-> identity
                               contact.db/public-key->new-contact
                               contact.db/enrich-contact))
                 :request-parameters request-parameters
                 :chat-id chat-id
                 :symbol symbol
                 :amount-text amount-text
                 :request? true
                 :from-chat? true})
     :dispatch [:open-modal :prepare-send-transaction]}))

(fx/defn set-and-validate-amount-request
  {:events [:wallet.request/set-and-validate-amount]}
  [{:keys [db]} amount symbol decimals]
  (let [{:keys [value error]} (wallet.db/parse-amount amount decimals)]
    {:db (-> db
             (assoc-in [:wallet :request-transaction :amount] (money/formatted->internal value symbol decimals))
             (assoc-in [:wallet :request-transaction :amount-text] amount)
             (assoc-in [:wallet :request-transaction :amount-error] error))}))

(fx/defn set-symbol-request
  {:events [:wallet.request/set-symbol]}
  [{:keys [db]} symbol]
  {:db (assoc-in db [:wallet :request-transaction :symbol] symbol)})

(re-frame/reg-fx
 ::resolve-address
 (fn [{:keys [registry ens-name cb]}]
   (ens/get-addr registry ens-name cb)))

(fx/defn on-recipient-address-resolved
  {:events [::recipient-address-resolved]}
  [{:keys [db]} address]
  {:db (assoc-in db [:wallet/prepare-transaction :to :address] address)
   :signing/update-gas-price {:success-event :wallet.send/update-gas-price-success
                              :network-id  (get-in (ethereum/current-network db)
                                                   [:config :NetworkId])}})

(fx/defn prepare-transaction-from-chat
  {:events [:wallet/prepare-transaction-from-chat]}
  [{:keys [db]}]
  (let [chain (ethereum/chain-keyword db)
        identity (:current-chat-id db)
        {:keys [ens-verified name] :as contact}
        (or (get-in db [:contacts/contacts identity])
            (-> identity
                contact.db/public-key->new-contact
                contact.db/enrich-contact))]
    (cond-> {:db (assoc db
                        :wallet/prepare-transaction
                        {:from (ethereum/get-default-account
                                (:multiaccount/accounts db))
                         :to contact
                         :symbol :ETH
                         :from-chat? true})
             :dispatch [:open-modal :prepare-send-transaction]}
      ens-verified
      (assoc ::resolve-address
             {:registry (get ens/ens-registries chain)
              :ens-name (if (= (.indexOf ^js name ".") -1)
                          (stateofus/subdomain name)
                          name)
              ;;TODO handle errors and timeout for ens name resolution
              :cb #(re-frame/dispatch [::recipient-address-resolved %])}))))

(fx/defn prepare-request-transaction-from-chat
  {:events [:wallet/prepare-request-transaction-from-chat]}
  [{:keys [db]}]
  (let [identity (:current-chat-id db)]
    {:db (assoc db :wallet/prepare-transaction
                {:from (ethereum/get-default-account (:multiaccount/accounts db))
                 :to   (or (get-in db [:contacts/contacts identity])
                           (-> identity
                               contact.db/public-key->new-contact
                               contact.db/enrich-contact))
                 :symbol :ETH
                 :from-chat? true
                 :request-command? true})
     :dispatch [:open-modal :request-transaction]}))

(fx/defn prepare-transaction-from-wallet
  {:events [:wallet/prepare-transaction-from-wallet]}
  [{:keys [db]} account]
  {:db (assoc db :wallet/prepare-transaction
              {:from       account
               :to         nil
               :symbol     :ETH
               :from-chat? false})
   :dispatch [:open-modal :prepare-send-transaction]
   :signing/update-gas-price {:success-event :wallet.send/update-gas-price-success
                              :network-id  (get-in (ethereum/current-network db)
                                                   [:config :NetworkId])}})

(fx/defn cancel-transaction-command
  {:events [:wallet/cancel-transaction-command]}
  [{:keys [db]}]
  (let [identity (:current-chat-id db)]
    {:db (dissoc db :wallet/prepare-transaction)}))

(fx/defn finalize-transaction-from-command
  {:events [:wallet/finalize-transaction-from-command]}
  [{:keys [db]} account to symbol amount]
  {:db (assoc db :wallet/prepare-transaction
              {:from       account
               :to         to
               :symbol     symbol
               :amount     amount
               :from-command? true})})

(fx/defn view-only-qr-scanner-allowed
  {:events [:wallet.add-new/qr-scanner]}
  [{:keys [db] :as cofx} options]
  (fx/merge cofx
            {:db (update-in db [:add-account] dissoc :address)}
            (qr-scaner/scan-qr-code options)))

(fx/defn wallet-send-set-symbol
  {:events [:wallet.send/set-symbol]}
  [{:keys [db] :as cofx} symbol]
  (fx/merge cofx
            {:db (assoc-in db [:wallet/prepare-transaction :symbol] symbol)}
            (bottom-sheet/hide-bottom-sheet)))

(fx/defn wallet-send-set-field
  {:events [:wallet.send/set-field]}
  [{:keys [db] :as cofx} field value]
  (fx/merge cofx
            {:db (assoc-in db [:wallet/prepare-transaction field] value)}
            (bottom-sheet/hide-bottom-sheet)))

(fx/defn wallet-request-set-field
  {:events [:wallet.request/set-field]}
  [{:keys [db] :as cofx} field value]
  (fx/merge cofx
            {:db (assoc-in db [:wallet/prepare-transaction field] value)}
            (bottom-sheet/hide-bottom-sheet)))

(fx/defn navigate-to-recipient-code
  {:events [:wallet.send/navigate-to-recipient-code]}
  [{:keys [db] :as cofx}]
  (fx/merge cofx
            {:db (-> db
                     (assoc :wallet/recipient {}))}
            (bottom-sheet/hide-bottom-sheet)
            (navigation/open-modal :recipient nil)))

(fx/defn show-delete-account-confirmation
  {:events [:wallet.settings/show-delete-account-confirmation]}
  [_ account]
  {:ui/show-confirmation {:title               (i18n/label :t/are-you-sure?)
                          :confirm-button-text (i18n/label :t/yes)
                          :cancel-button-text  (i18n/label :t/no)
                          :on-accept           #(re-frame/dispatch [:wallet.accounts/delete-account account])
                          :on-cancel           #()}})

(re-frame/reg-fx
 ::check-recent-history
 (fn [addresses]
   (log/info "[wallet] check recent history" addresses)
   (json-rpc/call
    {:method            "wallet_checkRecentHistory"
     :params            [addresses]
     :on-success        #(log/info "[wallet] wallet_checkRecentHistory success")
     :on-error          #(log/error "[wallet] wallet_checkRecentHistory error" %)})))

(def ms-2-min (datetime/minutes 2))
(def ms-3-min (datetime/minutes 3))
(def ms-5-min (datetime/minutes 5))
(def ms-10-min (datetime/minutes 10))
(def ms-20-min (datetime/minutes 20))

(def custom-intervals
  {:ms-2-min  ms-2-min
   :ms-3-min  ms-3-min
   :ms-5-min  ms-5-min
   :ms-10-min ms-10-min
   :ms-20-min ms-20-min})

(def next-custom-interval
  {:ms-2-min  :ms-3-min
   :ms-3-min  :ms-5-min
   :ms-5-min  :ms-10-min})

(defn get-next-custom-interval
  [{:keys [:wallet-service/custom-interval]}]
  (get next-custom-interval custom-interval))

(defn get-max-block-with-transfer [db]
  (reduce
   (fn [block [_ {:keys [max-block]}]]
     (if (or (nil? block)
             (> max-block block))
       max-block
       block))
   nil
   (get-in db [:wallet :accounts])))

(defn get-restart-interval [db]
  (let [max-block       (get-max-block-with-transfer db)
        custom-interval (get db :wallet-service/custom-interval)]
    (cond
      (ethereum/custom-rpc-node?
       (ethereum/current-network db))
      ms-2-min

      (and max-block
           (zero? max-block)
           (nil? (get db :wallet/keep-watching-until-ms)))
      (log/info "[wallet] No transactions found")

      :else
      (get custom-intervals custom-interval ms-20-min))))

(defn get-watching-interval [db]
  (if (ethereum/custom-rpc-node?
       (ethereum/current-network db))
    ms-2-min
    ms-10-min))

(fx/defn after-checking-history
  [{:keys [db] :as cofx}]
  (log/info "[wallet] after-checking-history")
  {:db (dissoc db
               :wallet/recent-history-fetching-started?
               :wallet/refreshing-history?)})

(defn set-timeout [db]
  (when-let [interval (get-restart-interval db)]
    (utils.utils/set-timeout
     #(re-frame.core/dispatch [::restart])
     interval)))

(fx/defn check-recent-history
  [{:keys [db] :as cofx}
   {:keys [on-recent-history-fetching force-restart?]}]
  (let [addresses   (map :address (get db :multiaccount/accounts))
        old-timeout (get db :wallet-service/restart-timeout)
        timeout     (if force-restart?
                      old-timeout
                      (set-timeout db))]
    {:db            (-> db
                        (assoc :wallet-service/restart-timeout timeout
                               :wallet-service/custom-interval (get-next-custom-interval db)
                               :wallet/was-started? true
                               :wallet/on-recent-history-fetching on-recent-history-fetching))
     ::check-recent-history addresses
     ::utils.utils/clear-timeouts
     [(when (not= timeout old-timeout) old-timeout)]}))

(fx/defn restart-wallet-service
  [{:keys [db] :as cofx}
   {:keys [force-restart? on-recent-history-fetching]
    :as   params}]
  (when (:multiaccount db)
    (let [syncing-allowed? (mobile-network-utils/syncing-allowed? cofx)]
      (log/info "restart-wallet-service"
                "force-restart?" force-restart?
                "syncing-allowed?" syncing-allowed?)
      (if (or syncing-allowed?
              force-restart?)
        (check-recent-history cofx params)
        (after-checking-history cofx)))))

(def background-cooldown-time (datetime/minutes 3))

(fx/defn restart-wallet-service-after-background
  [{:keys [now db] :as cofx} background-time]
  (when (and (get db :wallet/was-started?)
             (> (- now background-time)
                background-cooldown-time))
    (restart-wallet-service cofx nil)))

(fx/defn restart
  {:events [::restart]}
  [{:keys [db] :as cofx} force-restart?]
  (restart-wallet-service
   cofx
   {:force-restart? force-restart?}))

(def pull-to-refresh-cooldown-period (* 1 60 1000))

(fx/defn restart-on-pull
  {:events [:wallet.ui/pull-to-refresh-history]}
  [{:keys [db now] :as cofx}]
  (let [last-pull         (get db :wallet/last-pull-time)
        fetching-history? (get db :wallet/recent-history-fetching-started?)]
    (when (and (not fetching-history?)
               (or (not last-pull)
                   (> (- now last-pull) pull-to-refresh-cooldown-period)))
      (fx/merge
       {:db (assoc db
                   :wallet/last-pull-time now
                   :wallet/refreshing-history? true)}
       (restart-wallet-service
        {:force-restart? true})))))

(re-frame/reg-fx
 ::start-watching
 (fn [hashes]
   (log/info "[wallet] watch transactions" hashes)
   (doseq [hash hashes]
     (json-rpc/call
      {:method     "wallet_watchTransaction"
       :params     [hash]
       :on-success #(re-frame.core/dispatch [::restart true])
       :on-error   #(log/info "[wallet] watch transaction error" % "hash" hash)}))))

(fx/defn watch-tx
  {:events [:watch-tx]}
  [{:keys [db] :as cofx} tx-id]
  {::start-watching [tx-id]})

(fx/defn watch-transsactions
  [_ hashes]
  {::start-watching hashes})

(fx/defn clear-timeouts
  [{:keys [db]}]
  (log/info "[wallet] clear-timeouts")
  (let [restart-timeout-id (get db :wallet-service/restart-timeout)]
    {:db                          (dissoc db :wallet-service/restart-timeout)
     ::utils.utils/clear-timeouts [restart-timeout-id]}))

(fx/defn get-buy-crypto-preference
  {:events [::get-buy-crypto]}
  [_]
  {::async-storage/get {:keys [:buy-crypto-hidden]
                        :cb   #(re-frame/dispatch [::store-buy-crypto-preference %])}})

(fx/defn wallet-will-focus
  {:events [::wallet-stack]}
  [{:keys [db]}]
  (let [wallet-set-up-passed? (get-in db [:multiaccount :wallet-set-up-passed?])
        sign-phrase-showed? (get db :wallet/sign-phrase-showed?)]
    {:dispatch-n [[:wallet.ui/pull-to-refresh]] ;TODO temporary simple fix for v1
                  ;;[:show-popover {:view [signing-phrase/signing-phrase]}]]
     :db       (if (or wallet-set-up-passed? sign-phrase-showed?)
                 db
                 (assoc db :wallet/sign-phrase-showed? true))}))

(fx/defn wallet-wallet-add-custom-token
  {:events [:wallet/wallet-add-custom-token]}
  [{:keys [db]}]
  {:db (dissoc db :wallet/custom-token-screen)})

(fx/defn hide-buy-crypto
  {:events [::hide-buy-crypto]}
  [{:keys [db]}]
  {:db                  (assoc db :wallet/buy-crypto-hidden true)
   ::async-storage/set! {:buy-crypto-hidden true}})

(fx/defn store-buy-crypto
  {:events [::store-buy-crypto-preference]}
  [{:keys [db]} {:keys [buy-crypto-hidden]}]
  {:db (assoc db :wallet/buy-crypto-hidden buy-crypto-hidden)})

(fx/defn contract-address-paste
  {:events [:wallet.custom-token.ui/contract-address-paste]}
  [_]
  {:wallet.custom-token/contract-address-paste nil})

(fx/defn transactions-add-filter
  {:events [:wallet.transactions/add-filter]}
  [{:keys [db]} id]
  {:db (update-in db [:wallet :filters] conj id)})

(fx/defn transactions-remove-filter
  {:events [:wallet.transactions/remove-filter]}
  [{:keys [db]} id]
  {:db (update-in db [:wallet :filters] disj id)})

(fx/defn transactions-add-all-filters
  {:events [:wallet.transactions/add-all-filters]}
  [{:keys [db]}]
  {:db (assoc-in db [:wallet :filters]
                 wallet.db/default-wallet-filters)})

(fx/defn settings-navigate-back-pressed
  {:events [:wallet.settings.ui/navigate-back-pressed]}
  [cofx on-close]
  (fx/merge cofx
            (when on-close
              {:dispatch on-close})
            (navigation/navigate-back)))

(fx/defn stop-fetching-on-empty-tx-history
  [{:keys [db now] :as cofx} transfers]
  (let [non-empty-history?    (get db :wallet/non-empty-tx-history?)
        custom-node?          (ethereum/custom-rpc-node?
                               (ethereum/current-network db))
        until-ms              (get db :wallet/keep-watching-until-ms)]
    (when-not (and until-ms (> until-ms now))
      (fx/merge
       cofx
       {:db (dissoc db :wallet/keep-watching-until-ms)}
       (if (and (not non-empty-history?)
                (empty? transfers)
                (not custom-node?))
         (clear-timeouts)
         (fn [{:keys [db]}]
           {:db (assoc db :wallet/non-empty-tx-history? true)}))))))

(fx/defn keep-watching-history
  {:events [:wallet/keep-watching]}
  [{:keys [db now] :as cofx}]
  (let [non-empty-history? (get db :wallet/non-empty-tx-history?)
        old-timeout        (get db :wallet-service/restart-timeout)
        db                 (assoc db :wallet-service/custom-interval :ms-2-min)
        timeout            (set-timeout db)]
    {:db (assoc db
                :wallet/keep-watching-until-ms (+ now (datetime/minutes 30))
                :wallet-service/restart-timeout timeout
                :wallet-service/custom-interval (get-next-custom-interval db))
     ::utils.utils/clear-timeouts [old-timeout]}))

(re-frame/reg-fx
 ::set-inital-range
 (fn []
   (json-rpc/call
    {:method            "wallet_setInitialBlocksRange"
     :params            []
     :number-of-retries 10
     :on-success        #(log/info "Initial blocks range was successfully set")
     :on-error          #(log/info "Initial blocks range was not set")})))

(fx/defn set-initial-blocks-range
  [{:keys [db]}]
  {:db                (assoc db :wallet/new-account true)
   ::set-inital-range nil})

(fx/defn tab-opened
  {:events [:wallet/tab-opened]}
  [{:keys [db] :as cofx}]
  (when-not (get db :wallet/was-started?)
    (restart-wallet-service cofx nil)))

(fx/defn set-max-block [{:keys [db]} address block]
  (log/debug "set-max-block"
             "address" address
             "block" block)
  {:db (assoc-in db [:wallet :accounts address :max-block] block)})

(fx/defn set-max-block-with-transfers
  [{:keys [db] :as cofx} address transfers]
  (let [max-block (reduce
                   (fn [max-block {:keys [block]}]
                     (if (> block max-block)
                       block
                       max-block))
                   (get-in db [:wallet :accounts address :max-block] 0)
                   transfers)]
    (set-max-block cofx address max-block)))

(fx/defn share
  {:events [:wallet/share-popover]}
  [{:keys [db] :as cofx} address]
  (let [non-empty-history? (get db :wallet/non-empty-tx-history?)
        restart?     (and (not (get db :wallet/non-empty-tx-history?))
                          (not (get db :wallet-service/restart-timeout)))]
    (fx/merge
     cofx
     (popover.core/show-popover
      {:view    :share-account
       :address address})
     (keep-watching-history))))

(re-frame/reg-fx
 ::get-pending-transactions
 (fn []
   (json-rpc/call
    {:method     "wallet_getPendingTransactions"
     :params     []
     :on-success #(re-frame/dispatch [:wallet/on-retreiving-pending-transactions %])})))

(fx/defn get-pending-transactions
  {:events [:wallet/get-pending-transactions]}
  [_]
  (log/info "[wallet] get pending transactions")
  {::get-pending-transactions nil})

(defn normalize-transaction
  [db {:keys [symbol gasPrice gasLimit value from to] :as transaction}]
  (let [symbol (keyword symbol)
        token (tokens/symbol->token (:wallet/all-tokens db) symbol)]
    (-> transaction
        (select-keys [:timestamp :hash :data])
        (assoc :from (eip55/address->checksum from)
               :to (eip55/address->checksum to)
               :type :pending
               :symbol symbol
               :token token
               :value (money/bignumber value)
               :gas-price (money/bignumber gasPrice)
               :gas-limit (money/bignumber gasLimit)))))

(fx/defn on-retriving-pending-transactions
  {:events [:wallet/on-retreiving-pending-transactions]}
  [{:keys [db]} raw-transactions]
  (log/info "[wallet] pending transactions")
  {:db
   (reduce (fn [db {:keys [from hash] :as transaction}]
             (let [path [:wallet :accounts from :transactions hash]]
               (if-not (get-in db path)
                 (assoc-in db path transaction)
                 db)))
           db
           (map (partial normalize-transaction db) raw-transactions))
   ::start-watching (map :hash raw-transactions)})

(re-frame/reg-fx
 :wallet/delete-pending-transactions
 (fn [hashes]
   (log/info "[wallet] delete pending transactions")
   (doseq [hash hashes]
     (json-rpc/call
      {:method     "wallet_deletePendingTransaction"
       :params     [hash]
       :on-success #(log/info "[wallet] pending transaction deleted" hash)}))))

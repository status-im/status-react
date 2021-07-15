(ns status-im.ui.screens.signing.views
  (:require-macros [status-im.utils.views :as views])
  (:require [status-im.ui.components.react :as react]
            [re-frame.core :as re-frame]
            [status-im.multiaccounts.core :as multiaccounts]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.copyable-text :as copyable-text]
            [status-im.wallet.utils :as wallet.utils]
            [status-im.ui.components.list.views :as list]
            [status-im.keycard.common :as keycard.common]
            [status-im.ui.screens.keycard.keycard-interaction :as keycard-sheet]
            [status-im.ui.components.chat-icon.screen :as chat-icon]
            [status-im.ui.components.icons.icons :as icons]
            [status-im.i18n.i18n :as i18n]
            [status-im.utils.security :as security]
            [status-im.ui.screens.signing.sheets :as sheets]
            [status-im.ethereum.tokens :as tokens]
            [status-im.utils.types :as types]
            [status-im.utils.platform :as platform]
            [clojure.string :as string]
            [quo.core :as quo]
            [quo.gesture-handler :as gh]
            [status-im.ui.screens.signing.styles :as styles]
            [status-im.react-native.resources :as resources]
            [status-im.ui.screens.keycard.pin.views :as pin.views]
            [status-im.ui.components.bottom-panel.views :as bottom-panel]
            [status-im.utils.utils :as utils]
            [reagent.core :as reagent]
            [status-im.signing.eip1559 :as eip1559]))

(defn separator []
  [react/view {:height 1 :background-color colors/gray-lighter}])

(defn displayed-name [contact]
  (if (or (:preferred-name contact) (:name contact))
    (multiaccounts/displayed-name contact)
    (utils/get-shortened-checksum-address (:address contact))))

(defn contact-item [title contact]
  [copyable-text/copyable-text-view
   {:copied-text (:address contact)}
   [quo/list-item
    {:title              title
     :title-prefix-width 45
     :size               :small
     :accessory          :text
     :accessory-text     [quo/text
                          {:ellipsize-mode  :middle
                           :number-of-lines 1
                           :monospace       true}
                          (displayed-name contact)]}]])

(defn token-item [{:keys [icon color] :as token} display-symbol]
  (when token
    [react/view
     [quo/list-item
      {:size      :small
       :title     (i18n/label :t/wallet-asset)
       :accessory [react/view {:flex-direction :row}
                   [quo/text {:color :secondary
                              :style {:margin-right 8}}
                    display-symbol]
                   (if icon
                     [list/item-image
                      (assoc icon
                             :style {:background-color colors/gray-lighter
                                     :border-radius    16}
                             :image-style {:width 24 :height 24})]
                     [chat-icon/custom-icon-view-list (:name token) color 32])]}]
     [separator]]))

(defn header
  [{:keys [in-progress?] :as sign}
   {:keys [contact amount approve? cancel? hash]}
   display-symbol fee fee-display-symbol]
  [react/view styles/header
   (when sign
     [react/touchable-highlight (when-not in-progress? {:on-press #(re-frame/dispatch [:set :signing/sign nil])})
      [react/view {:padding-right 16}
       [icons/icon :main-icons/back]]])
   [react/view {:flex 1}
    (if amount
      [react/text {:style {:typography :title-bold}} (str (cond approve?
                                                                (i18n/label :t/authorize)
                                                                cancel?
                                                                (i18n/label :t/cancelling)
                                                                :else
                                                                (i18n/label :t/sending))
                                                          (if cancel?
                                                            (str " " (utils/get-shortened-address hash))
                                                            (str " " amount " " display-symbol)))]
      [react/text {:style {:typography :title-bold}} (i18n/label :t/contract-interaction)])
    (if sign
      [react/nested-text {:style           {:color colors/gray}
                          :ellipsize-mode  :middle
                          :number-of-lines 1} (i18n/label :t/to) " "
       [{:style {:color colors/black}} (displayed-name contact)]]
      [react/text {:style {:margin-top 6 :color colors/gray}}
       (str fee " " fee-display-symbol " " (string/lower-case (i18n/label :t/network-fee)))])]
   [react/view {:padding-horizontal 24}
    [quo/button (merge {:type :secondary}
                       (when-not in-progress? {:on-press #(re-frame/dispatch [:signing.ui/cancel-is-pressed])}))
     (i18n/label :t/cancel)]]])

(views/defview keycard-pin-view []
  (views/letsubs [pin [:keycard/pin]
                  small-screen? [:dimensions/small-screen?]
                  error-label [:keycard/pin-error-label]
                  enter-step [:keycard/pin-enter-step]
                  status [:keycard/pin-status]
                  retry-counter [:keycard/retry-counter]]
    (let [enter-step (or enter-step :sign)
          margin-bottom (if platform/ios? 40 0)]
      [react/view {:margin-bottom margin-bottom}
       [pin.views/pin-view
        {:pin           pin
         :retry-counter retry-counter
         :step          enter-step
         :small-screen? small-screen?
         :status        status
         :error-label   error-label}]])))

(defn sign-with-keycard-button
  [amount-error gas-error]
  (let [disabled? (or amount-error gas-error)]
    [react/touchable-highlight {:on-press #(when-not disabled?
                                             (re-frame/dispatch [:signing.ui/sign-with-keycard-pressed]))}
     [react/view (styles/sign-with-keycard-button disabled?)
      [react/text {:style (styles/sign-with-keycard-button-text disabled?)}
       (i18n/label :t/sign-with)]
      [react/view {:padding-right 16}
       [react/image {:source (resources/get-image :keycard-logo)
                     :style  (merge {:width         64
                                     :margin-bottom 7
                                     :height        26}
                                    (when (colors/dark?)
                                      {:tint-color colors/white-persist}))}]]]]))

(defn- signing-phrase-view [phrase]
  [react/view {:align-items :center}
   [react/text {:style {:color colors/gray :padding-bottom 8}} (i18n/label :t/signing-phrase)]
   [react/text phrase]])

(defn- keycard-view
  [{:keys [keycard-step]} phrase]
  [react/view
   [signing-phrase-view phrase]
   (case keycard-step
     :pin     [keycard-pin-view]
     [react/view {:align-items :center :margin-top 16 :margin-bottom 40}
      [sign-with-keycard-button nil nil]])])

(defn redeem-tx-header [account receiver small-screen?]
  (fn []
    [react/view {:style {:align-self :stretch :margin-top 30}}
     [separator]
     [react/view {:style {:flex-direction :row
                          :justify-content :space-between
                          :align-items :center
                          :padding-left 16 :margin-vertical 8}}
      [react/text {:style {:flex 2 :margin-right 16}} (i18n/label :t/keycard-redeem-title)]
      [react/text {:number-of-lines 1
                   :ellipsize-mode :middle
                   :style {:padding-left 16
                           :color colors/gray
                           :flex 3}}
       (if account (:name account) receiver)]
      (when account
        [react/view {:style {:flex 1 :padding-left 8}}
         [chat-icon/custom-icon-view-list (:name account) (:color account) (if small-screen? 20 32)]])]
     [separator]]))

(defn signature-request-header [amount currency small-screen? fiat-amount fiat-currency]
  (fn []
    [react/view {:style {:align-self :stretch :margin-vertical 30}}
     [react/nested-text {:style {:font-weight "500" :font-size (if small-screen? 34 44)
                                 :text-align :center}}
      (str amount " ")
      [{:style {:color colors/gray}} currency]]
     [react/text {:style {:font-size 19 :text-align :center
                          :margin-bottom 16}}
      (str fiat-amount " " fiat-currency)]
     [separator]]))

(defn terminal-button [{:keys [on-press theme disabled? height]} label]
  [react/touchable-opacity {:disabled disabled?
                            :on-press on-press
                            :style    {:height           height
                                       :border-radius    16
                                       :flex             1
                                       :justify-content  :center
                                       :align-items      :center
                                       :background-color (if (= theme :negative)
                                                           colors/red-transparent-10
                                                           colors/blue-light)}}
   [quo/text {:size            :large
              :number-of-lines 1
              :color           (if (= theme :negative)
                                 :negative
                                 :link)
              :weight          :medium}
    label]])

(defn signature-request-footer [keycard-step small-screen?]
  (fn []
    [react/view {:style {:padding 16 :align-items :center}}
     [react/view {:style {:flex-direction :row}}
      [terminal-button {:disabled? (= keycard-step :success)
                        :height    (if small-screen? 52 64)
                        :on-press  #(re-frame/dispatch [:show-popover {:view :transaction-data}])}
       (i18n/label :t/show-transaction-data)]]]))

(defn signature-request [{:keys [formatted-data account fiat-amount fiat-currency keycard-step]}
                         connected?
                         small-screen?]
  (let [message (:message formatted-data)]
    [react/view (assoc (styles/message) :padding-vertical 16)
     [keycard-sheet/connect-keycard
      {:on-connect    ::keycard.common/on-card-connected
       :on-disconnect ::keycard.common/on-card-disconnected
       :connected?    connected?
       :on-cancel     #(re-frame/dispatch [:signing.ui/cancel-is-pressed])
       :params
       (cond
         (:receiver message) {:title (i18n/label :t/confirmation-request)
                              :header (redeem-tx-header account (:receiver message) small-screen?)
                              :footer (signature-request-footer keycard-step small-screen?)
                              :small-screen? small-screen?
                              :state-translations {:init {:title :t/keycard-redeem-tx
                                                          :description :t/keycard-redeem-tx-desc}}}
         (:currency message) {:title (i18n/label :t/confirmation-request)
                              :header (signature-request-header (:formatted-amount message)
                                                                (:formatted-currency message)
                                                                small-screen? fiat-amount fiat-currency)
                              :footer (signature-request-footer keycard-step small-screen?)
                              :small-screen? small-screen?}
         :else {:title (i18n/label :t/confirmation-request)
                :header (signature-request-header (:formatted-amount message)
                                                  (:formatted-currency message)

                                                  small-screen? fiat-amount fiat-currency)
                :footer (signature-request-footer keycard-step small-screen?)
                :small-screen? small-screen?})}]]))

(defn- transaction-data-item [data]
  (let [text (types/clj->pretty-json data 2)]
    [react/view
     [react/text {:style {:font-size     17
                          :line-height   20
                          :margin-bottom 24}}
      text]]))

(views/defview transaction-data []
  (views/letsubs
    [{:keys [formatted-data]} [:signing/sign]]
    [react/view {:style {:flex 1}}
     [react/view {:style {:margin-horizontal 24
                          :margin-top        24}}
      [react/text {:style {:font-size   17
                           :font-weight "700"}}
       (i18n/label :t/transaction-data)]]
     [react/scroll-view {:style {:flex               1
                                 :margin-horizontal  8
                                 :padding-horizontal 16
                                 :padding-vertical   10
                                 :margin-vertical    14}}
      [transaction-data-item formatted-data]]
     [separator]
     [react/view {:style {:margin-horizontal 8
                          :margin-vertical   16}}
      [quo/button  {:on-press #(re-frame/dispatch [:hide-popover])}
       (i18n/label :t/close)]]]))

(views/defview password-view [{:keys [type error in-progress? enabled?] :as sign}]
  (views/letsubs [phrase [:signing/phrase]]
    (case type
      :password
      [react/view {:padding-top 8 :padding-bottom 8}
       [signing-phrase-view phrase]
       [react/view {:padding-horizontal 16
                    :padding-vertical   12}
        [quo/text-input
         {:secure-text-entry   true
          :placeholder         (i18n/label :t/enter-password)
          :on-change-text      #(re-frame/dispatch [:signing.ui/password-is-changed (security/mask-data %)])
          :accessibility-label :enter-password-input
          :auto-capitalize     :none
          :editable            (not in-progress?)
          :error               error
          :show-cancel         false}]]
       [react/view {:align-items :center :height 60}
        (if in-progress?
          [react/activity-indicator {:animating true
                                     :size      :large}]
          [quo/button {:on-press  #(re-frame/dispatch [:signing.ui/sign-is-pressed])
                       :disabled  (not enabled?)}
           (i18n/label :t/transactions-sign)])]]
      :keycard
      [keycard-view sign phrase]
      [react/view])))

(views/defview message-sheet []
  (views/letsubs [{:keys [formatted-data type] :as sign} [:signing/sign-message]
                  small-screen? [:dimensions/small-screen?]
                  keycard [:keycard]]
    (if (= type :pinless)
      [signature-request sign (:card-connected? keycard) small-screen?]
      [react/view (styles/message)
       [react/view styles/message-header
        [react/text {:style {:typography :title-bold}} (i18n/label :t/signing-a-message)]
        [react/touchable-highlight {:on-press #(re-frame/dispatch [:signing.ui/cancel-is-pressed])}
         [react/view {:padding 6}
          [react/text {:style {:color colors/blue}} (i18n/label :t/cancel)]]]]
       [separator]
       [react/view {:padding-top 16 :flex 1}
        [react/view styles/message-border
         [react/scroll-view
          [react/text (or formatted-data "")]]]
        [password-view sign]]])))

(defn error-item []
  (fn [title show-error]
    [gh/touchable-highlight {:on-press #(swap! show-error not)}
     [react/view {:style {:align-items    :center
                          :flex-direction :row}}
      [react/text {:style {:color colors/red :margin-right 8}}
       (i18n/label title)]
      [icons/icon :warning {:color colors/red}]]]))

(defn amount-item []
  (let [show-error (reagent/atom false)]
    (fn [prices wallet-currency amount amount-error display-symbol fee-display-symbol prices-loading?]
      (let [converted-value (* amount (get-in prices [(keyword display-symbol) (keyword (:code wallet-currency)) :price]))]
        [quo/list-item
         {:size      :small
          :title     (if amount-error
                       [error-item :t/send-request-amount show-error]
                       (i18n/label :t/send-request-amount))
          :error     (when (and amount-error @show-error) amount-error)
          :animated  false
          :accessory [react/view {:style {:flex-direction :row}}
                      [react/nested-text {:style {:color colors/gray}}
                       [{:style {:color colors/black}} (utils/format-decimals amount 6)]
                       " "
                       (or display-symbol fee-display-symbol)
                       " • "]
                      (if prices-loading?
                        [react/small-loading-indicator]
                        [react/text {:style {:color colors/black}}
                         (i18n/format-currency converted-value (:code wallet-currency))])
                      [react/text {:style {:color colors/gray}} (str " " (:code wallet-currency))]]}]))))

(views/defview fee-item [prices wallet-currency fee-display-symbol fee gas-error gas-error-state prices-loading?]
  (views/letsubs [{:keys [gas-price-loading? gas-loading?]} [:signing/edit-fee]
                  show-error (reagent/atom false)]
    (let [converted-fee-value (* fee (get-in prices [(keyword fee-display-symbol) (keyword (:code wallet-currency)) :price]))]
      [quo/list-item
       {:size      :small
        :title     (if (and (not (or gas-price-loading? gas-loading?)) gas-error)
                     [error-item :t/network-fee show-error]
                     (i18n/label :t/network-fee))
        :error     (when (and (not (or gas-price-loading? gas-loading?)) gas-error @show-error)
                     gas-error)
        :disabled  (or gas-price-loading? gas-loading?)
        :chevron   true
        :animated  false
        :accessory (if (or gas-price-loading? gas-loading?)
                     [react/small-loading-indicator]
                     (if (= :gas-isnt-set gas-error-state)
                       [react/text {:style               {:color colors/blue}
                                    :accessibility-label :custom-gas-fee}
                        (i18n/label :t/set-custom-fee)]
                       [react/view {:style               {:flex-direction :row}
                                    :accessibility-label :custom-gas-fee}
                        [react/nested-text {:style {:color colors/gray}}
                         [{:style {:color colors/black}} (utils/format-decimals fee 6)]
                         " "
                         fee-display-symbol
                         " • "]
                        (if prices-loading?
                          [react/small-loading-indicator]
                          [react/text {:style {:color colors/black}}
                           (i18n/format-currency converted-fee-value (:code wallet-currency))])
                        [react/text {:style {:color colors/gray}} (str " " (:code wallet-currency))]]))
        :on-press  #(re-frame/dispatch
                     [:signing.ui/open-fee-sheet
                      {:content        (fn []
                                         (if (eip1559/sync-enabled?)
                                           [sheets/fee-bottom-sheet-eip1559 fee-display-symbol]
                                           [sheets/fee-bottom-sheet fee-display-symbol]))
                       :content-height 270}])}])))

(views/defview network-item []
  (views/letsubs [network-name [:network-name]]
    [quo/list-item
     {:title          (i18n/label :t/network)
      :size           :small
      :accessory      :text
      :accessory-text network-name}]))

(views/defview sheet
  [{:keys [from contact amount token cancel?] :as tx}]
  (views/letsubs [fee                   [:signing/fee]
                  sign                  [:signing/sign]
                  chain                 [:ethereum/chain-keyword]
                  {:keys [amount-error gas-error gas-error-state]}
                  [:signing/amount-errors (:address from)]
                  keycard-multiaccount? [:keycard-multiaccount?]
                  prices                [:prices]
                  wallet-currency       [:wallet/currency]
                  mainnet?              [:mainnet?]
                  prices-loading?       [:prices-loading?]]
    (let [display-symbol     (wallet.utils/display-symbol token)
          fee-display-symbol (wallet.utils/display-symbol (tokens/native-currency chain))]
      [react/view (styles/sheet)
       [header sign tx display-symbol fee fee-display-symbol]
       [separator]
       (if sign
         [react/view {:padding-top 20}
          [password-view sign]]
         [react/view
          (when-not mainnet?
            [react/view
             [network-item]
             [separator]])
          [contact-item (i18n/label :t/from) from]
          [separator]
          [contact-item (i18n/label :t/to) contact]
          (when-not cancel?
            [separator])
          (when-not cancel?
            [token-item token display-symbol])
          (when-not cancel?
            [amount-item prices wallet-currency amount amount-error display-symbol fee-display-symbol prices-loading?])
          [separator]
          [fee-item prices wallet-currency fee-display-symbol fee gas-error gas-error-state prices-loading?]
          (when (= :gas-is-set gas-error-state)
            [react/text {:style {:color colors/gray :margin-horizontal 32 :text-align :center}}
             (i18n/label :t/tx-fail-description1)])
          [react/view {:align-items :center :margin-top 16 :margin-bottom 40}
           (if keycard-multiaccount?
             [sign-with-keycard-button amount-error gas-error]
             (if (= :gas-isnt-set gas-error-state)
               [react/text {:style {:color colors/gray :margin-horizontal 32 :text-align :center}}
                (i18n/label :t/tx-fail-description2)]
               [quo/button {:on-press #(re-frame/dispatch [:set :signing/sign {:type :password}])
                            :disabled (or amount-error gas-error)
                            :theme    (if gas-error-state :negative :main)}
                (i18n/label (if gas-error-state
                              :t/sign-anyway
                              :t/sign-with-password))]))]])])))

(views/defview signing []
  (views/letsubs [tx [:signing/tx]]
    [bottom-panel/animated-bottom-panel
     ;;we use select-keys here because we don't want to update view if other keys in map are changed
     (when tx (select-keys tx [:from :contact :amount :token :approve? :message :cancel? :hash]))
     #(if (:message %)
        [message-sheet]
        [sheet %])
     #(re-frame/dispatch [:hide-signing-sheet])]))

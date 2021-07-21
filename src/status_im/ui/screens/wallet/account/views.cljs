(ns status-im.ui.screens.wallet.account.views
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [status-im.ethereum.core :as ethereum]
            [status-im.i18n.i18n :as i18n]
            [status-im.ui.components.animation :as animation]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.icons.icons :as icons]
            [quo.core :as quo]
            [status-im.ui.components.list.views :as list]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.topbar :as topbar]
            [status-im.ui.screens.wallet.account.styles :as styles]
            [status-im.ui.screens.wallet.accounts.sheets :as sheets]
            [status-im.ui.screens.wallet.accounts.views :as accounts]
            [status-im.ui.screens.wallet.buy-crypto.views :as buy-crypto]
            [status-im.ui.screens.wallet.transactions.views :as history]
            [status-im.utils.money :as money]
            [status-im.wallet.utils :as wallet.utils]
            [status-im.ui.components.tabs :as tabs]
            [quo.design-system.colors :as quo-colors])
  (:require-macros [status-im.utils.views :as views]))

(def state (reagent/atom {:tab :assets}))

(defn button [label icon color handler]
  [react/touchable-highlight {:on-press handler :style {:flex 1}}
   [react/view {:flex 1 :align-items :center :justify-content :center}
    [react/view {:flex-direction :row :align-items :center}
     [icons/icon icon {:color color}]
     [react/text {:style {:margin-left 8 :color color}} label]]]])

(def button-group-height 52)

(views/defview account-card [{:keys [address color type] :as account}]
  (views/letsubs [currency        [:wallet/currency]
                  portfolio-value [:account-portfolio-value address]
                  window-width    [:dimensions/window-width]
                  prices-loading? [:prices-loading?]]
    [react/view {:style (styles/card window-width color)}
     [react/view {:padding 16 :padding-bottom 12 :flex 1 :justify-content :space-between}
      [react/view {:style {:flex-direction :row}}
       (if prices-loading?
         [react/small-loading-indicator :colors/white-persist]
         [react/text {:style {:font-size 32 :color colors/white-persist :font-weight "600"}} portfolio-value])
       [react/text {:style {:font-size 32 :color colors/white-transparent-persist :font-weight "600"}} (str " " (:code currency))]]
      [quo/text {:number-of-lines 1
                 :ellipsize-mode  :middle
                 :monospace       true
                 :size            :small
                 :style           {:width       (/ window-width 3)
                                   :line-height 22
                                   :color       colors/white-transparent-70-persist}}
       (ethereum/normalized-hex address)]]
     [react/view {:position :absolute :top 12 :right 12}
      [react/touchable-highlight {:on-press #(re-frame/dispatch [:wallet/share-popover address])}
       [icons/icon :main-icons/share {:color                      colors/white-persist
                                      :accessibility-label :share-wallet-address-icon}]]]
     [react/view {:height                     button-group-height :background-color          colors/black-transparent-20
                  :border-bottom-right-radius 8                   :border-bottom-left-radius 8 :flex-direction :row}
      (if (= type :watch)
        [react/view {:flex 1 :align-items :center :justify-content :center}
         [react/text {:style {:margin-left 8 :color colors/white-persist}}
          (i18n/label :t/watch-only)]]
        [button
         (i18n/label :t/wallet-send)
         :main-icons/send
         colors/white-persist
         #(re-frame/dispatch [:wallet/prepare-transaction-from-wallet account])])
      [react/view {:style (styles/divider)}]
      [button
       (i18n/label :t/receive)
       :main-icons/receive
       colors/white-persist
       #(re-frame/dispatch [:wallet/share-popover address])]]]))

(defn render-collectible [{:keys [name icon amount] :as collectible}]
  (let [items-number (money/to-fixed amount)]
    [quo/list-item
     {:title          (wallet.utils/display-symbol collectible)
      :subtitle       name
      :icon           [list/item-image icon]
      :accessory      :text
      :accessory-text items-number}]))

(views/defview transactions [address]
  (views/letsubs [data [:wallet.transactions.history/screen address]]
    [history/history-list data address]))

(defn collectibles-link []
  [react/touchable-highlight
   {:on-press #(re-frame/dispatch [:browser.ui/open-url "https://opensea.io/account/"])}
   [react/view
    {:style {:flex             1
             :padding-horizontal 14
             :flex-direction   :row
             :align-items :center
             :background-color colors/blue-light
             :height           52}}
    [icons/tiny-icon
     :tiny-icons/tiny-external
     {:color           colors/blue
      :container-style {:margin-right 5}}]
    [react/text
     {:style {:color colors/blue}}
     (i18n/label :t/check-on-opensea)]]])

(views/defview assets-and-collections [address]
  (views/letsubs [{:keys [tokens nfts]} [:wallet/visible-assets-with-values address]
                  currency [:wallet/currency]]
    (let [{:keys [tab]} @state]
      [react/view {:flex 1}
       [react/view {:flex-direction :row :margin-bottom 8 :padding-horizontal 4}
        [tabs/tab-title state :assets (i18n/label :t/wallet-assets) (= tab :assets)]
        [tabs/tab-title state :nft (i18n/label :t/wallet-collectibles) (= tab :nft)]
        [tabs/tab-title state :history (i18n/label :t/history) (= tab :history)]]
       (cond
         (= tab :assets)
         [:<>
          [buy-crypto/banner]
          (for [item tokens]
            ^{:key (:name item)}
            [accounts/render-asset item nil nil (:code currency)])]
         (= tab :nft)
         [react/view
          [collectibles-link]
          (if (seq nfts)
            [:<>
             (for [item nfts]
               ^{:key (:name item)}
               [render-collectible item nil nil (:code currency)])]
            [react/view {:align-items :center :margin-top 32}
             [react/text {:style {:color colors/gray}}
              (i18n/label :t/no-collectibles)]])]
         (= tab :history)
         [transactions address])])))

(views/defview bottom-send-recv-buttons [{:keys [address type] :as account} anim-y]
  [react/animated-view {:style {:background-color colors/white
                                :bottom           0
                                :flex-direction   :row
                                :height           button-group-height
                                :position         :absolute
                                :shadow-offset    {:width 0 :height 1}
                                :shadow-opacity   0.75
                                :shadow-radius    1
                                :transform        [{:translateY anim-y}]
                                :width            "100%"}}
   (if (= type :watch)
     [react/view {:flex 1 :align-items :center :justify-content :center}
      [react/text {:style {:margin-left 8 :color colors/blue-persist}}
       (i18n/label :t/watch-only)]]
     [button
      (i18n/label :t/wallet-send)
      :main-icons/send
      colors/blue-persist
      #(re-frame/dispatch [:wallet/prepare-transaction-from-wallet account])])
   [button
    (i18n/label :t/receive)
    :main-icons/receive
    colors/blue-persist
    #(re-frame/dispatch [:wallet/share-popover address])]])

(defn anim-listener [anim-y scroll-y]
  (let [to-show (atom false)]
    (animation/add-listener
     scroll-y
     (fn [anim]
       (let [y-trigger 149]
         (cond
           (and (>= (.-value anim) y-trigger) (not @to-show))
           (animation/start
            (styles/bottom-send-recv-buttons-raise anim-y)
            #(reset! to-show true))

           (and (< (.-value anim) y-trigger) @to-show)
           (animation/start
            (styles/bottom-send-recv-buttons-lower anim-y button-group-height)
            #(reset! to-show false))))))))

(views/defview account []
  (views/letsubs [{:keys [name address] :as account} [:multiaccount/current-account]
                  fetching-error [:wallet/fetching-error]
                  refreshing-history? [:wallet/refreshing-history?]]
    (let [anim-y (animation/create-value button-group-height)
          scroll-y (animation/create-value 0)]
      (anim-listener anim-y scroll-y)
      [:<>
       [topbar/topbar
        {:title name
         :right-accessories
         [{:icon     :main-icons/more
           :on-press #(re-frame/dispatch [:bottom-sheet/show-sheet
                                          {:content        sheets/account-settings
                                           :content-height 60}])}]}]
       [react/animated-scroll-view
        {:contentContainerStyle {:padding-bottom button-group-height}
         :on-scroll             (animation/event
                                 [{:nativeEvent {:contentOffset {:y scroll-y}}}]
                                 {:useNativeDriver true})
         :scrollEventThrottle   1
         :refreshControl        (accounts/refresh-control
                                 (and
                                  @accounts/updates-counter
                                  refreshing-history?))}
        (when fetching-error
          [react/view {:style {:flex 1
                               :align-items :center
                               :margin 8}}
           [icons/icon
            :main-icons/warning
            {:color           :red
             :container-style {:background-color (quo-colors/get-color :negative-02)
                               :height           40
                               :width            40
                               :border-radius    20
                               :align-items      :center
                               :justify-content  :center}}]
           [react/view
            {:style {:justify-content   :center
                     :align-items       :center
                     :margin-top        8
                     :margin-horizontal 67.5
                     :text-align        :center}}
            [quo/text
             {:color :secondary
              :size  :small
              :style {:text-align :center}}
             (i18n/label :t/transfers-fetching-failure)]]])
        [react/view {:padding-left 16}
         [react/scroll-view {:horizontal true}
          [react/view {:flex-direction :row :padding-top 8 :padding-bottom 12}
           [account-card account]]]]
        [assets-and-collections address]]
       [bottom-send-recv-buttons account anim-y]])))

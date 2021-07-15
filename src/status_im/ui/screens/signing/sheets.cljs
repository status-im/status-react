(ns status-im.ui.screens.signing.sheets
  (:require-macros [status-im.utils.views :as views])
  (:require [status-im.ui.components.react :as react]
            [re-frame.core :as re-frame]
            [status-im.i18n.i18n :as i18n]
            [quo.core :as quo]
            [quo.design-system.colors :as quo-colors]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.slider :as slider]
            [status-im.utils.money :as money]
            [status-im.ui.components.icons.icons :as icons]
            [clojure.string :as clojure.string]
            [status-im.signing.gas :as gas]))

(views/defview fee-bottom-sheet [fee-display-symbol]
  (views/letsubs [{gas-edit :gas gas-price-edit :gasPrice max-fee :max-fee} [:signing/edit-fee]]
    [react/view
     [react/view {:style {:margin-horizontal 16 :margin-top 8}}
      [react/text {:style {:typography :title-bold}} (i18n/label :t/network-fee)]
      [react/view {:style {:flex-direction :row
                           :margin-top     8
                           :align-items    :flex-end}}
       [react/view {:flex 1}
        [quo/text-input
         {:on-change-text  #(re-frame/dispatch [:signing.edit-fee.ui/edit-value :gas %])
          :label           (i18n/label :t/gas-limit)
          :bottom-value    0
          :error           (:error gas-edit)
          :default-value   (:value gas-edit)
          :keyboard-type   :numeric
          :auto-capitalize :none
          :placeholder     "0"
          :show-cancel     false
          :auto-focus      false}]]
       [react/view {:flex         1
                    :padding-left 16}
        [quo/text-input
         {:label           (i18n/label :t/gas-price)
          :on-change-text  #(re-frame/dispatch [:signing.edit-fee.ui/edit-value :gasPrice %])
          :bottom-value    0
          :error           (:error gas-price-edit)
          :default-value   (:value gas-price-edit)
          :keyboard-type   :numeric
          :auto-capitalize :none
          :placeholder     "0.000"
          :show-cancel     false
          :auto-focus      false}]]
       [react/view {:padding-left   8
                    :padding-bottom 12}
        [react/text (i18n/label :t/gwei)]]]

      [react/view {:margin-vertical 16 :align-items :center}
       [react/text {:style {:color colors/gray}} (i18n/label :t/wallet-transaction-total-fee)]
       [react/view {:height 8}]
       [react/nested-text {:style {:font-size 17}}
        max-fee " "
        [{:style {:color colors/gray}} fee-display-symbol]]]]
     [react/view {:height 1 :background-color colors/gray-lighter}]
     [react/view {:margin-horizontal 16 :align-items :center :justify-content :space-between :flex-direction :row :margin-top 6}
      [quo/button
       {:type     :secondary
        :on-press #(re-frame/dispatch [:bottom-sheet/hide])}
       (i18n/label :t/cancel)]
      [quo/button
       {:type      :secondary
        :on-press  #(re-frame/dispatch [:signing.edit-fee.ui/submit])
        :disabled  (or (:error gas-edit) (:error gas-price-edit))}
       (i18n/label :t/update)]]]))

(declare fee-bottom-sheet-eip1559)

(defn fee-bottom-sheet-eip1559-custom [fee-display-symbol]
  (let [{gas-edit                      :gas
         max-fee-per-gas-edit          :maxFeePerGas
         max-priority-fee-per-gas-edit :maxPriorityFeePerGas}
        @(re-frame/subscribe [:signing/edit-fee])
        base-fee @(re-frame/subscribe [:wallet/latest-base-fee])
        [fee-currency fiat-currency price]
        @(re-frame/subscribe [:signing/currencies])
        fee-eth
        (if (and (:value-number gas-edit)
                 (:value-number max-fee-per-gas-edit))
          (money/mul
           (money/wei->ether
            (money/->wei :gwei (:value-number max-fee-per-gas-edit)))
           (:value-number gas-edit))
          (money/bignumber 0))]
    [react/view
     [react/view {:style {:margin-horizontal 16 :margin-top 8}}
      [react/text {:style {:typography :title-bold}} (i18n/label :t/max-priority-fee)]
      [react/text {:style {:color      (quo-colors/get-color :text-02)
                           :margin-top 12}}
       (i18n/label :t/miners-higher-fee)]
      [react/view
       {:style {:margin-top      12
                :flex-direction  :row
                :align-items     :center
                :justify-content :space-between}}
       [quo/text (i18n/label :t/current-base-fee)]
       [quo/text
        (money/to-fixed (money/wei-> :gwei base-fee))
        " "
        (i18n/label :t/gwei)]]]
     [react/view {:margin-vertical   12
                  :margin-horizontal 16
                  :height            1
                  :background-color  colors/gray-lighter}]
     [react/view
      {:margin-horizontal 16
       :margin-top        4
       :margin-bottom     26}
      [quo/text-input
       {:label          (i18n/label :t/gas-amount-limit)
        :error          (:error gas-edit)
        :default-value  (:value gas-edit)
        :on-change-text #(re-frame/dispatch [:signing.edit-fee.ui/edit-value :gas %])
        :show-cancel    false}]]
     [react/view
      {:margin-horizontal 16
       :margin-top        4
       :margin-bottom     26}
      [quo/text-input
       {:label          (i18n/label :t/per-gas-tip-limit)
        :error          (or (:error max-priority-fee-per-gas-edit)
                            (get-in max-priority-fee-per-gas-edit [:fee-error :label]))
        :default-value  (:value max-priority-fee-per-gas-edit)
        :on-change-text #(re-frame/dispatch [:signing.edit-fee.ui/edit-value :maxPriorityFeePerGas %])
        :show-cancel    false
        :after          {:component [quo/text
                                     {:style {:color (quo-colors/get-color :text-02)}}
                                     (i18n/label :t/gwei)]}}]]
     [react/view
      {:margin-horizontal 16
       :margin-top        4
       :margin-bottom     12}
      [quo/text-input
       {:label          (i18n/label :t/per-gas-price-limit)
        :error          (or (:error max-fee-per-gas-edit)
                            (get-in max-fee-per-gas-edit [:fee-error :label]))
        :default-value  (:value max-fee-per-gas-edit)
        :on-change-text #(re-frame/dispatch [:signing.edit-fee.ui/edit-value :maxFeePerGas %])
        :show-cancel    false
        :after          {:component [quo/text
                                     {:style {:color (quo-colors/get-color :text-02)}}
                                     (i18n/label :t/gwei)]}}]]
     [react/view {:margin-vertical  12
                  :height           1
                  :background-color colors/gray-lighter}]
     [react/view
      {:style {:margin-top        4
               :margin-horizontal 16
               :flex-direction    :row
               :align-items       :center
               :justify-content   :space-between}}
      [quo/text (i18n/label :t/maximum-fee) ":"]
      [react/view
       {:style {:flex-direction  :row
                :align-items     :center
                :justify-content :flex-end}}
       [quo/text
        {:style {:margin-right 6
                 :color        (quo-colors/get-color :text-02)}}
        (str (money/to-fixed fee-eth 6) " " fee-currency)]
       [quo/text
        (money/to-fixed (money/mul fee-eth (money/bignumber price)) 2)
        " "
        fiat-currency]]]
     [react/text {:style {:color  (quo-colors/get-color :text-02)
                          :margin 16}}
      (i18n/label :t/fee-explanation)]
     [react/view
      {:style {:margin-left     12
               :margin-right    16
               :flex-direction  :row
               :align-items     :center
               :justify-content :space-between}}
      [quo/button
       {:type     :secondary
        :on-press #(re-frame/dispatch
                    [:bottom-sheet/show-sheet
                     {:content        (fn []
                                        [fee-bottom-sheet-eip1559 fee-display-symbol])
                      :content-height 270}])}
       (i18n/label :t/see-suggestions)]
      [quo/button
       {:type  :primary
        :on-press #(re-frame/dispatch [:signing.edit-fee.ui/submit])
        :theme :accent}
       (i18n/label :t/save)]]]))

(defn fee-bottom-sheet-eip1559 [fee-display-symbol]
  (let [{priority-fee-edit :maxPriorityFeePerGas
         fee-edit :maxFeePerGas
         gas :gas}
        @(re-frame/subscribe [:signing/edit-fee])
        [min-value max-value]
        @(re-frame/subscribe [:signing/priority-fee-suggestions-range])
        [fee-currency fiat-currency price] @(re-frame/subscribe [:signing/currencies])
        fee-eth
        (if (and (:value-number gas)
                 (:value-number fee-edit))
          (money/mul
           (money/wei->ether
            (money/->wei :gwei (:value-number fee-edit)))
           (:value-number gas))
          (money/bignumber 0))]
    [react/view
     [react/view {:style {:margin-horizontal 16 :margin-top 8}}
      [react/text {:style {:typography :title-bold}} (i18n/label :t/max-priority-fee)]
      [react/text {:style {:color (quo-colors/get-color :text-02)
                           :margin-top 12}}
       (i18n/label :t/miners-higher-fee)]
      [quo/text {:style {:margin-horizontal 8
                         :margin-top 24}
                 :size :large}
       (clojure.string/join
        " "
        [(money/to-fixed fee-eth 6)
         fee-currency
         "•"
         (money/to-fixed (money/mul fee-eth (money/bignumber price)) 2)
         fiat-currency])]
      [slider/animated-slider
       {:style {:margin-horizontal 5
                :margin-vertical 4}
        :minimumValue min-value
        :value (js/parseFloat (:value priority-fee-edit))
        :onValueChange #(re-frame/dispatch [:signing.edit-fee.ui/set-priority-fee (str %)])
        :maximumValue max-value
        :minimumTrackTintColor (quo-colors/get-color :icon-04)}]
      [react/view
       {:style {:margin-horizontal 8
                :flex-direction    :row
                :align-items       :center
                :justify-content   :space-between}}
       [react/text {:style {:color (quo-colors/get-color :text-02)}} (i18n/label :t/slow)]
       [react/text {:style {:color (quo-colors/get-color :text-02)}} (i18n/label :t/optimal)]
       [react/text {:style {:color (quo-colors/get-color :text-02)}} (i18n/label :t/fast)]]]
     [react/view
      {:style {:margin-left 12
               :margin-right 16
               :margin-top 38}
       :flex-direction    :row
       :align-items       :center
       :justify-content   :space-between}
      [quo/button
       {:type :secondary
        :on-press #(re-frame/dispatch
                    [:bottom-sheet/show-sheet
                     {:content        (fn []
                                        [fee-bottom-sheet-eip1559-custom fee-display-symbol])
                      :content-height 270}])}
       (i18n/label :t/set-custom-fee)]
      [quo/button
       {:type :primary
        :theme :accent
        :on-press #(re-frame/dispatch [:signing.edit-fee.ui/submit])}
       (i18n/label :t/save)]]]))

(defn gwei [val]
  (str val " " (i18n/label :t/gwei)))

(defn fees-warning []
  (let [base-fee     @(re-frame/subscribe [:wallet/latest-base-fee])
        base-fee-gwei (money/wei-> :gwei (money/bignumber base-fee))
        priority-fee @(re-frame/subscribe [:wallet/latest-priority-fee])
        priority-fee-gwei (money/wei-> :gwei (money/bignumber priority-fee))
        {priority-fee-edit :maxPriorityFeePerGas
         fee-edit          :maxFeePerGas}
        @(re-frame/subscribe [:signing/edit-fee])]
    [react/view
     [react/view {:margin-top        24
                  :margin-horizontal 24
                  :margin-bottom     32
                  :align-items       :center}
      [react/view {:background-color colors/blue-light
                   :width            32
                   :height           32
                   :border-radius    16
                   :align-items      :center
                   :justify-content  :center}
       [icons/icon :main-icons/warning {:color colors/black}]]
      [react/text {:style {:typography    :title-bold
                           :margin-top    16
                           :margin-bottom 8}}
       (i18n/label :t/are-you-sure)]
      [react/text {:style {:color             colors/gray
                           :text-align        :center
                           :margin-horizontal 24}}
       (i18n/label :t/bad-fees-description)]]
     [react/view
      {:style {:flex-direction    :row
               :justify-content   :space-between
               :margin-horizontal 32}}
      [react/text (i18n/label :t/current-base-fee)]
      [react/text (gwei (money/to-fixed base-fee-gwei 2))]]
     [react/view
      {:style {:flex-direction    :row
               :justify-content   :space-between
               :margin-horizontal 32}}
      [react/text (i18n/label :t/current-minimum-tip)]
      [react/text (gwei gas/minimum-priority-fee)]]
     [react/view
      {:style {:flex-direction    :row
               :justify-content   :space-between
               :margin-horizontal 32}}
      [react/text (i18n/label :t/current-average-tip)]
      [react/text (gwei (money/to-fixed priority-fee-gwei 2))]]
     [react/view {:margin-vertical  16
                  :height           1
                  :background-color colors/gray-lighter}]
     [react/view
      {:style {:flex-direction    :row
               :justify-content   :space-between
               :margin-horizontal 32
               :color             :red}}
      [react/text {:style {:color (quo-colors/get-color :negative-01)}}
       (i18n/label :t/your-tip-limit)]
      [react/text {:style {:color (quo-colors/get-color :negative-01)}}
       (gwei (money/to-fixed (:value-number priority-fee-edit) 2))]]
     [react/view
      {:style {:flex-direction    :row
               :justify-content   :space-between
               :margin-horizontal 32}}
      [react/text {:style {:color (quo-colors/get-color :negative-01)}}
       (i18n/label :t/your-price-limit)]
      [react/text {:style {:color (quo-colors/get-color :negative-01)}}
       (gwei (money/to-fixed (:value-number fee-edit) 2))]]
     [react/view {:style
                  {:background-color   colors/gray-lighter
                   :padding-horizontal 32
                   :padding-vertical   16
                   :margin-vertical    16}}
      [react/view
       {:style {:flex-direction  :row
                :justify-content :space-between}}
       [react/text (i18n/label :t/suggested-min-tip)]
       [react/text (gwei gas/minimum-priority-fee)]]
      [react/view
       {:style {:flex-direction  :row
                :justify-content :space-between}}
       [react/text (i18n/label :t/suggested-price-limit)]
       [react/text (gwei (money/to-fixed (money/add base-fee-gwei priority-fee-gwei) 2))]]]
     [react/view
      {:style {:align-items     :center
               :justify-content :center
               :margin-top      8}}
      [quo/button
       {:type :primary
        :on-press #(re-frame/dispatch [:hide-popover])}
       (i18n/label :t/change-tip)]]
     [react/view
      {:style {:align-items     :center
               :justify-content :center
               :margin-top      8
               :margin-bottom   16}}
      [quo/button
       {:type :secondary
        :on-press #(do (re-frame/dispatch [:hide-popover])
                       (re-frame/dispatch [:signing.edit-fee.ui/submit true]))}
       (i18n/label :t/continue-anyway)]]]))

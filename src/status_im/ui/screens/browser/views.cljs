(ns status-im.ui.screens.browser.views
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [status-im.browser.core :as browser]
            [status-im.browser.webview-ref :as webview-ref]
            [status-im.i18n.i18n :as i18n]
            [status-im.ui.components.chat-icon.screen :as chat-icon]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.connectivity.view :as connectivity]
            [status-im.ui.components.icons.icons :as icons]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.tooltip.views :as tooltip]
            [status-im.ui.components.webview :as components.webview]
            [status-im.ui.screens.browser.accounts :as accounts]
            [status-im.ui.screens.browser.permissions.views :as permissions.views]
            [status-im.ui.screens.browser.site-blocked.views :as site-blocked.views]
            [status-im.ui.screens.browser.styles :as styles]
            [status-im.utils.debounce :as debounce]
            [status-im.utils.http :as http]
            [status-im.utils.js-resources :as js-res]
            [status-im.utils.contenthash :as contenthash]
            [status-im.ui.components.permissions :as components.permissions]
            [quo.core :as quo]
            [status-im.ui.screens.wallet.components.views :as components]
            [status-im.ui.screens.browser.options.views :as options]
            [status-im.qr-scanner.core :as qr-scanner])
  (:require-macros [status-im.utils.views :as views]))

(defn toolbar-content [url url-original secure? url-editing? unsafe?]
  (let [url-text (atom url)]
    [react/view (styles/toolbar-content)
     [react/touchable-highlight {:on-press #(re-frame/dispatch [:browser.ui/lock-pressed secure?])
                                 :accessibility-label :security-icon}
      (if secure?
        [icons/tiny-icon :tiny-icons/tiny-lock {:color colors/green}]
        [icons/tiny-icon :tiny-icons/tiny-lock-broken {:color colors/dark}])]
     (if url-editing?
       [react/text-input {:on-change-text    #(reset! url-text %)
                          :on-blur           #(re-frame/dispatch [:browser.ui/url-input-blured])
                          :on-submit-editing #(re-frame/dispatch [:browser.ui/url-submitted @url-text])
                          :placeholder       (i18n/label :t/enter-url)
                          :auto-capitalize   :none
                          :auto-correct      false
                          :auto-focus        true
                          :default-value     url
                          :ellipsize         :end
                          :style             styles/url-input
                          :accessibility-label :browser-input}]
       [react/touchable-highlight {:style    styles/url-text-container
                                   :on-press #(re-frame/dispatch [:browser.ui/url-input-pressed])}
        [react/text {:number-of-lines 1} (http/url-host url-original)]])
     (when-not unsafe?
       [react/touchable-highlight {:on-press #(.reload ^js @webview-ref/webview-ref)
                                   :accessibility-label :refresh-page-button}
        [icons/icon :main-icons/refresh]])]))

(defn- web-view-error [_ _ desc]
  (reagent/as-element
   [react/view styles/web-view-error
    [react/image {:style  {:width 140 :height 140 :margin-bottom 16}
                  :source {:uri (contenthash/url
                                 "e3010170122001bbe2f5bfba0305a3bdc2047fddc47ee595a591bdee61de6040ffc2456624e1")}}]
    [react/i18n-text {:style styles/web-view-error-text :key :web-view-error}]
    [react/text {:style styles/web-view-error-text}
     (str desc)]]))

(views/defview navigation [{:keys [url can-go-back? can-go-forward? dapps-account empty-tab browser-id name]}]
  (views/letsubs [accounts [:accounts-without-watch-only]]
    [react/view (styles/navbar)
     [react/touchable-highlight {:on-press            #(if can-go-back?
                                                         (re-frame/dispatch [:browser.ui/previous-page-button-pressed])
                                                         (do
                                                           (re-frame/dispatch [:browser.ui/remove-browser-pressed browser-id])
                                                           (re-frame/dispatch [:browser.ui/open-empty-tab])))
                                 :disabled            empty-tab
                                 :style               (when empty-tab styles/disabled-button)
                                 :accessibility-label :previous-page-button}
      [react/view
       [icons/icon :main-icons/arrow-left]]]
     [react/touchable-highlight {:on-press            #(re-frame/dispatch [:browser.ui/next-page-button-pressed])
                                 :disabled            (not can-go-forward?)
                                 :style               (when-not can-go-forward? styles/disabled-button)
                                 :accessibility-label :next-page-button}
      [react/view
       [icons/icon :main-icons/arrow-right]]]
     [react/touchable-highlight
      {:accessibility-label :select-account
       :on-press            #(re-frame/dispatch [:bottom-sheet/show-sheet
                                                 {:content (accounts/accounts-list accounts dapps-account)}])}
      [chat-icon/custom-icon-view-list (:name dapps-account) (:color dapps-account) 32]]

     [react/touchable-highlight
      {:on-press #(re-frame/dispatch [:set-stack-root :browser-stack :browser-tabs])
       :accessibility-label :browser-open-tabs}
      [icons/icon :main-icons/tabs]]

     (if empty-tab
       [react/touchable-highlight
        {:accessibility-label :universal-qr-scanner
         :on-press            #(re-frame/dispatch
                                [::qr-scanner/scan-code
                                 {:handler ::qr-scanner/on-scan-success}])}
        [icons/icon :main-icons/qr]]
       [react/touchable-highlight
        {:on-press #(re-frame/dispatch
                     [:bottom-sheet/show-sheet
                      {:content (options/browser-options
                                 url
                                 dapps-account
                                 empty-tab
                                 name)}])
         :accessibility-label :browser-options}
        [icons/icon :main-icons/more]])]))

(def resources-to-permissions-map {"android.webkit.resource.VIDEO_CAPTURE" :camera
                                   "android.webkit.resource.AUDIO_CAPTURE" :record-audio})

(views/defview request-resources-panel [resources url webview-ref]
  [react/view styles/blocked-access-container
   [react/view styles/blocked-access-icon-container
    [icons/icon :main-icons/camera styles/blocked-access-camera-icon]]
   [react/view styles/blocked-access-text-container
    [react/text {:style styles/blocked-access-text}
     (str url " " (i18n/label :t/page-would-like-to-use-camera))]]
   [react/view styles/blocked-access-buttons-container
    [react/view styles/blocked-access-button-wrapper
     [quo/button
      {:theme    :positive
       :style    styles/blocked-access-button
       :on-press (fn []
                   (components.permissions/request-permissions
                    {:permissions (map resources-to-permissions-map resources)
                     :on-allowed  #(.answerPermissionRequest ^js webview-ref true resources)
                     :on-denied  #(.answerPermissionRequest ^js webview-ref false)})
                   (re-frame/dispatch [:bottom-sheet/hide]))}
      (i18n/label :t/allow)]]
    [react/view styles/blocked-access-button-wrapper
     [quo/button
      {:theme    :negative
       :style    styles/blocked-access-button
       :on-press (fn []
                   (.answerPermissionRequest ^js webview-ref false)
                   (re-frame/dispatch [:bottom-sheet/hide]))}
      (i18n/label :t/deny)]]]])

(views/defview block-resources-panel [url]
  [react/view styles/blocked-access-container
   [react/view styles/blocked-access-icon-container
    [icons/icon :main-icons/camera styles/blocked-access-camera-icon]]
   [react/view styles/blocked-access-text-container
    [react/text {:style styles/blocked-access-text}
     (str url " " (i18n/label :t/page-camera-request-blocked))]]])

(defn request-resources-access-for-page [resources url webview-ref]
  (re-frame/dispatch
   [:bottom-sheet/show-sheet
    {:content        (fn [] [request-resources-panel resources url webview-ref])
     :show-handle?       false
     :backdrop-dismiss?  false
     :disable-drag?      true
     :back-button-cancel false}]))

(defn block-resources-access-and-notify-user [url]
  (.answerPermissionRequest ^js @webview-ref/webview-ref false)
  (re-frame/dispatch [:bottom-sheet/show-sheet
                      {:content (fn [] [block-resources-panel url])}]))

;; should-component-update is called only when component's props are changed,
;; that's why it can't be used in `browser`, because `url` comes from subs
(views/defview browser-component
  [{:keys [error? url browser-id unsafe? can-go-back? ignore-unsafe
           can-go-forward? resolving? network-id url-original dapp? dapp
           show-permission show-tooltip name dapps-account resources-permission?]}]
  {:should-component-update (fn [_ _ args]
                              (let [[_ props] args]
                                (not (nil? (:url props)))))}
  [react/view {:flex 1
               :elevation -10}
   [react/view {:flex 1}
    (if (and unsafe? (not= (http/url-host url) ignore-unsafe))
      [site-blocked.views/view {:can-go-back? can-go-back?
                                :site         browser-id}]
      [components.webview/webview
       {:dapp?                                      dapp?
        :dapp-name                                  name
        :ref                                        #(reset! webview-ref/webview-ref %)
        :source                                     (when (and url (not resolving?)) {:uri url})
        :java-script-enabled                        true
        :bounces                                    false
        :local-storage-enabled                      true
        :render-error                               web-view-error
        :on-load-start                              #(do
                                                       (re-frame/dispatch [:set-in [:ens/registration :state] :searching])
                                                       (debounce/debounce-and-dispatch
                                                        [:browser/navigation-state-changed (.-nativeEvent %) error?]
                                                        500))
        :on-permission-request                      #(if resources-permission?
                                                       (request-resources-access-for-page (-> ^js % .-nativeEvent .-resources) url @webview-ref/webview-ref)
                                                       (block-resources-access-and-notify-user url))
        ;; Extract event data here due to
        ;; https://reactjs.org/docs/events.html#event-pooling
        :on-message                                 #(re-frame/dispatch [:browser/bridge-message-received (.. ^js % -nativeEvent -data)])
        :on-load                                    #(re-frame/dispatch [:browser/loading-started])
        :on-error                                   #(re-frame/dispatch [:browser/error-occured])
        :injected-java-script-before-content-loaded (js-res/ethereum-provider (str network-id))}])]
   [navigation {:url url-original
                :name name
                :can-go-back? can-go-back?
                :can-go-forward? can-go-forward?
                :dapps-account dapps-account
                :browser-id browser-id}]
   [permissions.views/permissions-panel [dapp? dapp dapps-account] show-permission]
   (when show-tooltip
     [tooltip/bottom-tooltip-info
      (if (= show-tooltip :secure)
        (i18n/label :t/browser-secure)
        (i18n/label :t/browser-not-secure))
      #(re-frame/dispatch [:browser.ui/close-tooltip-pressed])])])

(views/defview browser []
  (views/letsubs [window-width [:dimensions/window-width]
                  {:keys [browser-id dapp? dapp name unsafe? ignore-unsafe secure?] :as browser} [:get-current-browser]
                  {:keys [url error? loading? url-editing? show-tooltip show-permission resolving?]} [:browser/options]
                  dapps-account [:dapps-account]
                  network-id [:chain-id]
                  {:keys [webview-allow-permission-requests?]} [:multiaccount]]
    (let [can-go-back?    (browser/can-go-back? browser)
          can-go-forward? (browser/can-go-forward? browser)
          url-original    (browser/get-current-url browser)]
      [react/view {:style styles/browser}
       [toolbar-content url url-original (and (not error?) secure?) url-editing? unsafe?]
       [components/separator-dark]
       [react/view
        (when loading?
          [connectivity/loading-indicator-anim window-width])]
       [browser-component {:dapp?                 dapp?
                           :dapp                  dapp
                           :error?                error?
                           :url                   url
                           :url-original          url-original
                           :browser-id            browser-id
                           :unsafe?               unsafe?
                           :ignore-unsafe         ignore-unsafe
                           :can-go-back?          can-go-back?
                           :can-go-forward?       can-go-forward?
                           :resolving?            resolving?
                           :network-id            network-id
                           :show-permission       show-permission
                           :show-tooltip          show-tooltip
                           :name                  name
                           :dapps-account         dapps-account
                           :resources-permission? webview-allow-permission-requests?}]])))

(ns status-im.ui.screens.chat.views
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [status-im.i18n.i18n :as i18n]
            [status-im.ui.components.chat-icon.screen :as chat-icon.screen]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.connectivity.view :as connectivity]
            [status-im.ui.components.icons.icons :as icons]
            [status-im.ui.components.list.views :as list]
            [status-im.ui.screens.chat.components.reply :as reply]
            [status-im.ui.screens.chat.components.edit :as edit]
            [status-im.ui.components.react :as react]
            [quo.animated :as animated]
            [quo.react-native :as rn]
            [status-im.ui.screens.chat.audio-message.views :as audio-message]
            [quo.react :as quo.react]
            [status-im.ui.screens.chat.message.message :as message]
            [status-im.ui.screens.chat.stickers.views :as stickers]
            [status-im.ui.screens.chat.styles.main :as style]
            [status-im.ui.screens.chat.toolbar-content :as toolbar-content]
            [status-im.ui.screens.chat.image.views :as image]
            [status-im.ui.screens.chat.state :as state]
            [status-im.ui.screens.chat.extensions.views :as extensions]
            [status-im.ui.screens.chat.group :as chat.group]
            [status-im.ui.screens.chat.message.gap :as gap]
            [status-im.ui.components.invite.chat :as invite.chat]
            [status-im.ui.screens.chat.components.accessory :as accessory]
            [status-im.ui.screens.chat.components.input :as components]
            [status-im.ui.screens.chat.message.datemark :as message-datemark]
            [status-im.ui.components.toolbar :as toolbar]
            [quo.core :as quo]
            [clojure.string :as string]
            [status-im.constants :as constants]
            [status-im.utils.platform :as platform]
            [status-im.utils.utils :as utils]
            [status-im.ui.screens.chat.sheets :as sheets]
            [status-im.react-native.resources :as resources]))

(defn invitation-requests [chat-id admins]
  (let [current-pk @(re-frame/subscribe [:multiaccount/public-key])
        admin? (get admins current-pk)]
    (when admin?
      (let [invitations @(re-frame/subscribe [:group-chat/pending-invitations-by-chat-id chat-id])]
        (when (seq invitations)
          [react/touchable-highlight
           {:on-press            #(re-frame/dispatch [:navigate-to :group-chat-invite])
            :accessibility-label :invitation-requests-button}
           [react/view {:style (style/add-contact)}
            [react/text {:style style/add-contact-text}
             (i18n/label :t/group-membership-request)]]])))))

(defn add-contact-bar [public-key]
  (when-not @(re-frame/subscribe [:contacts/contact-added? public-key])
    [react/touchable-highlight
     {:on-press
      #(re-frame/dispatch [:contact.ui/add-to-contact-pressed public-key])
      :accessibility-label :add-to-contacts-button}
     [react/view {:style (style/add-contact)}
      [icons/icon :main-icons/add
       {:color colors/blue}]
      [react/i18n-text {:style style/add-contact-text :key :add-to-contacts}]]]))

(defn contact-request [public-key]
  [react/view {:style {:width "100%"
                       :justify-content :center
                       :align-items :center
                       :border-top-width 1
                       :border-color colors/gray-transparent-10}}
   [react/image {:source (resources/get-image :hand-wave)
                 :style  {:width 112
                          :height 96.71
                          :margin-top 17}}]
   [quo/text {:style {:margin-top 14}
              :weight :bold
              :size   :large}
    (i18n/label :t/say-hi)]
   [quo/text {:style {:margin-top 2
                      :margin-bottom 14}}
    (i18n/label :t/send-contact-request-message)]
   [react/view {:style {:padding-horizontal 16
                        :padding-bottom 8}}
    [quo/button
     {:style {:width "100%"}
      :accessibility-label :contact-request--button
      :on-press #(println "Contact request button pressed" public-key)}
     (i18n/label :t/contact-request)]]])

(defn chat-intro [{:keys [chat-id
                          chat-name
                          chat-type
                          group-chat
                          invitation-admin
                          contact-name
                          color
                          loading-messages?
                          no-messages?
                          contact-added?]}]
  [react/view {:style (style/intro-header-container loading-messages? no-messages?)
               :accessibility-label :history-chat}
   ;; Icon section
   [react/view {:style {:margin-top    42
                        :margin-bottom 24}}
    [chat-icon.screen/chat-intro-icon-view
     chat-name chat-id group-chat
     {:default-chat-icon      (style/intro-header-icon 120 color)
      :default-chat-icon-text style/intro-header-icon-text
      :size                   120}]]
   ;; Chat title section
   [react/text {:style (style/intro-header-chat-name)}
    (if group-chat chat-name contact-name)]
   ;; Description section
   (if group-chat
     [chat.group/group-chat-description-container {:chat-id chat-id
                                                   :invitation-admin invitation-admin
                                                   :loading-messages? loading-messages?
                                                   :chat-name chat-name
                                                   :chat-type chat-type
                                                   :no-messages? no-messages?}]
     [react/text {:style (assoc style/intro-header-description
                                :margin-bottom 32)}

      (str
       (i18n/label :t/empty-chat-description-one-to-one)
       contact-name)])
   (when-not (and contact-added? no-messages?)
     [contact-request chat-id])])

(defn chat-intro-one-to-one [{:keys [chat-id chat-type] :as opts}]
  (let [contact-names @(re-frame/subscribe
                        [:contacts/contact-two-names-by-identity chat-id])]
    [chat-intro (assoc opts
                       :contact-name (first contact-names)
                       :contact-added? (and (= chat-type constants/one-to-one-chat-type)
                                            @(re-frame/subscribe [:contacts/contact-added? chat-id])))]))

(defn chat-intro-header-container
  [{:keys [group-chat invitation-admin
           chat-type
           synced-to
           color chat-id chat-name
           public?]}
   no-messages]
  [react/touchable-without-feedback
   {:style               {:flex        1
                          :align-items :flex-start}
    :on-press (fn [_]
                (react/dismiss-keyboard!))}
   (let [opts
         {:chat-id chat-id
          :group-chat group-chat
          :invitation-admin invitation-admin
          :chat-type chat-type
          :chat-name chat-name
          :public? public?
          :color color
          :loading-messages? (not (pos? synced-to))
          :no-messages? no-messages}]
     (if group-chat
       [chat-intro opts]
       [chat-intro-one-to-one opts]))])

(defonce messages-list-ref (atom nil))

(defn on-viewable-items-changed [^js e]
  (when @messages-list-ref
    (reset! state/first-not-visible-item
            (when-let [^js last-visible-element (aget (.-viewableItems e) (dec (.-length ^js (.-viewableItems e))))]
              (let [index (.-index last-visible-element)
                    ;; Get first not visible element, if it's a datemark/gap
                    ;; we might unnecessarely add messages on receiving as
                    ;; they do not have a clock value, but most of the times
                    ;; it will be a message
                    first-not-visible (aget (.-data ^js (.-props ^js @messages-list-ref)) (inc index))]
                (when (and first-not-visible
                           (= :message (:type first-not-visible)))
                  first-not-visible))))))

(defn bottom-sheet [input-bottom-sheet]
  (case input-bottom-sheet
    :stickers
    [stickers/stickers-view]
    :extensions
    [extensions/extensions-view]
    :images
    [image/image-view]
    :audio
    [audio-message/audio-message-view]
    nil))

(defn invitation-bar [chat-id]
  (let [{:keys [state chat-id] :as invitation}
        (first @(re-frame/subscribe [:group-chat/invitations-by-chat-id chat-id]))
        {:keys [retry? message]} @(re-frame/subscribe [:chats/current-chat-membership])]
    [react/view {:margin-horizontal 16 :margin-top 10}
     (cond
       (and invitation (= constants/invitation-state-requested state) (not retry?))
       [toolbar/toolbar {:show-border? true
                         :center
                         [quo/button
                          {:type     :secondary
                           :disabled true}
                          (i18n/label :t/request-pending)]}]

       (and invitation (= constants/invitation-state-rejected state) (not retry?))
       [toolbar/toolbar {:show-border? true
                         :right
                         [quo/button
                          {:type     :secondary
                           :accessibility-label :retry-button
                           :on-press #(re-frame/dispatch [:group-chats.ui/membership-retry])}
                          (i18n/label :t/mailserver-retry)]
                         :left
                         [quo/button
                          {:type     :secondary
                           :accessibility-label :remove-group-button
                           :on-press #(re-frame/dispatch [:group-chats.ui/remove-chat-confirmed chat-id])}
                          (i18n/label :t/remove-group)]}]
       :else
       [toolbar/toolbar {:show-border? true
                         :center
                         [quo/button
                          {:type                :secondary
                           :accessibility-label :introduce-yourself-button
                           :disabled            (string/blank? message)
                           :on-press            #(re-frame/dispatch [:send-group-chat-membership-request])}
                          (i18n/label :t/request-membership)]}])]))

(defn get-space-keeper-ios [bottom-space panel-space active-panel text-input-ref]
  (fn [state]
    ;; NOTE: Only iOs now because we use soft input resize screen on android
    (when platform/ios?
      (cond
        (and state
             (< @bottom-space @panel-space)
             (not @active-panel))
        (reset! bottom-space @panel-space)

        (and (not state)
             (< @panel-space @bottom-space))
        (do
          (some-> ^js (quo.react/current-ref text-input-ref) .focus)
          (reset! panel-space @bottom-space)
          (reset! bottom-space 0))))))

(defn get-set-active-panel [active-panel]
  (fn [panel]
    (rn/configure-next
     (:ease-opacity-200 rn/custom-animations))
    (reset! active-panel panel)
    (reagent/flush)
    (when panel
      (js/setTimeout #(react/dismiss-keyboard!) 100))))

(defn list-footer [{:keys [chat-id chat-type] :as chat}]
  (let [loading-messages? @(re-frame/subscribe [:chats/loading-messages? chat-id])
        no-messages? @(re-frame/subscribe [:chats/chat-no-messages? chat-id])
        all-loaded? @(re-frame/subscribe [:chats/all-loaded? chat-id])]
    [react/view {:style (when platform/android? {:scaleY -1})}
     (if (or loading-messages? (not chat-id) (not all-loaded?))
       [react/view {:height 324 :align-items :center :justify-content :center}
        [react/activity-indicator {:animating true}]]
       [chat-intro-header-container chat no-messages?])
     (when (= chat-type constants/one-to-one-chat-type)
       [invite.chat/reward-messages])]))

(defn list-header [{:keys [chat-id chat-type invitation-admin]}]
  (when (= chat-type constants/private-group-chat-type)
    [react/view {:style (when platform/android? {:scaleY -1})}
     [chat.group/group-chat-footer chat-id invitation-admin]]))

(defn render-fn [{:keys [outgoing type] :as message}
                 idx
                 _
                 {:keys [group-chat public? current-public-key space-keeper chat-id show-input? message-pin-enabled edit-enabled in-pinned-view?]}]
  [react/view {:style (when (and platform/android? (not in-pinned-view?)) {:scaleY -1})}
   (if (= type :datemark)
     [message-datemark/chat-datemark (:value message)]
     (if (= type :gap)
       [gap/gap message idx messages-list-ref false chat-id]
       ; message content
       [message/chat-message
        (assoc message
               :incoming-group (and group-chat (not outgoing))
               :group-chat group-chat
               :public? public?
               :current-public-key current-public-key
               :show-input? show-input?
               :message-pin-enabled message-pin-enabled
               :edit-enabled edit-enabled)
        space-keeper]))])

(def list-key-fn #(or (:message-id %) (:value %)))
(def list-ref #(reset! messages-list-ref %))

;;TODO this is not really working in pair with inserting new messages because we stop inserting new messages
;;if they outside the viewarea, but we load more here because end is reached,so its slowdown UI because we
;;load and render 20 messages more, but we can't prevent this , because otherwise :on-end-reached will work wrong
(defn list-on-end-reached []
  (if @state/scrolling
    (re-frame/dispatch [:chat.ui/load-more-messages-for-current-chat])
    (utils/set-timeout #(re-frame/dispatch [:chat.ui/load-more-messages-for-current-chat])
                       (if platform/low-device? 700 200))))

(defn get-render-data [{:keys [group-chat chat-id public? community-id admins space-keeper show-input? edit-enabled in-pinned-view?]}]
  (let [current-public-key @(re-frame/subscribe [:multiaccount/public-key])
        community @(re-frame/subscribe [:communities/community community-id])
        group-admin? (get admins current-public-key)
        community-admin? (when community (community :admin))
        message-pin-enabled (and (not public?)
                                 (or (not group-chat)
                                     (and group-chat
                                          (or group-admin?
                                              community-admin?))))]
    {:group-chat          group-chat
     :public?             public?
     :current-public-key  current-public-key
     :space-keeper        space-keeper
     :chat-id             chat-id
     :show-input?         show-input?
     :message-pin-enabled message-pin-enabled
     :edit-enabled        edit-enabled
     :in-pinned-view?     in-pinned-view?}))

(defn messages-view [{:keys [chat bottom-space pan-responder space-keeper show-input?]}]
  (let [{:keys [group-chat chat-id public? community-id admins]} chat
        messages @(re-frame/subscribe [:chats/chat-messages-stream chat-id])]
    ;;do not use anonymous functions for handlers
    [list/flat-list
     (merge
      pan-responder
      {:key-fn                       list-key-fn
       :ref                          list-ref
       :header                       [list-header chat]
       :footer                       [list-footer chat]
       :data                         messages
       :render-data                  (get-render-data {:group-chat      group-chat
                                                       :chat-id         chat-id
                                                       :public?         public?
                                                       :community-id    community-id
                                                       :admins          admins
                                                       :space-keeper    space-keeper
                                                       :show-input?     show-input?
                                                       :edit-enabled    true
                                                       :in-pinned-view? false})
       :render-fn                    render-fn
       :on-viewable-items-changed    on-viewable-items-changed
       :on-end-reached               list-on-end-reached
       :on-scroll-to-index-failed    identity              ;;don't remove this
       :content-container-style      {:padding-top (+ bottom-space 16)
                                      :padding-bottom 16}
       :scroll-indicator-insets      {:top bottom-space}    ;;ios only
       :keyboard-dismiss-mode        :interactive
       :keyboard-should-persist-taps :handled
       :onMomentumScrollBegin        state/start-scrolling
       :onMomentumScrollEnd          state/stop-scrolling
       ;;TODO https://github.com/facebook/react-native/issues/30034
       :inverted                     (when platform/ios? true)
       :style                        (when platform/android? {:scaleY -1})})]))

(defn topbar-button []
  (re-frame/dispatch [:bottom-sheet/show-sheet
                      {:content (fn []
                                  [sheets/current-chat-actions])
                       :height  256}]))

(defn topbar []
  [toolbar-content/toolbar-content-view-inner @(re-frame/subscribe [:chats/current-chat])])

(defn chat []
  (let [curr-chat-id (:chat-id @(re-frame/subscribe [:chats/current-chat-chat-view]))
        bottom-space (reagent/atom 0)
        panel-space (reagent/atom 52)
        active-panel (reagent/atom nil)
        position-y (animated/value 0)
        pan-state (animated/value 0)
        text-input-ref (quo.react/create-ref)
        on-update #(when-not (zero? %) (reset! panel-space %))
        pan-responder (accessory/create-pan-responder position-y pan-state)
        space-keeper (get-space-keeper-ios bottom-space panel-space active-panel text-input-ref)
        set-active-panel (get-set-active-panel active-panel)
        on-close #(set-active-panel nil)]
    (reagent/create-class
     {:component-will-unmount #(re-frame/dispatch-sync [:close-chat curr-chat-id])
      :component-did-mount (fn [] (js/setTimeout #(re-frame/dispatch [:set :ignore-close-chat false]) 500))
      :reagent-render
      (fn []
        (let [{:keys [chat-id chat-type show-input? group-chat admins invitation-admin] :as chat}
              ;;we want to react only on these fields, do not use full chat map here
              @(re-frame/subscribe [:chats/current-chat-chat-view])
              max-bottom-space (max @bottom-space @panel-space)
              needs-contact-request? (and (= chat-type constants/one-to-one-chat-type)
                                          @(re-frame/subscribe [:contacts/contact-added? chat-id]))]
          [:<>
           [connectivity/loading-indicator]
           (when chat-id
             (if group-chat
               [invitation-requests chat-id admins]
               [add-contact-bar chat-id]))
           ;;MESSAGES LIST
           [messages-view {:chat          chat
                           :bottom-space  max-bottom-space
                           :pan-responder pan-responder
                           :space-keeper  space-keeper
                           :show-input?   show-input?}]
           (when (and group-chat invitation-admin)
             [accessory/view {:y               position-y
                              :on-update-inset on-update}
              [invitation-bar chat-id]])
           [components/autocomplete-mentions text-input-ref max-bottom-space]
           (when show-input?
             ;; NOTE: this only accepts two children
             [accessory/view {:y               position-y
                              :pan-state       pan-state
                              :has-panel       (boolean @active-panel)
                              :on-close        on-close
                              :on-update-inset on-update}
              [react/view
               [edit/edit-message-auto-focus-wrapper text-input-ref]
               [reply/reply-message-auto-focus-wrapper text-input-ref]
               ;; We set the key so we can force a re-render as
               ;; it does not rely on ratom but just atoms
               ^{:key (str @components/chat-input-key "chat-input")}
               [components/chat-toolbar
                {:chat-id          chat-id
                 :active-panel     @active-panel
                 :set-active-panel set-active-panel
                 :text-input-ref   text-input-ref}]]
              [bottom-sheet @active-panel]])]))})))

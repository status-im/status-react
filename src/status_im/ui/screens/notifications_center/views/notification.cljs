(ns status-im.ui.screens.notifications-center.views.notification
  (:require [status-im.ui.components.react :as react]
            [re-frame.core :as re-frame]
            [quo.core :as quo]
            [clojure.string :as string]
            [status-im.i18n.i18n :as i18n]
            [status-im.ui.screens.notifications-center.styles :as styles]
            [status-im.utils.handlers :refer [<sub]]
            [status-im.ui.screens.chat.photos :as photos]
            [status-im.multiaccounts.core :as multiaccounts]
            [status-im.ui.components.icons.icons :as icons]
            [status-im.utils.contenthash :as contenthash]
            [status-im.constants :as constants]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.screens.home.views.inner-item :as home-item]))

(defn mention-element [from]
  (let [contact-name @(re-frame/subscribe [:contacts/contact-name-by-identity from])]
    (str (when-not (= (subs contact-name 0 1) "@") "@") contact-name)))

(def max-notification-length 160)
(def max-notification-lines 2)
(def max-reply-lines 1)

(defn add-parsed-to-message [acc style text-weight {:keys [type destination literal children]}]
  (let [result (case type
                 "paragraph"
                 (reduce
                  (fn [{:keys [_ length] :as acc-paragraph} parsed-child]
                    (if (>= length max-notification-length)
                      (reduced acc-paragraph)
                      (add-parsed-to-message acc-paragraph style text-weight parsed-child)))
                  {:components [quo/text {:style style
                                          :weight text-weight}]
                   :length     0}
                  children)

                 "mention"
                 {:components [quo/text {:style (merge style styles/mention-text)} [mention-element literal]]
                  :length     4}                            ;; we can't predict name length so take the smallest possible

                 "status-tag"
                 (home-item/truncate-literal (str "#" literal))

                 "link"
                 (home-item/truncate-literal destination)

                 (home-item/truncate-literal (string/replace literal #"\n" " ")))]
    {:components (conj (:components acc) (:components result))
     :length     (+ (:length acc) (:length result))}))

(defn message-wrapper
  ([] (message-wrapper 1 styles/notification-reply-text))
  ([number-of-lines style]
   [react/text-class {:style               style
                      :number-of-lines     number-of-lines
                      :ellipsize-mode      :tail
                      :accessibility-label :chat-message-text}]))

(defn render-message
  "Render the preview of a message with a maximum length, maximum lines, style and font weight"
  ([parsed-text] (render-message parsed-text max-notification-length max-notification-lines styles/notification-message-text :regular))
  ([parsed-text max-length number-of-lines style text-weight]
   (let [result
         (reduce
          (fn [{:keys [_ length] :as acc-text} new-text-chunk]
            (if (>= length max-length)
              (reduced acc-text)
              (add-parsed-to-message acc-text style text-weight new-text-chunk)))
          {:components (message-wrapper number-of-lines style)
           :length     0}
          parsed-text)]
     (:components result))))

(defn message-content-text [{:keys [content content-type community-id]} max-number-of-lines style text-weight]
  [react/view
   (cond

     (not (and content content-type))
     [react/text {:style               (merge
                                        style
                                        {:color colors/gray})
                  :accessibility-label :no-messages-text}
      (i18n/label :t/no-messages)]

     (= constants/content-type-sticker content-type)
     [react/image {:style  {:margin 1 :width 20 :height 20}
                   ;;TODO (perf) move to event
                   :source {:uri (contenthash/url (-> content :sticker :hash))}}]

     (= constants/content-type-image content-type)
     [react/text {:style               style
                  :accessibility-label :no-messages-text}
      (i18n/label :t/image)]

     (= constants/content-type-audio content-type)
     [react/text {:style               style
                  :accessibility-label :no-messages-text}
      (i18n/label :t/audio)]

     (= constants/content-type-community content-type)
     (let [{:keys [name]}
           @(re-frame/subscribe [:communities/community community-id])]
       [react/text {:style               style
                    :accessibility-label :no-messages-text}
        (i18n/label :t/community-message-preview {:community-name name})])

     (string/blank? (:text content))
     [react/text {:style style}
      ""]

     (:text content)
     (render-message (:parsed-text content) max-notification-length max-number-of-lines style text-weight))])

(defn activity-text-item [home-item opts]
  (let [{:keys [chat-id chat-name message last-message reply-message muted read group-chat timestamp type]} home-item
        message (or message last-message)
        {:keys [community-id]} (<sub [:chat-by-id chat-id])
        {:keys [name]} @(re-frame/subscribe [:communities/community community-id])
        contact (when message @(re-frame/subscribe [:contacts/contact-by-identity (message :from)]))
        sender (when message (first @(re-frame/subscribe [:contacts/contact-two-names-by-identity (message :from)])))]
    [react/touchable-opacity (merge {:style (styles/notification-container read)} opts)
     [react/view {:style styles/notification-content-container}
      [react/view {:style styles/photo-container}
       [photos/photo
        (multiaccounts/displayed-photo contact)
        {:size 40
         :accessibility-label :current-account-photo}]]
      [quo/text {:weight              :medium
                 :color               (when muted :secondary)
                 :accessibility-label :chat-name-or-sender-text
                 :ellipsize-mode      :tail
                 :number-of-lines     1
                 :style               styles/title-text}
       (if (or
            (= type constants/activity-center-notification-type-mention)
            (= type constants/activity-center-notification-type-reply))
         sender
         [home-item/chat-item-title chat-id muted group-chat chat-name])]
      [react/text {:style               styles/datetime-text
                   :number-of-lines     1
                   :accessibility-label :notification-time-text}
       ;;TODO (perf) move to event
       (home-item/memo-timestamp timestamp)]
      [react/view {:style styles/notification-message-container}
       [message-content-text (select-keys message [:content :content-type :community-id]) max-notification-lines styles/notification-message-text]
       (cond (= type constants/activity-center-notification-type-mention)
             [react/view {:style styles/group-info-container
                          :accessibility-label :chat-name-container}
              [icons/icon
               (if community-id :main-icons/tiny-community :main-icons/tiny-group)
               {:color  colors/gray
                :width  16
                :height 16
                :container-style styles/group-icon}]
              (when community-id
                [react/view {:style styles/community-info-container}
                 [quo/text {:color :secondary
                            :weight :medium
                            :size :small}
                  name]
                 [icons/icon
                  :main-icons/chevron-down
                  {:color  colors/gray
                   :width  16
                   :height 22}]])
              [quo/text {:color :secondary
                         :weight :medium
                         :size :small}
               (str (when community-id "#") chat-name)]]

             (= type constants/activity-center-notification-type-reply)
             [react/view {:style styles/reply-message-container
                          :accessibility-label :reply-message-container}
              [icons/icon
               :main-icons/tiny-reply
               {:color  colors/gray
                :width  18
                :height 18
                :container-style styles/reply-icon}]
              [message-content-text (select-keys reply-message [:content :content-type :community-id]) max-reply-lines styles/notification-reply-text :medium]])]]]))

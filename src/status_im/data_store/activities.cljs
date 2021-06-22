(ns status-im.data-store.activities
  (:require [status-im.data-store.messages :as messages]
            [status-im.constants :as constants]
            [status-im.ui.components.colors :as colors]
            clojure.set))

(defn rpc->type [{:keys [type name] :as chat}]
  (cond
    (= constants/activity-center-notification-type-reply type)
    (assoc chat
           :chat-name name
           :chat-type constants/private-group-chat-type)

    (= constants/activity-center-notification-type-mention type)
    (assoc chat
           :chat-type constants/private-group-chat-type
           :chat-name name)

    (= constants/activity-center-notification-type-private-group-chat type)
    (assoc chat
           :chat-type constants/private-group-chat-type
           :chat-name name
           :public? false
           :group-chat true)

    (= constants/activity-center-notification-type-one-to-one-chat type)
    (assoc chat
           :chat-type constants/one-to-one-chat-type
           :chat-name name
           :public? false
           :group-chat false)))

(defn <-rpc [item]
  (-> item
      rpc->type
      (clojure.set/rename-keys {:lastMessage  :last-message
                                :replyMessage :reply-message
                                :chatId       :chat-id})
      (assoc :color (rand-nth colors/chat-colors))
      (update :last-message #(when % (messages/<-rpc %)))
      (update :message #(when % (messages/<-rpc %)))
      (update :reply-message #(when % (messages/<-rpc %)))
      (dissoc :chatId)))

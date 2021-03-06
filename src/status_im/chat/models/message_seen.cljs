(ns status-im.chat.models.message-seen
  (:require [status-im.utils.fx :as fx]
            [status-im.data-store.messages :as messages-store]))

(defn subtract-seen-messages
  [old-count new-seen-messages-ids]
  (max 0 (- old-count (count new-seen-messages-ids))))

(fx/defn update-chats-unviewed-messages-count
  [{:keys [db]} {:keys [chat-id _]}]
  (let [{:keys [loaded-unviewed-messages-ids unviewed-messages-count]}
        (get-in db [:chats chat-id])]
    ;; We currently only use this for private group chats and one-to-ones
    ;; but this method would have to be changed if we showed unviewed-mentions-count
    ;; in one to ones and private group chats as well
    {:db (update-in db [:chats chat-id] assoc
                    :unviewed-messages-count (subtract-seen-messages
                                              unviewed-messages-count
                                              loaded-unviewed-messages-ids))}))

(fx/defn mark-messages-seen
  "Marks all unviewed loaded messages as seen in particular chat"
  [{:keys [db] :as cofx} chat-id]
  (let [loaded-unviewed-ids (get-in db [:chats chat-id :loaded-unviewed-messages-ids])]
    (when (seq loaded-unviewed-ids)
      (fx/merge cofx
                {:db (reduce (fn [acc message-id]
                               (assoc-in acc [:messages chat-id message-id :seen]
                                         true))
                             db
                             loaded-unviewed-ids)}
                (messages-store/mark-messages-seen chat-id loaded-unviewed-ids nil)
                (update-chats-unviewed-messages-count {:chat-id chat-id})))))

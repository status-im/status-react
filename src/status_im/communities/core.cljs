(ns status-im.communities.core
  (:require
   [re-frame.core :as re-frame]
   [clojure.walk :as walk]
   [clojure.string :as string]
   [clojure.set :as clojure.set]
   [taoensso.timbre :as log]
   [status-im.async-storage.core :as async-storage]
   [status-im.utils.fx :as fx]
   [status-im.constants :as constants]
   [status-im.bottom-sheet.core :as bottom-sheet]
   [status-im.utils.universal-links.core :as universal-links]
   [status-im.ethereum.json-rpc :as json-rpc]
   [status-im.ui.components.colors :as colors]
   [status-im.navigation :as navigation]))

(def crop-size 1000)

(defn universal-link [community-id]
  (str (:external universal-links/domains)
       "/c/"
       community-id))

(defn sanitize-name
  "sanitize-name replace uppercase with lower case and
  spaces with dashes"
  [n]
  (->
   n
   str
   string/lower-case
   (string/replace " " "-")))

(def featured
  [{:name "Status"
    :id constants/status-community-id}])

(defn <-request-to-join-community-rpc [r]
  (clojure.set/rename-keys r {:communityId :community-id
                              :publicKey :public-key
                              :chatId :chat-id}))

(defn <-requests-to-join-community-rpc [requests]
  (reduce (fn [acc r]
            (assoc acc (:id r) (<-request-to-join-community-rpc r)))
          {}
          requests))

(defn <-chats-rpc [chats]
  (reduce-kv (fn [acc k v]
               (assoc acc
                      (name k)
                      (-> v
                          (assoc :can-post? (:canPost v))
                          (dissoc :canPost)
                          (update :members walk/stringify-keys))))
             {}
             chats))

(defn <-rpc [c]
  (-> c
      (clojure.set/rename-keys {:canRequestAccess :can-request-access?
                                :canManageUsers :can-manage-users?
                                :canJoin :can-join?
                                :requestedToJoinAt :requested-to-join-at
                                :isMember :is-member?})
      (update :members walk/stringify-keys)
      (update :chats <-chats-rpc)))

(defn fetch-community-id-input [{:keys [db]}]
  (:communities/community-id-input db))

(fx/defn handle-request-to-join [{:keys [db]} r]
  (let [{:keys [id community-id] :as request} (<-request-to-join-community-rpc r)]
    {:db (assoc-in db [:communities/requests-to-join community-id id] request)}))

(fx/defn handle-removed-chats [{:keys [db]} chat-ids]
  {:db (reduce (fn [db chat-id]
                 (update db :chats dissoc chat-id))
               db
               chat-ids)})

(fx/defn handle-communities
  {:events [::fetched]}
  [{:keys [db]} communities]
  {:db (reduce (fn [db {:keys [id] :as community}]
                 (assoc-in db [:communities id] (<-rpc community)))
               db
               communities)})

(fx/defn handle-response [_ response-js]
  {:dispatch [:sanitize-messages-and-process-response response-js]})

(fx/defn left
  {:events [::left]}
  [cofx response-js]
  (fx/merge cofx
            (handle-response cofx response-js)
            (navigation/navigate-to-cofx :home {})))

(fx/defn joined
  {:events [::joined ::requested-to-join]}
  [cofx response-js]
  (handle-response cofx response-js))

(fx/defn export
  {:events [::export-pressed]}
  [cofx community-id]
  {::json-rpc/call [{:method     "wakuext_exportCommunity"
                     :params     [community-id]
                     :on-success #(re-frame/dispatch [:show-popover {:view          :export-community
                                                                     :community-key %}])
                     :on-error   #(do
                                    (log/error "failed to export community" community-id %)
                                    (re-frame/dispatch [::failed-to-export %]))}]})

(fx/defn import-community
  {:events [::import]}
  [cofx community-key]
  {::json-rpc/call [{:method     "wakuext_importCommunity"
                     :params     [community-key]
                     :js-response true
                     :on-success #(re-frame/dispatch [::community-imported %])
                     :on-error   #(do
                                    (log/error "failed to import community" %)
                                    (re-frame/dispatch [::failed-to-import %]))}]})

(fx/defn join
  {:events [::join]}
  [cofx community-id]
  {::json-rpc/call [{:method "wakuext_joinCommunity"
                     :params [community-id]
                     :js-response true
                     :on-success #(re-frame/dispatch [::joined %])
                     :on-error #(do
                                  (log/error "failed to join community" community-id %)
                                  (re-frame/dispatch [::failed-to-join %]))}]})

(fx/defn request-to-join
  {:events [::request-to-join]}
  [cofx community-id]
  {::json-rpc/call [{:method "wakuext_requestToJoinCommunity"
                     :params [{:communityId community-id}]
                     :js-response true
                     :on-success #(re-frame/dispatch [::requested-to-join %])
                     :on-error #(do
                                  (log/error "failed to request to join community" community-id %)
                                  (re-frame/dispatch [::failed-to-request-to-join %]))}]})

(fx/defn leave
  {:events [::leave]}
  [cofx community-id]
  {::json-rpc/call [{:method     "wakuext_leaveCommunity"
                     :params     [community-id]
                     :js-response true
                     :on-success #(re-frame/dispatch [::left %])
                     :on-error   #(do
                                    (log/error "failed to leave community" community-id %)
                                    (re-frame/dispatch [::failed-to-leave %]))}]})

(fx/defn fetch [_]
  {::json-rpc/call [{:method "wakuext_communities"
                     :params []
                     :on-success #(re-frame/dispatch [::fetched %])
                     :on-error #(do
                                  (log/error "failed to fetch communities" %)
                                  (re-frame/dispatch [::failed-to-fetch %]))}]})

(fx/defn chat-created
  {:events [::chat-created]}
  [_ community-id user-pk]
  {::json-rpc/call [{:method     "wakuext_sendChatMessage"
                     :params     [{:chatId      user-pk
                                   :text        "Upgrade here to see an invitation to community"
                                   :communityId community-id
                                   :contentType constants/content-type-community}]
                     :js-response true
                     :on-success
                     #(re-frame/dispatch [:transport/message-sent %])
                     :on-failure #(log/error "failed to send a message" %)}]})

(fx/defn invite-users
  {:events [::invite-people-confirmation-pressed]}
  [cofx user-pk contacts]
  (let [community-id (fetch-community-id-input cofx)
        pks (if (seq user-pk)
              (conj contacts user-pk)
              contacts)]
    (when (seq pks)
      {::json-rpc/call [{:method     "wakuext_inviteUsersToCommunity"
                         :params     [{:communityId community-id
                                       :users pks}]
                         :js-response true
                         :on-success #(re-frame/dispatch [::people-invited %])
                         :on-error   #(do
                                        (log/error "failed to invite-user community" %)
                                        (re-frame/dispatch [::failed-to-invite-people %]))}]})))
(fx/defn share-community
  {:events [::share-community-confirmation-pressed]}
  [cofx user-pk contacts]
  (let [community-id (fetch-community-id-input cofx)
        pks (if (seq user-pk)
              (conj contacts user-pk)
              contacts)]
    (when (seq pks)
      {::json-rpc/call [{:method     "wakuext_shareCommunity"
                         :params     [{:communityId community-id
                                       :users pks}]
                         :js-response true
                         :on-success #(re-frame/dispatch [::people-invited %])
                         :on-error   #(do
                                        (log/error "failed to invite-user community" %)
                                        (re-frame/dispatch [::failed-to-share-community %]))}]})))

(fx/defn create
  {:events [::create-confirmation-pressed]}
  [{:keys [db]}]
  (let [{:keys [name description image]} (get db :communities/create)]
    ;; If access is ENS only, we set the access to require approval and set the rule
    ;; of ens only
    (let [params {:name name
                  :description description
                  :membership constants/community-on-request-access
                  :color (rand-nth colors/chat-colors)
                  :image (string/replace-first (str image) #"file://" "")
                  :imageAx 0
                  :imageAy 0
                  :imageBx crop-size
                  :imageBy crop-size}]

      {::json-rpc/call [{:method     "wakuext_createCommunity"
                         :params     [params]
                         :js-response true
                         :on-success #(re-frame/dispatch [::community-created %])
                         :on-error   #(do
                                        (log/error "failed to create community" %)
                                        (re-frame/dispatch [::failed-to-create-community %]))}]})))

(fx/defn edit
  {:events [::edit-confirmation-pressed]}
  [{:keys [db]}]
  (let [{:keys [id name description membership new-image color]} (get db :communities/create)]
    {::json-rpc/call [{:method     "wakuext_editCommunity"
                       :params     [{:communityID id
                                     :name name
                                     :description description
                                     :color color
                                     :image (string/replace-first (str new-image) #"file://" "")
                                     :imageAx 0
                                     :imageAy 0
                                     :imageBx crop-size
                                     :imageBy crop-size
                                     :membership membership}]
                       :on-success #(re-frame/dispatch [::community-edited %])
                       :on-error   #(do
                                      (log/error "failed to edit community" %)
                                      (re-frame/dispatch [::failed-to-edit-community %]))}]}))

(fx/defn create-channel
  {:events [::create-channel-confirmation-pressed]}
  [cofx community-channel-name community-channel-description]
  (let [community-id (fetch-community-id-input cofx)]
    {::json-rpc/call [{:method     "wakuext_createCommunityChat"
                       :params     [community-id
                                    {:identity    {:display_name community-channel-name
                                                   :color        (rand-nth colors/chat-colors)
                                                   :description  community-channel-description}
                                     :permissions {:access constants/community-channel-access-no-membership}}]
                       :js-response true
                       :on-success #(re-frame/dispatch [::community-channel-created %])
                       :on-error   #(do
                                      (log/error "failed to create community channel" %)
                                      (re-frame/dispatch [::failed-to-create-community-channel %]))}]}))

(defn require-membership? [permissions]
  (not= constants/community-no-membership-access (:access permissions)))

(def community-id-length 68)

(defn can-post? [community _ local-chat-id]
  (let [chat-id (subs local-chat-id community-id-length)]
    (get-in community [:chats chat-id :can-post?])))

(fx/defn reset-community-id-input [{:keys [db]} id]
  {:db (assoc db :communities/community-id-input id)})

(fx/defn invite-people-pressed
  {:events [::invite-people-pressed]}
  [cofx id]
  (fx/merge cofx
            (reset-community-id-input id)
            (bottom-sheet/hide-bottom-sheet)
            (navigation/navigate-to :invite-people-community {:invite? true})))

(fx/defn share-community-pressed
  {:events [::share-community-pressed]}
  [cofx id]
  (fx/merge cofx
            (reset-community-id-input id)
            (bottom-sheet/hide-bottom-sheet)
            (navigation/navigate-to :invite-people-community {})))

(fx/defn create-channel-pressed
  {:events [::create-channel-pressed]}
  [cofx id]
  (fx/merge cofx
            (reset-community-id-input id)
            (navigation/navigate-to :create-community-channel nil)))

(fx/defn community-created
  {:events [::community-created]}
  [cofx response-js]
  (fx/merge cofx
            (navigation/navigate-back)
            (handle-response response-js)))

(fx/defn community-edited
  {:events [::community-edited]}
  [cofx response-js]
  (fx/merge cofx
            (navigation/navigate-back)
            (handle-response response-js)))

(fx/defn open-create-community
  {:events [::open-create-community]}
  [{:keys [db] :as cofx}]
  (fx/merge cofx
            {:db (assoc db :communities/create {:membership constants/community-no-membership-access})}
            (navigation/navigate-to :community-create nil)))

(fx/defn open-edit-community
  {:events [::open-edit-community]}
  [{:keys [db] :as cofx} id]
  (let [{:keys [name description images permissions color]} (get-in db [:communities id])
        {:keys [access]}                                   permissions]
    (fx/merge cofx
              {:db (assoc db :communities/create {:id          id
                                                  :name        name
                                                  :description description
                                                  :image       (get-in images [:large :uri])
                                                  :membership  access
                                                  :color       color
                                                  :editing?    true})}
              (navigation/navigate-to :communities {:screen :community-edit}))))

(fx/defn community-imported
  {:events [::community-imported]}
  [cofx response-js]
  (fx/merge cofx
            (navigation/navigate-back)
            (handle-response response-js)))

(fx/defn people-invited
  {:events [::people-invited]}
  [cofx response-js]
  (fx/merge cofx
            (navigation/navigate-back)
            (handle-response response-js)))

(fx/defn community-channel-created
  {:events [::community-channel-created]}
  [cofx response-js]
  (fx/merge cofx
            (navigation/navigate-back)
            (handle-response response-js)))

(fx/defn create-field
  {:events [::create-field]}
  [{:keys [db]} field value]
  {:db (assoc-in db [:communities/create field] value)})

(fx/defn remove-field
  {:events [::remove-field]}
  [{:keys [db]} field]
  {:db (update-in db [:communities/create] dissoc field)})

(fx/defn member-banned
  {:events [::member-banned]}
  [cofx response-js]
  (fx/merge cofx
            (bottom-sheet/hide-bottom-sheet)
            (handle-response response-js)))

(fx/defn member-ban
  {:events [::member-ban]}
  [cofx community-id public-key]
  {::json-rpc/call [{:method     "wakuext_banUserFromCommunity"
                     :params     [{:communityId community-id
                                   :user public-key}]
                     :js-response true
                     :on-success #(re-frame/dispatch [::member-banned %])
                     :on-error   #(log/error "failed to ban user from community" community-id public-key %)}]})

(fx/defn member-kicked
  {:events [::member-kicked]}
  [cofx response-js]
  (fx/merge cofx
            (bottom-sheet/hide-bottom-sheet)
            (handle-response response-js)))

(fx/defn member-kick
  {:events [::member-kick]}
  [cofx community-id public-key]
  {::json-rpc/call [{:method     "wakuext_removeUserFromCommunity"
                     :params     [community-id public-key]
                     :js-response true
                     :on-success #(re-frame/dispatch [::member-kicked %])
                     :on-error   #(log/error "failed to remove user from community" community-id public-key %)}]})

(fx/defn delete-community
  {:events [::delete-community]}
  [cofx community-id]
  (log/error "Community delete is not yet implemented"))

(fx/defn requests-to-join-fetched
  {:events [::requests-to-join-fetched]}
  [{:keys [db]} community-id requests]
  {:db (assoc-in db [:communities/requests-to-join community-id] (<-requests-to-join-community-rpc requests))})

(fx/defn fetch-requests-to-join
  {:events [::fetch-requests-to-join]}
  [cofx community-id]
  {::json-rpc/call [{:method     "wakuext_pendingRequestsToJoinForCommunity"
                     :params     [community-id]
                     :on-success #(re-frame/dispatch [::requests-to-join-fetched community-id %])
                     :on-error   #(log/error "failed to fetch requests-to-join" community-id %)}]})

(defn fetch-requests-to-join! [community-id]
  (re-frame/dispatch [::fetch-requests-to-join community-id]))

(fx/defn request-to-join-accepted
  {:events [::request-to-join-accepted]}
  [{:keys [db] :as cofx} community-id request-id response-js]
  (fx/merge cofx
            {:db (update-in db [:communities/requests-to-join community-id] dissoc request-id)}
            (handle-response response-js)))

(fx/defn request-to-join-declined
  {:events [::request-to-join-declined]}
  [{:keys [db] :as cofx} community-id request-id response-js]
  (fx/merge cofx
            {:db (update-in db [:communities/requests-to-join community-id] dissoc request-id)}
            (handle-response response-js)))

(fx/defn accept-request-to-join-pressed
  {:events [:communities.ui/accept-request-to-join-pressed]}
  [cofx community-id request-id]
  {::json-rpc/call [{:method     "wakuext_acceptRequestToJoinCommunity"
                     :params     [{:id request-id}]
                     :js-response true
                     :on-success #(re-frame/dispatch [::request-to-join-accepted community-id request-id %])
                     :on-error   #(log/error "failed to accept requests-to-join" community-id request-id %)}]})

(fx/defn decline-request-to-join-pressed
  {:events [:communities.ui/decline-request-to-join-pressed]}
  [cofx community-id request-id]
  {::json-rpc/call [{:method     "wakuext_declineRequestToJoinCommunity"
                     :params     [{:id request-id}]
                     :js-response true
                     :on-success #(re-frame/dispatch [::request-to-join-declined community-id request-id %])
                     :on-error   #(log/error "failed to decline requests-to-join" community-id request-id)}]})

(fx/defn switch-communities-enabled
  {:events [:multiaccounts.ui/switch-communities-enabled]}
  [{:keys [db]} enabled?]
  {::async-storage/set! {:communities-enabled? enabled?}
   :db (assoc db :communities/enabled? enabled?)})

(ns status-im.ethereum.json-rpc
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame]
            [status-im.ethereum.abi-spec :as abi-spec]
            [status-im.ethereum.decode :as decode]
            [status-im.native-module.core :as status]
            [status-im.utils.money :as money]
            [status-im.utils.types :as types]
            [status-im.utils.utils :as utils]
            [taoensso.timbre :as log]))

(def json-rpc-api
  {"eth_call" {}
   "eth_getBalance"
   {:on-result money/bignumber}
   "eth_estimateGas"
   {:on-result #(money/bignumber (if (= (int %) 21000) % (int (* % 1.2))))}
   "eth_gasPrice"
   {:on-result money/bignumber}
   "eth_getBlockByHash"
   {:on-result #(-> (update % :number decode/uint)
                    (update :timestamp decode/uint))}
   "eth_getTransactionByHash" {}
   "eth_getTransactionReceipt" {}
   "eth_getBlockByNumber" {}
   "eth_maxPriorityFeePerGas" {}
   "eth_newBlockFilter" {:subscription? true}
   "eth_newFilter" {:subscription? true}
   "eth_getCode" {}
   "eth_syncing" {}
   "net_version" {}
   "web3_clientVersion" {}
   "shh_generateSymKeyFromPassword" {}
   "shh_getSymKey" {}
   "shh_markTrustedPeer" {}
   "waku_generateSymKeyFromPassword" {}
   "waku_getSymKey" {}
   "waku_markTrustedPeer" {}
   "wakuext_post" {}
   "wakuext_requestAllHistoricMessages" {}
   "wakuext_editMessage" {}
   "wakuext_fillGaps" {}
   "wakuext_syncChatFromSyncedFrom" {}
   "wakuext_createPublicChat" {}
   "wakuext_createOneToOneChat" {}
   "wakuext_createProfileChat" {}
   "wakuext_startMessenger" {}
   "wakuext_sendPairInstallation" {}
   "wakuext_syncDevices" {}
   "wakuext_requestMessages" {}
   "wakuext_sendDirectMessage" {}
   "wakuext_sendPublicMessage" {}
   "wakuext_enableInstallation" {}
   "wakuext_disableInstallation" {}
   "wakuext_sendChatMessage" {}
   "wakuext_sendChatMessages" {}
   "wakuext_confirmJoiningGroup" {}
   "wakuext_addAdminsToGroupChat" {}
   "wakuext_addMembersToGroupChat" {}
   "wakuext_removeMemberFromGroupChat" {}
   "wakuext_leaveGroupChat" {}
   "wakuext_changeGroupChatName" {}
   "wakuext_createGroupChatWithMembers" {}
   "wakuext_createGroupChatFromInvitation" {}
   "wakuext_reSendChatMessage" {}
   "wakuext_getOurInstallations" {}
   "wakuext_setInstallationMetadata" {}
   "wakuext_loadFilters" {}
   "wakuext_loadFilter" {}
   "wakuext_removeFilters" {}
   "wakuext_sendContactUpdate" {}
   "wakuext_sendContactUpdates" {}
   "wakuext_chats" {}
   "wakuext_activeChats" {}
   "wakuext_addSystemMessages" {}
   "wakuext_deleteMessagesFrom" {}
   "wakuext_deleteMessagesByChatID" {}
   "wakuext_deleteMessage" {}
   "wakuext_markMessagesSeen" {}
   "wakuext_markAllRead" {}
   "wakuext_confirmMessagesProcessedByID" {}
   "wakuext_updateMessageOutgoingStatus" {}
   "wakuext_chatMessages" {}
   "wakuext_saveChat" {}
   "wakuext_muteChat" {}
   "wakuext_unmuteChat" {}
   "wakuext_contacts" {}
   "wakuext_removeContact" {}
   "wakuext_clearHistory" {}
   "wakuext_prepareContent" {}
   "wakuext_blockContact" {}
   "wakuext_updateMailservers" {}
   "wakuext_sendEmojiReaction" {}
   "wakuext_sendEmojiReactionRetraction" {}
   "wakuext_emojiReactionsByChatID" {}
   "wakuext_getLinkPreviewWhitelist" {}
   "wakuext_getLinkPreviewData" {}
   "wakuext_requestCommunityInfoFromMailserver" {}
   "wakuext_deactivateChat" {}
   "wakuext_sendPinMessage" {}
   "wakuext_chatPinnedMessages" {}
   ;;TODO not used anywhere?
   "wakuext_deleteChat" {}
   "wakuext_saveContact" {}
   "wakuext_verifyENSNames" {}
   "wakuext_requestAddressForTransaction" {}
   "wakuext_requestTransaction" {}
   "wakuext_acceptRequestAddressForTransaction" {}
   "wakuext_declineRequestAddressForTransaction" {}
   "wakuext_declineRequestTransaction" {}
   "wakuext_sendTransaction" {}
   "wakuext_acceptRequestTransaction" {}
   "wakuext_signMessageWithChatKey" {}
   "wakuext_sendGroupChatInvitationRequest" {}
   "wakuext_sendGroupChatInvitationRejection" {}
   "wakuext_getGroupChatInvitations" {}
   "wakuext_registerForPushNotifications" {}
   "wakuext_unregisterFromPushNotifications" {}
   "wakuext_enablePushNotificationsFromContactsOnly" {}
   "wakuext_disablePushNotificationsFromContactsOnly" {}
   "wakuext_startPushNotificationsServer" {}
   "wakuext_stopPushNotificationsServer" {}
   "wakuext_disableSendingNotifications" {}
   "wakuext_enableSendingNotifications" {}
   "wakuext_addPushNotificationsServer" {}
   "wakuext_getPushNotificationsServers" {}
   "wakuext_enablePushNotificationsBlockMentions" {}
   "wakuext_disablePushNotificationsBlockMentions" {}
   "wakuext_unreadActivityCenterNotificationsCount" {}
   "multiaccounts_getIdentityImages" {}
   "multiaccounts_getIdentityImage" {}
   "multiaccounts_storeIdentityImage" {}
   "multiaccounts_deleteIdentityImage" {}
   "wakuext_createCommunity" {}
   "wakuext_editCommunity" {}
   "wakuext_createCommunityChat" {}
   "wakuext_editCommunityChat" {}
   "wakuext_inviteUsersToCommunity" {}
   "wakuext_shareCommunity" {}
   "wakuext_removeUserFromCommunity" {}
   "wakuext_banUserFromCommunity" {}
   "wakuext_requestToJoinCommunity" {}
   "wakuext_acceptRequestToJoinCommunity" {}
   "wakuext_declineRequestToJoinCommunity" {}
   "wakuext_pendingRequestsToJoinForCommunity" {}
   "wakuext_joinCommunity" {}
   "wakuext_leaveCommunity" {}
   "wakuext_communities" {}
   "wakuext_importCommunity" {}
   "wakuext_exportCommunity" {}
   "wakuext_ensVerified" {}
   "wakuext_dismissActivityCenterNotifications" {}
   "wakuext_acceptActivityCenterNotifications" {}
   "wakuext_dismissAllActivityCenterNotifications" {}
   "wakuext_acceptAllActivityCenterNotifications" {}
   "wakuext_markAllActivityCenterNotificationsRead" {}
   "wakuext_activityCenterNotifications" {}
   "status_chats" {}
   "rpcstats_getStats" {}
   "rpcstats_reset" {}
   "localnotifications_switchWalletNotifications" {}
   "localnotifications_notificationPreferences" {}
   "wallet_setInitialBlocksRange" {}
   "wallet_getTransfersByAddress" {}
   "wallet_watchTransaction" {}
   "wallet_checkRecentHistory" {}
   "wallet_getCachedBalances" {}
   "wallet_storePendingTransaction" {}
   "wallet_deletePendingTransaction" {}
   "wallet_getPendingTransactions" {}
   "wallet_getTokensBalances" {}
   "wallet_getCustomTokens" {}
   "wallet_addCustomToken" {}
   "wallet_addFavourite" {}
   "wallet_getFavourites" {}
   "wallet_deleteCustomToken" {}
   "wallet_getCryptoOnRamps" {}
   "browsers_getBrowsers" {}
   "browsers_addBrowser" {}
   "browsers_deleteBrowser" {}
   "browsers_getBookmarks" {}
   "browsers_storeBookmark" {}
   "browsers_updateBookmark" {}
   "browsers_deleteBookmark" {}
   "mailservers_getMailserverRequestGaps" {}
   "mailservers_addMailserverRequestGaps" {}
   "mailservers_deleteMailserverRequestGaps" {}
   "mailservers_deleteMailserverRequestGapsByChatID" {}
   "permissions_addDappPermissions" {}
   "permissions_getDappPermissions" {}
   "permissions_deleteDappPermissions" {}
   "settings_saveSetting" {}
   "settings_getSettings" {}
   "accounts_getAccounts" {}
   "accounts_saveAccounts" {}
   "accounts_deleteAccount" {}
   "mailservers_ping" {}
   "mailservers_addMailserver" {}
   "mailservers_getMailservers" {}
   "mailservers_deleteMailserver" {}
   "mailservers_addMailserverTopic" {}
   "mailservers_addMailserverTopics" {}
   "mailservers_getMailserverTopics" {}
   "mailservers_deleteMailserverTopic" {}
   "mailservers_addChatRequestRange" {}
   "mailservers_addChatRequestRanges" {}
   "mailservers_getChatRequestRanges" {}
   "mailservers_deleteChatRequestRange" {}
   "appmetrics_saveAppMetrics" {}
   "appmetrics_getAppMetrics" {}})

(defn on-error-retry
  [call-method {:keys [method number-of-retries delay on-error] :as arg}]
  (if (pos? number-of-retries)
    (fn [error]
      (let [updated-delay (if delay
                            (min 2000 (* 2 delay))
                            50)]
        (log/debug "[on-error-retry]" method
                   "number-of-retries" number-of-retries
                   "delay" delay
                   "error" error)
        (utils/set-timeout #(call-method (-> arg
                                             (update :number-of-retries dec)
                                             (assoc :delay updated-delay)))
                           updated-delay)))
    on-error))

(defn call-ext-method [method]
  (str "wakuext_" method))

(defn call
  [{:keys [method params on-success on-error js-response] :as arg}]
  (if-let [method-options (json-rpc-api method)]
    (let [params (or params [])
          {:keys [id on-result subscription?]
           :or {on-result identity
                id 1}} method-options
          on-error (or
                    on-error
                    (on-error-retry call arg)
                    #(log/warn :json-rpc/error method :error % :params params))]
      (if (nil? method)
        (do
          (log/error :json-rpc/method-not-found method)
          (on-error :json-rpc/method-not-found))
        (status/call-private-rpc
         (types/clj->json {:jsonrpc "2.0"
                           :id      id
                           :method  (if subscription?
                                      "eth_subscribeSignal"
                                      method)
                           :params  (if subscription?
                                      [method params]
                                      params)})
         (fn [response]
           (if (string/blank? response)
             (on-error {:message "Blank response"})
             (let [response-js (types/json->js response)]
               (if (.-error response-js)
                 (on-error (types/js->clj (.-error response-js)))
                 (if subscription?
                   (re-frame/dispatch
                    [:ethereum.callback/subscription-success
                     (types/js->clj (.-result response-js)) on-success])
                   (on-success (on-result (if js-response
                                            (.-result response-js)
                                            (types/js->clj (.-result response-js)))))))))))))
    (log/warn "method" method "not found" arg)))

(defn eth-call
  [{:keys [contract method params outputs on-success block]
    :or {block "latest"
         params []}
    :as arg}]
  (call {:method "eth_call"
         :params [{:to contract
                   :data (abi-spec/encode method params)}
                  (if (int? block)
                    (abi-spec/number-to-hex block)
                    block)]
         :on-success
         (if outputs
           #(on-success (abi-spec/decode % outputs))
           on-success)
         :on-error
         (on-error-retry eth-call arg)}))

;; effects
(re-frame/reg-fx
 ::call
 (fn [params]
   (doseq [param params]
     (call param))))

(re-frame/reg-fx
 ::eth-call
 (fn [params]
   (doseq [param params]
     (eth-call param))))

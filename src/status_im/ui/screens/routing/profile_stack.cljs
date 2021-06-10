(ns status-im.ui.screens.routing.profile-stack
  (:require [status-im.ui.screens.profile.user.views :as profile.user]
            [status-im.ui.screens.ens.views :as ens]
            [status-im.ui.screens.contacts-list.views :as contacts-list]
            [status-im.ui.screens.bootnodes-settings.edit-bootnode.views
             :as
             edit-bootnode]
            [status-im.ui.screens.bootnodes-settings.views :as bootnodes-settings]
            [status-im.ui.screens.pairing.views :as pairing]
            [status-im.ui.screens.offline-messaging-settings.edit-mailserver.views
             :as
             edit-mailserver]
            [status-im.ui.screens.offline-messaging-settings.views
             :as
             offline-messaging-settings]
            [status-im.ui.screens.dapps-permissions.views :as dapps-permissions]
            [status-im.ui.screens.link-previews-settings.views :as link-previews-settings]
            [status-im.ui.screens.anonymous-metrics-settings.views :as anonymous-metrics-settings]
            [status-im.ui.screens.privacy-and-security-settings.views :as privacy-and-security]
            [status-im.ui.screens.privacy-and-security-settings.messages-from-contacts-only :as messages-from-contacts-only]
            [status-im.ui.screens.sync-settings.views :as sync-settings]
            [status-im.ui.screens.advanced-settings.views :as advanced-settings]
            [status-im.ui.screens.help-center.views :as help-center]
            [status-im.ui.screens.glossary.view :as glossary]
            [status-im.ui.screens.about-app.views :as about-app]
            [status-im.ui.screens.mobile-network-settings.view
             :as
             mobile-network-settings]
            [status-im.ui.screens.default-sync-period-settings.view
             :as
             default-sync-period-settings]
            [status-im.ui.screens.network.edit-network.views :as edit-network]
            [status-im.ui.screens.network.views :as network]
            [status-im.ui.screens.network.network-details.views :as network-details]
            [status-im.ui.screens.network-info.views :as network-info]
            [status-im.ui.screens.rpc-usage-info :as rpc-usage-info]
            [status-im.ui.screens.log-level-settings.views :as log-level-settings]
            [status-im.ui.screens.fleet-settings.views :as fleet-settings]
            [status-im.ui.screens.profile.seed.views :as profile.seed]
            [status-im.ui.screens.keycard.pin.views :as keycard.pin]
            [status-im.ui.screens.keycard.pairing.views :as keycard.pairing]
            [status-im.ui.screens.keycard.settings.views :as keycard.settings]
            [status-im.ui.components.tabbar.styles :as tabbar.styles]
            [status-im.ui.screens.routing.core :as navigation]
            [status-im.ui.screens.appearance.views :as appearance]
            [status-im.ui.screens.notifications-settings.views :as notifications-settings]
            [status-im.ui.screens.privacy-and-security-settings.delete-profile :as delete-profile]))

(defonce stack (navigation/create-stack))

(defn profile-stack []
  [stack {:initial-route-name :my-profile
          :header-mode        :none}
   [{:name      :my-profile
     :insets    {:top false}
     :style     {:padding-bottom tabbar.styles/tabs-diff}
     :component profile.user/my-profile}
    {:name      :contacts-list
     :component contacts-list/contacts-list}
    {:name      :ens-main
     :component ens/main}
    {:name      :ens-search
     :component ens/search}
    {:name      :ens-checkout
     :component ens/checkout}
    {:name      :ens-confirmation
     :component ens/confirmation}
    {:name      :ens-terms
     :component ens/terms}
    {:name      :ens-name-details
     :component ens/name-details}
    {:name      :blocked-users-list
     :component contacts-list/blocked-users-list}
    {:name      :bootnodes-settings
     :component bootnodes-settings/bootnodes-settings}
    {:name      :installations
     :component pairing/installations}
    {:name      :edit-bootnode
     :component edit-bootnode/edit-bootnode}
    {:name      :offline-messaging-settings
     :component offline-messaging-settings/offline-messaging-settings}
    {:name      :edit-mailserver
     :component edit-mailserver/edit-mailserver}
    {:name      :dapps-permissions
     :component dapps-permissions/dapps-permissions}
    {:name      :link-previews-settings
     :component link-previews-settings/link-previews-settings}
    {:name      :anonymous-metrics-settings
     :component anonymous-metrics-settings/settings}
    {:name      :privacy-and-security
     :component privacy-and-security/privacy-and-security}
    {:name      :messages-from-contacts-only
     :component messages-from-contacts-only/messages-from-contacts-only}
    {:name      :privacy-and-security-profile-pic-show-to
     :component privacy-and-security/profile-pic-show-to}
    {:name      :appearance
     :component appearance/appearance}
    {:name      :appearance-profile-pic
     :component appearance/profile-pic}
    {:name      :notifications
     :component notifications-settings/notifications-settings}
    {:name      :notifications-servers
     :component notifications-settings/notifications-servers}
    {:name      :sync-settings
     :component sync-settings/sync-settings}
    {:name      :advanced-settings
     :component advanced-settings/advanced-settings}
    {:name      :help-center
     :component help-center/help-center}
    {:name      :glossary
     :component glossary/glossary}
    {:name      :about-app
     :component about-app/about-app}
    {:name      :manage-dapps-permissions
     :component dapps-permissions/manage}
    {:name      :network-settings
     :component network/network-settings}
    {:name      :network-details
     :component network-details/network-details}
    {:name      :network-info
     :component network-info/network-info}
    {:name      :rpc-usage-info
     :component rpc-usage-info/usage-info}
    {:name      :edit-network
     :component edit-network/edit-network}
    {:name      :log-level-settings
     :component log-level-settings/log-level-settings}
    {:name      :fleet-settings
     :component fleet-settings/fleet-settings}
    {:name      :mobile-network-settings
     :component mobile-network-settings/mobile-network-settings}
    {:name      :default-sync-period-settings
     :component default-sync-period-settings/default-sync-period-settings}
    {:name      :backup-seed
     :component profile.seed/backup-seed}
    {:name       :delete-profile
     :transition :presentation-ios
     :insets     {:bottom true}
     :component  delete-profile/delete-profile}
    ;; {:name:my-profile-ext-settings
    ;;          :component}

    ;; KEYCARD
    {:name      :keycard-settings
     :component keycard.settings/keycard-settings}
    {:name      :reset-card
     :component keycard.settings/reset-card}
    {:name      :keycard-pin
     :component keycard.settings/reset-pin}
    {:name      :enter-pin-settings
     :component keycard.pin/enter-pin}
    {:name      :change-pairing-code
     :component keycard.pairing/change-pairing-code}]])

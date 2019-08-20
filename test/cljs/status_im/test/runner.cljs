(ns status-im.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [status-im.test.browser.core]
            [status-im.test.browser.permissions]
            [status-im.test.chat.commands.core]
            [status-im.test.chat.commands.impl.transactions]
            [status-im.test.chat.commands.input]
            [status-im.test.chat.db]
            [status-im.test.chat.models.input]
            [status-im.test.chat.models.loading]
            [status-im.test.chat.models.message-content]
            [status-im.test.chat.models.message]
            [status-im.test.chat.models]
            [status-im.test.chat.views.photos]
            [status-im.test.transport.filters.core]
            [status-im.test.contact-recovery.core]
            [status-im.test.contacts.device-info]
            [status-im.test.data-store.chats]
            [status-im.test.ethereum.abi-spec]
            [status-im.test.ethereum.core]
            [status-im.test.ethereum.eip55]
            [status-im.test.ethereum.eip681]
            [status-im.test.ethereum.ens]
            [status-im.test.ethereum.mnemonic]
            [status-im.test.extensions.core]
            [status-im.test.ethereum.stateofus]
            [status-im.test.extensions.ethereum]
            [status-im.test.fleet.core]
            [status-im.test.group-chats.core]
            [status-im.test.hardwallet.core]
            [status-im.test.i18n]
            [status-im.test.init.core]
            [status-im.test.mailserver.core]
            [status-im.test.mailserver.topics]
            [status-im.test.models.bootnode]
            [status-im.test.models.contact]
            [status-im.test.models.network]
            [status-im.test.multiaccounts.model]
            [status-im.test.multiaccounts.recover.core]
            [status-im.test.multiaccounts.update.core]
            [status-im.test.node.core]
            [status-im.test.pairing.core]
            [status-im.test.search.core]
            [status-im.test.sign-in.flow]
            [status-im.test.stickers.core]
            [status-im.test.transport.core]
            [status-im.test.transport.utils]
            [status-im.test.tribute-to-talk.core]
            [status-im.test.tribute-to-talk.db]
            [status-im.test.tribute-to-talk.whitelist]
            [status-im.test.ui.screens.add-new.models]
            [status-im.test.ui.screens.currency-settings.models]
            [status-im.test.ui.screens.wallet.db]
            [status-im.test.utils.async]
            [status-im.test.utils.clocks]
            [status-im.test.utils.contenthash]
            [status-im.test.utils.datetime]
            [status-im.test.utils.fx]
            [status-im.test.utils.gfycat.core]
            [status-im.test.utils.http]
            [status-im.test.utils.keychain.core]
            [status-im.test.utils.money]
            [status-im.test.utils.prices]
            [status-im.test.utils.random]
            [status-im.test.utils.signing-phrase.core]
            [status-im.test.utils.transducers]
            [status-im.test.utils.universal-links.core]
            [status-im.test.utils.utils]
            [status-im.test.wallet.subs]
            [status-im.test.wallet.transactions.subs]
            [status-im.test.wallet.transactions]
            [status-im.test.contacts.db]
            [status-im.test.signing.core]
            [status-im.test.signing.gas]))

(enable-console-print!)

;; Or doo will exit with an error, see:
;; https://github.com/bensu/doo/issues/83#issuecomment-165498172
(set! (.-error js/console) (fn [x] (.log js/console x)))

(set! goog.DEBUG false)

#_(doo-tests
   'status-im.test.browser.core
   'status-im.test.browser.permissions
   'status-im.test.chat.commands.core
   'status-im.test.chat.commands.impl.transactions
   'status-im.test.chat.commands.input
   'status-im.test.chat.db
   'status-im.test.chat.models
   'status-im.test.chat.models.input
   'status-im.test.chat.models.loading
   'status-im.test.chat.models.message
   'status-im.test.chat.models.message-content
   'status-im.test.chat.views.photos
   'status-im.test.transport.filters.core
   'status-im.test.contact-recovery.core
   'status-im.test.contacts.db
   'status-im.test.contacts.device-info
   'status-im.test.data-store.chats
   'status-im.test.data-store.messages
   'status-im.test.data-store.contacts
   'status-im.test.ethereum.abi-spec
   'status-im.test.ethereum.core
   'status-im.test.ethereum.eip55
   'status-im.test.ethereum.eip681
   'status-im.test.ethereum.ens
   'status-im.test.ethereum.mnemonic
   'status-im.test.ethereum.stateofus
   'status-im.test.extensions.core
   'status-im.test.extensions.ethereum
   'status-im.test.fleet.core
   'status-im.test.group-chats.core
   'status-im.test.hardwallet.core
   'status-im.test.i18n
   'status-im.test.init.core
   'status-im.test.mailserver.core
   'status-im.test.mailserver.topics
   'status-im.test.models.bootnode
   'status-im.test.models.contact
   'status-im.test.models.network
   'status-im.test.multiaccounts.model
   'status-im.test.multiaccounts.recover.core
   'status-im.test.node.core
   'status-im.test.pairing.core
   'status-im.test.search.core
   'status-im.test.sign-in.flow
   'status-im.test.stickers.core
   'status-im.test.signing.core
   'status-im.test.signing.gas
   'status-im.test.transport.core
   'status-im.test.transport.utils
   'status-im.test.tribute-to-talk.core
   'status-im.test.tribute-to-talk.db
   'status-im.test.tribute-to-talk.whitelist
   'status-im.test.ui.screens.add-new.models
   'status-im.test.ui.screens.currency-settings.models
   'status-im.test.ui.screens.wallet.db
   'status-im.test.utils.async
   'status-im.test.utils.clocks
   'status-im.test.utils.contenthash
   'status-im.test.utils.datetime
   'status-im.test.utils.fx
   'status-im.test.utils.gfycat.core
   'status-im.test.utils.http
   'status-im.test.utils.keychain.core
   'status-im.test.utils.money
   'status-im.test.utils.prices
   'status-im.test.utils.random
   'status-im.test.utils.signing-phrase.core
   'status-im.test.utils.transducers
   'status-im.test.utils.universal-links.core
   'status-im.test.utils.utils
   'status-im.test.wallet.subs
   'status-im.test.wallet.transactions
   'status-im.test.wallet.transactions.subs)

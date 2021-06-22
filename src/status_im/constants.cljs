(ns status-im.constants
  (:require [status-im.react-native.resources :as resources]))

(def ^:const ms-in-bg-for-require-bioauth 5000)

(def ^:const content-type-text 1)
(def ^:const content-type-sticker 2)
(def ^:const content-type-status 3)
(def ^:const content-type-emoji 4)
(def ^:const content-type-command 5)
(def ^:const content-type-system-text 6)
(def ^:const content-type-image 7)
(def ^:const content-type-audio 8)
(def ^:const content-type-community 9)
(def ^:const content-type-gap 10)

(def ^:const emoji-reaction-love 1)
(def ^:const emoji-reaction-thumbs-up  2)
(def ^:const emoji-reaction-thumbs-down  3)
(def ^:const emoji-reaction-laugh 4)
(def ^:const emoji-reaction-sad 5)
(def ^:const emoji-reaction-angry 6)

(def ^:const one-to-one-chat-type 1)
(def ^:const public-chat-type 2)
(def ^:const private-group-chat-type 3)
(def ^:const profile-chat-type 4)
(def ^:const timeline-chat-type 5)
(def ^:const community-chat-type 6)

(def request-to-join-pending-state 1)

(def reactions {emoji-reaction-love        (:love resources/reactions)
                emoji-reaction-thumbs-up   (:thumbs-up resources/reactions)
                emoji-reaction-thumbs-down (:thumbs-down resources/reactions)
                emoji-reaction-laugh       (:laugh resources/reactions)
                emoji-reaction-sad         (:sad resources/reactions)
                emoji-reaction-angry       (:angry resources/reactions)})

(def ^:const invitation-state-unknown 0)
(def ^:const invitation-state-requested 1)
(def ^:const invitation-state-rejected  2)
(def ^:const invitation-state-approved  3)
(def ^:const invitation-state-granted 4)
(def ^:const invitation-state-removed 5)

(def ^:const message-type-one-to-one 1)
(def ^:const message-type-public-group 2)
(def ^:const message-type-private-group 3)
(def ^:const message-type-private-group-system-message 4)
(def ^:const message-type-community-chat 5)
(def ^:const message-type-gap 6)

(def ^:const command-state-request-address-for-transaction 1)
(def ^:const command-state-request-address-for-transaction-declined 2)
(def ^:const command-state-request-address-for-transaction-accepted 3)
(def ^:const command-state-request-transaction 4)
(def ^:const command-state-request-transaction-declined 5)
(def ^:const command-state-transaction-pending 6)
(def ^:const command-state-transaction-sent 7)

(def ^:const profile-pictures-visibility-contacts-only 1)
(def ^:const profile-pictures-visibility-everyone 2)
(def ^:const profile-pictures-visibility-none 3)

(def ^:const min-password-length 6)
(def ^:const max-group-chat-participants 20)
(def ^:const default-number-of-messages 20)
(def ^:const default-number-of-pin-messages 3)

(def ^:const mailserver-password "status-offline-inbox")

(def ^:const send-transaction-failed-parse-response 1)
(def ^:const send-transaction-failed-parse-params   2)
(def ^:const send-transaction-no-account-selected   3)
(def ^:const send-transaction-invalid-tx-sender     4)
(def ^:const send-transaction-err-decrypt           5)

(def ^:const web3-send-transaction "eth_sendTransaction")
(def ^:const web3-personal-sign "personal_sign")
(def ^:const web3-eth-sign "eth_sign")
(def ^:const web3-sign-typed-data "eth_signTypedData")
(def ^:const web3-sign-typed-data-v3 "eth_signTypedData_v3")
(def ^:const web3-sign-typed-data-v4 "eth_signTypedData_v4")

(def ^:const web3-keycard-sign-typed-data "keycard_signTypedData")

(def ^:const status-create-address "status_createaddress")

(def ^:const community-no-membership-access 1)
(def ^:const community-invitation-only-access 2)
(def ^:const community-on-request-access 3)

;; Community rules for joining
(def ^:const community-rule-ens-only "ens-only")

(def ^:const community-channel-access-no-membership 1)
(def ^:const community-channel-access-invitation-only 2)
(def ^:const community-channel-access-on-request 3)

; BIP44 Wallet Root Key, the extended key from which any wallet can be derived
(def ^:const path-wallet-root "m/44'/60'/0'/0")
; EIP1581 Root Key, the extended key from which any whisper key/encryption key can be derived
(def ^:const path-eip1581 "m/43'/60'/1581'")
; BIP44-0 Wallet key, the default wallet key
(def ^:const path-default-wallet (str path-wallet-root "/0"))
; EIP1581 Chat Key 0, the default whisper key
(def ^:const path-whisper (str path-eip1581 "/0'/0"))

(def ^:const path-default-wallet-keyword (keyword path-default-wallet))
(def ^:const path-whisper-keyword (keyword path-whisper))
(def ^:const path-wallet-root-keyword (keyword path-wallet-root))
(def ^:const path-eip1581-keyword (keyword path-eip1581))

(def ^:const method-id-transfer "0xa9059cbb")
(def ^:const method-id-approve "0x095ea7b3")
(def ^:const method-id-approve-and-call "0xcae9ca51")

(def regx-emoji #"^((?:[\u261D\u26F9\u270A-\u270D]|\uD83C[\uDF85\uDFC2-\uDFC4\uDFC7\uDFCA-\uDFCC]|\uD83D[\uDC42\uDC43\uDC46-\uDC50\uDC66-\uDC69\uDC6E\uDC70-\uDC78\uDC7C\uDC81-\uDC83\uDC85-\uDC87\uDCAA\uDD74\uDD75\uDD7A\uDD90\uDD95\uDD96\uDE45-\uDE47\uDE4B-\uDE4F\uDEA3\uDEB4-\uDEB6\uDEC0\uDECC]|\uD83E[\uDD18-\uDD1C\uDD1E\uDD1F\uDD26\uDD30-\uDD39\uDD3D\uDD3E\uDDD1-\uDDDD])(?:\uD83C[\uDFFB-\uDFFF])?|(?:[\u231A\u231B\u23E9-\u23EC\u23F0\u23F3\u25FD\u25FE\u2614\u2615\u2648-\u2653\u267F\u2693\u26A1\u26AA\u26AB\u26BD\u26BE\u26C4\u26C5\u26CE\u26D4\u26EA\u26F2\u26F3\u26F5\u26FA\u26FD\u2705\u270A\u270B\u2728\u274C\u274E\u2753-\u2755\u2757\u2795-\u2797\u27B0\u27BF\u2B1B\u2B1C\u2B50\u2B55]|\uD83C[\uDC04\uDCCF\uDD8E\uDD91-\uDD9A\uDDE6-\uDDFF\uDE01\uDE1A\uDE2F\uDE32-\uDE36\uDE38-\uDE3A\uDE50\uDE51\uDF00-\uDF20\uDF2D-\uDF35\uDF37-\uDF7C\uDF7E-\uDF93\uDFA0-\uDFCA\uDFCF-\uDFD3\uDFE0-\uDFF0\uDFF4\uDFF8-\uDFFF]|\uD83D[\uDC00-\uDC3E\uDC40\uDC42-\uDCFC\uDCFF-\uDD3D\uDD4B-\uDD4E\uDD50-\uDD67\uDD7A\uDD95\uDD96\uDDA4\uDDFB-\uDE4F\uDE80-\uDEC5\uDECC\uDED0-\uDED2\uDEEB\uDEEC\uDEF4-\uDEF8]|\uD83E[\uDD10-\uDD3A\uDD3C-\uDD3E\uDD40-\uDD45\uDD47-\uDD4C\uDD50-\uDD6B\uDD80-\uDD97\uDDC0\uDDD0-\uDDE6])|(?:[#\*0-9\xA9\xAE\u203C\u2049\u2122\u2139\u2194-\u2199\u21A9\u21AA\u231A\u231B\u2328\u23CF\u23E9-\u23F3\u23F8-\u23FA\u24C2\u25AA\u25AB\u25B6\u25C0\u25FB-\u25FE\u2600-\u2604\u260E\u2611\u2614\u2615\u2618\u261D\u2620\u2622\u2623\u2626\u262A\u262E\u262F\u2638-\u263A\u2640\u2642\u2648-\u2653\u2660\u2663\u2665\u2666\u2668\u267B\u267F\u2692-\u2697\u2699\u269B\u269C\u26A0\u26A1\u26AA\u26AB\u26B0\u26B1\u26BD\u26BE\u26C4\u26C5\u26C8\u26CE\u26CF\u26D1\u26D3\u26D4\u26E9\u26EA\u26F0-\u26F5\u26F7-\u26FA\u26FD\u2702\u2705\u2708-\u270D\u270F\u2712\u2714\u2716\u271D\u2721\u2728\u2733\u2734\u2744\u2747\u274C\u274E\u2753-\u2755\u2757\u2763\u2764\u2795-\u2797\u27A1\u27B0\u27BF\u2934\u2935\u2B05-\u2B07\u2B1B\u2B1C\u2B50\u2B55\u3030\u303D\u3297\u3299]|\uD83C[\uDC04\uDCCF\uDD70\uDD71\uDD7E\uDD7F\uDD8E\uDD91-\uDD9A\uDDE6-\uDDFF\uDE01\uDE02\uDE1A\uDE2F\uDE32-\uDE3A\uDE50\uDE51\uDF00-\uDF21\uDF24-\uDF93\uDF96\uDF97\uDF99-\uDF9B\uDF9E-\uDFF0\uDFF3-\uDFF5\uDFF7-\uDFFF]|\uD83D[\uDC00-\uDCFD\uDCFF-\uDD3D\uDD49-\uDD4E\uDD50-\uDD67\uDD6F\uDD70\uDD73-\uDD7A\uDD87\uDD8A-\uDD8D\uDD90\uDD95\uDD96\uDDA4\uDDA5\uDDA8\uDDB1\uDDB2\uDDBC\uDDC2-\uDDC4\uDDD1-\uDDD3\uDDDC-\uDDDE\uDDE1\uDDE3\uDDE8\uDDEF\uDDF3\uDDFA-\uDE4F\uDE80-\uDEC5\uDECB-\uDED2\uDEE0-\uDEE5\uDEE9\uDEEB\uDEEC\uDEF0\uDEF3-\uDEF8]|\uD83E[\uDD10-\uDD3A\uDD3C-\uDD3E\uDD40-\uDD45\uDD47-\uDD4C\uDD50-\uDD6B\uDD80-\uDD97\uDDC0\uDDD0-\uDDE6])\uFE0F|[\t-\r \xA0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF])+$")
(def regx-bold #"\*[^*]+\*")
(def regx-italic #"~[^~]+~")
(def regx-backquote #"`[^`]+`")
(def regx-universal-link #"((^https?://join.status.im/)|(^status-im://))[\x00-\x7F]+$")
(def regx-community-universal-link #"((^https?://join.status.im/)|(^status-im://))c/([\x00-\x7F]+)$")
(def regx-deep-link #"((^ethereum:.*)|(^status-im://[\x00-\x7F]+$))")

(def ^:const dapp-permission-contact-code "contact-code")
(def ^:const dapp-permission-web3 "web3")
(def ^:const dapp-permission-qr-code "qr-code")
(def ^:const api-response "api-response")
(def ^:const api-request "api-request")
(def ^:const history-state-changed "history-state-changed")
(def ^:const web3-send-async-read-only "web3-send-async-read-only")
(def ^:const web3-send-async-callback "web3-send-async-callback")
(def ^:const scan-qr-code "scan-qr-code")

(def ^:const faq "https://status.im/faq/")
(def ^:const faq-keycard (str faq "#keycard"))
(def ^:const keycard-integration-link "https://status.im/keycard-integration")

(def ^:const status-community-id "0x039b2da47552aa117a96ea8f1d4d108ba66637c7517a3c94a57b99dbb8a002eda2")

(def ^:const timeline-chat-id "@timeline70bd746ddcc12beb96b2c9d572d0784ab137ffc774f5383e50585a932080b57cca0484b259e61cecbaa33a4c98a300a")

(def ^:const one-day (* 60 60 24))
(def ^:const three-days (* one-day 3))
(def ^:const one-week (* one-day 7))
(def ^:const one-month (* one-day 31))

(def ^:const metrics-github-link
  "https://github.com/status-im/status-go/blob/develop/_docs/app-metrics.md")
(def ^:const privacy-policy-link "https://status.im/privacy-policy/")
(def ^:const terms-of-service-link "https://status.im/terms-of-use")
(def ^:const docs-link "https://status.im/docs/")
(def ^:const principles-link "https://our.status.im/our-principles/")

(def ^:const activity-center-notification-type-one-to-one-chat 1)
(def ^:const activity-center-notification-type-private-group-chat 2)
(def ^:const activity-center-notification-type-mention 3)
(def ^:const activity-center-notification-type-reply 4)

(ns status-im.react-native.js-dependencies)

(def config                 (js/require "react-native-config"))
(def fs                     (js/require "react-native-fs"))
(def http-bridge            (js/require "react-native-http-bridge"))
(def keychain               (js/require "react-native-keychain"))
(def qr-code                (js/require "react-native-qrcode"))
(def react-native           (js/require "react-native"))
(def realm                  (js/require "realm"))
(def webview-bridge         (js/require "react-native-webview-bridge"))
(def secure-random          (.-generateSecureRandom (js/require "react-native-securerandom")))
(def EventEmmiter           (js/require "react-native/Libraries/vendor/emitter/EventEmitter"))
(def fetch                  (.-default (js/require "react-native-fetch-polyfill")))

;; We don't use platform/desktop? here because that would lead to a circular dependency
;; since platform.cljs itself depends on js-dependencies
(def desktop?
    (= (.-OS (.-Platform react-native)) "desktop"))

;; For desktop build we are creating bundle with all js sources. That bundle includes also
;; packages referenced by require. js_dependencies contains js/require calls so they are
;; bundled despite the fact we don't use them in js. To prevent this we are wrapping
;; js/require function
(def js-require js/require)

(if desktop?
  (do (def i18n                   (js-require "react-native-i18n"))
      (def camera                 #js {:constants {:Aspect "Portrait"}})
      (def dialogs                #js {})
      (def dismiss-keyboard       #js {})
      (def image-crop-picker      #js {})
      (def image-resizer          #js {})
      (def instabug               #js {:IBGLog ( fn [])})
      (def nfc                    #js {})
      (def svg                    #js {})
      (def react-native-fcm       #js {:default #js {:getFCMToken (fn [])
                                                     :requestPermissions (fn [])}})
      (def snoopy                 #js {})
      (def snoopy-filter          #js {})
      (def snoopy-bars            #js {})
      (def snoopy-buffer          #js {})
      (def background-timer       #js {:setTimeout (fn [])})
      (def testfairy #js {}))
  (do (def i18n                   (.-default (js-require "react-native-i18n")))
      (def camera                 (js-require "react-native-camera"))
      (def dialogs                (js-require "react-native-dialogs"))
      (def dismiss-keyboard       (js-require "dismissKeyboard"))
      (def image-crop-picker      (js-require "react-native-image-crop-picker"))
      (def image-resizer          (js-require "react-native-image-resizer"))
      (def instabug               (js-require "instabug-reactnative"))
      (def nfc                    (js-require "nfc-react-native"))
      (def svg                    (js-require "react-native-svg"))
      (def react-native-fcm       (js-require "react-native-fcm"))
      (def snoopy                 (js-require "rn-snoopy"))
      (def snoopy-filter          (js-require "rn-snoopy/stream/filter"))
      (def snoopy-bars            (js-require "rn-snoopy/stream/bars"))
      (def snoopy-buffer          (js-require "rn-snoopy/stream/buffer"))
      (def background-timer       (.-default (js-require "react-native-background-timer")))
      (def testfairy              (js-require "react-native-testfairy"))))

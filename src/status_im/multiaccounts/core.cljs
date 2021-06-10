(ns status-im.multiaccounts.core
  (:require [re-frame.core :as re-frame]
            [status-im.ethereum.stateofus :as stateofus]
            [status-im.multiaccounts.update.core :as multiaccounts.update]
            [status-im.bottom-sheet.core :as bottom-sheet]
            [status-im.anon-metrics.core :as anon-metrics]
            [status-im.native-module.core :as native-module]
            [status-im.ethereum.json-rpc :as json-rpc]
            [status-im.utils.fx :as fx]
            [status-im.utils.gfycat.core :as gfycat]
            [status-im.utils.identicon :as identicon]
            [status-im.utils.theme :as utils.theme]
            [status-im.theme.core :as theme]
            [status-im.utils.utils :as utils]
            [quo.platform :as platform]
            [taoensso.timbre :as log]
            [clojure.string :as string]))

;; validate that the given mnemonic was generated from Status Dictionary
(re-frame/reg-fx
 ::validate-mnemonic
 (fn [[passphrase callback]]
   (native-module/validate-mnemonic passphrase callback)))

(defn contact-names
  "Returns map of all existing names for contact"
  [{:keys [name preferred-name alias public-key ens-verified nickname]}]
  (let [ens-name (or preferred-name
                     name)]
    (cond-> {:nickname         nickname
             :three-words-name (or alias (gfycat/generate-gfy public-key))}
      ;; Preferred name is our own otherwise we make sure it's verified
      (or preferred-name (and ens-verified name))
      (assoc :ens-name (str "@" (or (stateofus/username ens-name) ens-name))))))

(defn contact-two-names
  "Returns vector of two names in next order nickname, ens name, three word name, public key"
  [{:keys [names public-key] :as contact} public-key?]
  (let [{:keys [nickname ens-name three-words-name]} (or names (contact-names contact))
        short-public-key (when public-key? (utils/get-shortened-address public-key))]
    (cond (not (string/blank? nickname))
          [nickname (or ens-name three-words-name short-public-key)]
          (not (string/blank? ens-name))
          [ens-name (or three-words-name short-public-key)]
          (not (string/blank? three-words-name))
          [three-words-name short-public-key]
          :else
          (when public-key?
            [short-public-key short-public-key]))))

(defn contact-with-names
  "Returns contact with :names map "
  [contact]
  (assoc contact :names (contact-names contact)))

(defn displayed-name
  "Use preferred name, name or alias in that order"
  [{:keys [name preferred-name alias public-key ens-verified]}]
  (let [ens-name (or preferred-name
                     name)]
    ;; Preferred name is our own otherwise we make sure it's verified
    (if (or preferred-name (and ens-verified name))
      (let [username (stateofus/username ens-name)]
        (str "@" (or username ens-name)))
      (or alias (gfycat/generate-gfy public-key)))))

(def photo-quality-thumbnail :thumbnail)
(def photo-quality-large :large)

(defn displayed-photo
  "If a photo, a image or an images array is set use it, otherwise fallback on identicon or generate"
  [{:keys [images identicon public-key]}]
  (cond
    (pos? (count images))
    (:uri (or (photo-quality-thumbnail images)
              (photo-quality-large images)
              (first images)))

    (not (string/blank? identicon))
    identicon

    :else
    (identicon/identicon public-key)))

(re-frame/reg-fx
 ::webview-debug-changed
 (fn [value]
   (when platform/android?
     (native-module/toggle-webview-debug value))))

(re-frame/reg-fx
 ::blank-preview-flag-changed
 (fn [flag]
   (native-module/set-blank-preview-flag flag)))

(fx/defn confirm-wallet-set-up
  {:events [:multiaccounts.ui/wallet-set-up-confirmed]}
  [cofx]
  (multiaccounts.update/multiaccount-update cofx
                                            :wallet-set-up-passed? true {}))

(fx/defn confirm-home-tooltip
  {:events [:multiaccounts.ui/hide-home-tooltip]}
  [cofx]
  (multiaccounts.update/multiaccount-update cofx
                                            :hide-home-tooltip? true {}))

(fx/defn switch-webview-debug
  {:events [:multiaccounts.ui/switch-webview-debug]}
  [{:keys [db] :as cofx} value]
  (fx/merge cofx
            {::webview-debug-changed value}
            (multiaccounts.update/multiaccount-update
             :webview-debug (boolean value)
             {})))

(fx/defn switch-preview-privacy-mode
  {:events [:multiaccounts.ui/preview-privacy-mode-switched]}
  [{:keys [db] :as cofx} private?]
  (fx/merge cofx
            {::blank-preview-flag-changed private?}
            (multiaccounts.update/multiaccount-update
             :preview-privacy? (boolean private?)
             {})))

(fx/defn switch-webview-permission-requests?
  {:events [:multiaccounts.ui/webview-permission-requests-switched]}
  [cofx enabled?]
  (multiaccounts.update/multiaccount-update
   cofx
   :webview-allow-permission-requests? (boolean enabled?)
   {}))

(fx/defn switch-default-sync-period
  {:events [:multiaccounts.ui/default-sync-period-switched]}
  [cofx value]
  (multiaccounts.update/multiaccount-update
   cofx
   :default-sync-period value
   {}))

(fx/defn switch-share-anonymous-usage-data
  {:events [:multiaccounts.ui/share-anonymous-usage-data-switched]}
  [cofx opted-in?]
  (fx/merge cofx
            (multiaccounts.update/multiaccount-update
             :anon-metrics/should-send? (boolean opted-in?)
             {})
            (if opted-in?
              (anon-metrics/start-transferring)
              (anon-metrics/stop-transferring))))

(fx/defn switch-preview-privacy-mode-flag
  [{:keys [db]}]
  (let [private? (get-in db [:multiaccount :preview-privacy?])]
    {::blank-preview-flag-changed private?}))

(re-frame/reg-fx
 ::switch-theme
 (fn [theme-id]
   (let [theme (if (or (= 2 theme-id) (and (= 0 theme-id) (utils.theme/is-dark-mode)))
                 :dark
                 :light)]
     (theme/change-theme theme))))

(fx/defn switch-appearance
  {:events [:multiaccounts.ui/appearance-switched]}
  [cofx theme]
  (fx/merge cofx
            {::switch-theme theme}
            (multiaccounts.update/multiaccount-update :appearance theme {})))

(fx/defn switch-profile-picture-show-to
  {:events [:multiaccounts.ui/profile-picture-show-to-switched]}
  [cofx id]
  (multiaccounts.update/multiaccount-update cofx :profile-pictures-show-to id {}))

(fx/defn switch-appearance-profile
  {:events [:multiaccounts.ui/appearance-profile-switched]}
  [cofx id]
  (multiaccounts.update/multiaccount-update cofx :profile-pictures-visibility id {}))

(defn clean-path [path]
  (if path
    (string/replace-first path #"file://" "")
    (log/warn "[native-module] Empty path was provided")))

(fx/defn save-profile-picture
  {:events [::save-profile-picture]}
  [cofx path ax ay bx by]
  (let [key-uid (get-in cofx [:db :multiaccount :key-uid])]
    (fx/merge cofx
              {::json-rpc/call [{:method     "multiaccounts_storeIdentityImage"
                                 :params     [key-uid (clean-path path) ax ay bx by]
                                 ;; NOTE: In case of an error we can show a toast error
                                 :on-success #(re-frame/dispatch [::update-local-picture %])}]}
              (multiaccounts.update/optimistic :images [{:url  path
                                                         :type (name photo-quality-large)}])
              (bottom-sheet/hide-bottom-sheet))))

(fx/defn delete-profile-picture
  {:events [::delete-profile-picture]}
  [cofx name]
  (let [key-uid (get-in cofx [:db :multiaccount :key-uid])]
    (fx/merge cofx
              {::json-rpc/call [{:method     "multiaccounts_deleteIdentityImage"
                                 :params     [key-uid]
                                 ;; NOTE: In case of an error we could fallback to previous image in UI with a toast error
                                 :on-success #(log/info "[multiaccount] Delete profile image" %)}]}
              (multiaccounts.update/optimistic :images nil)
              (bottom-sheet/hide-bottom-sheet))))

(fx/defn get-profile-picture
  [cofx]
  (let [key-uid (get-in cofx [:db :multiaccount :key-uid])]
    {::json-rpc/call [{:method     "multiaccounts_getIdentityImages"
                       :params     [key-uid]
                       :on-success #(re-frame/dispatch [::update-local-picture %])}]}))

(fx/defn store-profile-picture
  {:events [::update-local-picture]}
  [cofx pics]
  (multiaccounts.update/optimistic cofx :images pics))

(comment
  ;; Test seed for Dim Venerated Yaffle, it's not here by mistake, this is just a test account
  (native-module/validate-mnemonic "rocket mixed rebel affair umbrella legal resemble scene virus park deposit cargo" prn))

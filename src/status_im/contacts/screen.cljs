(ns status-im.contacts.screen
  (:require-macros [status-im.utils.views :refer [defview]])
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [status-im.components.react :refer [view
                                                text
                                                image
                                                icon
                                                touchable-highlight
                                                linear-gradient
                                                scroll-view
                                                list-view
                                                list-item] :as react]
            [status-im.components.action-button :refer [action-button
                                                        action-button-item]]
            [status-im.components.status-bar :refer [status-bar]]
            [status-im.components.toolbar.view :refer [toolbar-with-search toolbar]]
            [status-im.components.toolbar.actions :as act]
            [status-im.components.toolbar.styles :as tst]
            [status-im.components.drawer.view :refer [open-drawer]]
            [status-im.components.icons.custom-icons :refer [ion-icon]]
            [status-im.components.context-menu :refer [context-menu]]
            [status-im.contacts.views.contact :refer [contact-view]]
            [status-im.utils.platform :refer [platform-specific ios?]]
            [status-im.i18n :refer [label]]
            [status-im.contacts.styles :as st]
            [status-im.components.toolbar.styles :as tst]
            [status-im.components.styles :refer [color-blue
                                                 create-icon]]))

(def contacts-limit 5)

(defn toolbar-actions []
  (let [new-contact? (get-in platform-specific [:contacts :new-contact-in-toolbar?])
        options (into []
                      (concat
                        (when new-contact? [{:value #(dispatch [:navigate-to :new-contact])
                                             :text (label :t/new-contact)}])
                        [{:value #(dispatch [:set-in [:contacts-ui-props :edit?] true]) :text (label :t/edit)}
                         {:value #(dispatch [:open-contact-group-list]) :text (label :t/new-group)}]))]
    [view {:flex-direction "row"}
     [touchable-highlight
      {:on-press #(dispatch [:navigate-to :group-contacts nil :show-search])}
      [view st/search-btn
       [icon :search_dark]]]
     [view st/more-btn
      [context-menu
       [icon :options_dark]
       options]]]))

(defn toolbar-view []
 [toolbar {:style          tst/toolbar-with-search
           :title          (label :t/contacts)
           :nav-action     (act/hamburger open-drawer)
           :custom-action  (toolbar-actions)}])

(defn toolbar-edit []
  [toolbar {:style          tst/toolbar-with-search ;;TODO: maybe better use new style
            :nav-action     (act/back #(dispatch [:set-in [:contacts-ui-props :edit?] false]))
            :title          (label :t/edit-contacts)}])

(defn options-btn [group]
  (let [options [{:value #(dispatch [:navigate-to :contact-group group]) :text (label :t/edit-group)}]]
    [view st/more-btn
     [context-menu
      [icon :options_gray]
      options]]))

(defn subtitle-view [subtitle contacts-count group extended?]
  [view (get-in platform-specific [:component-styles :contacts :group-header])
   [text {:style      (merge st/contact-group-subtitle
                             (get-in platform-specific [:component-styles :contacts :subtitle]))
          :uppercase? (get-in platform-specific [:contacts :uppercase-subtitles?])
          :font       :medium}
    subtitle]
   [text {:style      (merge st/contact-group-count
                             (get-in platform-specific [:component-styles :contacts :subtitle]))
          :uppercase? (get-in platform-specific [:contacts :uppercase-subtitles?])
          :font       :medium}
    (str contacts-count)]
   (when extended?
     [options-btn group])])

(defn group-top-view []
  [linear-gradient {:style  st/contact-group-header-gradient-bottom
                    :colors st/contact-group-header-gradient-bottom-colors}])

(defn group-bottom-view []
  [linear-gradient {:style  st/contact-group-header-gradient-top
                    :colors st/contact-group-header-gradient-top-colors}])

(defn line-view []
  [view {:style {:background-color "#D7D7D7"
                 :height           1}}])

(defn on-scroll-animation [e show-toolbar-shadow?]
  (let [offset (.. e -nativeEvent -contentOffset -y)]
    (reset! show-toolbar-shadow?
            (> offset 0))))

(defn contact-group-view [contacts contacts-count subtitle group edit? click-handler]
  (let [shadows? (get-in platform-specific [:contacts :group-block-shadows?])]
    [view st/contact-group
     (when subtitle
       [subtitle-view subtitle contacts-count group edit?])
     (when (and subtitle shadows?)
         [group-top-view])
     [view
      (doall
        (map (fn [contact]
               ^{:key contact}
               [contact-view
                {:contact       contact
                 :extended?     edit?
                 :on-click      (when-not edit? click-handler)
                 :extend-options (when group
                                  [{:value #(dispatch [:remove-contact contact])
                                    :text (label :t/delete-contact)}
                                   {:value #(dispatch [:remove-contact-from-group contact group])
                                    :text (label :t/remove-from-group)}])}])
             contacts))]
     (when (< contacts-limit contacts-count)
       [view st/show-all
        [touchable-highlight (when-not edit? {:on-press #(dispatch [:navigate-to :group-contacts group])})
         [view
          [text {:style st/show-all-text
                 :font  :medium}
           (str (- contacts-count contacts-limit) " " (label :t/more))]]]])
     (when shadows?
       [group-bottom-view])]))

(defview contact-group-view-new [{:keys [name] :as group} edit? click-handler]
  [contacts [:all-added-group-contacts group]
   contacts-count [:all-added-group-contacts-count group]]
  [contact-group-view contacts contacts-count name group edit? click-handler])

(defn contacts-action-button []
  [action-button {:button-color color-blue
                  :offset-x     16
                  :offset-y     22
                  :hide-shadow  true
                  :spacing      13}
   [action-button-item
    {:title       (label :t/new-contact)
     :buttonColor :#9b59b6
     :onPress     #(dispatch [:navigate-to :new-contact])}
    [ion-icon {:name  :md-create
               :style create-icon}]]])

(defview contact-list [current-view?]
  [contacts             [:get-added-contacts-with-limit contacts-limit]
   contacts-count       [:added-contacts-count]
   click-handler        [:get :contacts-click-handler]
   edit?                [:get-in [:contacts-ui-props :edit?]]
   groups               [:get :groups]
   show-toolbar-shadow? (r/atom false)]
  [view st/contacts-list-container
   (if edit?
     [toolbar-edit]
     [toolbar-view])
   (when @show-toolbar-shadow?
     [linear-gradient {:style  st/contact-group-header-gradient-bottom
                       :colors st/contact-group-header-gradient-bottom-colors}])
   (if (pos? (+ (count groups) contacts-count))
     [scroll-view {:style    st/contact-groups
                   :onScroll #(on-scroll-animation % show-toolbar-shadow?)}
      (when (pos? contacts-count)
        [contact-group-view
         contacts
         contacts-count
         nil
         nil
         edit?
         click-handler])
      (for [group groups]
        ^{:key group}
        [contact-group-view-new
         group
         edit?
         click-handler])]
     [view st/empty-contact-groups
      [react/icon :group_big st/empty-contacts-icon]
      [text {:style st/empty-contacts-text} (label :t/no-contacts)]])
   (when (and (not edit?) (get-in platform-specific [:contacts :action-button?]))
       [contacts-action-button])])

(ns status-im.ui.screens.communities.create
  (:require [clojure.string :as str]
            [quo.core :as quo]
            [quo.design-system.colors :as colors]
            [quo.react-native :as rn]
            ["react-native-color-picker" :refer [ColorPicker]]
            [reagent.core :as reagent]
            [status-im.communities.core :as communities]
            [status-im.i18n.i18n :as i18n]
            [status-im.ui.components.icons.icons :as icons]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.toolbar :as toolbar]
            [status-im.utils.colors :as utils.colors]
            [status-im.utils.debounce :as debounce]
            [status-im.utils.handlers :refer [<sub >evt]]
            [status-im.utils.image :as utils.image]))

(def color-picker (reagent/adapt-react-class ColorPicker))
(def close-icon-size 40)
(def max-name-length 30)
(def max-description-length 140)

(defn valid? [community-name community-description]
  (and (not (str/blank? community-name))
       (not (str/blank? community-description))
       (<= (count community-name) max-name-length)
       (<= (count community-description) max-description-length)))

(defn community-text-color [hex-color]
  (if
   (< (utils.colors/color-brightness hex-color) 125)
    colors/white colors/black))

(def crop-size 1000)
(def crop-opts {:cropping             true
                :cropperCircleOverlay true
                :width                crop-size
                :height               crop-size})

(defn pick-pic []
  ;;we need timeout because first we need to close bottom sheet and then open picker
  (js/setTimeout
   (fn []
     (react/show-image-picker
      #(do (>evt [::communities/create-field :image (.-path ^js %)])
           (>evt [::communities/create-field :new-image (.-path ^js %)]))

      crop-opts))
   300))

(defn take-pic []
  ;;we need timeout because first we need to close bottom sheet and then open picker
  (js/setTimeout
   (fn []
     (react/show-image-picker-camera
      #(do (>evt [::communities/create-field :image (.-path ^js %)])
           (>evt [::communities/create-field :new-image (.-path ^js %)]))
      crop-opts))
   300))

(defn bottom-sheet [has-picture editing?]
  (fn []
    [:<>
     [quo/list-item {:accessibility-label :take-photo
                     :theme               :accent
                     :icon                :main-icons/camera
                     :title               (i18n/label :t/community-image-take)
                     :on-press            #(do
                                             (>evt [:bottom-sheet/hide])
                                             (take-pic))}]
     [quo/list-item {:accessibility-label :pick-photo
                     :icon                :main-icons/gallery
                     :theme               :accent
                     :title               (i18n/label :t/community-image-pick)
                     :on-press            #(do
                                             (>evt [:bottom-sheet/hide])
                                             (pick-pic))}]
     (when (and has-picture (not editing?))
       [quo/list-item {:accessibility-label :remove-photo
                       :icon                :main-icons/delete
                       :theme               :accent
                       :title               (i18n/label :t/community-image-remove)
                       :on-press            #(do
                                               (>evt [:bottom-sheet/hide])
                                               (>evt [::communities/remove-field :image]))}])]))

(defn photo-picker []
  (let [{:keys [image editing?]} (<sub [:communities/create])]
    [rn/view {:style {:padding-top 16
                      :align-items :center}}
     [rn/touchable-opacity {:on-press #(>evt [:bottom-sheet/show-sheet
                                              {:content (bottom-sheet (boolean image) editing?)}])}
      [rn/view {:style {:width  128
                        :height 128}}
       [rn/view {:style {:flex             1
                         :border-radius    64
                         :background-color (colors/get-color :ui-01)
                         :justify-content  :center
                         :align-items      :center}}
        (if image
          [rn/image {:source              (utils.image/source image)
                     :style               {:width         128
                                           :height        128
                                           :border-radius 64}
                     :resize-mode         :cover
                     :accessibility-label :community-image}]
          [:<>
           [icons/icon :main-icons/photo {:color (colors/get-color :icon-02)}]
           [quo/text {:color :secondary}
            (i18n/label :t/community-thumbnail-upload)]])]
       [rn/view {:style {:position :absolute
                         :top      0
                         :right    7}}
        [rn/view {:style {:width            40
                          :height           40
                          :background-color (colors/get-color :interactive-01)
                          :border-radius    20
                          :align-items      :center
                          :justify-content  :center
                          :shadow-offset    {:width 0 :height 1}
                          :shadow-radius    6
                          :shadow-opacity   1
                          :shadow-color     (colors/get-color :shadow-01)
                          :elevation        2}}
         [icons/icon :main-icons/add {:color colors/white}]]]]]]))

(defn countable-label [{:keys [label text max-length]}]
  [rn/view {:style {:padding-bottom  10
                    :justify-content :space-between
                    :align-items     :flex-end
                    :flex-direction  :row
                    :flex-wrap       :nowrap}}
   [quo/text label]
   [quo/text {:size  :small
              :color (if (> (count text) max-length)
                       :negative
                       :secondary)}
    (str (count text) "/" max-length)]])

(defn form []
  (let [visible         (reagent/atom false)
        community-color (reagent/atom colors/black)
        community-color-text (reagent/atom colors/white)]
    (fn [{:keys [name description]}]
      [:<>
       [rn/scroll-view {:keyboard-should-persist-taps :handled
                        :style                        {:flex 1}
                        :content-container-style      {:padding-vertical 16}}
        [rn/view {:style {:padding-bottom     16
                          :padding-top        10
                          :padding-horizontal 16}}
         [countable-label {:label      (i18n/label :t/name-your-community)
                           :text       name
                           :max-length max-name-length}]
         [quo/text-input {:placeholder     (i18n/label :t/name-your-community-placeholder)
                          :default-value   name
                          :on-change-text  #(>evt  [::communities/create-field :name %])
                          :auto-focus      true}]]
        [rn/view {:style {:padding-bottom     16
                          :padding-top        10
                          :padding-horizontal 16}}
         [countable-label {:label      (i18n/label :t/give-a-short-description-community)
                           :text       description
                           :max-length max-description-length}]
         [quo/text-input {:placeholder    (i18n/label :t/give-a-short-description-community)
                          :multiline      true
                          :default-value  description
                          :on-change-text #(>evt [::communities/create-field :description %])}]]
        [quo/list-header {:color :main} (i18n/label :t/community-thumbnail-image)]
        [photo-picker]
        [rn/view {:style {:padding-bottom     16
                          :padding-top        10
                          :padding-horizontal 16}}
         [quo/text {:style {:padding-bottom 10}} (i18n/label :t/community-color)]
         [rn/touchable-opacity {:on-press #(reset! visible true)}
          [rn/view {:style {:height 44
                            :border-radius 8
                            :padding-horizontal 20
                            :flex-direction   :row
                            :justify-content :space-between
                            :background-color @community-color
                            :align-items      :center}}
           [quo/text {:style {:font-weight :bold
                              :color @community-color-text
                              :text-transform :uppercase}} @community-color]
           [icons/icon :main-icons/next {:color @community-color-text}]]]]]
       (when @visible
         [rn/modal {:on-request-close #(reset! visible false)
                    :transparent      false}
          [rn/view  {:style {:margin-top 40
                             :margin-horizontal 20}}
           [rn/touchable-opacity {:on-press       #(reset! visible false)
                                  :style {:align-self :flex-end}}
            [icons/icon :main-icons/close {:color (colors/get-color :icon-04)
                                           :width close-icon-size
                                           :height close-icon-size}]]
           [color-picker {:onColorSelected (fn [c]
                                             (reset! visible false)
                                             (reset! community-color c)
                                             (reset! community-color-text (community-text-color c)))
                          :style {:height 400}}]]])])))

(defn view []
  (let [{:keys [name description]} (<sub [:communities/create])]
    [rn/keyboard-avoiding-view {:style {:flex 1}}
     [form]
     [toolbar/toolbar
      {:show-border? true
       :center
       [quo/button {:disabled (not (valid? name description))
                    :type     :secondary
                    :on-press #(debounce/dispatch-and-chill [::communities/create-confirmation-pressed] 3000)}
        (i18n/label :t/create)]}]]))

import time

import emoji
import random
import string
from selenium.common.exceptions import TimeoutException

from tests import marks
from tests.base_test_case import MultipleDeviceTestCase, SingleDeviceTestCase
from tests.users import transaction_senders, basic_user, ens_user, ens_user_ropsten
from views.sign_in_view import SignInView
from views.send_transaction_view import SendTransactionView


class TestMessagesOneToOneChatMultiple(MultipleDeviceTestCase):

    @marks.testrail_id(6283)
    @marks.high
    def test_push_notification_1_1_chat_no_pn_activity_center(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        device_1_home, device_2_home = device_1.create_user(), device_2.create_user(enable_notifications=True)
        device_2.just_fyi("Device_1 = Enables Notifications from Profile; Device_2 - from onboarding")

        profile_1 = device_1_home.profile_button.click()
        default_username_1 = profile_1.default_username_text.text
        profile_1.profile_notifications_button.click()
        profile_1.profile_notifications_toggle_button.click()
        device_1_home = profile_1.home_button.click()
        device_2_public_key = device_2_home.get_public_key_and_username()
        message_no_pn, message = 'No PN', 'Text push notification'

        device_2.just_fyi("Device 2 check there is no PN when receiving new message to activity centre")
        device_2.put_app_to_background()
        device_1_chat = device_1_home.add_contact(device_2_public_key)
        device_1_chat.send_message(message_no_pn)
        device_2.open_notification_bar()
        if device_2.element_by_text_part(message_no_pn).is_element_displayed():
            self.errors.append("Push notification with text was received for new message in activity centre")
        device_2.get_app_from_background()
        device_2.home_button.click()
        device_2_home.get_chat(default_username_1).click()
        device_2_home.profile_button.click()

        device_2.just_fyi("Device 2 puts app on background being on Profile view to receive PN with text")
        device_2.click_system_home_button()
        device_1_chat.send_message(message)

        device_1.just_fyi("Device 1 puts app on background to receive emoji push notification")
        device_1.profile_button.click()
        device_1.click_system_home_button()

        device_2.just_fyi("Check text push notification and tap it")
        device_2.open_notification_bar()
        if not (device_2.element_by_text_part(message).is_element_displayed()
                and device_2.element_by_text_part(default_username_1).is_element_displayed()):
            device_2.driver.fail("Push notification with text was not received")
        chat_view_device_2 = device_2.click_upon_push_notification_by_text(message)

        device_2.just_fyi("Send emoji message to Device 1 while it's on backround")
        emoji_message = random.choice(list(emoji.EMOJI_UNICODE))
        emoji_unicode = emoji.EMOJI_UNICODE[emoji_message]
        chat_view_device_2.send_message(emoji.emojize(emoji_message))

        device_1.just_fyi("Device 1 checks PN with emoji")
        device_1.open_notification_bar()
        if not device_1.element_by_text_part(emoji_unicode).is_element_displayed(10):
            device_1.driver.fail("Push notification with emoji was not received")
        chat_view_device_1 = device_1.click_upon_push_notification_by_text(emoji_unicode)
        device_1.just_fyi("Check Device 1 is actually on chat")
        if not (chat_view_device_1.element_by_text_part(message).is_element_displayed()
                and chat_view_device_1.element_by_text_part(emoji_unicode).is_element_displayed()):
            device_1.driver.fail("Failed to open chat view after tap on PN")

        device_1.just_fyi("Checks there are no PN after message was seen")
        [device.click_system_home_button() for device in (device_1, device_2)]
        [device.open_notification_bar() for device in (device_1, device_2)]
        if (device_2.element_by_text_part(message).is_element_displayed()
                or device_1.element_by_text_part(emoji_unicode).is_element_displayed()):
            self.errors.append("PN are keep staying after message was seen by user")
        self.errors.verify_no_errors()

    @marks.testrail_id(5310)
    @marks.critical
    def test_offline_is_shown_messaging_1_1_chat_sent_delivered(self):
        self.create_drivers(2)
        home_1, home_2 = SignInView(self.drivers[0]).create_user(), SignInView(self.drivers[1]).create_user()
        public_key_1 = home_1.get_public_key_and_username()
        home_1.home_button.click()

        home_1.just_fyi('turn on airplane mode and check that offline status is shown on home view')
        home_1.toggle_airplane_mode()
        home_1.connection_offline_icon.wait_and_click(20)
        for element in home_1.not_connected_to_node_text, home_1.not_connected_to_peers_text:
            if not element.is_element_displayed():
                self.errors.append(
                    'Element "%s" is not shown in Connection status screen if device is offline' % element.locator)
        home_1.click_system_back_button()

        profile_2 = home_2.profile_button.click()
        username_2 = profile_2.default_username_text.text
        profile_2.home_button.click()
        chat_2 = home_2.add_contact(public_key_1)
        message_1 = 'test message'

        home_2.just_fyi("check sent status")
        chat_2.send_message(message_1)
        if chat_2.chat_element_by_text(message_1).status != 'sent':
            self.errors.append('Message status is not sent, it is %s!' % chat_2.chat_element_by_text(message_1).status)
        chat_2.toggle_airplane_mode()

        home_1.just_fyi('go back online and check that 1-1 chat will be fetched')
        home_1.toggle_airplane_mode()
        chat_element = home_1.get_chat(username_2)
        chat_element.wait_for_visibility_of_element(30)
        chat_1 = chat_element.click()
        chat_1.chat_element_by_text(message_1).wait_for_visibility_of_element(2)

        home_1.just_fyi('checking offline fetching for another message, check delivered stutus for first message')
        chat_2.toggle_airplane_mode()
        if chat_2.chat_element_by_text(message_1).status != 'delivered':
            self.errors.append(
                'Message status is not delivered, it is %s!' % chat_2.chat_element_by_text(message_1).status)
        home_1.toggle_airplane_mode()
        message_2 = 'one more message'
        chat_2.send_message(message_2)
        home_1.toggle_airplane_mode()
        chat_1 = chat_element.click()
        chat_1.chat_element_by_text(message_2).wait_for_visibility_of_element(180)
        self.errors.verify_no_errors()

    @marks.testrail_id(5315)
    @marks.high
    def test_send_non_english_message_to_newly_added_contact_on_different_networks(self):
        self.create_drivers(2)
        device_1_home, device_2_home = SignInView(self.drivers[0]).create_user(), SignInView(
            self.drivers[1]).create_user()
        profile_1 = device_1_home.profile_button.click()
        profile_1.switch_network()

        profile_1.just_fyi("Getting public keys and usernames for both users")
        device_1_home.profile_button.click()
        default_username_1 = profile_1.default_username_text.text
        profile_1.home_button.double_click()
        # Skip until edit-profile feature returned
        # profile_1 = device_1_home.profile_button.click()
        # profile_1.edit_profile_picture('sauce_logo.png')
        # profile_1.home_button.click()
        device_2_public_key, default_username_2 = device_2_home.get_public_key_and_username(return_username=True)
        device_2_home.home_button.click()

        profile_1.just_fyi("Add user to contacts and send messages on different language")
        device_1_chat = device_1_home.add_contact(device_2_public_key + ' ')
        messages = ['hello', '¿Cómo estás tu año?', 'ё, доброго вечерочка', '®	æ ç ♥']
        timestamp_message = messages[3]
        for message in messages:
            device_1_chat.send_message(message)
        device_2_chat = device_2_home.get_chat(default_username_1).click()
        sent_time_variants = device_1_chat.convert_device_time_to_chat_timestamp()
        for message in messages:
            if not device_2_chat.chat_element_by_text(message).is_element_displayed():
                self.errors.append("Message with test '%s' was not received" % message)
        if not device_2_chat.add_to_contacts.is_element_displayed():
            self.errors.append('Add to contacts button is not shown')
        if device_2_chat.user_name_text.text != default_username_1:
            self.errors.append("Default username '%s' is not shown in one-to-one chat" % default_username_1)

        profile_1.just_fyi("Check timestamps for sender and receiver")
        for chat in device_1_chat, device_2_chat:
            chat.verify_message_is_under_today_text(timestamp_message, self.errors)
            timestamp = chat.chat_element_by_text(timestamp_message).timestamp_message.text
            if timestamp not in sent_time_variants:
                self.errors.append(
                    "Timestamp is not shown, expected '%s', in fact '%s'" % (sent_time_variants.join(","), timestamp))

        device_2_home.just_fyi("Add user to contact and verify his default username")
        device_2_chat.add_to_contacts.click()
        device_2_chat.chat_options.click()
        device_2_chat.view_profile_button.click()
        if not device_2_chat.remove_from_contacts.is_element_displayed():
            self.errors.append("Remove from contacts in not shown after adding contact from 1-1 chat bar")
        device_2_chat.close_button.click()
        device_2_chat.home_button.double_click()
        device_2_home.plus_button.click()
        device_2_contacts = device_2_home.start_new_chat_button.click()
        if not device_2_contacts.element_by_text(default_username_1).is_element_displayed():
            self.errors.append('%s is not added to contacts' % default_username_1)
        if device_1_chat.user_name_text.text != default_username_2:
            self.errors.append("Default username '%s' is not shown in one-to-one chat" % default_username_2)
        device_1_chat.chat_options.click()
        device_1_chat.view_profile_button.click()

        # TODO: skip until edit-profile feature returned

        # if not device_2_chat.contact_profile_picture.is_element_image_equals_template('sauce_logo.png'):
        #     self.errors.append("Updated profile picture is not shown in one-to-one chat")
        self.errors.verify_no_errors()

    @marks.testrail_id(695843)
    @marks.high
    def test_edit_message_in_one_to_one_and_public_chats(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        device_1_home, device_2_home = device_1.create_user(), device_2.create_user()

        device_2.just_fyi("Create public chat on Device1, send message and edit it then")
        public_key_1, username_1 = device_1_home.get_public_key_and_username(return_username=True)
        public_key_2, username_2 = device_2_home.get_public_key_and_username(return_username=True)
        device_1_home.home_button.click()
        device_2_home.home_button.click()
        chat_name = device_1_home.get_random_chat_name()
        device_1_home.join_public_chat(chat_name)
        device_1_public_chat = device_1_home.get_chat_view()
        message_before_edit = "Message BEFORE edit 1"
        message_after_edit = "Message AFTER edit 2"
        device_1_public_chat.send_message(message_before_edit)
        device_1_public_chat.edit_message_in_chat(message_before_edit, message_after_edit)
        if not device_1_public_chat.element_by_text_part("⌫ Edited").is_element_displayed():
            self.errors.append('No mark in message bubble about this message was edited')

        device_2.just_fyi("Device 1 sends text message and edits it in 1-1 chat. Device2 checks edited message is shown")
        device_2_chat = device_2_home.add_contact(public_key_1)
        message_before_edit_1_1 = "Message before edit 1-1"
        message_after_edit_1_1 = "AFTER"
        device_2_chat.send_message(message_before_edit_1_1)
        device_1_home.home_button.click()

        device_1_one_to_one_chat_element = device_1_home.get_chat(username_2)
        device_2_chat.edit_message_in_chat(message_before_edit_1_1, message_after_edit_1_1)
        device_2_home.home_button.click()
        if not device_1_home.element_by_text_part(message_after_edit_1_1).is_element_present():
            self.errors.append('UNedited message version displayed on preview')
        device_1_one_to_one_chat_element.click()
        if not device_1_home.element_by_text_part(message_after_edit_1_1).is_element_present():
            self.errors.append('No edited message in 1-1 chat displayed')
        if not device_1_home.element_by_text_part("⌫ Edited").is_element_present():
            self.errors.append('No mark in message bubble about this message was edited on receiver side')

        device_2.just_fyi("Verify Device1 can not edit received message from Device2")
        device_1_home.element_by_text_part(message_after_edit_1_1).long_press_element()
        if device_1_home.element_by_translation_id("edit").is_element_present():
            self.errors.append('Option to edit someone else message available!')

        device_2_home.home_button.click()
        device_2_public_chat = device_2_home.join_public_chat(chat_name)
        if not device_2_public_chat.element_by_text_part("⌫ Edited").is_element_displayed():
            self.errors.append('No mark in message bubble about this message was edited')
        if not device_2_public_chat.element_by_text_part(message_after_edit).is_element_displayed():
            self.errors.append('Message is not edited.')

        self.errors.verify_no_errors()

    @marks.testrail_id(5782)
    @marks.critical
    def test_install_pack_and_send_sticker(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        device_1_home, device_2_home = device_1.create_user(), device_2.create_user()

        device_1_home.just_fyi('join public chat and check that stickers are not available on Ropsten')
        chat_name = device_1_home.get_random_chat_name()
        device_1_home.join_public_chat(chat_name)
        device_1_public_chat = device_1_home.get_chat_view()
        if device_1_public_chat.show_stickers_button.is_element_displayed():
            self.errors.append('Sticker button is shown while on Ropsten')

        device_1_home.just_fyi('switch to mainnet')
        device_1_public_chat.get_back_to_home_view()
        device_1_profile, device_2_profile = device_1_home.profile_button.click(), device_2_home.profile_button.click()
        device_2_public_key = device_2_profile.get_public_key_and_username()
        device_1_public_key, device_1_username = device_1_profile.get_public_key_and_username(return_username=True)

        for device in device_2_profile, device_1_profile:
            device.switch_network('Mainnet with upstream RPC')
        device_1_home.get_chat('#' + chat_name).click()

        device_1_home.just_fyi('install free sticker pack and use it in public chat')
        device_1_public_chat.show_stickers_button.click()
        device_1_public_chat.get_stickers.click()
        device_1_public_chat.install_sticker_pack_by_name('Status Cat')
        device_1_public_chat.back_button.click()
        time.sleep(2)
        device_1_public_chat.swipe_left()
        device_1_public_chat.sticker_icon.click()
        if not device_1_public_chat.sticker_message.is_element_displayed():
            self.errors.append('Sticker was not sent')
        device_1_public_chat.swipe_right()
        if not device_1_public_chat.sticker_icon.is_element_displayed():
            self.errors.append('Sticker is not shown in recently used list')
        device_1_public_chat.get_back_to_home_view()

        device_1_home.just_fyi('send stickers in 1-1 chat from Recent')
        device_1_one_to_one_chat = device_1_home.add_contact(device_2_public_key)
        device_1_one_to_one_chat.show_stickers_button.click()
        device_1_one_to_one_chat.sticker_icon.click()
        if not device_1_one_to_one_chat.chat_item.is_element_displayed():
            self.errors.append('Sticker was not sent from Recent')

        device_2_home.just_fyi('check that can install stickers by tapping on sticker message')
        device2_one_to_one_chat = device_2_home.get_chat(device_1_username).click()
        device2_one_to_one_chat.chat_item.click()
        if not device2_one_to_one_chat.element_by_text_part('Status Cat').is_element_displayed():
            self.errors.append('Stickerpack is not available for installation after tapping on sticker message')
        device2_one_to_one_chat.element_by_text_part('Free').click()
        if device2_one_to_one_chat.element_by_text_part('Free').is_element_displayed():
            self.errors.append('Stickerpack was not installed')

        device_2_home.just_fyi('check that can navigate to another user profile via long tap on sticker message')
        device2_one_to_one_chat.close_sticker_view_icon.click()
        device2_one_to_one_chat.chat_item.long_press_element()
        device2_one_to_one_chat.element_by_text('View Details').click()
        if not device2_one_to_one_chat.profile_add_to_contacts.is_element_displayed():
            self.errors.append('No navigate to user profile after tapping View Details on sticker message')

        self.errors.verify_no_errors()

    @marks.testrail_id(6305)
    @marks.critical
    def test_image_in_one_to_one_send_save_reply_timeline(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        device_1_home, device_2_home = device_1.create_user(), device_2.create_user()
        device_1_profile, device_2_profile = device_1_home.profile_button.click(), device_2_home.profile_button.click()
        device_2_public_key = device_2_profile.get_public_key_and_username()

        device_1_home.just_fyi('set status in profile')
        device_1_status = 'Hey hey hey'
        timeline = device_1.status_button.click()
        timeline.set_new_status(device_1_status, image=True)
        for element in timeline.element_by_text(device_1_status), timeline.image_chat_item:
            if not element.is_element_displayed():
                self.drivers[0].fail('Status is not set')

        device_1_public_key, device_1_username = device_1_profile.get_public_key_and_username(return_username=True)
        [home.click() for home in [device_1_profile.home_button, device_2_profile.home_button]]

        device_1_home.just_fyi('start 1-1 chat')
        device_1_chat = device_1_home.add_contact(device_2_public_key)

        device_1_home.just_fyi('send image in 1-1 chat from Gallery, check options for sender')
        image_description = 'description'
        device_1_chat.show_images_button.click()
        device_1_chat.first_image_from_gallery.click()
        if not device_1_chat.cancel_send_image_button.is_element_displayed():
            self.errors.append("Can't cancel sending images, expected image preview is not shown!")
        device_1_chat.chat_message_input.set_value(image_description)
        device_1_chat.send_message_button.click()
        device_1_chat.chat_message_input.click()
        for message in device_1_chat.image_chat_item, device_1_chat.chat_element_by_text(image_description):
            if not message.is_element_displayed():
                self.errors.append('Image or description is not shown in chat after sending for sender')
        device_1_chat.show_images_button.click()
        device_1_chat.image_from_gallery_button.click()
        device_1_chat.click_system_back_button()
        device_1_chat.image_chat_item.long_press_element()
        for element in device_1_chat.reply_message_button, device_1_chat.save_image_button:
            if not element.is_element_displayed():
                self.errors.append('Save and reply are not available on long-press on own image messages')
        if device_1_chat.view_profile_button.is_element_displayed():
            self.errors.append('"View profile" is shown on long-press on own message')

        device_2_home.just_fyi('check image, description and options for receiver')
        device_2_chat = device_2_home.get_chat(device_1_username).click()
        for message in device_2_chat.image_chat_item, device_2_chat.chat_element_by_text(image_description):
            if not message.is_element_displayed():
                self.errors.append('Image or description is not shown in chat after sending for receiver')

        device_2_home.just_fyi('View user profile and check status')
        device_2_chat.chat_options.click()
        timeline_device_1 = device_2_chat.view_profile_button.click()
        for element in timeline_device_1.element_by_text(device_1_status), timeline_device_1.image_chat_item:
            element.scroll_to_element()
            if not element.is_element_displayed():
                self.drivers[0].fail('Status of another user not shown when open another user profile')
        device_2_chat.close_button.click()

        device_2_home.just_fyi('check options on long-press image for receiver')
        device_2_chat.image_chat_item.long_press_element()
        for element in (device_2_chat.reply_message_button, device_2_chat.save_image_button):
            if not element.is_element_displayed():
                self.errors.append('Save and reply are not available on long-press on received image messages')

        device_1_home.just_fyi('save image')
        device_1_chat.save_image_button.click()
        device_1_chat.show_images_button.click_until_presence_of_element(device_1_chat.image_from_gallery_button)
        device_1_chat.image_from_gallery_button.click()
        device_1_chat.wait_for_element_starts_with_text('Recent')
        if not device_1_chat.recent_image_in_gallery.is_element_displayed():
            self.errors.append('Saved image is not shown in Recent')

        device_2_home.just_fyi('reply to image message')
        device_2_chat.reply_message_button.click()
        if device_2_chat.quote_username_in_message_input.text != "↪ Replying to %s" % device_1_username:
            self.errors.append("Username is not displayed in reply quote snippet replying to image message")
        reply_to_message_from_receiver = "image reply"
        device_2_chat.send_message(reply_to_message_from_receiver)
        reply_message = device_2_chat.chat_element_by_text(reply_to_message_from_receiver)
        if not reply_message.image_in_reply.is_element_displayed():
            self.errors.append("Image is not displayed in reply")

        self.errors.verify_no_errors()

    @marks.testrail_id(6316)
    @marks.critical
    def test_send_audio_message_with_push_notification_check(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        home_1, home_2 = device_1.create_user(), device_2.create_user(enable_notifications=True)
        profile_1 = home_1.profile_button.click()
        default_username_1 = profile_1.default_username_text.text
        home_1 = profile_1.home_button.click()
        public_key_2 = home_2.get_public_key_and_username()
        chat_1 = home_1.add_contact(public_key_2)
        chat_1.send_message('hey')
        home_2.home_button.double_click()
        home_2.get_chat(default_username_1).click()

        home_2.just_fyi("Put app on background (to check Push notification received for audio message)")
        home_2.click_system_home_button()

        home_2.just_fyi("Sending audio message to device who is on background")
        chat_1.record_audio_message(message_length_in_seconds=125)
        if not chat_1.element_by_text("Maximum recording time reached").is_element_displayed():
            self.drivers[0].fail("Exceeded 2 mins limit of recording time.")
        else:
            chat_1.ok_button.click()
        if chat_1.audio_message_recorded_time.text != "1:59":
            self.errors.append("Timer exceed 2 minutes")
        chat_1.send_message_button.click()

        device_2.open_notification_bar()
        chat_2 = home_2.click_upon_push_notification_by_text("Audio")

        listen_time = 5

        device_2.home_button.click()
        home_2.get_chat(default_username_1).click()
        chat_2.play_audio_message(listen_time)
        if chat_2.audio_message_in_chat_timer.text not in ("00:05", "00:06", "00:07", "00:08"):
            self.errors.append("Listened 5 seconds but timer shows different listened time in audio message")

        self.errors.verify_no_errors()

    @marks.testrail_id(695847)
    @marks.medium
    def test_can_pin_messages_in_ono_to_one_and_group_chats(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])

        home_1, home_2 = device_1.create_user(), device_2.create_user()
        public_key_1, username_1 = home_1.get_public_key_and_username(return_username=True)
        public_key_2, username_2 = home_2.get_public_key_and_username(return_username=True)
        home_1.home_button.click()
        chat_1 = home_1.add_contact(public_key_2)

        home_1.just_fyi("Check that Device1 can pin own message in 1-1 chat")
        message_1,message_2,message_3,message_4 = "Message1","Message2","Message3","Message4",
        chat_1.send_message(message_1)
        chat_1.send_message(message_2)
        chat_1.pin_message(message_1)
        if not chat_1.chat_element_by_text(message_1).pinned_by_label.is_element_present():
            self.drivers[0].fail("Message is not pinned!")

        home_1.just_fyi("Check that Device2 can pin Device1 message in 1-1 chat and two pinned "
                        "messages are in Device1 profile")
        home_2.home_button.click()
        chat_2 = home_2.get_chat(username_1).click()
        chat_2.pin_message(message_2)
        chat_2.chat_options.click()
        chat_2.view_profile_button.click()
        if not chat_2.pinned_messages_button.count == "2":
            self.drivers[0].fail("Pinned message count is not 2 as expected!")

        home_1.just_fyi("Check pinned message are visible in Pinned panel for both users")
        chat_1.chat_options.click()
        chat_1.view_profile_button.click()
        chat_1.pinned_messages_button.click()
        if not (chat_1.chat_element_by_text(message_1).pinned_by_label.is_element_present() and
        chat_1.chat_element_by_text(message_2).pinned_by_label.is_element_present() and
        chat_1.chat_element_by_text(message_1).is_element_present() and
        chat_1.chat_element_by_text(message_2).is_element_present()):
            self.drivers[0].fail("Something missed on Pinned messaged on Device 1!")
        chat_2.pinned_messages_button.click()
        if not (chat_1.chat_element_by_text(message_1).pinned_by_label.is_element_present() and
        chat_2.chat_element_by_text(message_2).pinned_by_label.is_element_present() and
        chat_2.chat_element_by_text(message_1).is_element_present() and
        chat_2.chat_element_by_text(message_2).is_element_present()):
            self.drivers[0].fail("Something missed on Pinned messaged on Device 2!")
        chat_1.close_button.click()

        home_1.just_fyi("Check that Device1 can not pin more than 3 messages and 'Unpin' dialog appears"
                        "messages are in Device1 profile")
        chat_1.send_message(message_3)
        chat_1.send_message(message_4)
        chat_1.pin_message(message_3)
        chat_1.pin_message(message_4)
        if not chat_1.unpin_message_popup.is_element_present():
            self.drivers[0].fail("No 'Unpin' dialog appears when pining 4th message")

        home_1.just_fyi("Unpin one message so that another could be pinned")
        chat_1.unpin_message_popup.message_text(message_1).click()
        chat_1.unpin_message_popup.click_unpin_message_button()

        if chat_1.unpin_message_popup.is_element_present():
            self.drivers[0].fail("Unpin message pop up keep staying after Unpin button pressed")
        if chat_1.chat_element_by_text(message_1).pinned_by_label.is_element_present():
            self.drivers[0].fail("Message is not unpinned!")
        if not chat_1.chat_element_by_text(message_4).pinned_by_label.is_element_present():
            self.drivers[0].fail("Message is not pinned!")

        home_1.just_fyi("Unpin another message and check it's unpinned for another user")
        chat_2.close_button.click()
        chat_2.pin_message(message_4, action="unpin")
        chat_1.chat_element_by_text(message_4).pinned_by_label.wait_for_invisibility_of_element()
        if chat_1.chat_element_by_text(message_4).pinned_by_label.is_element_present():
            self.drivers[0].fail("Message_4 is not unpinned!")

        home_1.just_fyi("Create group chat and pin message there. It's pinned for both members.")
        chat_2.home_button.click()
        chat_1.home_button.click()
        group_chat_name = "GroupChat"
        group_chat_1 = home_1.create_group_chat(user_names_to_add=[username_2], group_chat_name=group_chat_name)
        home_2.get_chat(group_chat_name).click()
        group_chat_2 = home_2.get_chat_view()
        group_chat_2.join_chat_button.click()
        group_chat_1.send_message(message_1)
        group_chat_1.pin_message(message_1)
        if not (group_chat_1.chat_element_by_text(message_1).pinned_by_label.is_element_present() and
                group_chat_2.chat_element_by_text(message_1).pinned_by_label.is_element_present()):
            self.errors.append("Message is not pinned in group chat!")

        home_1.just_fyi("Check that non admin user can not unpin messages")
        group_chat_2.chat_element_by_text(message_1).long_press_element()
        if group_chat_2.element_by_translation_id("unpin").is_element_present():
            self.errors.append("Unpin option is available for non-admin user")

        home_1.just_fyi("Grant another user with admin rights and check he can unpin message now")
        group_chat_1.chat_options.click()
        group_info_view = group_chat_1.group_info.click()
        options = group_info_view.get_username_options(username_2).click()
        options.make_admin_button.click()
        group_chat_2.click_system_back_button()
        group_chat_2.pin_message(message_1, action="unpin")
        if (group_chat_1.chat_element_by_text(message_1).pinned_by_label.is_element_present() and
                group_chat_2.chat_element_by_text(message_1).pinned_by_label.is_element_present()):
            self.errors.append("Message failed be unpinned by user who granted admin permissions!")

        self.errors.verify_no_errors()

    @marks.testrail_id(5373)
    @marks.high
    def test_send_and_open_links_with_previews(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])

        home_1, home_2 = device_1.create_user(), device_2.create_user()
        profile_1 = home_1.profile_button.click()
        default_username_1 = profile_1.default_username_text.text
        home_1 = profile_1.home_button.click()
        public_key_2 = home_2.get_public_key_and_username()
        home_2.home_button.click()
        chat_1 = home_1.add_contact(public_key_2)

        home_1.just_fyi("Check that can send emoji in 1-1 chat")
        emoji_name = random.choice(list(emoji.EMOJI_UNICODE))
        emoji_unicode = emoji.EMOJI_UNICODE[emoji_name]
        chat_1.send_message(emoji.emojize(emoji_name))
        chat_2 = home_2.get_chat(default_username_1).click()
        for chat in chat_1, chat_2:
            if not chat.chat_element_by_text(emoji_unicode).is_element_displayed():
                self.errors.append('Message with emoji was not sent or received in 1-1 chat')

        home_1.just_fyi("Check that link can be opened from 1-1 chat")
        url_message = 'http://status.im'
        chat_1.send_message(url_message)
        chat_1.home_button.double_click()
        chat_2.element_starts_with_text(url_message, 'button').click()
        web_view = chat_2.open_in_status_button.click()
        try:
            web_view.element_by_text('Private, Secure Communication').find_element()
        except TimeoutException:
            self.errors.append('Device 2: URL was not opened from 1-1 chat')
        home_2.dapp_tab_button.double_click()
        chat_2.home_button.click()

        home_1.just_fyi("Check that link can be opened from public chat")
        chat_name = ''.join(random.choice(string.ascii_lowercase) for _ in range(7))
        chat_1 = home_1.join_public_chat(chat_name)
        chat_2 = home_2.join_public_chat(chat_name)
        chat_2.send_message(url_message)
        chat_1.element_starts_with_text(url_message, 'button').click()
        web_view = chat_1.open_in_status_button.click()
        try:
            web_view.element_by_text('Private, Secure Communication').find_element()
        except TimeoutException:
            self.errors.append('Device 1: URL was not opened from 1-1 chat')
        home_1.home_button.click(desired_view='chat')

        preview_urls = {'github_pr': {'url': 'https://github.com/status-im/status-react/pull/11707',
                                      'txt': 'Update translations by jinhojang6 · Pull Request #11707 · status-im/status-react',
                                      'subtitle': 'GitHub'},
                        'yotube': {
                            'url': 'https://www.youtube.com/watch?v=XN-SVmuJH2g&list=PLbrz7IuP1hrgNtYe9g6YHwHO6F3OqNMao',
                            'txt': 'Status & Keycard – Hardware-Enforced Security',
                            'subtitle': 'YouTube'}}

        home_1.just_fyi("Check enabling and sending first gif")
        giphy_url = 'https://giphy.com/gifs/this-is-fine-QMHoU66sBXqqLqYvGO'
        chat_2.send_message(giphy_url)
        chat_2.element_by_translation_id("dont-ask").click()
        chat_1.element_by_translation_id("enable").wait_and_click()
        chat_1.element_by_translation_id("enable-all").wait_and_click()
        chat_1.close_modal_view_from_chat_button.click()
        if not chat_1.get_preview_message_by_text(giphy_url).preview_image:
            self.errors.append("No preview is shown for %s" % giphy_url)
        for key in preview_urls:
            home_2.just_fyi("Checking %s preview case" % key)
            data = preview_urls[key]
            chat_2.send_message(data['url'])
            message = chat_1.get_preview_message_by_text(data['url'])
            if message.preview_title.text != data['txt']:
                self.errors.append("Title '%s' does not match expected" % message.preview_title.text)
            if message.preview_subtitle.text != data['subtitle']:
                self.errors.append("Subtitle '%s' does not match expected" % message.preview_subtitle.text)

        home_2.just_fyi("Check if after do not ask again previews are not shown and no enable button appear")
        if chat_2.element_by_translation_id("enable").is_element_displayed():
            self.errors.append("Enable button is still shown after clicking on 'Den't ask again'")
        if chat_2.get_preview_message_by_text(giphy_url).preview_image:
            self.errors.append("Preview is shown for sender without permission")
        self.errors.verify_no_errors()

    @marks.testrail_id(5362)
    @marks.medium
    def test_unread_messages_counter_1_1_chat(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        device_1_home, device_2_home = device_1.create_user(), device_2.create_user()
        profile_2 = device_2_home.profile_button.click()
        default_username_2 = profile_2.default_username_text.text
        device_2_home = profile_2.home_button.click()
        device_1_public_key = device_1_home.get_public_key_and_username()
        device_1_home.home_button.click()

        device_2_chat = device_2_home.add_contact(device_1_public_key)

        message, message_2 = 'test message', 'test message2'
        device_2_chat.send_message(message)
        chat_element = device_1_home.get_chat(default_username_2)
        device_1.dapp_tab_button.click()
        device_2_chat.send_message(message_2)

        if device_1_home.home_button.counter.text != '1':
            self.errors.append('New messages counter is not shown on Home button')
        device_1.home_button.click()
        if chat_element.new_messages_counter.text != '1':
            self.errors.append('New messages counter is not shown on chat element')

        chat_element.click()
        device_1_home.get_back_to_home_view()

        if device_1_home.home_button.counter.is_element_displayed():
            self.errors.append('New messages counter is shown on Home button for already seen message')

        if chat_element.new_messages_counter.text == '1':
            self.errors.append('New messages counter is shown on chat element for already seen message')
        self.errors.verify_no_errors()












###############################################################
    ########################################################



    @marks.testrail_id(53622121212)
    @marks.medium
    def test_unread_messages_countewwwwwwwwr_1_1_chat(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        device_1_home, device_2_home = device_1.create_user(enable_notifications=True), device_2.create_user()
        device_1_public_key, default_username_1 = device_1_home.get_public_key_and_username(return_username=True)
        device_2_public_key, default_username_2 = device_2_home.get_public_key_and_username(return_username=True)
        device_1_home.home_button.click()
        profile_2 = device_2_home.get_profile_view()
        profile_2.switch_network()
        device_2_chat = device_2_home.add_contact(device_1_public_key)

        device_2_home.just_fyi('install free sticker pack and use it in 1-1 chat')
        device_2_chat.show_stickers_button.click()
        device_2_chat.get_stickers.click()
        device_2_chat.install_sticker_pack_by_name('Status Cat')
        device_2_chat.back_button.click()
        time.sleep(2)
        device_2_chat.swipe_left()
        device_1_chat = device_1_home.add_contact(device_2_public_key)


        #def send_sticker():
        #    device_2_chat.show_stickers_button.click()
        #    device_2_chat.swipe_right()
        #    device_2_chat.sticker_icon.click()


        ###### Separate method aka Return apps to default state ######
        device_1_chat.put_app_to_background()
        device_1.open_notification_bar()
        device_2_chat.get_back_to_home_view(2)
        device_2_home.get_chat_from_home_view(default_username_1).click()
        ###############################################################

        device_2_chat.show_stickers_button.click()
        #device_2_chat.swipe_right()
        device_2_chat.sticker_icon.click()
        if not device_1.element_by_text_part("Sticker").is_element_displayed():
            self.errors.append("Sticker not appeared in Push Notification")
            device_1.status_app_icon.click()
        else:
            device_1.element_by_text_part("Sticker").click()
        message = device_2_chat.chat_element_by_text("sticker")
        device_1_chat.set_reaction("sticker")
        if message.emojis_below_message(own=False) != 1:
            self.errors.append("Counter of reaction is not set on Sticker for message receiver!")
        device_1_chat.set_reaction("sticker")
        if message.emojis_below_message(own=False) == 1:
            self.errors.append("Counter of reaction is not re-set on Sticker for message receiver!")


        self.errors.verify_no_errors()

    ###################################
    ###################################
    ###################################






    @marks.testrail_id(5425)
    @marks.medium
    # TODO: should be completed with quoting after fix 9480
    def test_markdown_support_in_messages(self):
        self.create_drivers(2)
        sign_in_1, sign_in_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        device_1_home, device_2_home = sign_in_1.create_user(), sign_in_2.create_user()
        profile_2 = device_2_home.profile_button.click()
        default_username_2 = profile_2.default_username_text.text
        device_2_home = profile_2.home_button.click()
        device_1_public_key = device_1_home.get_public_key_and_username()
        device_1_home.home_button.click()

        device_2_chat = device_2_home.add_contact(device_1_public_key)
        device_2_chat.chat_message_input.send_keys('test message')
        device_2_chat.send_message_button.click()
        device_1_chat = device_1_home.get_chat(default_username_2).click()

        markdown = {
            'bold text in asterics': '**',
            'bold text in underscores': '__',
            'italic text in asteric': '*',
            'italic text in underscore': '_',
            'inline code': '`',
            'code blocks': '```',
            # 'quote reply (one row)' : '>',
        }

        for message, symbol in markdown.items():
            device_1_home.just_fyi('checking that "%s" is applied (%s) in 1-1 chat' % (message, symbol))
            message_to_send = symbol + message + symbol if 'quote' not in message else symbol + message
            device_2_chat.chat_message_input.send_keys(message_to_send)
            device_2_chat.send_message_button.click()
            if not device_2_chat.chat_element_by_text(message).is_element_displayed():
                self.errors.append('%s is not displayed with markdown in 1-1 chat for the sender \n' % message)

            if not device_1_chat.chat_element_by_text(message).is_element_displayed():
                self.errors.append('%s is not displayed with markdown in 1-1 chat for the recipient \n' % message)

        device_1_chat.get_back_to_home_view()
        device_2_chat.get_back_to_home_view()
        chat_name = device_1_home.get_random_chat_name()
        device_1_home.join_public_chat(chat_name)
        device_2_home.join_public_chat(chat_name)

        for message, symbol in markdown.items():
            device_1_home.just_fyi('checking that "%s" is applied (%s) in public chat' % (message, symbol))
            message_to_send = symbol + message + symbol if 'quote' not in message else symbol + message
            device_2_chat.chat_message_input.send_keys(message_to_send)
            device_2_chat.send_message_button.click()
            if not device_2_chat.chat_element_by_text(message).is_element_displayed():
                self.errors.append('%s is not displayed with markdown in public chat for the sender \n' % message)

            if not device_1_chat.chat_element_by_text(message).is_element_displayed():
                self.errors.append('%s is not displayed with markdown in public chat for the recipient \n' % message)

        self.errors.verify_no_errors()


class TestMessagesOneToOneChatSingle(SingleDeviceTestCase):

    @marks.testrail_id(5317)
    @marks.critical
    def test_copy_and_paste_messages(self):
        sign_in = SignInView(self.driver)
        home = sign_in.create_user()

        home.join_public_chat(''.join(random.choice(string.ascii_lowercase) for _ in range(7)))
        chat = sign_in.get_chat_view()
        message_text = {'text_message': 'mmmeowesage_text'}
        formatted_message = {'message_with_link': 'https://status.im',
                             # TODO: blocked with 11161
                             # 'message_with_tag': '#successishere'
                             }
        message_input = chat.chat_message_input
        message_input.send_keys(message_text['text_message'])
        chat.send_message_button.click()

        chat.copy_message_text(message_text['text_message'])

        message_input.paste_text_from_clipboard()
        if message_input.text != message_text['text_message']:
            self.errors.append('Message %s text was not copied in a public chat' % message_text['text_message'])
        message_input.clear()

        for message in formatted_message:
            message_input.send_keys(formatted_message[message])
            chat.send_message_button.click()

            message_bubble = chat.chat_element_by_text(formatted_message[message])
            message_bubble.timestamp_message.long_press_element()
            chat.element_by_text('Copy').click()

            message_input.paste_text_from_clipboard()
            if message_input.text != formatted_message[message]:
                self.errors.append('Message %s text was not copied in a public chat' % formatted_message[message])
            message_input.clear()

        self.errors.verify_no_errors()

    @marks.testrail_id(5322)
    @marks.medium
    def test_delete_cut_and_paste_messages(self):
        sign_in = SignInView(self.driver)
        home = sign_in.create_user()
        chat = home.add_contact(transaction_senders['N']['public_key'])

        message_text = 'test'
        message_input = chat.chat_message_input
        message_input.send_keys(message_text)

        message_input.delete_last_symbols(2)
        current_text = message_input.text
        if current_text != message_text[:-2]:
            self.driver.fail("Message input text '%s' doesn't match expected '%s'" % (current_text, message_text[:-2]))

        message_input.cut_text()

        message_input.paste_text_from_clipboard()
        chat.send_message_button.click()

        chat.chat_element_by_text(message_text[:-2]).wait_for_visibility_of_element(2)

    @marks.testrail_id(5783)
    @marks.critical
    def test_can_use_purchased_stickers_on_recovered_account(self):
        sign_in_view = SignInView(self.driver)
        home_view = sign_in_view.recover_access(ens_user['passphrase'])

        sign_in_view.just_fyi('switch to Mainnet')
        profile_view = home_view.profile_button.click()
        profile_view.switch_network('Mainnet with upstream RPC')

        sign_in_view.just_fyi('join to public chat, buy and install stickers')
        chat = home_view.join_public_chat(home_view.get_random_chat_name())
        chat.show_stickers_button.click()
        chat.get_stickers.click()
        chat.install_sticker_pack_by_name('Tozemoon')
        chat.back_button.click()

        sign_in_view.just_fyi('check that can use installed pack')
        time.sleep(2)
        chat.swipe_left()
        chat.sticker_icon.click()
        if not chat.chat_item.is_element_displayed():
            self.driver.fail('Sticker was not sent')

    @marks.testrail_id(5403)
    @marks.critical
    def test_start_chat_with_ens_mention_in_one_to_one(self):
        home = SignInView(self.driver).create_user()

        home.just_fyi('Start new chat with public key and check user profile from 1-1 header > options')
        chat = home.add_contact(ens_user_ropsten['public_key'])
        chat.chat_options.click_until_presence_of_element(chat.view_profile_button)
        chat.view_profile_button.click()
        for element in (chat.profile_block_contact, chat.remove_from_contacts, chat.profile_send_message):
            if not element.is_element_displayed():
                self.errors.append('Expected %s is not visible' % element.locator)
        chat.close_button.click()
        chat.home_button.click()

        home.just_fyi('Start new chat with ENS and check that ENS is resolved')
        ens = ens_user_ropsten['ens']
        home.add_contact(ens, add_in_contacts=False)
        if not chat.element_by_text("@" + ens).is_element_displayed():
            self.driver.fail('Wrong user is resolved from username when starting 1-1 chat.')

        home.just_fyi('Mention user by ENS in 1-1 chat')
        message = '@%s hey!' % ens
        chat.send_message(message)
        chat.chat_element_by_text(message).click()
        if not chat.profile_block_contact.is_element_displayed():
            self.errors.append('No redirect to user profile after tapping on message with mention (ENS) in 1-1 chat')

        home.just_fyi('Set nickname and mention user by nickname in 1-1 chat')
        russian_nickname = 'МОЙ дорогой ДРУх'
        chat.set_nickname(russian_nickname)
        chat.close_button.click()
        chat.select_mention_from_suggestion_list(russian_nickname + ' @' + ens)
        chat.chat_element_by_text('%s hey!' % russian_nickname).click()
        if not chat.profile_block_contact.is_element_displayed():
            self.errors.append(
                'No redirect to user profile after tapping on message with mention (nickname) in 1-1 chat')

        home.just_fyi('My_profile button at Start new chat view opens own QR code with public key pop-up')
        chat.close_button.click()
        home.home_button.double_click()
        home.plus_button.click()
        home.start_new_chat_button.click()
        home.my_profile_on_start_new_chat_button.click()
        account = home.get_profile_view()
        if not (account.public_key_text.is_element_displayed() and account.share_button.is_element_displayed()
                and account.qr_code_image.is_element_displayed()):
            self.errors.append('No self profile pop-up data displayed after My_profile button tap')

        self.errors.verify_no_errors()

    @marks.testrail_id(6298)
    @marks.medium
    def test_can_scan_qr_with_chat_key_from_home_start_chat(self):
        sign_in_view = SignInView(self.driver)
        home_view = sign_in_view.recover_access(basic_user['passphrase'])
        profile = home_view.profile_button.click()
        profile.switch_network()

        url_data = {
            'ens_with_stateofus_domain_deep_link': {
                'url': 'https://join.status.im/u/%s.stateofus.eth' % ens_user['ens'],
                'username': ens_user['username']
            },
            'ens_without_stateofus_domain_deep_link': {
                'url': 'https://join.status.im/u/%s' % ens_user['ens'],
                'username': ens_user['username']
            },
            'ens_another_domain_deep_link': {
                'url': 'status-im://u/%s' % ens_user['ens_another_domain'],
                'username': ens_user['username']
            },
            'own_profile_key_deep_link': {
                'url': 'https://join.status.im/u/%s' % basic_user['public_key'],
                'error': "That's you"
            },
            'other_user_profile_key_deep_link': {
                'url': 'https://join.status.im/u/%s' % ens_user['public_key'],
                'username': ens_user['username']
            },
            'other_user_profile_key_deep_link_invalid': {
                'url': 'https://join.status.im/u/%sinvalid' % ens_user['public_key'],
                'error': 'Please enter or scan a valid chat key'
            },
            # TODO: comment until clarification case with scanning QR with ENS names only
            # 'ens_another_domain':{
            #     'url': ens_user['ens_another_domain'],
            #     'username': ens_user['username']
            # },
            'own_profile_key': {
                'url': basic_user['public_key'],
                'error': "That's you"
            },
            # 'ens_without_stateofus_domain': {
            #     'url': ens_user['ens'],
            #     'username': ens_user['username']
            # },
            'other_user_profile_key': {
                'url': ens_user['public_key'],
                'username': ens_user['username']
            },
            'other_user_profile_key_invalid': {
                'url': '%s123' % ens_user['public_key'],
                'error': 'Please enter or scan a valid chat key'
            },
        }

        for key in url_data:
            home_view.plus_button.click_until_presence_of_element(home_view.start_new_chat_button)
            contact_view = home_view.start_new_chat_button.click()
            sign_in_view.just_fyi('Checking scanning qr for "%s" case' % key)
            contact_view.scan_contact_code_button.click()
            if contact_view.allow_button.is_element_displayed():
                contact_view.allow_button.click()
            contact_view.enter_qr_edit_box.scan_qr(url_data[key]['url'])
            from views.chat_view import ChatView
            chat_view = ChatView(self.driver)
            if url_data[key].get('error'):
                if not chat_view.element_by_text_part(url_data[key]['error']).is_element_displayed():
                    self.errors.append('Expected error %s is not shown' % url_data[key]['error'])
                chat_view.ok_button.click()
            if url_data[key].get('username'):
                if not chat_view.chat_message_input.is_element_displayed():
                    self.errors.append(
                        'In "%s" case chat input is not found after scanning, so no redirect to 1-1' % key)
                if not chat_view.element_by_text(url_data[key]['username']).is_element_displayed():
                    self.errors.append('In "%s" case "%s" not found after scanning' % (key, url_data[key]['username']))
                chat_view.get_back_to_home_view()
        self.errors.verify_no_errors()

    @marks.testrail_id(6322)
    @marks.medium
    def test_can_scan_different_links_with_universal_qr_scanner(self):
        user = transaction_senders['L']
        home_view = SignInView(self.driver).recover_access(user['passphrase'])
        wallet_view = home_view.wallet_button.click()
        wallet_view.set_up_wallet()
        wallet_view.home_button.click()
        send_transaction_view = SendTransactionView(self.driver)

        url_data = {
            'ens_without_stateofus_domain_deep_link': {
                'url': 'https://join.status.im/u/%s' % ens_user_ropsten['ens'],
                'username': ens_user_ropsten['username']
            },

            'other_user_profile_key_deep_link': {
                'url': 'status-im://u/%s' % basic_user['public_key'],
                'username': basic_user['username']
            },
            'other_user_profile_key_deep_link_invalid': {
                'url': 'https://join.status.im/u/%sinvalid' % ens_user['public_key'],
                'error': 'Unable to read this code'
            },
            'own_profile_key': {
                'url': user['public_key'],
            },
            'other_user_profile_key': {
                'url': ens_user['public_key'],
                'username': ens_user['username']
            },
            'wallet_validation_wrong_address_transaction': {
                'url': 'ethereum:0x744d70fdbe2ba4cf95131626614a1763df805b9e@3/transfer?address=blablabla&uint256=1e10',
                'error': 'Invalid address',
            },
            'wallet_eip_ens_for_receiver': {
                'url': 'ethereum:0xc55cf4b03948d7ebc8b9e8bad92643703811d162@3/transfer?address=nastya.stateofus.eth&uint256=1e-1',
                'data': {
                    'asset': 'STT',
                    'amount': '0.1',
                    'address': '0x58d8…F2ff',
                },
            },
            'wallet_eip_payment_link': {
                'url': 'ethereum:pay-0xc55cf4b03948d7ebc8b9e8bad92643703811d162@3/transfer?address=0x3d597789ea16054a084ac84ce87f50df9198f415&uint256=1e1',
                'data': {
                    'amount': '10',
                    'asset': 'STT',
                    'address': '0x3D59…F415',
                },
            },
            'dapp_deep_link': {
                'url': 'https://join.status.im/b/simpledapp.eth',
            },
            'dapp_deep_link_https': {
                'url': 'https://join.status.im/b/https://simpledapp.eth',
            },
            'public_chat_deep_link': {
                'url': 'https://join.status.im/baga-ma-2020',
                'chat_name': 'baga-ma-2020'
            },
        }

        for key in url_data:
            home_view.plus_button.click_until_presence_of_element(home_view.start_new_chat_button)
            home_view.just_fyi('Checking %s case' % key)
            if home_view.universal_qr_scanner_button.is_element_displayed():
                home_view.universal_qr_scanner_button.click()
            if home_view.allow_button.is_element_displayed():
                home_view.allow_button.click()
            home_view.enter_qr_edit_box.scan_qr(url_data[key]['url'])
            from views.chat_view import ChatView
            chat_view = ChatView(self.driver)
            if url_data[key].get('error'):
                if not chat_view.element_by_text_part(url_data[key]['error']).is_element_displayed():
                    self.errors.append('Expected error %s is not shown' % url_data[key]['error'])
                chat_view.ok_button.click()
            if url_data[key].get('username'):
                if key == 'own_profile_key':
                    if chat_view.profile_nickname.is_element_displayed():
                        self.errors.append('In %s case was not redirected to own profile' % key)
                else:
                    if not chat_view.profile_nickname.is_element_displayed():
                        self.errors.append('In %s case block user button is not shown' % key)
                if not chat_view.element_by_text(url_data[key]['username']).is_element_displayed():
                    self.errors.append('In %s case username not shown' % key)
            if 'wallet' in key:
                if url_data[key].get('data'):
                    actual_data = send_transaction_view.get_values_from_send_transaction_bottom_sheet()
                    difference_in_data = url_data[key]['data'].items() - actual_data.items()
                    if difference_in_data:
                        self.errors.append(
                            'In %s case returned value does not match expected in %s' % (key, repr(difference_in_data)))
                    wallet_view.close_send_transaction_view_button.click()
                wallet_view.home_button.click()
            if 'dapp' in key:
                home_view.open_in_status_button.click()
                if not (chat_view.allow_button.is_element_displayed() or chat_view.element_by_text("Can't find web3 library").is_element_displayed()):
                    self.errors.append('No allow button is shown in case of navigating to Status dapp!')
                chat_view.dapp_tab_button.click()
                chat_view.home_button.click()
            if 'public' in key:
                if not chat_view.chat_message_input.is_element_displayed():
                    self.errors.append('No message input is shown in case of navigating to public chat via deep link!')
                if not chat_view.element_by_text_part(url_data[key]['chat_name']).is_element_displayed():
                    self.errors.append('Chat name is not shown in case of navigating to public chat via deep link!')
            chat_view.get_back_to_home_view()

        self.errors.verify_no_errors()

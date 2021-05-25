from datetime import datetime, timedelta
import dateutil.parser
import time

from selenium.common.exceptions import NoSuchElementException

from tests import emojis
from time import sleep
from views.base_element import Button, EditBox, Text, BaseElement, SilentButton
from views.base_view import BaseView
from views.profile_view import ProfilePictureElement


class CommandsButton(Button):
    def __init__(self, driver):
        super().__init__(driver, accessibility_id="show-extensions-icon")

    def click(self):
        self.click_until_presence_of_element(SendCommand(self.driver))
        return self.navigate()


class SendCommand(Button):
    def __init__(self, driver):
        super().__init__(driver, translation_id="send-transaction")

    def navigate(self):
        from views.send_transaction_view import SendTransactionView
        return SendTransactionView(self.driver)

    def click(self):
        self.wait_for_element().click()
        return self.navigate()


class RequestCommand(Button):
    def __init__(self, driver):
        super().__init__(driver, translation_id="request-transaction")

    def navigate(self):
        from views.send_transaction_view import SendTransactionView
        return SendTransactionView(self.driver)

    def click(self):
        self.wait_for_element().click()
        return self.navigate()


class GroupInfoButton(Button):
    def __init__(self, driver):
        super().__init__(driver, translation_id="group-info")

    def navigate(self):
        return GroupChatInfoView(self.driver)

    def click(self):
        self.wait_for_element().click()
        return self.navigate()


class UnblockContactButton(Button):
    def __init__(self, driver):
        super().__init__(driver, accessibility_id="Unblock-item-button")

    def click(self):
        self.scroll_to_element()
        self.wait_for_element().click()


class OpenInStatusButton(Button):
    def __init__(self, driver):
        super().__init__(driver, translation_id="browsing-open-in-status")

    def navigate(self):
        from views.web_views.base_web_view import BaseWebView
        return BaseWebView(self.driver)


class ViewProfileButton(Button):
    def __init__(self, driver):
        super().__init__(driver, translation_id="view-profile")

    def navigate(self):
        return ChatView(self.driver)


class ChatOptionsButton(Button):
    def __init__(self, driver):
        super().__init__(driver, accessibility_id="chat-menu-button")

    def click(self):
        self.click_until_presence_of_element(ChatView(self.driver).clear_history_button)

    def navigate(self):
        return ChatView(self.driver)


class ProfileSendMessageButton(Button):
    def __init__(self, driver):
        super().__init__(driver, accessibility_id="Chat-item-button")

    def navigate(self):
        return ChatView(self.driver)


class ProfileBlockContactButton(Button):
    def __init__(self, driver):
        super(ProfileBlockContactButton, self).__init__(driver, accessibility_id="Block-item-button")

    def click(self):
        self.scroll_to_element()
        self.wait_for_element().click()


class ChatElementByText(Text):
    def __init__(self, driver, text):
        self.message_text = text
        self.message_locator = "//*[starts-with(@text,'%s')]" % text
        super().__init__(driver, prefix=self.message_locator,
                         xpath="/ancestor::android.view.ViewGroup[@content-desc='chat-item']")

    def find_element(self):
        for _ in range(2):
            try:
                return super(ChatElementByText, self).find_element()
            except NoSuchElementException:
                self.wait_for_visibility_of_element(20)

    @property
    def image_in_reply(self):
        class ImageInReply(BaseElement):
            def __init__(self, driver, parent_locator: str):
                super().__init__(driver, prefix=parent_locator, xpath="//android.widget.ImageView")
        try:
            return ImageInReply(self.driver, self.locator)
        except NoSuchElementException:
            return ''

    @property
    def timestamp_message(self):
        class TimeStampText(Text):
            def __init__(self, driver, parent_locator: str):
                super().__init__(driver, xpath="(%s//android.widget.TextView)[last()]" % parent_locator)

        return TimeStampText(self.driver, self.locator)


    @property
    def member_photo(self):
        class MemberPhoto(Button):
            def __init__(self, driver, parent_locator: str):
                super().__init__(driver, prefix=parent_locator, xpath="//*[@content-desc='member-photo']")

        return MemberPhoto(self.driver, self.locator)

    @property
    def username(self):
        class Username(Text):
            def __init__(self, driver, parent_locator: str):
                super().__init__(driver, prefix=parent_locator, xpath="/*[2]/android.widget.TextView" )

        return Username(self.driver, self.locator)

    def contains_text(self, text, wait_time=5) -> bool:
        element = Text(self.driver, prefix=self.locator,
                       xpath="//android.view.ViewGroup//android.widget.TextView[contains(@text,'%s')]" % text)
        return element.is_element_displayed(wait_time)

    @property
    def uncollapse(self) -> bool:
        class Collapse(Button):
            def __init__(self, driver, parent_locator: str):
                super().__init__(driver, prefix=parent_locator, xpath="/../../..//android.widget.ImageView[@content-desc='icon']")

        return Collapse(self.driver, self.locator).is_element_displayed()

    @property
    def status(self) -> str:
        sent = Text(self.driver, prefix=self.locator, xpath="//*[@content-desc='sent']")
        delivered = Text(self.driver, prefix=self.locator, xpath="//*[@content-desc='delivered']")
        status = ''
        if sent.is_element_displayed(10, ignored_exceptions=NoSuchElementException):
            status = 'sent'
        if delivered.is_element_displayed(30, ignored_exceptions=NoSuchElementException):
            status = 'delivered'
        return status

    @property
    def replied_message_text(self):
        class RepliedMessageText(Text):
            def __init__(self, driver, parent_locator: str):
                super().__init__(driver, prefix=parent_locator, xpath="/preceding-sibling::*[1]/android.widget.TextView[2]")
        try:
            return RepliedMessageText(self.driver, self.message_locator).text
        except NoSuchElementException:
            return ''

    @property
    def replied_to_username_text(self):
        class RepliedToUsernameText(Text):
            def __init__(self, driver, parent_locator: str):
                super().__init__(driver, prefix=parent_locator,
                                 xpath="/preceding-sibling::*[1]/android.widget.TextView[1]")
        try:
            return RepliedToUsernameText(self.driver, self.message_locator).text
        except NoSuchElementException:
            return ''

    def emojis_below_message(self, emoji: str = 'thumbs-up', own=True):
        class EmojisNumber(Text):
            def __init__(self, driver, parent_locator: str):
                self.own = own
                self.emoji = emoji
                self.emojis_id = 'emoji-' + str(emojis[self.emoji]) + '-is-own-' + str(self.own).lower()
                super().__init__(driver, prefix=parent_locator, xpath="/../..//*[@content-desc='%s']" % self.emojis_id)

            @property
            def text(self):
                try:
                    text = self.find_element().text
                    self.driver.info('%s is %s for %s where my reaction is set on message is %s' % (self.name, text, self.emoji, str(self.own)))
                    return text
                except NoSuchElementException:
                    return 0
        return int(EmojisNumber(self.driver, self.locator).text)


class UsernameOptions(Button):
    def __init__(self, driver, username):
        super().__init__(driver, xpath="//*[@text='%s']/..//*[@content-desc='menu-option']" % username)

    def navigate(self):
        return ChatView(self.driver)

    def click(self):
        self.scroll_to_element()
        self.wait_for_element().click()
        return self.navigate()


class UsernameCheckbox(Button):
    def __init__(self, driver, username):
        self.username = username
        super().__init__(driver, xpath="//*[@text='%s']" % username)

    def click(self):
        try:
                self.scroll_to_element(20).click()
        except NoSuchElementException:
                self.scroll_to_element(direction='up', depth=20).click()


class GroupChatInfoView(BaseView):
    def __init__(self, driver):
        super().__init__(driver)
        self.add_members = Button(self.driver, translation_id="add-members")

    def get_username_options(self, username: str):
        return UsernameOptions(self.driver, username)

    def user_admin(self, username: str):
        return Button(self.driver,
                      xpath="//*[@text='%s']/..//*[@text='%s']" % (username, self.get_translation_by_key("group-chat-admin")))

    def get_user_from_group_info(self, username: str):
        return Text(self.driver, xpath="//*[@text='%s']" % username)


class PreviewMessage(ChatElementByText):
    def __init__(self, driver, text:str):
        super().__init__(driver, text=text)
        self.locator+="/android.view.ViewGroup/android.view.ViewGroup"

    @staticmethod
    def return_element_or_empty(obj):
        try:
            return obj.scroll_to_element()
        except NoSuchElementException:
            return ''

    @property
    def preview_image(self):
        class PreviewImage(SilentButton):
            def __init__(self, driver, parent_locator: str):
                super().__init__(driver, prefix=parent_locator, xpath="/android.widget.ImageView")
        return PreviewMessage.return_element_or_empty(PreviewImage(self.driver, self.locator))

    @property
    def preview_title(self):
        class PreviewTitle(SilentButton):
            def __init__(self, driver, parent_locator: str):
                super().__init__(driver, prefix=parent_locator, xpath="/android.widget.TextView[1]")
        return PreviewMessage.return_element_or_empty(PreviewTitle(self.driver, self.locator))

    @property
    def preview_subtitle(self):
        class PreviewSubTitle(SilentButton):
            def __init__(self, driver, parent_locator: str):
                super().__init__(driver, prefix=parent_locator, xpath="/android.widget.TextView[2]")
        return PreviewMessage.return_element_or_empty(PreviewSubTitle(self.driver, self.locator))


class TransactionMessage(ChatElementByText):
    def __init__(self, driver, text:str, transaction_value):
        super().__init__(driver, text=text)
        if transaction_value:
            self.xpath = "//*[starts-with(@text,'%s')]/../*[@text='%s']/ancestor::android.view.ViewGroup[@content-desc='chat-item']" % (text, transaction_value)
        # Common statuses for incoming and outgoing transactions
        self.address_requested = self.get_translation_by_key("address-requested")
        self.confirmed = self.get_translation_by_key("status-confirmed")
        self.pending = self.get_translation_by_key("pending")
        self.declined = self.get_translation_by_key("transaction-declined")

    @property
    def transaction_status(self):
        class TransactionStatus(SilentButton):
            def __init__(self, driver, parent_locator: str):
                super().__init__(driver, prefix=parent_locator, xpath="/*[1]/*[1]/*[5]/android.widget.TextView")

        return TransactionStatus(self.driver, self.locator)

    @property
    def decline_transaction(self):
        class DeclineTransaction(Button):
            def __init__(self, driver, parent_locator):
                super().__init__(driver, prefix=parent_locator, translation_id="decline")

            def click(self):
                self.wait_for_element().click()
                return self.navigate()

        return DeclineTransaction(self.driver, self.locator)


class OutgoingTransaction(TransactionMessage):
    def __init__(self, driver, account_name: str, transaction_value):
        super().__init__(driver, text="↑ Outgoing transaction", transaction_value=transaction_value)
        self.account_name = account_name
        self.address_request_accepted = self.get_translation_by_key("address-request-accepted")
        self.address_received = self.get_translation_by_key("address-received")

    @property
    def sign_and_send(self):
        class SignAndSend(Button):
            def __init__(self, driver, parent_locator):
                super().__init__(driver, prefix=parent_locator, translation_id="sign-and-send")

            def navigate(self):
                from views.send_transaction_view import SendTransactionView
                return SendTransactionView(self.driver)

            def click(self):
                self.wait_for_element().click()
                return self.navigate()

        return SignAndSend(self.driver, self.locator)


class IncomingTransaction(TransactionMessage):
    def __init__(self, driver, account_name: str, transaction_value):
        super().__init__(driver, text="↓ Incoming transaction", transaction_value=transaction_value)
        self.account_name = account_name
        self.shared_account = "Shared '%s'" % account_name

    @property
    def accept_and_share_address(self):
        class AcceptAndShareAddress(Button):
            def __init__(self, driver, parent_locator):
                super().__init__(driver, prefix=parent_locator, translation_id="accept-and-share-address")

            def navigate(self):
                from views.send_transaction_view import SendTransactionView
                return SendTransactionView(self.driver)

            def click(self):
                self.wait_for_element().click()
                return self.navigate()

        return AcceptAndShareAddress(self.driver, self.locator)


class ChatView(BaseView):
    def __init__(self, driver):
        super().__init__(driver)

        # Start new chat
        self.public_key_edit_box = EditBox(self.driver, accessibility_id="enter-contact-code-input")
        self.scan_contact_code_button = Button(self.driver, accessibility_id="scan-contact-code-button")

        # Chat header
        self.user_name_text = Text(self.driver, accessibility_id="chat-name-text")
        self.add_to_contacts = Button(self.driver, accessibility_id="add-to-contacts-button")
        ## Options
        self.chat_options = ChatOptionsButton(self.driver)
        self.delete_chat_button = Button(self.driver, translation_id="delete-chat")
        self.clear_history_button = Button(self.driver, translation_id="clear-history")
        self.reply_message_button = Button(self.driver, translation_id="message-reply")
        self.share_chat_button = Button(self.driver, accessibility_id="share-chat-button")
        self.clear_button = Button(self.driver, translation_id="clear", uppercase=True)
        self.view_profile_button = ViewProfileButton(self.driver)
        self.view_profile_by_avatar_button = Button(self.driver, accessibility_id="member-photo")
        self.user_options = Button(self.driver, accessibility_id="options")
        self.open_in_status_button = OpenInStatusButton(self.driver)

        # Chat input
        self.chat_message_input = EditBox(self.driver, accessibility_id="chat-message-input")
        self.quote_username_in_message_input = EditBox(self.driver,
                                                       xpath="//android.view.ViewGroup[@content-desc='cancel-message-reply']/..//android.widget.TextView[1]")
        self.cancel_reply_button = Button(self.driver, accessibility_id="cancel-message-reply")
        self.chat_item = Button(self.driver, accessibility_id="chat-item")
        self.chat_name_editbox = EditBox(self.driver, accessibility_id="chat-name-input")
        self.commands_button = CommandsButton(self.driver)
        self.send_command = SendCommand(self.driver)
        self.request_command = RequestCommand(self.driver)

        # General chat view
        self.history_start_icon = Button(self.driver, accessibility_id="history-chat")

        #Stickers
        self.show_stickers_button = Button(self.driver, accessibility_id="show-stickers-icon")
        self.get_stickers = Button(self.driver, translation_id="get-stickers")
        self.sticker_icon = Button(self.driver, accessibility_id="sticker-icon")
        self.sticker_message = Button(self.driver, accessibility_id="sticker-message")

        # Images
        self.show_images_button = Button(self.driver, accessibility_id="show-photo-icon")
        self.take_photo_button = Button(self.driver, accessibility_id="take-picture")
        self.image_from_gallery_button = Button(self.driver, accessibility_id="open-gallery")
        self.first_image_from_gallery = Button(self.driver,
                                               xpath="//*[@content-desc='open-gallery']/following-sibling::android.view.ViewGroup[1]")
        self.image_chat_item = Button(self.driver, accessibility_id="message-image")
        self.save_image_button = Button(self.driver, translation_id="save")
        self.recent_image_in_gallery = Button(self.driver,
                                              xpath="//*[contains(@resource-id,'thumbnail')]")
        self.cancel_send_image_button = Button(self.driver, accessibility_id="cancel-send-image")
        self.view_image_options = Button(self.driver,
                                         xpath="//*[@content-desc='icon']/android.widget.ImageView")

        #Audio
        self.audio_message_button = Button(self.driver, accessibility_id="show-audio-message-icon")
        self.record_audio_button = Button(self.driver, accessibility_id="start-stop-audio-recording-button")
        self.cancel_audio_message_button = Button(self.driver, accessibility_id="cancel-message-button")
        self.send_audio_message_button = Button(self.driver, accessibility_id="send-message-button")
        self.play_pause_audio_message_button = Button(self.driver, accessibility_id="play-pause-audio-message-button")
        self.audio_message_in_chat_timer = Text(self.driver,
                                                xpath="//*[@content-desc='play-pause-audio-message-button']/../..//android.widget.TextView[1]")
        self.audio_message_recorded_time = Text(self.driver, accessibility_id="audio-message-recorded-time")

        # Group chats
        self.group_info = GroupInfoButton(self.driver)
        self.leave_chat_button = Button(self.driver, accessibility_id="leave-chat-button")
        self.leave_button = Button(self.driver, translation_id="leave", uppercase=True)
        self.join_chat_button = Button(self.driver, accessibility_id="join-chat-button")
        self.decline_invitation_button = Button(self.driver, translation_id="group-chat-decline-invitation")
        self.remove_user_button = Button(self.driver, accessibility_id="remove-from-chat")
        self.make_admin_button = Button(self.driver, accessibility_id="make-admin")
        self.edit_group_chat_name_button = Button(self.driver, accessibility_id="edit-button")
        self.edit_group_chat_name_edit_box = EditBox(self.driver, accessibility_id="new-chat-name")
        self.done_button = Button(self.driver, accessibility_id="done")
        self.create_button = Button(self.driver, accessibility_id="create-group-chat-button")
        ## Group invites
        self.group_invite_button = Button(self.driver, accessibility_id="invite-chat-button")
        self.group_invite_link_text = Text(self.driver,
                                           xpath="//*[@content-desc='invitation-link']/android.widget.TextView")
        self.introduce_yourself_edit_box = EditBox(self.driver, accessibility_id="introduce-yourself-input")
        self.request_membership_button = Button(self.driver, translation_id="request-membership")
        self.group_membership_request_button = Button(self.driver, accessibility_id="invitation-requests-button")
        self.accept_group_invitation_button = Button(self.driver, accessibility_id="accept-invitation-button")
        self.decline_group_invitation_button = Button(self.driver, accessibility_id="decline-invitation-button")
        self.retry_group_invite_button = Button(self.driver, accessibility_id="retry-button")
        self.remove_group_invite_button = Button(self.driver, accessibility_id="remove-group-button")

        # Contact's profile
        self.contact_profile_picture = ProfilePictureElement(self.driver)
        self.profile_send_message = ProfileSendMessageButton(self.driver)
        self.profile_block_contact = ProfileBlockContactButton(self.driver)
        self.confirm_block_contact_button = Button(self.driver, accessibility_id="block-contact-confirm")
        self.unblock_contact_button = UnblockContactButton(self.driver)
        self.profile_add_to_contacts = Button(self.driver, accessibility_id="Add to contacts-item-button")
        self.profile_details = Button(self.driver, accessibility_id="share-button")
        self.profile_nickname = Text(self.driver,
                                     xpath="//*[@content-desc='profile-nickname-item']/android.widget.TextView[2]")
        self.profile_nickname_button = Button(self.driver, accessibility_id="profile-nickname-item")
        self.nickname_input_field = EditBox(self.driver, accessibility_id="nickname-input")
        self.remove_from_contacts = Button(self.driver, accessibility_id="Remove from contacts-item-button")

        # Timeline (My Status tab)
        self.timeline_add_new_status_button = Button(self.driver, accessibility_id="plus-button")
        self.timeline_my_status_editbox = EditBox(self.driver, accessibility_id="my-status-input")
        self.timeline_open_images_panel_button = Button(self.driver, accessibility_id="open-images-panel-button")
        self.timeline_send_my_status_button = Button(self.driver, accessibility_id="send-my-status-button")
        self.timeline_own_account_photo = Button(self.driver, accessibility_id="own-account-photo")


    def get_outgoing_transaction(self, account=None, transaction_value=None) -> object:
        if account is None:
            account = self.status_account_name
        return OutgoingTransaction(self.driver, account, transaction_value)

    def get_incoming_transaction(self, account=None, transaction_value=None) -> object:
        if account is None:
            account = self.status_account_name
        return IncomingTransaction(self.driver, account, transaction_value)

    def get_preview_message_by_text(self, text=None) -> object:
        self.driver.info('**Getting preview message for link:%s**' % text)
        return PreviewMessage(self.driver, text)


    def delete_chat(self):
        self.driver.info("**Delete chat via options**")
        self.chat_options.click()
        self.delete_chat_button.click()
        self.delete_button.click()

    def leave_chat(self):
        self.driver.info("**Leave chat via options**")
        self.chat_options.click()
        self.leave_chat_button.click()
        self.leave_button.click()

    def clear_history(self):
        self.driver.info("**Clear chat history via options**")
        self.chat_options.click()
        self.clear_history_button.click()
        self.clear_button.click()


    def leave_chat_via_group_info(self):
        self.driver.info("**Leave group chat via group info**")
        self.chat_options.click()
        self.group_info.click()
        self.leave_chat_button.click()
        self.leave_button.click()

    def rename_chat_via_group_info(self, new_chat_name):
        self.driver.info("**Rename group chat to %s**" % new_chat_name)
        self.chat_options.click()
        self.group_info.click()
        self.edit_group_chat_name_button.click()
        self.edit_group_chat_name_edit_box.set_value(new_chat_name)
        self.done_button.click()

    def get_group_invite_via_group_info(self):
        self.driver.info("**Copy group invite link**")
        self.chat_options.click()
        self.group_info.click()
        self.group_invite_button.click()
        return self.group_invite_link_text.text

    def request_membership_for_group_chat(self, intro_message):
        self.driver.info("**Requesting membership to group chat**")
        self.introduce_yourself_edit_box.set_value(intro_message)
        self.request_membership_button.click_until_presence_of_element(self.element_by_text('Request pending…'))

    def get_username_checkbox(self, username: str):
        self.driver.info("**Getting %s checkbox**" % username)
        return UsernameCheckbox(self.driver, username)

    def accept_membership_for_group_chat_via_chat_view(self, username, accept=True):
        info = "%s membership to group chat" % username
        self.driver.info("**Accept %s**" % info) if accept else  self.driver.info("**Decline %s**" % info)
        self.group_membership_request_button.click()
        self.element_by_text(username).click()
        self.accept_group_invitation_button.click() if accept else self.decline_group_invitation_button.click()

    def add_members_to_group_chat(self, user_names_to_add: list):
        self.driver.info("**Add %s to group chat**" % ', '.join(map(str, user_names_to_add)))
        self.chat_options.click()
        group_info_view = self.group_info.click()
        group_info_view.add_members.click()
        for user_name in user_names_to_add:
            user_contact = self.get_username_checkbox(user_name)
            user_contact.scroll_to_element()
            user_contact.click()
        self.add_button.click()

    def get_user_options(self, username: str):
        self.driver.info("**Get user options for %s**" % username)
        self.chat_options.click()
        group_info_view = self.group_info.click()
        group_info_view.get_username_options(username).click()
        return self

    def chat_element_by_text(self, text):
        self.driver.info("**Looking for a message by text: %s**" % text)
        return ChatElementByText(self.driver, text)

    def verify_message_is_under_today_text(self, text, errors):
        self.driver.info("**Veryfying that '%s' is under today**" % text)
        message_element = self.chat_element_by_text(text)
        message_element.wait_for_visibility_of_element()
        message_location = message_element.find_element().location['y']
        today_text_element = self.element_by_text('Today').find_element()
        today_location = today_text_element.location['y']
        today_height = today_text_element.size['height']
        if message_location < today_location + today_height:
            errors.append("Message '%s' is not under 'Today' text" % text)

    def send_message(self, message: str = 'test message'):
        self.driver.info("**Sending message '%s'**" % message)
        self.chat_message_input.send_keys(message)
        self.send_message_button.click()

    def copy_message_text(self, message_text):
        self.driver.info("**Copying '%s' message via long press**" % message_text)
        self.element_by_text_part(message_text).long_press_element()
        self.element_by_translation_id("copy-to-clipboard").click()

    def quote_message(self, message = str):
        self.driver.info("**Quote '%s' message**" % message)
        self.element_by_text_part(message).long_press_element()
        self.reply_message_button.click()

    def set_reaction(self, message: str,  emoji: str = 'thumbs-up'):
        self.driver.info("**Setting '%s' reaction**" % emoji)
        key = emojis[emoji]
        self.element_by_text_part(message).long_press_element()
        element = Button(self.driver, accessibility_id ='pick-emoji-%s' % key)
        element.click()
        element.wait_for_invisibility_of_element()

    def view_profile_long_press(self, message = str):
        self.chat_element_by_text(message).long_press_element()
        self.view_profile_by_avatar_button.click()
        self.profile_block_contact.wait_for_visibility_of_element(5)

    def wait_ens_name_resolved_in_chat(self, message = str, username_value = str):
        self.driver.info("**Waiting ENS name '%s' is resolved in chat**" % username_value)
        # self.view_profile_by_avatar_button.click()
        counter = 0
        while True:
            if counter >= 120:
                self.driver.fail('Username not updated to %s %s' % (60, username_value))
            elif not (self.chat_element_by_text(message).username.text == username_value):
                counter += 5
                time.sleep(5)
            else:
                return
        # self.profile_block_contact.wait_for_visibility_of_element(5)

    def move_to_messages_by_time_marker(self, marker='Today'):
        self.driver.info("**Moving to messages by time marker: '%s'**" % marker)
        Button(self.driver, xpath="//*[@text='%s'']"  % marker).scroll_to_element(depth=50, direction='up')

    def install_sticker_pack_by_name(self, pack_name: str):
        self.driver.info("**Installing '%s' stickerpack**" % pack_name)
        element = Button(self.driver,
                         xpath="//*[@content-desc='sticker-pack-name'][@text='%s']/..//*[@content-desc='sticker-pack-price']" % pack_name)
        element.scroll_to_element()
        element.click()
        element.wait_for_invisibility_of_element()

    def scroll_to_start_of_history(self, depth=20):
        self.driver.info('*Scrolling to the start of chat history*')
        for _ in range(depth):
            try:
                return self.history_start_icon.find_element()
            except NoSuchElementException:
                size = self.driver.get_window_size()
                self.driver.swipe(500, size["height"] * 0.25, 500, size["height"] * 0.8)
        else:
            raise Exception('Start of chat history is not reached!')

    def user_profile_image_in_mentions_list(self, username):
        return Button(self.driver, xpath="//*[@content-desc='suggestions-list']//*[@text='%s']/"
                                         "..//*[@content-desc='member-photo']" % username)

    def search_user_in_mention_suggestion_list(self, username):
        return Button(self.driver, xpath="//*[@content-desc='suggestions-list']//*[@text='%s']" % username)

    def select_mention_from_suggestion_list(self, username_in_list, typed_search_pattern = ''):
        self.driver.info("**Selecting '%s' from suggestion list by '%s'**" % (username_in_list, typed_search_pattern))
        self.chat_message_input.set_value('@' + typed_search_pattern)
        self.chat_message_input.click()
        self.search_user_in_mention_suggestion_list(username_in_list).wait_for_visibility_of_element(10).click()

    def record_audio_message(self, message_length_in_seconds=5):
        self.driver.info("**Recording audiomessage %ss**" % message_length_in_seconds)
        self.audio_message_button.click()
        self.allow_button.click()
        self.record_audio_button.click()
        sleep(message_length_in_seconds)

    def play_audio_message(self, listen_time=5):
        self.driver.info("**Playing audiomessage during %ss**" % listen_time)
        self.play_pause_audio_message_button.click()
        sleep(listen_time)
        self.play_pause_audio_message_button.click()

    def block_contact(self):
        self.driver.info("**Block contact from other user profile**")
        self.profile_block_contact.click()
        self.confirm_block_contact_button.click()

    def set_nickname(self, nickname):
        self.driver.info("**Setting nickname:%s**" % nickname)
        self.profile_nickname_button.click()
        self.nickname_input_field.send_keys(nickname)
        self.element_by_text('Done').click()

    def convert_device_time_to_chat_timestamp(self) -> list:
        sent_time_object = dateutil.parser.parse(self.driver.device_time)
        timestamp = datetime.strptime("%s:%s" % (sent_time_object.hour, sent_time_object.minute), '%H:%M').strftime("%I:%M %p")
        timestamp_obj = datetime.strptime(timestamp, '%I:%M %p')
        possible_timestamps_obj = [timestamp_obj + timedelta(0,0,0,0,1), timestamp_obj, timestamp_obj - timedelta(0,0,0,0,1)]
        timestamps = list(map(lambda x : x.strftime("%I:%M %p"), possible_timestamps_obj))
        final_timestamps = [t[1:] if t[0] == '0' else t for t in timestamps]
        return final_timestamps


    def set_new_status(self, status='something is happening', image=False):
        self.driver.info("**Setting new status:%s, image set is: %s**" % (status, str(image)))
        self.timeline_add_new_status_button.click_until_presence_of_element(self.timeline_my_status_editbox)
        self.timeline_my_status_editbox.set_value(status)

        if image:
            self.timeline_open_images_panel_button.click()
            if self.allow_button.is_element_present():
                self.allow_button.click()
            self.first_image_from_gallery.click()
        self.timeline_send_my_status_button.click()

    def get_transaction_message_by_asset(self, transaction_value, incoming=True) -> object:
        if incoming:
            transaction_message = self.get_incoming_transaction(account=None, transaction_value=transaction_value)
        else:
            transaction_message = self.get_outgoing_transaction(account=None, transaction_value=transaction_value)
        return transaction_message

    @staticmethod
    def get_resolved_chat_key(username, chat_key):
        return '%s • %s…%s' % (username, chat_key[:6], chat_key[-4:])

    # Group chat system messages
    @staticmethod
    def leave_system_message(username):
        return "%s left the group" % username

    @staticmethod
    def has_been_made_admin_system_message(admin, new_admin):
        return "%s has made %s admin" % (admin, new_admin)

    @staticmethod
    def create_system_message(admin, chat_name):
        return '%s created the group %s' % (admin, chat_name)

    @staticmethod
    def invite_system_message(admin, invited_user):
        return '%s has invited %s' % (admin, invited_user)

    @staticmethod
    def invited_to_join_system_message(username, chat_name):
        return '%s invited you to join the group %s' % (username, chat_name)

    @staticmethod
    def join_system_message(username):
        return '%s joined the group' % username

    @staticmethod
    def create_for_admin_system_message(chat_name):
        return 'You created the group %s' % chat_name

    @staticmethod
    def changed_group_name_system_message(admin, chat_name):
        return "%s changed the group's name to %s" % (admin, chat_name)
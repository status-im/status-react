from tests import marks, pytest_config_global, test_dapp_name
from tests.base_test_case import SingleDeviceTestCase, MultipleDeviceTestCase
from tests.users import upgrade_users, transaction_recipients
from views.sign_in_view import SignInView
import views.upgrade_dbs.chats.data as chat_data
import views.upgrade_dbs.dapps.data as dapp_data
import views.upgrade_dbs.pairing.data as sync_data

@marks.upgrade
class TestUpgradeApplication(SingleDeviceTestCase):

    @marks.testrail_id(6284)
    def test_unread_previews_public_chat_version_upgrade(self):
        sign_in = SignInView(self.driver)
        unread_one_to_one_name, unread_public_name = 'All Whopping Dassierat', '#before-upgrade'
        chats = chat_data.chats
        home = sign_in.import_db(user=upgrade_users['chats'], import_db_folder_name='chats')
        home.just_fyi("Grab profile version")

        profile = home.profile_button.click()
        profile.about_button.click()
        old_version = profile.app_version_text.text

        profile.upgrade_app()
        home = sign_in.sign_in(upgraded=True)

        home.profile_button.click()
        profile.about_button.click()
        new_version = profile.app_version_text.text
        if 'release' in pytest_config_global['apk_upgrade']:
            if new_version == old_version:
                 self.errors.append('Upgraded app version is %s vs base version is %s ' % (new_version, old_version))

        home.home_button.click()

        home.just_fyi("Check chat previews")
        for chat in chats.keys():
            actual_chat_preview = home.get_chat(chat).chat_preview
            expected_chat_preview = chats[chat]['preview']
            if actual_chat_preview != expected_chat_preview:
                self.errors.append('Expected preview for %s is "%s", in fact "%s"' % (chat, expected_chat_preview, actual_chat_preview))

        home.just_fyi("Check unread indicator")
        if home.home_button.counter.text != '1':
            self.errors.append('New messages counter is not shown on Home button')
        unread_one_to_one, unread_public = home.get_chat(unread_one_to_one_name), home.get_chat(unread_public_name)
        if unread_one_to_one.new_messages_counter.text != chats[unread_one_to_one_name]['unread']:
            self.errors.append('New messages counter is not shown on chat element')
        if not unread_public.new_messages_public_chat.is_element_displayed():
            self.errors.append('Unread messages badge is not shown in public chat')

        home.just_fyi("Check images / add to contacts")
        not_contact = unread_one_to_one_name
        not_contact_chat = home.get_chat(not_contact).click()
        if not not_contact_chat.add_to_contacts.is_element_displayed():
            self.errors.append('Add to contacts is not shown in 1-1 chat')
        images = not_contact_chat.image_chat_item.find_elements()
        if len(images) != 2:
            self.errors.append('%s images are shown instead of 2' % str(len(images)))
        for message in chats[not_contact]['messages']:
            if not not_contact_chat.chat_element_by_text(message).is_element_displayed():
                self.errors.append('"%s" is not shown after upgrade' % message)
        home.home_button.double_click()
        if unread_one_to_one.new_messages_counter.text == '1':
            self.errors.append('New messages counter is shown on chat element after opening chat')

        home.just_fyi("**Check public chat**")
        pub_chat_data = chats[unread_public_name]
        public_chat = home.get_chat(unread_public_name).click()
        public_chat.scroll_to_start_of_history()
        for key in pub_chat_data['preview_messages']:
            home.just_fyi("Checking %s preview case in public chat" % key)
            data =  pub_chat_data['preview_messages'][key]
            if not public_chat.element_by_text_part(data['txt']).is_element_displayed():
                public_chat.element_by_text_part(data['txt']).scroll_to_element()
            message = public_chat.get_preview_message_by_text(data['txt'])
            if not message.preview_image:
                self.errors.append('Preview message is not shown for %s' % key)
            if 'title' in data:
                if message.preview_title.text != data['title']:
                      self.errors.append("Title '%s' does not match expected" % message.preview_title.text)
                if message.preview_subtitle.text != data['subtitle']:
                      self.errors.append("Subtitle '%s' does not match expected" % message.preview_subtitle.text)
        home.home_button.click()

        home.just_fyi("Checking markdown messages in public chat")
        home.get_chat(unread_public_name).click()
        messages = list(pub_chat_data['quoted_text_messages'])
        public_chat.element_by_text(messages[0]).scroll_to_element(10, 'up')
        for i in range(len(messages)):
            if not public_chat.element_by_text(messages[i]).is_element_displayed():
                self.errors.append("Markdown message '%s' does not match expected" % messages[i])

        home.just_fyi("Checking that have uncollapse on long message")
        messages = pub_chat_data['messages']
        public_chat.element_starts_with_text(messages['long']).scroll_to_element()
        public_chat.element_by_text_part(messages['tag']).scroll_to_element()
        if not public_chat.chat_element_by_text(messages['long']).uncollapse:
            self.errors.append("No uncollapse icon on long message is shown!")

        home.just_fyi("Checking reaction, tag message and sticker")
        tag_message = public_chat.chat_element_by_text(messages['tag'])
        if tag_message.emojis_below_message(emoji='love', own=True) !=1:
            self.errors.append("Emojis are not displayed below tag message!")
        public_chat.sticker_message.scroll_to_element()
        public_chat.element_starts_with_text(messages['tag']).click()
        public_chat.history_start_icon.wait_for_visibility_of_element(20)
        if not public_chat.user_name_text.text == messages['tag']:
            self.errors.append('Could not redirect a user to a public chat tapping the tag message after upgrade')
        home.home_button.click()

        home.just_fyi("Checking reply and mention message")
        public_chat = home.get_chat(unread_public_name).click()
        public_replied_message = public_chat.chat_element_by_text(messages['reply'])
        if messages['long'] not in public_replied_message.replied_message_text:
            self.errors.append("Reply is not present in message received in public chat after upgrade")
        if not public_chat.chat_element_by_text(messages['mention']).is_element_displayed():
            self.errors.append("Mention is not present in public chat after upgrade")

        self.errors.verify_no_errors()

    @marks.testrail_id(695804)
    def test_dapps_browser_several_accounts_upgrade(self):
        sign_in = SignInView(self.driver)
        favourites = dapp_data.dapps['favourites']
        home = sign_in.import_db(user=transaction_recipients['K'], import_db_folder_name='dapps')
        home.upgrade_app()
        sign_in.sign_in()
        dapps = home.dapp_tab_button.click()

        sign_in.just_fyi('Check Dapps favourites')
        for key in favourites:
            if not dapps.element_by_text(key).is_element_displayed():
                self.errors.append('Name of bookmark "%s" is not shown in favourites!' % key)
            if not dapps.element_by_text(favourites[key]).is_element_displayed():
                self.errors.append('"%s" of bookmark is not shown in favourites!' % favourites[key])

        sign_in.just_fyi('Check dapps are still in history')
        browsing = sign_in.get_base_web_view()
        browsing.open_tabs_button.click()
        visited = dapp_data.dapps['history']['visited']
        for key in visited:
            if not dapps.element_by_text(key).is_element_displayed():
                self.errors.append('Name of tab "%s" is not shown in browser history!' % key)
            if not dapps.element_by_text(visited[key]).is_element_displayed():
                self.errors.append('"%s" of tab is not shown in browser history!' % visited[key])
        if dapps.element_by_text(dapp_data.dapps['history']['deleted']).is_element_displayed():
            self.errors.append('Closed tab is shown in browser!')

        sign_in.just_fyi('Check browser history is kept')
        github = dapp_data.dapps['browsed_page']
        dapps.element_by_text(github['name']).click()
        browsing.wait_for_d_aap_to_load()
        browsing.browser_previous_page_button.click()
        browsing.wait_for_d_aap_to_load()
        if not dapps.element_by_text(github['previous_text']).is_element_displayed():
            self.errors.append('Previous page is not opened!')

        sign_in.just_fyi('Check permissions for dapp')
        profile = dapps.profile_button.click()
        profile.privacy_and_security_button.click()
        profile.dapp_permissions_button.click()
        if profile.element_by_text_part( dapp_data.dapps['permissions']['deleted']).is_element_displayed():
            self.errors.append('Deleted permissions reappear after upgrade!')
        profile.element_by_text(test_dapp_name).click()
        permissions = dapp_data.dapps['permissions']['added'][test_dapp_name]
        for text in permissions:
            if not profile.element_by_text(text).is_element_displayed():
                self.errors.append('%s is deleted after upgrade from %s permissions' % (text, test_dapp_name))

        sign_in.just_fyi('Check that balance is preserved')
        accounts = dapp_data.wallets
        wallet = profile.wallet_button.click()
        for asset in ('ETH', 'ADI', 'STT'):
            wallet.wait_balance_is_changed(asset=asset)

        sign_in.just_fyi('Check accounts inside multiaccount')
        if not wallet.element_by_text(accounts['generated']['address']).is_element_displayed():
            self.errors.append('Address of generated account is not shown')
        generated = wallet.get_account_by_name(accounts['generated']['name'])
        if not generated.color_matches('multi_account_color.png'):
            self.errors.append('Colour of generated account does not match expected after upgrade')

        wallet.get_account_by_name(accounts['default']['name']).swipe_left_on_element()
        if not wallet.element_by_text(dapp_data.wallets['watch-only']['name']).is_element_displayed():
            self.errors.append('Watch-only account is not shown')
        if not wallet.element_by_text(accounts['watch-only']['address']).is_element_displayed():
            self.errors.append('Address of watch-only account is not shown')

        self.errors.verify_no_errors()

@marks.upgrade
class TestUpgradeMultipleApplication(MultipleDeviceTestCase):

    @marks.testrail_id(695783)
    def test_commands_audio_backward_compatibility_upgrade(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        device_2_home = device_2.create_user(previous_release=True)
        device_2_public_key = device_2_home.get_public_key_and_username()
        device_2_home.home_button.click()
        user = upgrade_users['chats']

        device_1.just_fyi("Import db, upgrade")
        home = device_1.import_db(user=user, import_db_folder_name='chats')
        home.upgrade_app()
        home = device_1.sign_in(upgraded=True)

        device_1.just_fyi("**Check messages in 1-1 chat**")
        command_username = 'Royal Defensive Solenodon'
        messages = chat_data.chats[command_username]['messages']
        chat = home.get_chat(command_username).click()
        if chat.add_to_contacts.is_element_displayed():
            self.errors.append('User is deleted from contacts after upgrade')
        chat.scroll_to_start_of_history()
        if chat.audio_message_in_chat_timer.text != messages['audio']['length']:
            self.errors.append('Timer is not shown for audiomessage')
        device_1.just_fyi('Check command messages')
        command_messages = chat_data.chats[command_username]['commands']
        for key in command_messages:
            device_1.just_fyi('Checking %s command messages' % key)
            amount = command_messages[key]['value']
            chat.element_by_text(amount).scroll_to_element()
            if 'incoming' in key:
                message = chat.get_transaction_message_by_asset(amount, incoming=True)
            else:
                message = chat.get_transaction_message_by_asset(amount, incoming=False)
            if not message.transaction_status != command_messages[key]['status']:
                self.errors.append('%s case transaction status is not equal expected after upgrade' % key)
            if key == 'outgoing_STT_sign':
                if not message.sign_and_send.is_element_displayed():
                     self.errors.append('No "sign and send" option is shown for %s' % key)
        chat.home_button.click()

        #TODO: blocked until resolving importing unread messages to Activity centre
        # device_1.just_fyi("Check messages in Activity centre")
        device_2.just_fyi("Create upgraded and non-upgraded app can exchange messages")
        message, response = "message after upgrade", "response"
        device_1_chat = home.add_contact(device_2_public_key)
        device_1_chat.send_message(message)
        device_2_chat = device_2_home.get_chat(user['username']).click()
        if not device_2_chat.chat_element_by_text(message).is_element_displayed():
            self.errors.append("Message sent from upgraded app is not shown on  previous release!")
        device_2_chat.send_message(response)
        if not device_1_chat.chat_element_by_text(response).is_element_displayed():
            self.errors.append("Message sent from previous release is not shown on upgraded app!")

        self.errors.verify_no_errors()

    @marks.testrail_id(695805)
    def test_devices_sync_contact_management_upgrade(self):
        self.create_drivers(2)
        user = transaction_recipients['K']
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])

        device_1.just_fyi("Import db, upgrade")
        home_1 = device_1.import_db(user=user, import_db_folder_name='pairing/main')
        home_2 = device_2.import_db(user=user, import_db_folder_name='pairing/secondary')
        for device in (device_1, device_2):
            device.upgrade_app()
            device.sign_in()

        device_1.just_fyi("Contacts: check blocked and removed contacts, contacts with ENS")
        if home_1.element_by_text(sync_data.chats['deleted']).is_element_displayed():
            self.error.append("Removed public chat reappears after upgrade!")
        profile_1 = home_1.profile_button.click()
        profile_1.contacts_button.click()
        synced = sync_data.contacts['synced']
        for username in list(synced.values()):
            if not profile_1.element_by_text(username).is_element_displayed():
                self.error.append("'%s' is not shown in contacts list after upgrade!" % username)
        if profile_1.element_by_text_part(sync_data.contacts['removed']).is_element_displayed():
            self.error.append("Removed user is shown in contacts list after upgrade!")
        profile_1.blocked_users_button.click()
        if not profile_1.element_by_text_part(sync_data.contacts['blocked']).is_element_displayed():
            self.error.append("Blocked user is not shown in contacts list after upgrade!")

        device_2.just_fyi("Pairing: check synced public chats on secondary device")
        for chat in sync_data.chats['synced_public']:
            if not home_2.element_by_text(chat).is_element_displayed():
                self.error.append("Synced public chat '%s' is not shown on secondary device after upgrade!" % chat)

        device_1.just_fyi("Pairing: check that can send messages to chats and they will appear on secondary device")
        main_1_1, secondary_1_1, group = synced['ens'], synced['username_ens'], sync_data.chats['group']
        message = 'Device pairing check'
        device_1.home_button.click()
        chat_1 = home_1.get_chat(main_1_1).click()
        chat_1.send_message(message)
        home_2.get_chat(secondary_1_1).wait_for_visibility_of_element()
        chat_2 = home_2.get_chat(secondary_1_1).click()
        if not chat_2.chat_element_by_text(message).is_element_displayed():
            self.error.append("Message in 1-1 chat does not appear on device 2 after sending from main device after upgrade")
        [chat.home_button.click() for chat in (chat_1, chat_2)]
        chat_1 = home_1.get_chat(group).click()
        chat_1.send_message(message)
        home_2.get_chat(group).wait_for_visibility_of_element()
        chat_2 = home_2.get_chat(group).click()
        if not chat_2.chat_element_by_text(message).is_element_displayed():
            self.error.append("Message in group chat does not appear on device 2 after sending from main device after upgrade")
        [chat.home_button.click() for chat in (chat_1, chat_2)]

        device_1.just_fyi("Pairing: add public chat and check it will appear on secondary device")
        public = sync_data.chats['added_public']
        chat_1 = home_1.join_public_chat(public[1:])
        chat_1.send_message(message)
        home_2.get_chat(public).wait_for_visibility_of_element()
        chat_2 = home_2.get_chat(public).click()
        if not chat_2.chat_element_by_text(message).is_element_displayed():
            self.error.append(
                "Message in public chat does not appear on device 2 after sending from main device after upgrade")
        [chat.home_button.click() for chat in (chat_1, chat_2)]

        device_1.just_fyi("Pairing: add contact and check that it will appear on secondary device")
        added = sync_data.contacts['added']
        chat_1 = home_1.add_contact(added['public_key'], nickname=added['name'])
        chat_1.send_message(message)
        home_2.get_chat(added['name']).wait_for_visibility_of_element()
        chat_2 = home_2.get_chat(added['name']).click()
        if not chat_2.chat_element_by_text(message).is_element_displayed():
            self.error.append(
                "Message in new 1-1 chat does not appear on device 2 after sending from main device after upgrade")

        device_2.just_fyi("Pairing: check that contacts/nicknames are synced")
        synced_secondary = {synced['nickname'], synced['username_nickname'], synced['username_ens'], added['name'], added['username']}
        profile_2 = chat_2.profile_button.click()
        profile_2.contacts_button.click()
        for username in synced_secondary:
            if not profile_2.element_by_text(username).is_element_displayed():
                self.error.append("'%s' is not shown in contacts list on synced device after upgrade!" % username)

        self.errors.verify_no_errors()


import re

from tests import marks, bootnode_address, mailserver_address, test_dapp_url, test_dapp_name, mailserver_ams, \
    mailserver_gc, mailserver_hk, used_fleet, common_password
from tests.base_test_case import SingleDeviceTestCase, MultipleDeviceTestCase
from tests.users import transaction_senders, basic_user, ens_user, ens_user_ropsten
from views.sign_in_view import SignInView
from time import time


class TestProfileSingleDevice(SingleDeviceTestCase):

    @marks.testrail_id(6318)
    @marks.medium
    def test_can_delete_several_multiaccounts(self):
        sign_in = SignInView(self.driver)
        sign_in.create_user()
        delete_alert_warning = sign_in.get_translation_by_key("delete-profile-warning")
        profile = sign_in.profile_button.click()
        profile.logout()
        if sign_in.ok_button.is_element_displayed():
            sign_in.ok_button.click()
        sign_in.your_keys_more_icon.click()
        sign_in.generate_new_key_button.click()
        sign_in.generate_key_button.click()
        sign_in.next_button.click()
        sign_in.next_button.click()
        sign_in.create_password_input.set_value(common_password)
        sign_in.next_button.click()
        sign_in.confirm_your_password_input.set_value(common_password)
        sign_in.next_button.click()
        sign_in.maybe_later_button.click_until_presence_of_element(sign_in.lets_go_button)
        sign_in.lets_go_button.click()
        sign_in.no_thanks_metric_button.click()

        sign_in.just_fyi('Delete 2nd multiaccount')
        public_key, username = sign_in.get_public_key_and_username(return_username=True)
        profile.privacy_and_security_button.click()
        profile.delete_my_profile_button.scroll_and_click()
        for text in (username, delete_alert_warning):
            if not profile.element_by_text(text).is_element_displayed():
                self.errors.append('Required %s is not shown when deleting multiaccount' % text)
        profile.delete_profile_button.click()
        if profile.element_by_translation_id("profile-deleted-title").is_element_displayed():
            self.driver.fail('Profile is deleted without confirmation with password')
        profile.delete_my_profile_password_input.set_value(common_password)
        profile.delete_profile_button.click_until_presence_of_element(profile.element_by_translation_id("profile-deleted-title"))
        profile.ok_button.click()

        sign_in.just_fyi('Delete last multiaccount')
        sign_in.sign_in()
        sign_in.profile_button.click()
        profile.privacy_and_security_button.click()
        profile.delete_my_profile_button.scroll_and_click()
        profile.delete_my_profile_password_input.set_value(common_password)
        profile.delete_profile_button.click()
        profile.ok_button.click()
        if not sign_in.get_started_button.is_element_displayed(20):
            self.errors.append('No redirected to carousel view after deleting last multiaccount')
        self.errors.verify_no_errors()

    @marks.testrail_id(5323)
    @marks.critical
    def test_share_copy_contact_code_and_wallet_address(self):
        home = SignInView(self.driver).create_user()
        profile = home.profile_button.click()

        home.just_fyi("Copying contact code")
        profile.share_my_profile_button.click()
        public_key = profile.public_key_text.text
        profile.public_key_text.long_press_element()
        profile.copy_text()

        home.just_fyi("Sharing contact code via messenger")
        profile.share_button.click()
        profile.share_via_messenger()
        if not profile.element_by_text_part(public_key).is_element_present():
            self.errors.append("Can't share public key")
        [profile.click_system_back_button() for _ in range(2)]
        profile.close_share_popup()

        home.just_fyi("Check that can paste contact code in chat message input")
        home = profile.home_button.click()
        chat = home.add_contact(transaction_senders['M']['public_key'])
        chat.chat_message_input.click()
        chat.paste_text()
        input_text = chat.chat_message_input.text
        if input_text not in public_key or len(input_text) < 1:
            self.errors.append('Public key was not copied')
        chat.chat_message_input.clear()
        chat.get_back_to_home_view()

        home.just_fyi("Copying wallet address")
        wallet = profile.wallet_button.click()
        wallet.set_up_wallet()
        wallet.accounts_status_account.click()
        request = wallet.receive_transaction_button.click()
        address = wallet.address_text.text
        request.share_button.click()
        request.element_by_translation_id("sharing-copy-to-clipboard").click()

        home.just_fyi("Sharing wallet address via messenger")
        request.share_button.click()
        wallet.share_via_messenger()
        if not wallet.element_by_text_part(address).is_element_present():
            self.errors.append("Can't share address")
        [wallet.click_system_back_button() for _ in range(2)]
        wallet.close_share_popup()

        home.just_fyi("Check that can paste wallet address in chat message input")
        wallet.home_button.click()
        home.get_chat(transaction_senders['M']['username']).click()
        chat.chat_message_input.click()
        chat.paste_text()
        if chat.chat_message_input.text != address:
            self.errors.append('Wallet address was not copied')
        self.errors.verify_no_errors()

    @marks.testrail_id(5502)
    @marks.critical
    def test_can_add_existing_ens(self):
        home = SignInView(self.driver).recover_access(ens_user['passphrase'])
        profile = home.profile_button.click()
        profile.switch_network('Mainnet with upstream RPC')
        home.profile_button.click()
        dapp_view = profile.ens_usernames_button.click()

        dapp_view.just_fyi('check if your name can be added via "ENS usernames" in Profile')
        dapp_view.element_by_text('Get started').click()
        dapp_view.ens_name_input.set_value(ens_user['ens'])
        dapp_view.check_ens_name.click_until_absense_of_element(dapp_view.check_ens_name)
        if not dapp_view.element_by_translation_id('ens-saved-title').is_element_displayed():
            self.errors.append('No message "Username added" after resolving own username')
        dapp_view.element_by_translation_id("ens-got-it").click()

        dapp_view.just_fyi('check that after adding username is shown in "ENS usernames" and profile')
        if not dapp_view.element_by_text(ens_user['ens']).is_element_displayed():
            self.errors.append('No ENS name is shown in own "ENS usernames" after adding')
        dapp_view.back_button.click()
        if not dapp_view.element_by_text('@%s' % ens_user['ens']).is_element_displayed():
            self.errors.append('No ENS name is shown in own profile after adding')
        if not dapp_view.element_by_text('%s.stateofus.eth' % ens_user['ens']).is_element_displayed():
            self.errors.append('No ENS name is shown in own profile after adding')
        profile.share_my_profile_button.click()
        if profile.ens_name_in_share_chat_key_text.text != '%s.stateofus.eth' % ens_user['ens']:
            self.errors.append('No ENS name is shown on tapping on share icon in Profile')
        profile.close_share_popup()

        self.errors.verify_no_errors()

    @marks.testrail_id(6296)
    @marks.high
    def test_recover_account_from_new_user_seedphrase(self):
        sign_in_view = SignInView(self.driver)
        sign_in_view.create_user()
        profile_view = sign_in_view.profile_button.click()
        profile_view.privacy_and_security_button.click()
        profile_view.backup_recovery_phrase_button.click()
        profile_view.ok_continue_button.click()
        recovery_phrase = " ".join(profile_view.get_recovery_phrase().values())
        profile_view.back_button.click()
        profile_view.back_button.click()
        public_key = profile_view.get_public_key_and_username()
        wallet_view = profile_view.wallet_button.click()
        wallet_view.set_up_wallet()
        address = wallet_view.get_wallet_address()
        sign_in_view.profile_button.click()
        profile_view.logout()
        self.driver.reset()
        sign_in_view.recover_access(recovery_phrase)
        wallet_view = sign_in_view.wallet_button.click()
        wallet_view.set_up_wallet()
        if wallet_view.get_wallet_address() != address:
            self.driver.fail("Seed phrase displayed in new accounts for back up does not recover respective address")
        profile_view = wallet_view.profile_button.click()
        if profile_view.get_public_key_and_username() != public_key:
            self.driver.fail("Seed phrase displayed in new accounts for back up does not recover respective public key")

    @marks.testrail_id(5433)
    @marks.medium
    def test_invite_friends(self):
        home = SignInView(self.driver).create_user()

        self.driver.info("Check it via 'Invite friends' on home view")
        home.invite_friends_button.click()
        home.share_via_messenger()
        home.element_by_text_part("Hey join me on Status: https://join.status.im/u/0x")
        home.click_system_back_button()

        self.driver.info("Check it via bottom sheet menu")
        home.plus_button.click()
        home.chats_menu_invite_friends_button.click()
        home.share_via_messenger()
        home.element_by_text_part("Hey join me on Status: https://join.status.im/u/0x")

    @marks.testrail_id(6312)
    @marks.medium
    def test_add_remove_contact_via_contacts_view(self):
        home = SignInView(self.driver).create_user()

        home.just_fyi('Check empty contacts view')
        profile = home.profile_button.click()
        profile.switch_network()
        home.profile_button.click()
        profile.contacts_button.click()
        if not profile.add_new_contact_button.is_element_displayed():
            self.driver.fail('No expected element on contacts view')

        users = {
            'scanning_ens_with_stateofus_domain_deep_link': {
                'contact_code': 'https://join.status.im/u/%s.stateofus.eth' % ens_user_ropsten['ens'],
                'username': ens_user_ropsten['username']
             },
            'scanning_public_key': {
                'contact_code': transaction_senders['A']['public_key'],
                'username': transaction_senders['A']['username'],
            },
            'pasting_public_key': {
                'contact_code': basic_user['public_key'],
                'username': basic_user['username'],
            },
            'pasting_ens_another_domain': {
                'contact_code': ens_user['ens_another_domain'],
                'username': '@%s' % ens_user['ens_another_domain'],
                'nickname': 'my_dear_friend'
            },

        }

        home.just_fyi('Add contact  and check that they appear in Contacts view')
        chat_view = home.get_chat_view()
        for key in users:
            profile.plus_button.click()
            home.just_fyi('Checking %s case' % key)
            if 'scanning' in key:
                chat_view.scan_contact_code_button.click()
                if chat_view.allow_button.is_element_displayed():
                    chat_view.allow_button.click()
                chat_view.enter_qr_edit_box.scan_qr(users[key]['contact_code'])
            else:
                chat_view.public_key_edit_box.click()
                chat_view.public_key_edit_box.send_keys(users[key]['contact_code'])
                if 'nickname' in users[key]:
                    chat_view.nickname_input_field.set_value(users[key]['nickname'])
                chat_view.confirm_until_presence_of_element(profile.contacts_button)
            if not profile.element_by_text(users[key]['username']).is_element_displayed():
                self.errors.append('In %s case username not found in contact view after scanning' % key)
            if 'nickname' in users[key]:
                if not profile.element_by_text(users[key]['nickname']).is_element_displayed():
                    self.errors.append('In %s case nickname %s not found in contact view after scanning' % (key, users[key]['nickname']))

        home.just_fyi('Remove contact and check that it disappeared')
        user_to_remove = '@%s' % ens_user['ens_another_domain']
        profile.element_by_text(user_to_remove).click()
        chat_view.remove_from_contacts.click()
        chat_view.back_button.click()
        if profile.element_by_text(user_to_remove).is_element_displayed():
            self.errors.append('Removed user is still shown in contact view')
        self.errors.verify_no_errors()

    @marks.testrail_id(5431)
    @marks.medium
    def test_add_custom_network(self):
        sign_in = SignInView(self.driver)
        sign_in.create_user()
        profile = sign_in.profile_button.click()
        profile.add_custom_network()
        sign_in.sign_in()
        sign_in.profile_button.click()
        profile.advanced_button.click()
        profile.network_settings_button.scroll_to_element(10, 'up')
        if not profile.element_by_text_part('custom_ropsten').is_element_displayed():
            self.driver.fail("Network custom_ropsten was not added!")

    @marks.testrail_id(6239)
    @marks.medium
    def test_backup_recovery_phrase(self):
        sign_in_view = SignInView(self.driver)
        sign_in_view.create_user()
        if sign_in_view.profile_button.counter.text != '1':
            self.errors.append('Profile button counter is not shown')
        profile_view = sign_in_view.profile_button.click()
        profile_view.logout()
        sign_in_view.sign_in()
        if sign_in_view.profile_button.counter.text != '1':
            self.errors.append('Profile button counter is not shown after re-login')
        sign_in_view.profile_button.click()
        profile_view.privacy_and_security_button.click()
        profile_view.backup_recovery_phrase_button.click()
        recovery_phrase = profile_view.backup_recovery_phrase()
        if sign_in_view.profile_button.counter.is_element_displayed():
            self.errors.append('Profile button counter is shown after recovery phrase backup')
        profile_view.backup_recovery_phrase_button.click()
        if not profile_view.backup_recovery_phrase_button.is_element_displayed():
            self.driver.fail('Back up seed phrase option is available after seed phrase backed up!')
        profile_view.back_button.click()
        profile_view.logout()
        sign_in_view.access_key_button.click()
        sign_in_view.enter_seed_phrase_button.click()
        sign_in_view.seedphrase_input.click()
        sign_in_view.seedphrase_input.set_value(' '.join(recovery_phrase.values()))
        sign_in_view.next_button.click()
        sign_in_view.element_by_translation_id(id="unlock", uppercase=True).click()
        sign_in_view.password_input.set_value(common_password)
        chats_view = sign_in_view.sign_in_button.click()
        chats_view.plus_button.click()
        if not chats_view.start_new_chat_button.is_element_displayed():
            self.errors.append("Can't proceed using account after it's re-recover twice.")
        self.errors.verify_no_errors()

    @marks.critical
    @marks.testrail_id(5419)
    @marks.flaky
    def test_logcat_backup_recovery_phrase(self):
        sign_in = SignInView(self.driver)
        home = sign_in.create_user()

        home.just_fyi("Check that badge on profile about back up seed phrase is presented")
        if home.profile_button.counter.text != '1':
            self.errors.append('Profile button counter is not shown')

        home.just_fyi("Back up seed phrase and check logcat")
        profile = home.profile_button.click()
        profile.privacy_and_security_button.click()
        profile.backup_recovery_phrase_button.click()
        profile.ok_continue_button.click()
        recovery_phrase = profile.get_recovery_phrase()
        profile.next_button.click()
        word_number = profile.recovery_phrase_word_number.number
        profile.recovery_phrase_word_input.set_value(recovery_phrase[word_number])
        profile.next_button.click()
        word_number_1 = profile.recovery_phrase_word_number.number
        profile.recovery_phrase_word_input.set_value(recovery_phrase[word_number_1])
        profile.done_button.click()
        profile.yes_button.click()
        profile.ok_got_it_button.click()
        if home.profile_button.counter.is_element_displayed():
            self.errors.append('Profile button counter is shown after recovery phrase backup')
        values_in_logcat = profile.find_values_in_logcat(passphrase1=recovery_phrase[word_number],
                                               passphrase2=recovery_phrase[word_number_1])
        if len(values_in_logcat) == 2:
            self.driver.fail(values_in_logcat)
        profile.profile_button.double_click()

        home.just_fyi("Try to restore same account from seed phrase (should be possible only to unlock existing account)")
        profile.logout()
        sign_in.access_key_button.click()
        sign_in.enter_seed_phrase_button.click()
        sign_in.seedphrase_input.click()
        sign_in.seedphrase_input.set_value(' '.join(recovery_phrase.values()))
        sign_in.next_button.click()
        sign_in.element_by_translation_id(id="unlock", uppercase=True).click()
        sign_in.password_input.set_value(common_password)
        chat = sign_in.sign_in_button.click()
        chat.plus_button.click()
        if not chat.start_new_chat_button.is_element_displayed():
            self.errors.append("Can't proceed using account after it's re-recover twice.")
        self.errors.verify_no_errors()

    @marks.testrail_id(5453)
    @marks.medium
    def test_privacy_policy_node_version_need_help_in_profile(self):
        signin = SignInView(self.driver)
        no_link_found_error_msg = 'Could not find privacy policy link at'
        no_link_open_error_msg = 'Could not open our privacy policy from'

        signin.just_fyi("Checking provacy policy from sign in and from profile")
        if not signin.privacy_policy_link.is_element_displayed():
            self.driver.fail('%s Sign in view!' % no_link_found_error_msg)
        web_page = signin.privacy_policy_link.click()
        web_page.open_in_webview()
        if not web_page.policy_summary.is_element_displayed():
            self.errors.append('%s Sign in view!' % no_link_open_error_msg)
        web_page.click_system_back_button()
        home = signin.create_user()
        profile = home.profile_button.click()
        profile.about_button.click()
        profile.privacy_policy_button.click()
        if not web_page.policy_summary.is_element_displayed():
            self.errors.append('%s Profile about view!' % no_link_open_error_msg)
        web_page.click_system_back_button()

        signin.just_fyi("Checking that version match expected format and can be copied")
        app_version = profile.app_version_text.text
        node_version = profile.node_version_text.text
        if not re.search(r'\d{1}[.]\d{1,2}[.]\d{1,2}\s[(]\d*[)]', app_version):
            self.errors.append("App version %s didn't match expected format" % app_version)
        if not re.search(r'StatusIM\/v.*\/android-\d{3}\/go\d{1}[.]\d{1,}', node_version):
            self.errors.append("Node version %s didn't match expected format" % node_version)
        profile.app_version_text.click()
        profile.back_button.click()
        profile.home_button.click()
        chat = home.join_public_chat(home.get_random_chat_name())
        message_input = chat.chat_message_input
        message_input.paste_text_from_clipboard()
        if message_input.text != app_version:
            self.errors.append('Version number was not copied to clipboard')

        signin.just_fyi("Checking Need help section")
        home.profile_button.double_click()
        profile.help_button.click()
        web_page = profile.faq_button.click()
        web_page.open_in_webview()
        web_page.wait_for_d_aap_to_load()
        if not profile.element_by_text_part("F.A.Q").is_element_displayed():
            self.errors.append("FAQ is not shown")
        profile.click_system_back_button()
        profile.submit_bug_button.click()
        if not profile.element_by_text_part("Welcome to Gmail").is_element_displayed():
            self.errors.append("Mail client is not opened when submitting bug")
        profile.click_system_back_button()
        profile.request_a_feature_button.click()
        if  not profile.element_by_text("#support").is_element_displayed():
            self.errors.append("Support channel is not suggested for requesting a feature")
        self.errors.verify_no_errors()

    @marks.testrail_id(5738)
    @marks.high
    def test_dapps_permissions(self):
        home = SignInView(self.driver).create_user()
        account_name = home.status_account_name

        home.just_fyi('open Status Test Dapp, allow all and check permissions in Profile')
        home.open_status_test_dapp()
        home.dapp_tab_button.click()
        profile = home.profile_button.click()
        profile.privacy_and_security_button.click()
        profile.dapp_permissions_button.click()
        profile.element_by_text(test_dapp_name).click()
        if not profile.element_by_text(account_name).is_element_displayed():
            self.errors.append('Wallet permission was not granted')
        if not profile.element_by_translation_id("chat-key").is_element_displayed():
            self.errors.append('Contact code permission was not granted')

        profile.just_fyi('revoke access and check that they are asked second time')
        profile.revoke_access_button.click()
        profile.back_button.click()
        dapp_view = profile.dapp_tab_button.click()
        dapp_view.open_url(test_dapp_url)
        if not dapp_view.element_by_text_part(account_name).is_element_displayed():
            self.errors.append('Wallet permission is not asked')
        if dapp_view.allow_button.is_element_displayed():
            dapp_view.allow_button.click(times_to_click=1)
        if not dapp_view.element_by_translation_id("your-contact-code").is_element_displayed():
            self.errors.append('Profile permission is not asked')
        self.errors.verify_no_errors()

    @marks.testrail_id(5368)
    @marks.medium
    def test_change_log_level_and_fleet(self):
        home = SignInView(self.driver).create_user()
        profile = home.profile_button.click()
        profile.advanced_button.click()
        default_log_level = 'INFO'
        for text in default_log_level, used_fleet:
            if not profile.element_by_text(text).is_element_displayed():
                self.errors.append('%s is not selected by default' % text)
        if home.find_values_in_geth('lvl=trce', 'lvl=dbug'):
            self.errors.append('"%s" is set, but found another entries!' % default_log_level)
        if not home.find_values_in_geth('lvl=info'):
            self.errors.append('"%s" is set, but no entries are found!' % default_log_level)

        home.just_fyi('Set another loglevel and check that changes are applied')
        profile.log_level_setting_button.click()
        changed_log_level = 'TRACE'
        profile.element_by_text(changed_log_level).click_until_presence_of_element(profile.confirm_button)
        profile.confirm_button.click()
        SignInView(self.driver).sign_in()
        home.profile_button.click()
        profile.advanced_button.click()
        if not profile.element_by_text(changed_log_level).is_element_displayed():
            self.errors.append('"%s" is not selected after change' % changed_log_level)
        if not home.find_values_in_geth('lvl=trc'):
            self.errors.append('"%s" is set, but no entries are found!' % changed_log_level)

        home.just_fyi('Set another fleet and check that changes are applied')
        profile.fleet_setting_button.click()
        changed_fleet = 'eth.prod'
        profile.element_by_text(changed_fleet).click_until_presence_of_element(profile.confirm_button)
        profile.confirm_button.click()
        SignInView(self.driver).sign_in()
        home.profile_button.click()
        profile.advanced_button.click()
        if not profile.element_by_text(changed_fleet).is_element_displayed():
            self.errors.append('"%s" fleet is not selected after change' % changed_fleet)
        if not home.find_values_in_geth(changed_fleet):
            self.errors.append('"%s" is set, but no entry is found!' % changed_fleet)

        self.errors.verify_no_errors()

    @marks.testrail_id(5766)
    @marks.medium
    @marks.flaky
    def test_use_pinned_mailserver(self):
        home = SignInView(self.driver).create_user()
        profile = home.profile_button.click()

        profile.just_fyi('pin history node')
        profile.sync_settings_button.click()
        node_gc, node_ams, node_hk = [profile.return_mailserver_name(history_node_name, used_fleet) for history_node_name in (mailserver_gc, mailserver_ams, mailserver_hk)]
        h_node = node_ams
        profile.mail_server_button.click()
        profile.mail_server_auto_selection_button.click()
        profile.mail_server_by_name(h_node).click()
        profile.confirm_button.click()
        if profile.element_by_translation_id("mailserver-error-title").is_element_displayed(10):
            h_node = node_hk
            profile.element_by_translation_id("mailserver-pick-another", uppercase=True).click()
            profile.mail_server_by_name(h_node).click()
            profile.confirm_button.click()
            if profile.element_by_translation_id("mailserver-error-title").is_element_displayed(10):
                self.driver.fail("Couldn't connect to any history node")

        profile.just_fyi('check that history node is pinned')
        profile.back_button.click()
        if not profile.element_by_text(h_node).is_element_displayed():
            self.errors.append('"%s" history node is not pinned' % h_node)
        profile.get_back_to_home_view()

        profile.just_fyi('Relogin and check that settings are preserved')
        home.relogin()
        home.profile_button.click()
        profile.sync_settings_button.click()
        if not profile.element_by_text(h_node).is_element_displayed():
            self.errors.append('"%s" history node is not pinned' % h_node)

        self.errors.verify_no_errors()

    @marks.testrail_id(6219)
    @marks.medium
    def test_set_primary_ens_custom_domain(self):
        home = SignInView(self.driver).recover_access(ens_user['passphrase'])
        ens_not_stateofus = ens_user['ens_another_domain']
        ens_stateofus = ens_user['ens']

        home.just_fyi('add 2 ENS names in Profile')
        profile = home.profile_button.click()
        dapp = profile.connect_existing_status_ens(ens_stateofus)
        profile.element_by_text("Add username").click()
        profile.element_by_text_part("another domain").click()
        dapp.ens_name_input.set_value(ens_not_stateofus)
        dapp.check_ens_name.click_until_presence_of_element(dapp.element_by_translation_id("ens-got-it"))
        dapp.element_by_translation_id("ens-got-it").click()

        home.just_fyi('check that by default %s ENS is set' % ens_stateofus)
        dapp.element_by_text('Primary username').click()
        message_to_check = 'Your messages are displayed to others with'
        if not dapp.element_by_text('%s\n@%s.stateofus.eth' % (message_to_check, ens_stateofus)).is_element_displayed():
             self.errors.append('%s ENS username is not set as primary by default' % ens_stateofus)

        home.just_fyi('check view in chat settings ENS from other domain: %s after set new primary ENS' % ens_not_stateofus)
        dapp.set_primary_ens_username(ens_user['ens_another_domain']).click()
        if profile.username_in_ens_chat_settings_text.text != '@' + ens_not_stateofus:
            self.errors.append('ENS username %s is not shown in ENS username Chat Settings after enabling' % ens_not_stateofus)

        self.errors.verify_no_errors()


    @marks.testrail_id(5468)
    @marks.medium
    @marks.skip
    # TODO: skip until profile picture change feature is enabled
    def test_deny_camera_access_changing_profile_photo(self):
        sign_in = SignInView(self.driver)
        sign_in.create_user()
        profile = sign_in.profile_button.click()
        profile.profile_picture.click()
        profile.capture_button.click()
        for _ in range(2):
            profile.deny_button.click()
        profile.element_by_translation_id("camera-access-error").wait_for_visibility_of_element(3)
        profile.ok_button.click()
        profile.profile_picture.click()
        profile.capture_button.click()
        profile.deny_button.wait_for_visibility_of_element(2)

    @marks.testrail_id(5469)
    @marks.medium
    @marks.skip
    # TODO: skip until profile picture change feature is enabled
    def test_deny_device_storage_access_changing_profile_photo(self):
        sign_in = SignInView(self.driver)
        sign_in.create_user()
        profile = sign_in.profile_button.click()
        profile.profile_picture.click()
        profile.select_from_gallery_button.click()
        profile.deny_button.click()
        profile.element_by_translation_id(id="external-storage-denied", element_type='text').wait_for_visibility_of_element(3)
        profile.ok_button.click()
        profile.profile_picture.click()
        profile.select_from_gallery_button.click()
        profile.deny_button.wait_for_visibility_of_element(2)


class TestProfileMultipleDevice(MultipleDeviceTestCase):

    @marks.testrail_id(6646)
    @marks.high
    def test_set_profile_picture(self):
        self.create_drivers(2)
        home_1, home_2 = SignInView(self.drivers[0]).create_user(), SignInView(self.drivers[1]).create_user()
        profile_1 = home_1.profile_button.click()
        public_key_1 = profile_1.get_public_key_and_username()

        profile_1.just_fyi("Set user Profile image from Gallery")
        profile_1.edit_profile_picture(file_name='sauce_logo.png')
        home_1.profile_button.click()
        profile_1.swipe_down()

        if not profile_1.profile_picture.is_element_image_similar_to_template('sauce_logo_profile.png'):
            self.drivers[0].fail('Profile picture was not updated')

        profile_1.just_fyi("Check user profile updated in chat")
        home = profile_1.home_button.click()
        message = "Text message"
        public_chat_name = home.get_random_chat_name()
        home_2.add_contact(public_key=public_key_1)
        home_2.home_button.click()
        public_chat_2 = home_2.join_public_chat(public_chat_name)
        public_chat_1 = home.join_public_chat(public_chat_name)
        public_chat_1.chat_message_input.send_keys(message)
        public_chat_1.send_message_button.click()
        if not public_chat_2.chat_element_by_text(message).member_photo.is_element_image_similar_to_template('sauce_logo.png'):
            self.drivers[0].fail('Profile picture was not updated in chat')

        profile_1.just_fyi("Set user Profile image by taking Photo")
        home_1.profile_button.click()
        profile_1.edit_profile_picture(file_name='sauce_logo.png', update_by='Make Photo')
        home_1.home_button.click(desired_view='chat')
        public_chat_1.chat_message_input.send_keys(message)
        public_chat_1.send_message_button.click()

        if public_chat_2.chat_element_by_text(message).member_photo.is_element_image_similar_to_template('sauce_logo.png'):
            self.drivers[0].fail('Profile picture was not updated in chat after making photo')

    @marks.testrail_id(6636)
    @marks.medium
    def test_show_profile_picture_of_setting(self):
        self.create_drivers(2)
        home_1, home_2 = SignInView(self.drivers[0]).create_user(), SignInView(self.drivers[1]).create_user()
        profile_1 = home_1.profile_button.click()
        public_key_1, default_username_1 = profile_1.get_public_key_and_username(return_username=True)

        profile_1.just_fyi("Set user Profile image from Gallery")
        profile_1.edit_profile_picture(file_name='sauce_logo.png')
        home_1.profile_button.click()
        profile_1.swipe_down()

        profile_1.just_fyi('set status in profile')
        device_1_status = 'My new update!'
        timeline = profile_1.status_button.click()
        timeline.set_new_status(device_1_status)
        if not timeline.timeline_own_account_photo.is_element_image_similar_to_template('sauce_logo.png'):
            self.errors.append('Profile picture was not updated in timeline')

        profile_1.just_fyi('Check profile image it is not in mentions because user not in contacts yet')
        one_to_one_chat_2 = home_2.add_contact(public_key_1, add_in_contacts=False)
        one_to_one_chat_2.chat_message_input.set_value('@' + default_username_1)
        one_to_one_chat_2.chat_message_input.click()
        if one_to_one_chat_2.user_profile_image_in_mentions_list(default_username_1).is_element_image_similar_to_template('sauce_logo.png'):
            self.errors.append('Profile picture is updated in 1-1 chat mentions list of contact not in Contacts list')

        profile_1.just_fyi('Check profile image is in mentions because now user was added in contacts')
        one_to_one_chat_2.add_to_contacts.click()
        one_to_one_chat_2.chat_message_input.set_value('@' + default_username_1)
        one_to_one_chat_2.chat_message_input.click()
        if not one_to_one_chat_2.user_profile_image_in_mentions_list(default_username_1).is_element_image_similar_to_template('sauce_logo.png'):
            self.errors.append('Profile picture was not updated in 1-1 chat mentions list')

        profile_1.just_fyi('Check profile image updated in user profile view and on Chats view')
        profile_2 = one_to_one_chat_2.profile_button.click()
        profile_2.contacts_button.click()
        profile_2.element_by_text(default_username_1).click()
        if not profile_2.profile_picture.is_element_image_similar_to_template('sauce_logo.png'):
            self.errors.append('Profile picture was not updated on user Profile view')
        profile_2.back_button.click()
        one_to_one_chat_2.home_button.click(desired_view='home')
        if not home_2.get_chat(default_username_1).chat_image.is_element_image_similar_to_template('sauce_logo.png'):
            self.errors.append('User profile picture was not updated on Chats view')

        profile_1.just_fyi('Check profile image updated in user profile view in Group chat views 4')
        home_1.home_button.click(desired_view='home')
        group_chat_message = 'Trololo'
        group_chat_2 = home_2.create_group_chat(user_names_to_add=[default_username_1])
        group_chat_2.send_message('Message')
        group_chat_1 = home_1.get_chat('new_group_chat').click()
        group_chat_1.join_chat_button.click()
        group_chat_1.send_message(group_chat_message)
        if not group_chat_2.chat_element_by_text(group_chat_message).member_photo.is_element_image_similar_to_template('sauce_logo.png'):
            self.errors.append('User profile picture was not updated in message Group chat view')

        profile_1.just_fyi('Check profile image updated in on login view')
        home_1.profile_button.click()
        profile_1.logout()
        sign_in_1 = home_1.get_sign_in_view()
        if not sign_in_1.get_multiaccount_by_position(1).account_logo.is_element_image_similar_to_template('sauce_logo.png'):
            self.errors.append('User profile picture was not updated on Multiaccounts list select login view')
        sign_in_1.element_by_text(default_username_1).click()
        if not sign_in_1.get_multiaccount_by_position(1).account_logo.is_element_image_similar_to_template('sauce_logo.png'):
            self.errors.append('User profile picture was not updated on account login view')
        sign_in_1.password_input.set_value(common_password)
        sign_in_1.sign_in_button.click()

        profile_1.just_fyi('Remove user from contact and check there is no profile image displayed')
        group_chat_2.profile_button.click()
        profile_2.contacts_button.click()
        profile_2.element_by_text(default_username_1).click()
        one_to_one_chat_2.remove_from_contacts.click()
        # Send message to User 2 so update of profile image picked up
        group_chat_1 = home_1.get_chat('new_group_chat').click()
        group_chat_1.send_message(group_chat_message)
        one_to_one_chat_2.back_button.click()
        one_to_one_chat_2.home_button.click(desired_view='home')
        if home_2.get_chat(default_username_1).chat_image.is_element_image_similar_to_template('sauce_logo.png'):
            self.errors.append('User profile picture is not default to default after user removed from Contacts')

        profile_2.just_fyi('Enable to see profile image from "Everyone" setting')
        home_2.profile_button.click()
        profile_2.appearance_button.click()
        profile_2.show_profile_pictures_of.click()
        profile_2.element_by_text('Everyone').click()
        group_chat_1.send_message(group_chat_message)
        profile_2.home_button.click(desired_view='home')
        if not home_2.get_chat(default_username_1).chat_image.is_element_image_similar_to_template('sauce_logo.png'):
            self.errors.append('User profile picture is not returned to default after user removed from Contacts')

    @marks.testrail_id(5432)
    @marks.medium
    def test_custom_bootnodes(self):
        self.create_drivers(2)
        home_1, home_2 = SignInView(self.drivers[0]).create_user(), SignInView(self.drivers[1]).create_user()
        public_key = home_2.get_public_key_and_username()

        profile_1, profile_2 = home_1.profile_button.click(), home_2.profile_button.click()
        username_1, username_2 = profile_1.default_username_text.text, profile_2.default_username_text.text

        profile_1.just_fyi('Add custom bootnode, enable bootnodes and check validation')
        profile_1.advanced_button.click()
        profile_1.bootnodes_button.click()
        profile_1.add_bootnode_button.click()
        profile_1.specify_name_input.set_value('test')
        profile_1.bootnode_address_input.set_value('invalid_bootnode_address')
        if not profile_1.element_by_text_part('Invalid format').is_element_displayed():
             self.errors.append('Validation message about invalid format of bootnode is not shown')
        profile_1.save_button.click()
        if profile_1.add_bootnode_button.is_element_displayed():
             self.errors.append('User was navigated to another screen when tapped on disabled "Save" button')
        profile_1.bootnode_address_input.clear()
        profile_1.bootnode_address_input.set_value(bootnode_address)
        profile_1.save_button.click()
        profile_1.enable_bootnodes.click()
        profile_1.home_button.click()

        profile_1.just_fyi('Add contact and send first message')
        chat_1 = home_1.add_contact(public_key)
        message = 'test message'
        chat_1.chat_message_input.send_keys(message)
        chat_1.send_message_button.click()
        profile_2.home_button.click()
        chat_2 = home_2.get_chat(username_1).click()
        chat_2.chat_element_by_text(message).wait_for_visibility_of_element()
        chat_2.add_to_contacts.click()

        profile_1.just_fyi('Disable custom bootnodes')
        chat_1.profile_button.click()
        profile_1.advanced_button.click()
        profile_1.bootnodes_button.click()
        profile_1.enable_bootnodes.click()
        profile_1.home_button.click()

        profile_1.just_fyi('Send message and check that it is received after disabling bootnodes')
        home_1.get_chat(username_2).click()
        message_1 = 'new message'
        chat_1.chat_message_input.send_keys(message_1)
        chat_1.send_message_button.click()
        for chat in chat_1, chat_2:
            if not chat.chat_element_by_text(message_1).is_element_displayed():
                self.errors.append('Message was not received after enabling bootnodes!')
        self.errors.verify_no_errors()

    @marks.testrail_id(5436)
    @marks.medium
    @marks.flaky
    def test_add_switch_delete_custom_mailserver(self):
        self.create_drivers(2)
        sign_in_1, sign_in_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        home_1, home_2 = sign_in_1.create_user(), sign_in_2.create_user()
        public_key = home_2.get_public_key_and_username()
        home_2.get_back_to_home_view()

        profile_1 = home_1.profile_button.click()
        username_1 = profile_1.default_username_text.text

        profile_1.just_fyi('disable autoselection')
        profile_1.sync_settings_button.click()
        profile_1.mail_server_button.click()
        mailserver = profile_1.return_mailserver_name(mailserver_hk, used_fleet)
        profile_1.mail_server_auto_selection_button.click()
        profile_1.mail_server_by_name(mailserver).click()
        profile_1.confirm_button.click()
        profile_1.just_fyi('add custom mailserver (check address/name validation) and connect to it')
        profile_1.plus_button.click()
        server_name = 'test'
        profile_1.save_button.click()
        if profile_1.element_by_text(mailserver).is_element_displayed():
            self.errors.append('Could add custom mailserver with empty address and name')
        profile_1.specify_name_input.set_value(server_name)
        profile_1.mail_server_address_input.set_value(mailserver_address[:-3])
        profile_1.save_button.click()
        if not profile_1.element_by_text_part("Invalid format").is_element_displayed():
            self.errors.append('could add custom mailserver with invalid address')
        profile_1.mail_server_address_input.clear()
        profile_1.mail_server_address_input.set_value(mailserver_address)
        profile_1.save_button.click()
        profile_1.mail_server_by_name(server_name).click()
        profile_1.mail_server_connect_button.click()
        profile_1.confirm_button.click()
        if profile_1.element_by_text_part("Error connecting").is_element_displayed(40):
            profile_1.retry_to_connect_to_mailserver()
        profile_1.get_back_to_home_view()
        profile_1.home_button.click()


        profile_1.just_fyi('start chat with user2 and check that all messages are delivered')
        chat_1 = home_1.add_contact(public_key)
        message = 'test message'
        chat_1.chat_message_input.send_keys(message)
        chat_1.send_message_button.click()
        chat_2 = home_2.get_chat(username_1).click()
        chat_2.chat_element_by_text(message).wait_for_visibility_of_element()
        message_1 = 'new message'
        chat_2.chat_message_input.send_keys(message_1)
        chat_2.send_message_button.click()
        chat_1.chat_element_by_text(message_1).wait_for_visibility_of_element()

        profile_1.just_fyi('delete custom mailserver')
        chat_1.profile_button.click()
        profile_1.sync_settings_button.click()
        profile_1.mail_server_button.click()
        profile_1.element_by_text(mailserver).scroll_to_element()
        profile_1.element_by_text(mailserver).click()
        profile_1.confirm_button.click()
        profile_1.element_by_text(server_name).scroll_to_element()
        profile_1.element_by_text(server_name).click()
        profile_1.mail_server_delete_button.scroll_to_element()
        profile_1.mail_server_delete_button.click()
        profile_1.mail_server_confirm_delete_button.click()
        if profile_1.element_by_text(server_name).is_element_displayed():
            self.errors.append('Deleted custom mailserver is shown')
        profile_1.get_back_to_home_view()
        profile_1.relogin()
        chat_1.profile_button.click()
        profile_1.sync_settings_button.click()
        profile_1.mail_server_button.click()
        if profile_1.element_by_text(server_name).is_element_displayed():
            self.errors.append('Deleted custom mailserver is shown after relogin')

        self.errors.verify_no_errors()

    @marks.testrail_id(5767)
    @marks.medium
    @marks.flaky
    def test_can_not_connect_to_mailserver(self):
        self.create_drivers(2)
        home_1, home_2= SignInView(self.drivers[0]).create_user(), SignInView(self.drivers[1]).create_user()
        profile_1 = home_1.profile_button.click()

        profile_1.just_fyi('add non-working mailserver and connect to it')
        profile_1.sync_settings_button.click()
        profile_1.mail_server_button.click()
        profile_1.mail_server_auto_selection_button.click()
        profile_1.plus_button.click()
        server_name = 'test'
        profile_1.specify_name_input.set_value(server_name)
        profile_1.mail_server_address_input.set_value(mailserver_address.replace('4','5'))
        profile_1.save_button.click()
        profile_1.mail_server_by_name(server_name).click()
        profile_1.mail_server_connect_button.click()
        profile_1.confirm_button.click()

        profile_1.just_fyi('check that popup "Error connecting" will not reappear if tap on "Cancel"')
        profile_1.element_by_translation_id(id='mailserver-error-title').wait_for_element(60)
        profile_1.cancel_button.click()
        profile_1.home_button.click()

        home_2.just_fyi('send several messages to public channel')
        public_chat_name = home_2.get_random_chat_name()
        message = 'test_message'
        public_chat_2 = home_2.join_public_chat(public_chat_name)
        public_chat_2.chat_message_input.send_keys(message)
        public_chat_2.send_message_button.click()
        public_chat_2.back_button.click()

        profile_1.just_fyi('join same public chat and try to reconnect via "Tap to reconnect" and check "Connecting"')
        profile_1.home_button.click()
        public_chat_1 = home_1.join_public_chat(public_chat_name)
        public_chat_1.relogin()

        profile_1.just_fyi('check that still connected to custom mailserver after relogin')
        home_1.profile_button.click()
        profile_1.sync_settings_button.click()
        if not profile_1.element_by_text(server_name).is_element_displayed():
            self.drivers[0].fail("Not connected to custom mailserver after re-login")

        profile_1.just_fyi('check that can RETRY to connect')
        profile_1.element_by_translation_id(id='mailserver-error-title').wait_for_element(60)
        public_chat_1.element_by_translation_id(id='mailserver-retry', uppercase=True).wait_and_click(60)

        profile_1.just_fyi('check that can pick another mailserver and receive messages')
        profile_1.element_by_translation_id(id='mailserver-error-title').wait_for_element(60)
        profile_1.element_by_translation_id(id='mailserver-pick-another', uppercase=True).wait_and_click(120)
        mailserver = profile_1.return_mailserver_name(mailserver_ams, used_fleet)
        profile_1.element_by_text(mailserver).click()
        profile_1.confirm_button.click()
        profile_1.home_button.click()
        home_1.get_chat('#%s' % public_chat_name).click()
        if not public_chat_1.chat_element_by_text(message).is_element_displayed(60):
            self.errors.append("Chat history wasn't fetched")

        self.errors.verify_no_errors()

    @marks.testrail_id(6332)
    @marks.medium
    def test_disable_use_history_node(self):
        self.create_drivers(2)
        home_1, home_2 = SignInView(self.drivers[0]).create_user(), SignInView(self.drivers[1]).create_user()
        profile_1 = home_1.profile_button.click()

        home_2.just_fyi('send several messages to public channel')
        public_chat_name = home_2.get_random_chat_name()
        message, message_no_history = 'test_message', 'history node is disabled'
        public_chat_2 = home_2.join_public_chat(public_chat_name)
        public_chat_2.send_message(message)

        profile_1.just_fyi('disable use_history_node and check that no history is fetched but you can still send messages')
        profile_1.sync_settings_button.click()
        profile_1.mail_server_button.click()
        profile_1.use_history_node_button.click()
        profile_1.home_button.click()
        public_chat_1 = home_1.join_public_chat(public_chat_name)
        if public_chat_1.chat_element_by_text(message).is_element_displayed(30):
            self.errors.append('Chat history was fetched when use_history_node is disabled')
        public_chat_1.send_message(message_no_history)
        if not public_chat_2.chat_element_by_text(message_no_history).is_element_displayed(30):
            self.errors.append('Message sent when use_history_node is disabled was not received')
        public_chat_1.profile_button.click()
        profile_1.relogin()
        home_1.get_chat('#%s'%public_chat_name).click()
        if public_chat_1.chat_element_by_text(message).is_element_displayed(30):
            self.drivers[0].fail('History was fetched after relogin when use_history_node is disabled')

        profile_1.just_fyi('enable use_history_node and check that history is fetched')
        home_1.profile_button.click()
        profile_1.sync_settings_button.click()
        profile_1.mail_server_button.click()
        profile_1.use_history_node_button.click()
        profile_1.home_button.click(desired_view='chat')
        if not public_chat_1.chat_element_by_text(message).is_element_displayed(30):
            self.errors.append('History was not fetched after enabling use_history_node')
        self.errors.verify_no_errors()

    @marks.testrail_id(5762)
    @marks.high
    def test_pair_devices_sync_one_to_one_contacts_nicknames_public_chat(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        device_1_home = device_1.create_user()
        device_1_home.profile_button.click()
        device_1_profile = device_1_home.get_profile_view()
        device_1_profile.privacy_and_security_button.click()
        device_1_profile.backup_recovery_phrase_button.click()
        device_1_profile.ok_continue_button.click()
        recovery_phrase = device_1_profile.get_recovery_phrase()
        device_1_profile.back_button.click()
        device_1_profile.get_back_to_home_view()
        device_1_name = 'device_%s' % device_1.driver.number
        device_2_name = 'device_%s' % device_2.driver.number
        message_before_sync = 'sent before sync'
        message_after_sync = 'sent after sync'
        public_chat_before_sync = 'before-pairing'
        public_chat_after_sync = 'after-pairing'

        device_1.just_fyi('add contact, start 1-1 chat with basic user')
        device_1_chat = device_1_home.add_contact(basic_user['public_key'])
        device_1_chat.chat_message_input.send_keys(message_before_sync)
        device_1_chat.send_message_button.click()

        device_1.just_fyi('join public chat')
        device_1_chat.get_back_to_home_view()
        device_1_public_chat = device_1_home.join_public_chat(public_chat_before_sync)
        device_2_home = device_2.recover_access(passphrase=' '.join(recovery_phrase.values()))
        device_1_profile, device_2_profile = device_1_home.profile_button.click(), device_2_home.profile_button.click()

        device_2.just_fyi('go to profile and set nickname for contact')
        device_1_profile.open_contact_from_profile(basic_user['username'])
        nickname = 'my_basic_user'
        device_1_chat.set_nickname(nickname)
        device_1_profile.back_button.click(2)

        device_2.just_fyi('go to profile > Devices, set device name, discover device 2 to device 1')
        device_2_profile.discover_and_advertise_device(device_2_name)
        device_1_profile.discover_and_advertise_device(device_1_name)
        device_1_profile.get_toggle_device_by_name(device_2_name).wait_and_click()
        device_1_profile.sync_all_button.click()
        device_1_profile.sync_all_button.wait_for_visibility_of_element(15)
        [device.profile_button.click() for device in (device_1_profile, device_2_profile)]

        device_2.just_fyi('check that contact with nickname is appeared in Contact list')
        device_2_profile.contacts_button.scroll_to_element(9, 'up')
        device_2_profile.contacts_button.click()
        for name in (basic_user['username'], nickname):
            if not device_2_profile.element_by_text(name).is_element_displayed():
                self.errors.append('"%s" is not found in Contacts after initial sync' % name)

        device_1.just_fyi('send message to 1-1 chat with basic user and add another contact')
        device_1_profile.home_button.click(desired_view='chat')
        device_1_public_chat.back_button.click()
        device_1_home.get_chat(nickname).click()
        device_1_chat.chat_message_input.send_keys(message_after_sync)
        device_1_chat.send_message_button.click()
        device_1_chat.back_button.click()
        device_1_home.add_contact(transaction_senders['A']['public_key'])

        device_2.just_fyi('check that messages appeared in 1-1 chat, public chats and new contacts are synced')
        if not device_2_profile.element_by_text(transaction_senders['A']['username']).is_element_displayed(60):
            self.errors.append(
                '"%s" is not found in Contacts after adding when devices are paired' % transaction_senders['A'][
                    'username'])

        device_1.just_fyi('Set nickname for added contact and check that it will be synced')
        device_1_home.profile_button.click()
        device_1_profile.contacts_button.scroll_to_element(9, 'up')
        device_1_profile.open_contact_from_profile(transaction_senders['A']['username'])
        nickname_after_sync = 'my_transaction sender'
        device_1_chat.set_nickname(nickname_after_sync)
        device_1_profile.back_button.click()
        device_1.home_button.click(desired_view='chat')
        if not device_2_profile.element_by_text(nickname_after_sync).is_element_displayed(60):
            self.errors.append(
                '"%s" is not updated in Contacts after setting nickname when devices are paired' % nickname_after_sync)

        device_2_profile.home_button.click()
        if not device_2_home.element_by_text_part(public_chat_before_sync).is_element_displayed():
            self.errors.append(
                '"%s" is not found in Home after initial sync when devices are paired' % public_chat_before_sync)
        chat = device_2_home.get_chat(nickname).click()
        if chat.chat_element_by_text(message_before_sync).is_element_displayed():
            self.errors.append('"%s" message sent before pairing is synced' % message_before_sync)
        if not chat.chat_element_by_text(message_after_sync).is_element_displayed(60):
            self.errors.append('"%s" message in 1-1 is not synced' % message_after_sync)

        device_1.just_fyi('add new public chat and check that it will be synced with device2')
        device_1_chat.get_back_to_home_view()
        device_1_home.join_public_chat(public_chat_after_sync)
        device_2_home = chat.get_back_to_home_view()
        if not device_2_home.element_by_text_part(public_chat_after_sync).is_element_displayed(20):
            self.errors.append(
                '"%s" public chat is not synced after adding when devices are paired' % public_chat_after_sync)

        self.errors.verify_no_errors()

    @marks.testrail_id(6226)
    @marks.critical
    def test_ens_mentions_pn_and_nickname_in_public_and_1_1_chats(self):
        self.create_drivers(2)
        device_1, device_2 = self.drivers[0], self.drivers[1]
        sign_in_1, sign_in_2 = SignInView(device_1), SignInView(device_2)
        user_1 = ens_user
        home_1 = sign_in_1.recover_access(user_1['passphrase'], enable_notifications=True)
        home_2 = sign_in_2.create_user()
        publuc_key_2, username_2 = home_2.get_public_key_and_username(return_username=True)
        home_2.home_button.double_click()

        home_1.just_fyi('switching to mainnet and add ENS')
        profile_1 = sign_in_1.profile_button.click()
        profile_1.switch_network('Mainnet with upstream RPC')
        home_1.profile_button.click()
        dapp_view_1 = profile_1.ens_usernames_button.click()
        dapp_view_1.element_by_text('Get started').click()
        dapp_view_1.ens_name_input.set_value(ens_user['ens'])
        expected_text = 'This user name is owned by you and connected with your chat key.'
        if not dapp_view_1.element_by_text_part(expected_text).is_element_displayed():
            dapp_view_1.click_system_back_button()
            dapp_view_1.wait_for_element_starts_with_text(expected_text)
        dapp_view_1.check_ens_name.click_until_presence_of_element(dapp_view_1.element_by_text('Ok, got it'))
        dapp_view_1.element_by_text('Ok, got it').click()

        home_1.just_fyi('check ENS name wallet address and public key')
        profile_1.element_by_text(user_1['ens']).click()
        for text in ('10 SNT, deposit unlocked', user_1['address'].lower(), user_1['public_key'] ):
            if not profile_1.element_by_text_part(text).is_element_displayed(40):
                self.errors.append('%s text is not shown' % text)
        dapp_view_1.get_back_to_home_view()
        profile_1.home_button.click()

        home_2.just_fyi('joining same public chat, set ENS name and check it in chat from device2')
        chat_name = home_1.get_random_chat_name()
        chat_2 = home_2.join_public_chat(chat_name)
        chat_1 = home_1.join_public_chat(chat_name)
        chat_1.get_back_to_home_view()
        home_1.profile_button.click()
        ens_name = '@' + user_1['ens']
        profile_1.element_by_text('Your ENS name').click()
        if profile_1.username_in_ens_chat_settings_text.text != ens_name:
            self.errors.append('ENS username is not shown in ENS usernames Chat Settings after enabling')
        profile_1.back_button.click()
        profile_1.home_button.click()
        home_1.get_chat('#' + chat_name).click()
        message_text_2 = 'message test text 1'
        chat_1.send_message(message_text_2)
        if not chat_2.wait_for_element_starts_with_text(ens_name):
            self.errors.append('ENS username is not shown in public chat')
        home_1.put_app_to_background()

        home_2.just_fyi('check that can mention user with ENS name')
        chat_2.select_mention_from_suggestion_list(user_1['ens'])
        if chat_2.chat_message_input.text != ens_name + ' ':
            self.errors.append('ENS username is not resolved in chat input after selecting it in mention suggestions list!')
        chat_2.send_message_button.click()
        chat_2.element_starts_with_text(ens_name,'button').click()
        for element in (chat_2.element_by_text(user_1['username']), chat_2.profile_add_to_contacts):
            if not element.is_element_displayed():
                self.errors.append('Was not redirected to user profile after tapping on mention!')

        home_1.just_fyi('check that PN is received and after tap you are redirected to public chat')
        home_1.open_notification_bar()
        home_1.element_by_text_part(username_2).click()
        chat_1.element_starts_with_text(user_1['ens'] +'.stateofus.eth','button').click()
        if not profile_1.contacts_button.is_element_displayed():
                self.errors.append('Was not redirected to own profile after tapping on mention of myself from another user!')

        home_2.just_fyi('check that ENS name is shown in 1-1 chat without adding user as contact in header, profile, options')
        chat_2_one_to_one = chat_2.profile_send_message.click()
        if chat_2_one_to_one.user_name_text.text != ens_name:
            self.errors.append('ENS username is not shown in 1-1 chat header')
        chat_2_one_to_one.chat_options.click()
        if not chat_2_one_to_one.element_by_text(ens_name).is_element_displayed():
            self.errors.append('ENS username is not shown in 1-1 chat options')
        chat_2_one_to_one.view_profile_button.click()
        if not chat_2_one_to_one.element_by_text(ens_name).is_element_displayed():
            self.errors.append('ENS username is not shown in user profile')

        home_2.just_fyi('add user to contacts and check that ENS name is shown in contact')
        chat_2_one_to_one.profile_add_to_contacts.click()
        chat_2.back_button.click()
        profile_2 = chat_2_one_to_one.profile_button.click()
        profile_2.open_contact_from_profile(ens_name)

        home_2.just_fyi('set nickname and recheck username in 1-1 header, profile, options, contacts')
        nickname = 'test user' + str(round(time()))
        chat_2.set_nickname(nickname)
        profile_2.back_button.click()
        for name in (nickname, ens_name):
            if not profile_2.element_by_text(name).is_element_displayed():
                self.errors.append('%s is not shown in contact list' % name)
        profile_2.home_button.click(desired_view='chat')
        if chat_2_one_to_one.user_name_text.text != nickname:
            self.errors.append('Nickname for user with ENS is not shown in 1-1 chat header')
        chat_2_one_to_one.chat_options.click()
        if not chat_2_one_to_one.element_by_text(nickname).is_element_displayed():
            self.errors.append('Nickname for user with ENS is not shown in 1-1 chat options')

        home_2.just_fyi('check nickname in public chat')
        chat_2.get_back_to_home_view()
        home_2.get_chat('#' + chat_name).click()
        chat_element = chat_2.chat_element_by_text(message_text_2)
        chat_element.find_element()
        if chat_element.username.text != '%s %s' % (nickname, ens_name):
            self.errors.append('Nickname for user with ENS is not shown in public chat')

        self.errors.verify_no_errors()


    @marks.testrail_id(6228)
    @marks.high
    def test_mobile_data_usage_complex_settings(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        device_1_home = device_1.create_user()
        public_chat_name, public_chat_message = 'e2e-started-before', 'message to pub chat'
        device_1_public = device_1_home.join_public_chat(public_chat_name)
        device_1_public.send_message(public_chat_message)

        device_1_home.just_fyi('set mobile data to "OFF" and check that peer-to-peer connection is still working')
        device_2_home = device_2.create_user()
        device_2_home.toggle_mobile_data()
        device_2_home.mobile_connection_off_icon.wait_for_visibility_of_element(20)
        for element in device_2_home.continue_syncing_button, device_2_home.stop_syncing_button, device_2_home.remember_my_choice_checkbox:
            if not element.is_element_displayed(10):
               self.drivers[0].fail('Element %s is not not shown in "Syncing mobile" bottom sheet' % element.locator)
        device_2_home.stop_syncing_button.click()
        if not device_2_home.mobile_connection_off_icon.is_element_displayed():
            self.drivers[0].fail('No mobile connection OFF icon is shown')
        device_2_home.mobile_connection_off_icon.click()
        for element in device_2_home.connected_to_n_peers_text, device_2_home.waiting_for_wi_fi:
            if not element.is_element_displayed():
                self.errors.append("Element '%s' is not shown in Connection status bottom sheet" % element.locator)
        device_2_home.click_system_back_button()
        device_2_public = device_2_home.join_public_chat(public_chat_name)
        if device_2_public.chat_element_by_text(public_chat_message).is_element_displayed(30):
            self.errors.append("Chat history was fetched with mobile data fetching off")
        public_chat_new_message = 'new message'
        device_1_public.send_message(public_chat_new_message)
        if not device_2_public.chat_element_by_text(public_chat_new_message).is_element_displayed(30):
            self.errors.append("Peer-to-peer connection is not working when  mobile data fetching is off")

        device_2_home.just_fyi('set mobile data to "ON"')
        device_2_home.home_button.click()
        device_2_home.mobile_connection_off_icon.click()
        device_2_home.use_mobile_data_switch.click()
        if not device_2_home.connected_to_node_text.is_element_displayed(10):
            self.errors.append("Not connected to history node after enabling fetching on mobile data")
        device_2_home.click_system_back_button()
        device_2_home.mobile_connection_on_icon.wait_for_visibility_of_element(10)
        if not device_2_home.mobile_connection_on_icon.is_element_displayed():
            self.errors.append('No mobile connection ON icon is shown')
        device_2_home.get_chat('#%s'% public_chat_name).click()
        if not device_2_public.chat_element_by_text(public_chat_message).is_element_displayed(30):
            self.errors.append("Chat history was not fetched with mobile data fetching ON")

        device_2_home.just_fyi('check redirect to sync settings by tappin "Sync" in connection status bottom sheet')
        device_2_home.home_button.click()
        device_2_home.mobile_connection_on_icon.click()
        device_2_home.connection_settings_button.click()
        if not device_2_home.element_by_translation_id("mobile-network-use-mobile").is_element_displayed():
            self.errors.append("Was not redirected to sync settings after tapping on Settings in connection bottom sheet")

        device_1_home.just_fyi("Check default preferences in Sync settings")
        device_1_profile = device_1_home.profile_button.click()
        device_1_profile.sync_settings_button.click()
        if not device_1_profile.element_by_translation_id("mobile-network-use-wifi").is_element_displayed():
            self.errors.append("Mobile data is enabled by default")
        device_1_profile.element_by_translation_id("mobile-network-use-wifi").click()
        if device_1_profile.ask_me_when_on_mobile_network.text != "ON":
            self.errors.append("'Ask me when on mobile network' is not enabled by default")

        device_1_profile.just_fyi("Disable 'ask me when on mobile network' and check that it is not shown")
        device_1_profile.ask_me_when_on_mobile_network.click()
        device_1_profile.toggle_mobile_data()
        if device_1_profile.element_by_translation_id("mobile-network-start-syncing").is_element_displayed(20):
            self.errors.append("Popup is shown, but 'ask me when on mobile network' is disabled")

        device_1_profile.just_fyi("Check 'Restore default' setting")
        device_1_profile.element_by_text('Restore Defaults').click()
        if device_1_profile.use_mobile_data.attribute_value("checked"):
            self.errors.append("Mobile data is enabled by default")
        if not device_1_profile.ask_me_when_on_mobile_network.attribute_value("checked"):
            self.errors.append("'Ask me when on mobile network' is not enabled by default")

        self.errors.verify_no_errors()


    @marks.testrail_id(5680)
    @marks.high
    @marks.skip
    # TODO: skip until edit userpic is enabled back
    def test_pair_devices_sync_name_photo_public_group_chats(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])
        device_1_home = device_1.create_user()
        device_1_home.profile_button.click()
        device_1_profile = device_1_home.get_profile_view()
        device_1_profile.privacy_and_security_button.click()
        device_1_profile.backup_recovery_phrase_button.click()
        recovery_phrase = device_1_profile.backup_recovery_phrase()
        device_1_profile.back_button.click()
        device_1_profile.get_back_to_home_view()
        device_1_name = 'device_%s' % device_1.driver.number
        device_2_name = 'device_%s' % device_2.driver.number
        public_chat_before_sync_name = 'b-public-%s' % device_1_home.get_random_chat_name()
        public_chat_after_sync_name = 'a-public-%s' % device_1_home.get_random_chat_name()
        group_chat_name = 'group-%s' % device_1_home.get_random_chat_name()
        message_after_sync = 'sent after sync'

        device_1.just_fyi('join public chat, create group chat, edit user picture')
        device_1_public_chat = device_1_home.join_public_chat(public_chat_before_sync_name)
        device_1_public_chat.back_button.click()
        device_1_one_to_one = device_1_home.add_contact(basic_user['public_key'])
        device_1_one_to_one.back_button.click()
        device_1_group_chat = device_1_home.create_group_chat([basic_user['username']], group_chat_name)
        device_1_group_chat.back_button.click()
        device_1_home.profile_button.click()
        device_1_profile = device_1_home.get_profile_view()
        device_1_profile.edit_profile_picture('sauce_logo.png')

        device_2.just_fyi('go to profile > Devices, set device name, discover device 2 to device 1')
        device_2_home = device_2.recover_access(passphrase=' '.join(recovery_phrase.values()))
        device_2_profile = device_2_home.get_profile_view()
        device_2_profile.discover_and_advertise_device(device_2_name)

        device_1.just_fyi('enable pairing of `device 2` and sync')
        device_1_profile.discover_and_advertise_device(device_1_name)
        device_1_profile.get_toggle_device_by_name(device_2_name).click()
        device_1_profile.sync_all_button.click()
        device_1_profile.sync_all_button.wait_for_visibility_of_element(15)

        device_2.just_fyi('check that public chat and profile details are updated')
        device_2_home = device_2_profile.get_back_to_home_view()
        if not device_2_home.element_by_text('#%s' % public_chat_before_sync_name).is_element_displayed():
            self.errors.append('Public chat "%s" doesn\'t appear after initial sync'
                               % public_chat_before_sync_name)
        device_2_home.home_button.click()
        device_2_home.profile_button.click()
        if not device_2_profile.profile_picture.is_element_image_equals_template('sauce_logo_profile.png'):
            self.errors.append('Profile picture was not updated after initial sync')
        device_2_profile.home_button.click()

        device_1.just_fyi('send message to group chat, and join to new public chat')
        device_1_home = device_1_profile.get_back_to_home_view()
        device_1_public_chat = device_1_home.join_public_chat(public_chat_after_sync_name)
        device_1_public_chat.back_button.click()
        device_1_home.element_by_text(group_chat_name).click()
        device_1_group_chat.chat_message_input.send_keys(message_after_sync)
        device_1_group_chat.send_message_button.click()
        device_1_group_chat.back_button.click()

        device_2.just_fyi('check that message in group chat is shown, public chats are synced')
        if not device_2_home.element_by_text('#%s' % public_chat_after_sync_name).is_element_displayed():
            self.errors.append('Public chat "%s" doesn\'t appear on other device when devices are paired'
                               % public_chat_before_sync_name)

        device_2_home.element_by_text(group_chat_name).click()
        device_2_group_chat = device_2_home.get_chat_view()

        if not device_2_group_chat.chat_element_by_text(message_after_sync).is_element_displayed():
            self.errors.append('"%s" message in group chat is not synced' % message_after_sync)

        self.errors.verify_no_errors()

from tests import marks, pair_code, common_password
from tests.base_test_case import SingleDeviceTestCase, MultipleDeviceTestCase
from views.sign_in_view import SignInView
from tests.users import basic_user, transaction_senders


class TestCreateAccount(SingleDeviceTestCase):

    @marks.testrail_id(6645)
    @marks.critical
    def test_restore_account_migrate_multiaccount_to_keycard(self):
        sign_in = SignInView(self.driver)
        seed = basic_user['passphrase']
        home = sign_in.recover_access(passphrase=seed)
        profile = home.profile_button.click()
        profile.logout()

        home.just_fyi("Checking keycard banner and starting migrate multiaccount to keycard")
        sign_in.multi_account_on_login_button.wait_for_visibility_of_element(30)
        sign_in.get_multiaccount_by_position(1).click()
        if not sign_in.get_keycard_banner.is_element_displayed():
            self.errors.append("Get a keycard banner is not shown on login screen for ordinary multiaccount")
        sign_in.options_button.click()
        sign_in.manage_keys_and_storage_button.click()
        if not sign_in.element_by_text(basic_user['username']).is_element_displayed():
            self.driver.fail("Default username is not shown when migrating multiaccount to keycard!")

        home.just_fyi("Checking validation of seed phrase during migration")
        sign_in.enter_seed_phrase_next_button.click()
        if sign_in.seedphrase_input.is_element_displayed():
            self.driver.fail("Proceeded to seedphrase input without confirmed Actions")
        sign_in.move_keystore_file_option.click()
        sign_in.enter_seed_phrase_next_button.click()
        sign_in.seedphrase_input.set_value(transaction_senders['A']['passphrase'])
        sign_in.choose_storage_button.click()
        if not sign_in.element_by_translation_id("seed-key-uid-mismatch").is_element_displayed():
            self.driver.fail("Can proceed with seed phrase of another user")
        sign_in.element_by_translation_id("try-again").click()
        sign_in.seedphrase_input.set_value(seed[:-1])
        sign_in.choose_storage_button.click()
        if not sign_in.custom_seed_phrase_label.is_element_displayed():
            self.driver.fail("Can proceed with invalid seed phrase")
        sign_in.cancel_button.click()
        sign_in.seedphrase_input.set_value(seed)
        sign_in.choose_storage_button.click()
        if not sign_in.get_keycard_banner.is_element_displayed():
            self.errors.append("Get a keycard banner is not shown on Key management screen")
        sign_in.keycard_required_option.click()
        if sign_in.get_keycard_banner.is_element_displayed():
            self.errors.append("Get a keycard banner is shown when keycard storage is chosen")

        home.just_fyi("Finishing migration to keycard")
        sign_in.confirm_button.click()
        keycard = sign_in.move_and_reset_button.click()
        keycard.begin_setup_button.click()
        keycard.connect_card_button.click()
        keycard.enter_default_pin()
        keycard.enter_default_pin()
        keycard.next_button.scroll_to_element()
        keycard.next_button.wait_for_visibility_of_element(20)
        keycard.next_button.click()
        keycard.yes_button.click()
        sign_in.maybe_later_button.wait_and_click(30)
        sign_in.lets_go_button.wait_and_click(30)
        sign_in.no_thanks_metric_button.click()

        sign_in.just_fyi('Check that after migrating account with assets is restored')
        wallet_view = sign_in.wallet_button.click()
        wallet_view.set_up_wallet()
        for asset in ['ETH', 'ADI', 'STT']:
            if wallet_view.get_asset_amount_by_name(asset) == 0:
                self.errors.append('Asset %s was not restored' % asset)

        sign_in.just_fyi('Check that after migration wallet address matches expected')
        address = wallet_view.get_wallet_address()
        if address != '0x%s' % basic_user['address']:
            self.errors.append('Restored address %s does not match expected' % address)

        sign_in.just_fyi('Check that after migration username and public key match expected')
        public_key, default_username = sign_in.get_public_key_and_username(return_username=True)
        profile = sign_in.get_profile_view()
        if public_key != basic_user['public_key']:
            self.errors.append('Public key %s does not match expected' % public_key)
        if default_username != basic_user['username']:
            self.errors.append('Default username %s does not match expected' % default_username)
        profile.logout()

        sign_in.just_fyi('Check that can login with migrated account, keycard banner is not shown and no option to migrate')
        sign_in.get_multiaccount_by_position(1).click()
        if sign_in.get_keycard_banner.is_element_displayed():
            self.errors.append("Get a keycard banner is shown on migrated keycard multiaccount")
        keycard.one_button.wait_for_visibility_of_element(10)
        keycard.connect_selected_card_button.click()
        keycard.enter_default_pin()
        if not sign_in.home_button.is_element_displayed(10):
            self.driver.fail('Keycard user is not logged in')

        self.errors.verify_no_errors()

    @marks.testrail_id(6240)
    @marks.critical
    def test_restore_account_from_mnemonic_to_keycard(self):
        sign_in = SignInView(self.driver)
        sign_in.recover_access(passphrase=basic_user['passphrase'], keycard=True)

        sign_in.just_fyi('Check that after restoring account with assets is restored')
        wallet_view = sign_in.wallet_button.click()
        wallet_view.set_up_wallet()
        for asset in ['ETH', 'ADI', 'STT']:
            if wallet_view.get_asset_amount_by_name(asset) == 0:
                self.errors.append('Asset %s was not restored' % asset)

        sign_in.just_fyi('Check that wallet address matches expected')
        address = wallet_view.get_wallet_address()
        if address != '0x%s' % basic_user['address']:
            self.errors.append('Restored address %s does not match expected' % address)

        sign_in.just_fyi('Check that username and public key match expected')
        public_key, default_username = sign_in.get_public_key_and_username(return_username=True)
        profile_view = sign_in.get_profile_view()
        if public_key != basic_user['public_key']:
            self.errors.append('Public key %s does not match expected' % public_key)
        if default_username != basic_user['username']:
            self.errors.append('Default username %s does not match expected' % default_username)
        profile_view.logout()

        sign_in.just_fyi('Check that can login with restored from mnemonic keycard account')
        sign_in.sign_in(keycard=True)
        if not sign_in.home_button.is_element_displayed(10):
            self.driver.fail('Keycard user is not logged in')

        self.errors.verify_no_errors()

    @marks.testrail_id(5742)
    @marks.medium
    def test_keycard_interruption_creating_onboarding_flow(self):
        sign_in = SignInView(self.driver)

        sign_in.just_fyi('Cancel on PIN code setup stage')
        sign_in.get_started_button.click()
        sign_in.generate_key_button.click()
        username = sign_in.first_username_on_choose_chat_name.text
        sign_in.next_button.click()
        keycard_flow = sign_in.keycard_storage_button.click()
        keycard_flow.next_button.click()
        keycard_flow.begin_setup_button.click()
        keycard_flow.connect_card_button.click()
        keycard_flow.enter_another_pin()
        keycard_flow.cancel_button.click()
        if not keycard_flow.element_by_translation_id("keycard-cancel-setup-title").is_element_displayed():
            self.driver.fail('No Dangerous operation popup is shown on canceling operation from PIN code stage')
        keycard_flow.yes_button.click()

        sign_in.just_fyi('Cancel on Pair code stage: initialized')
        keycard_flow.begin_setup_button.click()
        keycard_flow.enter_default_pin()
        keycard_flow.enter_default_pin()
        keycard_flow.wait_for_element_starts_with_text('Write codes down')
        pair_code = keycard_flow.pair_code_text.text
        keycard_flow.cancel_button.click()
        if not keycard_flow.element_by_translation_id("keycard-cancel-setup-title").is_element_displayed():
            self.driver.fail('No Dangerous operation popup is shown on canceling operation from Pair code stage')
        keycard_flow.yes_button.click()

        sign_in.just_fyi('Cancel from Confirm seed phrase: initialized + 1 pairing slot is used')
        keycard_flow.begin_setup_button.click()
        keycard_flow.pair_code_input.set_value(pair_code)
        keycard_flow.pair_to_this_device_button.click()
        seed_phrase = keycard_flow.get_seed_phrase()
        keycard_flow.confirm_button.click()
        keycard_flow.yes_button.click()
        keycard_flow.cancel_button.click()
        if not keycard_flow.element_by_translation_id("keycard-cancel-setup-title").is_element_displayed():
            self.driver.fail('No Dangerous operation popup is shown on canceling operation during Backup seed phrase stage')
        keycard_flow.yes_button.click()
        if not keycard_flow.element_by_text_part('Back up seed phrase').is_element_displayed():
            self.driver.fail('On canceling setup from Confirm seed phrase was not redirected to expected screen')

        sign_in.just_fyi('Cancel from Back Up seed phrase: initialized + 1 pairing slot is used')
        keycard_flow.cancel_button.click()
        keycard_flow.yes_button.click()
        keycard_flow.begin_setup_button.click()
        keycard_flow.element_by_translation_id("back-up-seed-phrase").wait_for_element(10)
        new_seed_phrase = keycard_flow.get_seed_phrase()
        if new_seed_phrase != seed_phrase:
            self.errors.append('Another seed phrase is shown after cancelling setup during Back up seed phrase')
        keycard_flow.backup_seed_phrase()
        keycard_flow.enter_default_pin()
        for element in sign_in.maybe_later_button, sign_in.lets_go_button, sign_in.no_thanks_metric_button:
            element.wait_for_visibility_of_element(30)
            element.click()
        sign_in.profile_button.wait_for_visibility_of_element(30)

        sign_in.just_fyi('Check username and relogin')
        profile = sign_in.get_profile_view()
        public_key, real_username = profile.get_public_key_and_username(return_username=True)
        if real_username != username:
            self.errors.append('Username was changed after interruption of creating account')
        profile.logout()
        home = sign_in.sign_in(keycard=True)
        if not home.wallet_button.is_element_displayed(10):
            self.errors.append("Failed to login to Keycard account")
        self.errors.verify_no_errors()

    @marks.testrail_id(6246)
    @marks.medium
    @marks.flaky
    def test_keycard_interruption_access_key_onboarding_flow(self):
        sign_in = SignInView(self.driver)
        sign_in.get_started_button.click()

        sign_in.access_key_button.click()
        sign_in.enter_seed_phrase_button.click()
        sign_in.seedphrase_input.click()
        sign_in.seedphrase_input.set_value(basic_user['passphrase'])
        sign_in.next_button.click()
        sign_in.reencrypt_your_key_button.click()
        keycard_flow = sign_in.keycard_storage_button.click()

        sign_in.just_fyi('Cancel on PIN code setup stage')
        keycard_flow.next_button.click()
        keycard_flow.begin_setup_button.click()
        keycard_flow.connect_card_button.click()
        keycard_flow.enter_another_pin()
        keycard_flow.cancel_button.click()
        if not keycard_flow.element_by_translation_id("keycard-cancel-setup-title").is_element_displayed():
            self.driver.fail('No Dangerous operation popup is shown on canceling operation from PIN code stage')
        keycard_flow.yes_button.click()

        sign_in.just_fyi('Cancel on Pair code stage: initialized')
        keycard_flow.begin_setup_button.click()
        keycard_flow.enter_default_pin()
        keycard_flow.enter_default_pin()
        keycard_flow.wait_for_element_starts_with_text('Write codes down')
        pair_code = keycard_flow.pair_code_text.text
        keycard_flow.cancel_button.click()
        if not keycard_flow.element_by_translation_id("keycard-cancel-setup-title").is_element_displayed():
            self.driver.fail('No Dangerous operation popup is shown on canceling operation from Pair code stage')
        keycard_flow.yes_button.click()

        sign_in.just_fyi('Finish setup and relogin')
        keycard_flow.begin_setup_button.click()
        if not keycard_flow.element_by_text_part('5 free pairing slots').is_element_displayed():
            self.errors.append('Number of free pairing slots is not shown or wrong')
        keycard_flow.pair_code_input.set_value(pair_code)
        keycard_flow.pair_to_this_device_button.click()
        keycard_flow.enter_default_pin()
        for element in sign_in.maybe_later_button, sign_in.lets_go_button, sign_in.no_thanks_metric_button:
            element.wait_for_visibility_of_element(30)
            element.click()
        sign_in.profile_button.wait_for_visibility_of_element(30)
        public_key, default_username = sign_in.get_public_key_and_username(return_username=True)
        profile_view = sign_in.get_profile_view()
        if public_key != basic_user['public_key']:
            self.errors.append('Public key %s does not match expected' % public_key)
        if default_username != basic_user['username']:
            self.errors.append('Default username %s does not match expected' % default_username)
        profile_view.logout()
        home = sign_in.sign_in(keycard=True)
        if not home.wallet_button.is_element_displayed(10):
            self.errors.append("Failed to login to Keycard account")
        self.errors.verify_no_errors()

    @marks.testrail_id(5758)
    @marks.high
    def test_keycard_can_recover_keycard_account_card_pairing(self):
        sign_in = SignInView(self.driver)
        recovered_user = transaction_senders['A']

        sign_in.just_fyi('Recover multiaccount')
        sign_in.get_started_button.click_until_presence_of_element(sign_in.access_key_button)
        sign_in.access_key_button.click()
        sign_in.recover_with_keycard_button.click()
        keycard_view = sign_in.begin_recovery_button.click()
        keycard_view.connect_pairing_card_button.click()
        keycard_view.pair_code_input.set_value(pair_code)
        sign_in.pair_to_this_device_button.click()
        keycard_view.enter_default_pin()
        sign_in.maybe_later_button.click_until_presence_of_element(sign_in.lets_go_button)
        sign_in.lets_go_button.click_until_absense_of_element(sign_in.lets_go_button)
        sign_in.home_button.wait_for_visibility_of_element(30)

        sign_in.just_fyi('Check assets after pairing keycard for recovered multiaccount')
        wallet_view = sign_in.wallet_button.click()
        wallet_view.set_up_wallet()
        for asset in ['ETH', 'LXS']:
            if wallet_view.get_asset_amount_by_name(asset) == 0:
                self.errors.append("%s value is not restored" % asset)

        sign_in.just_fyi('Check that wallet address matches expected for recovered multiaccount')
        address = wallet_view.get_wallet_address()
        if str(address).lower() != '0x%s' % recovered_user['address']:
            self.errors.append('Restored address %s does not match expected' % address)

        sign_in.just_fyi('Check that username and public key match expected for recovered multiaccount')
        public_key, default_username = sign_in.get_public_key_and_username(return_username=True)
        profile = sign_in.get_profile_view()
        if public_key != recovered_user['public_key']:
            self.errors.append('Public key %s does not match expected' % public_key)
        if default_username != recovered_user['username']:
            self.errors.append('Default username %s does not match expected' % default_username)
        profile.logout()

        sign_in.just_fyi('Check that can login with recovered keycard account')
        sign_in.sign_in(keycard=True)
        if not sign_in.home_button.is_element_displayed(10):
            self.driver.fail('Keycard user is not logged in')

        self.errors.verify_no_errors()

    @marks.testrail_id(6243)
    @marks.medium
    def test_keycard_can_recover_keycard_account_offline_and_add_watch_only_acc(self):
        sign_in = SignInView(self.driver)
        sign_in.toggle_airplane_mode()

        sign_in.just_fyi('Recover multiaccount offline')
        sign_in.get_started_button.click_until_presence_of_element(sign_in.access_key_button)
        sign_in.access_key_button.click()
        sign_in.recover_with_keycard_button.click()
        keycard_view = sign_in.begin_recovery_button.click()
        keycard_view.connect_pairing_card_button.click()
        keycard_view.pair_code_input.set_value(pair_code)
        sign_in.pair_to_this_device_button.click()
        keycard_view.enter_default_pin()
        sign_in.home_button.wait_for_visibility_of_element(30)
        wallet_view = sign_in.wallet_button.click()
        wallet_view.set_up_wallet()

        sign_in.just_fyi('Relogin offline')
        self.driver.close_app()
        self.driver.launch_app()
        sign_in.sign_in(keycard=True)
        if not sign_in.home_button.is_element_displayed(10):
            self.driver.fail('Keycard user is not logged in')

        sign_in.just_fyi('Turn off airplane mode and turn on cellular network')
        sign_in.toggle_airplane_mode()
        sign_in.toggle_mobile_data()
        sign_in.element_by_text_part('Stop syncing').wait_and_click(60)
        sign_in.wallet_button.click()
        if not wallet_view.element_by_text_part('LXS').is_element_displayed():
            self.errors.append('Token balance is not fetched while on cellular network!')

        wallet_view.just_fyi('Add watch-only account when on cellular network')
        wallet_view.add_account_button.click()
        wallet_view.add_watch_only_address_button.click()
        wallet_view.enter_address_input.send_keys(basic_user['address'])
        account_name = 'watch-only'
        wallet_view.account_name_input.send_keys(account_name)
        wallet_view.add_account_generate_account_button.click()
        account_button = wallet_view.get_account_by_name(account_name)
        if not account_button.is_element_displayed():
            self.driver.fail('Account was not added')

        wallet_view.just_fyi('Check that balance is changed after go back to WI-FI')
        sign_in.toggle_mobile_data()
        for asset in ('ADI', 'STT'):
            wallet_view.asset_by_name(asset).scroll_to_element()
            wallet_view.wait_balance_is_changed(asset, wait_time=60)

        wallet_view.just_fyi('Delete watch-only account')
        wallet_view.get_account_by_name(account_name).click()
        wallet_view.get_account_options_by_name(account_name).click()
        wallet_view.account_settings_button.click()
        wallet_view.delete_account_button.click()
        wallet_view.yes_button.click()
        if wallet_view.get_account_by_name(account_name).is_element_displayed(20):
            self.errors.append('Account was not deleted')

        self.errors.verify_no_errors()


    @marks.testrail_id(6311)
    @marks.medium
    def test_same_seed_added_inside_multiaccount_and_keycard(self):
        sign_in = SignInView(self.driver)
        recipient = "0x" + transaction_senders['G']['address']

        sign_in.just_fyi('Restore keycard multiaccount and logout')
        sign_in.recover_access(passphrase=basic_user['passphrase'], keycard=True)
        profile_view = sign_in.profile_button.click()
        profile_view.logout()

        sign_in.just_fyi('Create new multiaccount')
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

        sign_in.just_fyi('Add to wallet seed phrase for restored multiaccount')
        wallet_view = sign_in.wallet_button.click()
        wallet_view.set_up_wallet()
        wallet_view.add_account_button.click()
        wallet_view.enter_a_seed_phrase_button.click()
        wallet_view.enter_your_password_input.send_keys(common_password)
        account_name = 'subacc'
        wallet_view.account_name_input.send_keys(account_name)
        wallet_view.enter_seed_phrase_input.set_value(basic_user['passphrase'])
        wallet_view.add_account_generate_account_button.click()
        wallet_view.get_account_by_name(account_name).click()

        sign_in.just_fyi('Send transaction from added account and log out')
        transaction_amount_added = wallet_view.get_unique_amount()
        wallet_view.send_transaction(amount=transaction_amount_added, recipient=recipient, sign_transaction=True)
        wallet_view.profile_button.click()
        profile_view.logout()

        sign_in.just_fyi('Login to keycard account and send another transaction')
        sign_in.sign_in(position=2, keycard=True)
        sign_in.wallet_button.click()
        wallet_view.set_up_wallet()
        wallet_view.wait_balance_is_changed('ETH')
        wallet_view.accounts_status_account.click()
        transaction_amount_keycard = wallet_view.get_unique_amount()
        wallet_view.send_transaction(amount=transaction_amount_keycard, recipient=recipient, keycard=True, sign_transaction=True)

        sign_in.just_fyi('Check both transactions from keycard multiaccount and from added account in network')
        for amount in [transaction_amount_keycard, transaction_amount_added]:
            self.network_api.find_transaction_by_unique_amount(basic_user['address'], amount)

        self.errors.verify_no_errors()

class TestKeycardCreateMultiaccountMultipleDevice(MultipleDeviceTestCase):

    @marks.testrail_id(5689)
    @marks.critical
    def test_keycard_create_login_resotore_unlock_same_seed(self):
        self.create_drivers(2)
        device_1, device_2 = SignInView(self.drivers[0]), SignInView(self.drivers[1])

        device_1.just_fyi("Create keycard account and save seed phrase")
        device_1.get_started_button.click()
        device_1.generate_key_button.click_until_presence_of_element(device_1.next_button)
        device_1.next_button.click_until_absense_of_element(device_1.element_by_translation_id("intro-wizard-title2"))
        keycard_flow = device_1.keycard_storage_button.click()
        keycard_flow.confirm_pin_and_proceed()
        seed_phrase = keycard_flow.backup_seed_phrase()
        device_1.maybe_later_button.wait_for_visibility_of_element(30)
        device_1.maybe_later_button.click_until_presence_of_element(device_1.lets_go_button)
        device_1.lets_go_button.click_until_absense_of_element(device_1.lets_go_button)
        device_1.no_thanks_metric_button.click()
        device_1.profile_button.wait_for_visibility_of_element(30)

        device_2.just_fyi("Restore same multiaccount from seed phrase on another device")
        device_2.recover_access(seed_phrase)

        device_1.just_fyi('Check that after creating keycard account balance is 0, not ...')
        wallet_1 = device_1.wallet_button.click()
        wallet_1.set_up_wallet()
        wallet_address = wallet_1.get_wallet_address()
        wallet_1.wallet_button.double_click()
        if wallet_1.status_account_total_usd_value.text != '0':
            self.errors.append("Account USD value is not 0, it is %s" % wallet_1.status_account_total_usd_value.text)
        public_key, default_username = device_1.get_public_key_and_username(return_username=True)
        profile_1 = device_1.get_profile_view()
        profile_1.logout()

        profile_1.just_fyi('Check that can login with keycard account')
        device_1.multi_account_on_login_button.wait_for_visibility_of_element(5)
        device_1.multi_account_on_login_button.click()
        if not keycard_flow.element_by_text_part(default_username).is_element_displayed():
            self.errors.append("%s is not found on keycard login screen!" % default_username)
        keycard_flow.connect_selected_card_button.click()
        keycard_flow.enter_default_pin()
        if not device_1.home_button.is_element_displayed(10):
            self.errors.append('Keycard user is not logged in')

        device_2.just_fyi("Check username and wallet address on another device")
        wallet_2 = device_2.wallet_button.click()
        wallet_2.set_up_wallet()
        wallet_address_2 = wallet_2.get_wallet_address()
        wallet_2.wallet_button.double_click()
        if wallet_address != wallet_address_2:
            self.errors.append('Wallet address on restored multiaccount is not equal to created keycard multiaccount')
        public_key_2, default_username_2 = device_2.get_public_key_and_username(return_username=True)
        if public_key != public_key_2:
            self.errors.append('Public key on restored multiaccount is not equal to created keycard multiaccount')
        if default_username_2 != default_username:
            self.errors.append('Username on restored multiaccount is not equal to created keycard multiaccount')

        device_1.just_fyi('Unlock keycard multiaccount at attempt to restore same multiaccount from seed')
        device_1.profile_button.click()
        profile_1.logout()
        device_1.access_key_button.click()
        device_1.enter_seed_phrase_button.click()
        device_1.seedphrase_input.click()
        device_1.seedphrase_input.set_value(seed_phrase)
        device_1.next_button.click()
        device_1.element_by_translation_id(id="unlock", uppercase=True).click()
        keycard_flow.connect_selected_card_button.click()
        keycard_flow.enter_default_pin()
        device_1_home = device_1.home_button.click()
        device_1_home.plus_button.click()
        if not device_1_home.start_new_chat_button.is_element_displayed():
             self.errors.append("Can't proceed using account after it's re-recover twice.")
        self.errors.verify_no_errors()
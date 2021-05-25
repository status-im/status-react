import random

from support.utilities import fill_string_with_char
from tests import marks, common_password, unique_password
from tests.base_test_case import SingleDeviceTestCase
from views.sign_in_view import SignInView
from tests.users import basic_user, transaction_senders, recovery_users


class TestCreateAccount(SingleDeviceTestCase):

    @marks.testrail_id(5356)
    @marks.critical
    def test_switch_users_special_char_password_and_add_new_account_logcat(self):
        sign_in = SignInView(self.driver)

        sign_in.just_fyi("Creating multiaccount with special char password")
        password=basic_user['special_chars_password']
        home = sign_in.create_user(password=password)
        public_key, default_username = home.get_public_key_and_username(return_username=True)
        profile = home.get_profile_view()
        profile.logout()

        sign_in.just_fyi('Check that cannot login with incorrect password, and can login with valid data')
        if sign_in.ok_button.is_element_displayed():
            sign_in.ok_button.click()
        sign_in.multi_account_on_login_button.click()
        sign_in.password_input.set_value(common_password)
        sign_in.sign_in_button.click()
        sign_in.element_by_translation_id("wrong-password").wait_for_visibility_of_element(20)
        if not sign_in.element_by_text(default_username).is_element_displayed():
            self.driver.fail('Username is not shown while login')
        sign_in.password_input.set_value(password)
        sign_in.sign_in_button.click()
        if not sign_in.home_button.is_element_displayed(10):
            self.driver.fail('User is not logged in')
        values_in_logcat = sign_in.find_values_in_logcat(password=password)
        if values_in_logcat:
            self.driver.fail(values_in_logcat)
        sign_in.profile_button.click()
        profile.logout()

        sign_in.just_fyi('Create another multiaccount')
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
        if sign_in.get_public_key_and_username() == public_key:
            self.driver.fail('New account was not created')

    @marks.testrail_id(5394)
    @marks.high
    def test_account_recovery_with_uppercase_whitespaces_seed_phrase_special_char_passw_logcat(self):
        user = transaction_senders['A']
        passphrase = user['passphrase']
        password = basic_user['special_chars_password']
        passphrase = fill_string_with_char(passphrase.upper(), ' ', 3, True, True)
        sign_in = SignInView(self.driver)

        sign_in.just_fyi("Restore multiaccount from uppercase seed phrase with whitespaces and set password with special chars")
        sign_in.recover_access(passphrase, password=password)
        profile = sign_in.profile_button.click()
        username = profile.default_username_text.text
        public_key = sign_in.get_public_key_and_username()

        sign_in.just_fyi("Check public key matches expected and no back up seed phrase is available")
        profile.privacy_and_security_button.click()
        profile.backup_recovery_phrase_button.click()
        if not profile.backup_recovery_phrase_button.is_element_displayed():
            self.errors.append('Back up seed phrase option is active for recovered account!')
        if username != user['username'] or public_key != user['public_key']:
            self.driver.fail('Incorrect user was recovered')
        values_in_logcat = sign_in.find_values_in_logcat(passphrase=passphrase, password=password)
        if values_in_logcat:
            self.driver.fail(values_in_logcat)
        profile.profile_button.double_click()

        sign_in.just_fyi("Check relogin with special char password")
        sign_in.relogin(password=basic_user['special_chars_password'])
        self.errors.verify_no_errors()

    @marks.testrail_id(5379)
    @marks.high
    def test_home_view(self):
        sign_in = SignInView(self.driver)
        sign_in.get_started_button.click()
        sign_in.generate_key_button.click()
        from views.sign_in_view import MultiAccountButton
        account_button = sign_in.get_multiaccount_by_position(position=random.randint(1, 4), element_class=MultiAccountButton)
        username = account_button.username.text
        account_button.click()
        sign_in.next_button.click()
        sign_in.next_button.click()
        sign_in.create_password_input.set_value(common_password)
        sign_in.confirm_your_password_input.set_value(common_password)
        sign_in.next_button.click()
        [element.wait_and_click(10) for element in (sign_in.maybe_later_button, sign_in.lets_go_button)]
        sign_in.no_thanks_metric_button.click()
        home = sign_in.get_home_view()
        texts = ["chat-and-transact", "follow-your-interests"]
        for text in texts:
            if not home.element_by_translation_id(text).is_element_displayed():
                self.errors.append("'%s' text is not shown" % self.get_translation_by_key(text))
        for chat in ('#status', '#crypto'):
            sign_in.element_by_text(chat).click()
            sign_in.back_button.click_until_presence_of_element(home.search_input)
        profile = home.profile_button.click()
        shown_username = profile.default_username_text.text
        if shown_username != username:
            self.errors.append("Default username '%s' doesn't match '%s'" % (shown_username, username))
        profile.home_button.click_until_presence_of_element(home.element_by_text('#status'))
        home.cross_icon_inside_welcome_screen_button.click()
        for chat in ('#status', '#crypto'):
            home.delete_chat_long_press(chat)
        if home.element_by_text(texts[0]).is_element_displayed():
            self.errors.append("'%s' text is shown, but welcome view was closed" % texts[0])
        home.relogin()
        if home.element_by_text(texts[0]).is_element_displayed():
            self.errors.append("'%s' text is shown after relogin, but welcome view was closed" % texts[0])
        if not home.element_by_translation_id("welcome-blank-message").is_element_displayed():
            self.errors.append("'%s' text is not shown after welcome view was closed" %  home.get_translation_by_key("welcome-blank-message"))

        self.errors.verify_no_errors()

    @marks.testrail_id(5363)
    @marks.high
    def test_pass_phrase_validation(self):
        sign_in = SignInView(self.driver)
        sign_in.get_started_button.click_until_presence_of_element(sign_in.access_key_button)
        sign_in.access_key_button.click()
        validations = [
            {
                'case': 'empty value',
                'phrase': '    ',
                'validation message': 'Required field',
                'words count': 1,
                'popup': False
            },
            {
                'case': '1 word seed',
                'phrase': 'a',
                'validation message': '',
                'words count': 1,
                'popup' : False
            },
            {
                'case': 'mnemonic but checksum validation fails',
                'phrase': 'one two three four five six seven eight nine ten eleven twelve',
                'validation message': '',
                'words count': 12,
                'popup': True
            },
        ]

        sign_in.just_fyi("check that seed phrase is required (can't be empty)")
        sign_in.enter_seed_phrase_button.click()
        sign_in.next_button.click()
        if sign_in.reencrypt_your_key_button.is_element_displayed():
            self.errors.append("Possible to create account with empty seed phrase")
        for validation in validations:
            sign_in.just_fyi("Checking %s" % validation.get('case'))
            phrase, msg, words_count, popup = validation.get('phrase'), \
                                            validation.get('validation message'), \
                                            validation.get('words count'),\
                                            validation.get('popup')
            if sign_in.access_key_button.is_element_displayed():
                sign_in.access_key_button.click()
            if sign_in.enter_seed_phrase_button.is_element_displayed():
                sign_in.enter_seed_phrase_button.click()

            sign_in.seedphrase_input.set_value(phrase)

            if msg:
                if not sign_in.element_by_text(msg).is_element_displayed():
                    self.errors.append('"{}" message is not shown'.format(msg))

            sign_in.just_fyi('check that words count is shown')
            if words_count:
                if not sign_in.element_by_text('%s word' % words_count):
                    self.errors.append('"%s word" is not shown ' % words_count)
            else:
                if not sign_in.element_by_text('%s words' % words_count):
                    self.errors.append('"%s words" is not shown ' % words_count)

            sign_in.just_fyi('check that "Next" is disabled unless we use allowed count of words')
            if words_count != 12 or 15 or 18 or 21 or 24:
                sign_in.next_button.click()
                if sign_in.reencrypt_your_key_button.is_element_displayed():
                    self.errors.append("Possible to create account with wrong count (%s) of words" % words_count)

            sign_in.just_fyi('check behavior for popup "Custom seed phrase"')
            if popup:

                if not sign_in.custom_seed_phrase_label.is_element_displayed():
                    self.errors.append("Popup about custom seed phrase is not shown")
                sign_in.cancel_custom_seed_phrase_button.click()

            sign_in.click_system_back_button()

        self.errors.verify_no_errors()

    @marks.testrail_id(5460)
    @marks.medium
    def test_create_account_short_and_mismatch_password(self):
        sign_in = SignInView(self.driver)
        sign_in.get_started_button.click()
        sign_in.generate_key_button.click()
        sign_in.next_button.click()
        sign_in.next_button.click()
        cases = ['password is not confirmed', 'password is too short', "passwords don't match"]
        error = "Can create multiaccount when"

        sign_in.just_fyi('Checking case when %s' % cases[0])
        sign_in.create_password_input.send_keys('123456')
        sign_in.next_button.click()
        if sign_in.maybe_later_button.is_element_displayed(10):
            self.driver.fail('%s  %s' % (error, cases[0]))

        sign_in.just_fyi('Checking case when %s'% cases[1])
        sign_in.create_password_input.send_keys('123456')
        [field.send_keys('123456') for field in (sign_in.create_password_input, sign_in.confirm_your_password_input)]
        sign_in.confirm_your_password_input.delete_last_symbols(1)
        sign_in.create_password_input.delete_last_symbols(1)
        sign_in.next_button.click()
        if sign_in.maybe_later_button.is_element_displayed(10):
            self.driver.fail('%s  %s' % (error, cases[1]))

        sign_in.just_fyi("Checking case %s" % cases[2])
        sign_in.create_password_input.send_keys('1234565')
        sign_in.confirm_your_password_input.send_keys('1234567')
        if not sign_in.element_by_translation_id("password_error1").is_element_displayed():
            self.errors.append("'%s' is not shown" % sign_in.get_translation_by_key("password_error1"))
        sign_in.next_button.click()
        if sign_in.maybe_later_button.is_element_displayed(10):
            self.driver.fail('%s  %s' % (error, cases[2]))

        self.errors.verify_no_errors()

    @marks.testrail_id(5455)
    @marks.medium
    def test_recover_accounts_with_certain_seedphrase(self):
        sign_in = SignInView(self.driver)
        for phrase, account in recovery_users.items():
            home_view = sign_in.recover_access(passphrase=phrase, password=unique_password)
            wallet_view = home_view.wallet_button.click()
            wallet_view.set_up_wallet()
            address = wallet_view.get_wallet_address()
            if address != account:
                self.errors.append('Restored wallet address "%s" does not match expected "%s"' % (address, account))
            profile_view = home_view.profile_button.click()
            profile_view.logout()
        self.errors.verify_no_errors()




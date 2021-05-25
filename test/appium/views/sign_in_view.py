from appium.webdriver.common.touch_action import TouchAction
from selenium.common.exceptions import NoSuchElementException
import os
from tests import common_password, appium_root_project_path, app_path
from views.base_element import Button, EditBox, Text
from views.base_view import BaseView

class MultiAccountButton(Button):
    class Username(Text):
        def __init__(self, driver, locator_value):
            super(MultiAccountButton.Username, self).__init__(driver,
                                                              xpath="%s//android.widget.TextView[@content-desc='username']" % locator_value)
    def __init__(self, driver, position=1):
        super(MultiAccountButton, self).__init__(driver,
                                                 xpath="//*[@content-desc='select-account-button-%s']" % position)
        self.username = self.Username(driver, self.locator)


class MultiAccountOnLoginButton(Button):
    def __init__(self, driver, position=1):
        super(MultiAccountOnLoginButton, self).__init__(driver,
                                                        xpath="(//*[@content-desc='chat-icon'])[%s]/.." % position)

    @property
    def account_logo(self):
        class AccountLogo(Text):
            def __init__(self, driver, parent_locator: str):
                super().__init__(driver, xpath="%s//*[@content-desc='chat-icon']" % parent_locator)

        return AccountLogo(self.driver, self.locator)


class MoveAndResetButton(Button):
    def __init__(self, driver):
        super().__init__(driver, accessibility_id="move-and-reset-button")

    def navigate(self):
        from views.keycard_view import KeycardView
        return KeycardView(self.driver)


class BeginRecoveryButton(Button):
    def __init__(self, driver):
        super().__init__(driver, translation_id="keycard-recovery-intro-button-text")

    def navigate(self):
        from views.keycard_view import KeycardView
        return KeycardView(self.driver)

    def click(self):
        self.scroll_to_element().click()
        return self.navigate()


class SignInButton(Button):
    def __init__(self, driver):
        super().__init__(driver, translation_id="sign-in")

    def navigate(self):
        from views.home_view import HomeView
        return HomeView(self.driver)


class AccessKeyButton(Button):
    def __init__(self, driver):
        super().__init__(driver, translation_id="access-existing-keys")

    def click(self):
        self.scroll_to_element().click()
        return self.navigate()

class NoThanksButton(Button):
    def __init__(self, driver):
        super().__init__(driver, accessibility_id="opt-out-button")

    def click(self):
        size = self.driver.get_window_size()
        self.driver.swipe(500, size["height"] * 0.8, 500, size["height"] * 0.05)
        return super().click()


class KeycardKeyStorageButton(Button):
    def __init__(self, driver):
        super().__init__(driver, accessibility_id="select-storage-:advanced")

    def navigate(self):
        from views.keycard_view import KeycardView
        return KeycardView(self.driver)

    def click(self):
        self.scroll_to_element().click()
        return self.navigate()


class PrivacyPolicyLink(Button):
    def __init__(self, driver):
        super(PrivacyPolicyLink, self).__init__(driver, xpath="//*[contains(@text, 'privacy policy')]")

    def click(self):
        element = self.find_element()
        location = element.location
        size = element.size
        x = int(location['x'] + size['width'] * 0.9)
        y = int(location['y'] + size['height'] * 0.8)
        TouchAction(self.driver).tap(None, x, y).perform()
        self.driver.info('Click on link %s' % self.name)
        return self.navigate()

    def navigate(self):
        from views.web_views.base_web_view import BaseWebView
        return BaseWebView(self.driver)


class SignInView(BaseView):

    def __init__(self, driver):
        super().__init__(driver)
        self.driver = driver

        self.password_input = EditBox(self.driver, accessibility_id="password-input")
        self.sign_in_button = SignInButton(self.driver)
        self.access_key_button = AccessKeyButton(self.driver)
        self.generate_key_button = Button(self.driver, translation_id="generate-new-key")
        self.your_keys_more_icon = Button(self.driver, accessibility_id="your-keys-more-icon")
        self.generate_new_key_button = Button(self.driver, accessibility_id="generate-a-new-key")
        self.create_password_input = EditBox(self.driver,
                                             xpath="(//android.widget.EditText[@content-desc='password-input'])[1]")
        self.confirm_your_password_input = EditBox(self.driver,
                                                   xpath="(//android.widget.EditText[@content-desc='password-input'])[2]")
        self.enable_notifications_button = Button(self.driver, accessibility_id="enable-notifications")
        self.no_thanks_metric_button = NoThanksButton(self.driver)
        self.maybe_later_button = Button(self.driver, accessibility_id="maybe-later")
        self.privacy_policy_link = PrivacyPolicyLink(self.driver)
        self.lets_go_button = Button(self.driver, accessibility_id="lets-go-button")
        self.keycard_storage_button = KeycardKeyStorageButton(self.driver)
        self.first_username_on_choose_chat_name = Text(self.driver,
                                                       xpath="//*[@content-desc='select-account-button-0']//android.widget.TextView[1]")
        self.get_keycard_banner = Button(self.driver, translation_id="get-a-keycard")

        #keycard recovery
        self.recover_with_keycard_button = Button(self.driver, accessibility_id="recover-with-keycard-button")
        self.begin_recovery_button = BeginRecoveryButton(self.driver)
        self.pair_to_this_device_button = Button(self.driver, translation_id="pair-card")

        # restore from seed phrase
        self.seedphrase_input = EditBox(self.driver, xpath="//android.widget.EditText")
        self.enter_seed_phrase_button = Button(self.driver, accessibility_id="enter-seed-phrase-button")
        self.reencrypt_your_key_button = Button(self.driver, translation_id="re-encrypt-key")

        # migrate multiaccount
        self.options_button = Button(self.driver, accessibility_id="sign-in-options")
        self.manage_keys_and_storage_button = Button(self.driver, accessibility_id="manage-keys-and-storage-button")
        self.multi_account_on_login_button = MultiAccountOnLoginButton(self.driver)
        self.move_keystore_file_option =  Button(self.driver, accessibility_id="move-keystore-file")
        self.move_and_reset_button = MoveAndResetButton(self.driver)
        self.choose_storage_button = Button(self.driver, translation_id="choose-storage")
        self.enter_seed_phrase_next_button = Button(self.driver, translation_id="enter-seed-phrase")
        self.keycard_required_option = Button(self.driver, translation_id="empty-keycard-required")

        # errors
        self.custom_seed_phrase_label = Text(self.driver, translation_id="custom-seed-phrase")
        self.continue_custom_seed_phrase_button = Button(self.driver, accessibility_id="continue-custom-seed-phrase")
        self.cancel_custom_seed_phrase_button = Button(self.driver, accessibility_id="cancel-custom-seed-phrase")

    def create_user(self, password=common_password, keycard=False, enable_notifications=False,
                    second_user=False, previous_release=False):
        self.driver.info("**Creating new multiaccount (password:%s, keycard:%s)**" % (password, str(keycard)))
        if not second_user:
            self.get_started_button.click()
        self.generate_key_button.click_until_presence_of_element(self.next_button)
        self.next_button.click_until_absense_of_element(self.element_by_translation_id("intro-wizard-title2"))
        if keycard:
            keycard_flow = self.keycard_storage_button.click()
            keycard_flow.confirm_pin_and_proceed()
            keycard_flow.backup_seed_phrase()
        else:
            self.next_button.click()
            self.create_password_input.set_value(password)
            self.confirm_your_password_input.set_value(password)
            self.next_button.click()
        self.maybe_later_button.wait_for_visibility_of_element(30)
        if enable_notifications:
            self.enable_notifications_button.click()
        else:
            self.maybe_later_button.click_until_presence_of_element(self.lets_go_button)
        self.lets_go_button.click_until_absense_of_element(self.lets_go_button)
        # TODO: backward compatibility with 1.13
        if not previous_release:
            self.no_thanks_metric_button.click()
        self.profile_button.wait_for_visibility_of_element(30)
        self.driver.info("**New multiaccount is created successfully!**")
        return self.get_home_view()

    def recover_access(self, passphrase: str, password: str = common_password,
                       keycard=False, enable_notifications=False, previous_release=False):
        self.driver.info("**Recover access(password:%s, keycard:%s)**" % (password, str(keycard)))
        self.get_started_button.click_until_presence_of_element(self.access_key_button)
        self.access_key_button.click()
        self.enter_seed_phrase_button.click()
        self.seedphrase_input.click()
        self.seedphrase_input.set_value(passphrase)
        self.next_button.click()
        self.reencrypt_your_key_button.click()
        if keycard:
            keycard_flow = self.keycard_storage_button.click()
            keycard_flow.confirm_pin_and_proceed()
        else:
            self.next_button.click()
            self.create_password_input.set_value(password)
            self.confirm_your_password_input.set_value(password)
            self.next_button.click_until_presence_of_element(self.maybe_later_button)
        self.maybe_later_button.wait_for_element(30)
        if enable_notifications:
            self.enable_notifications_button.click_until_presence_of_element(self.lets_go_button)
        else:
            self.maybe_later_button.click_until_presence_of_element(self.lets_go_button)
        self.lets_go_button.click()
        # TODO: backward compatibility with 1.13
        if not previous_release:
            self.no_thanks_metric_button.click()
        self.profile_button.wait_for_visibility_of_element(30)
        self.driver.info("**Multiaccount is recovered successfully!**")
        return self.get_home_view()

    def sign_in(self, password=common_password, keycard=False, position=1, upgraded=False):
        self.driver.info("**Sign in (password:%s, keycard:%s)**" % (password, str(keycard)))
        self.multi_account_on_login_button.wait_for_visibility_of_element(30)
        self.get_multiaccount_by_position(position).click()

        if keycard:
            from views.keycard_view import KeycardView
            keycard_view = KeycardView(self.driver)
            keycard_view.one_button.wait_for_visibility_of_element(10)
            keycard_view.connect_selected_card_button.click()
            keycard_view.enter_default_pin()
        else:
            self.password_input.set_value(password)
            self.sign_in_button.click()
        # TODO: backward compatibility with 1.13
        if upgraded:
            self.click_system_back_button()
            self.no_thanks_metric_button.click()
        self.driver.info("**Signed in successfully!**")
        return self.get_home_view()


    def get_multiaccount_by_position(self, position: int, element_class=MultiAccountOnLoginButton):
        account_button = element_class(self.driver, position)
        if account_button.is_element_displayed():
            return account_button
        else:
            raise NoSuchElementException(
                'Device %s: Unable to find multiaccount by position %s' % (self.driver.number, position)) from None

    def open_weblink_and_login(self, url_weblink):
        self.driver.info("**Open weblink %s**" % url_weblink)
        self.open_universal_web_link(url_weblink)
        self.sign_in()

    def import_db(self, user, import_db_folder_name):
        self.just_fyi('**Importing database**')
        import_file_name = 'export.db'
        home = self.recover_access(user['passphrase'], previous_release=True)
        profile = home.profile_button.click()
        full_path_to_file = os.path.join(appium_root_project_path, 'views/upgrade_dbs/%s/%s' %
                                         (import_db_folder_name, import_file_name))
        profile.logout()
        self.multi_account_on_login_button.wait_for_visibility_of_element(30)
        self.get_multiaccount_by_position(1).click()
        self.password_input.set_value(common_password)
        self.driver.push_file(source_path=full_path_to_file, destination_path=app_path + import_file_name)
        self.options_button.click()
        self.element_by_text('Import unencrypted').click()
        self.element_by_text('Import unencrypted').wait_for_invisibility_of_element(40)
        self.sign_in_button.click()
        self.home_button.wait_for_element(40)
        self.just_fyi('**Importing database is finished!**')
        return self.get_home_view()

.PHONY: nix-add-gcroots clean nix-clean run-metro test release _list _fix-node-perms _tmpdir-mk _tmpdir-rm

help: SHELL := /bin/sh
help: ##@other Show this help
	@perl -e '$(HELP_FUN)' $(MAKEFILE_LIST)

# This is a code for automatic help generator.
# It supports ANSI colors and categories.
# To add new item into help output, simply add comments
# starting with '##'. To add category, use @category.
GREEN  := $(shell tput -Txterm setaf 2)
RED    := $(shell tput -Txterm setaf 1)
WHITE  := $(shell tput -Txterm setaf 7)
YELLOW := $(shell tput -Txterm setaf 3)
RESET  := $(shell tput -Txterm sgr0)
HELP_FUN = \
		   %help; \
		   while(<>) { push @{$$help{$$2 // 'options'}}, [$$1, $$3] if /^([a-zA-Z\-]+)\s*:.*\#\#(?:@([a-zA-Z\-]+))?\s(.*)$$/ }; \
		   print "Usage: make [target]\n\nSee STARTING_GUIDE.md for more info.\n\n"; \
		   for (sort keys %help) { \
			   print "${WHITE}$$_:${RESET}\n"; \
			   for (@{$$help{$$_}}) { \
				   $$sep = " " x (32 - length $$_->[0]); \
				   print "  ${YELLOW}$$_->[0]${RESET}$$sep${GREEN}$$_->[1]${RESET}\n"; \
			   }; \
			   print "\n"; \
		   }
HOST_OS := $(shell uname | tr '[:upper:]' '[:lower:]')

# This can come from Jenkins
ifndef BUILD_TAG
export BUILD_TAG := $(shell git rev-parse --short HEAD)
endif

# We don't want to use /run/user/$UID because it runs out of space too easilly
export TMPDIR = /tmp/tmp-status-react-$(BUILD_TAG)
# this has to be specified for both the Node.JS server process and the Qt process
export REACT_SERVER_PORT ?= 5001

# Our custom config is located in nix/nix.conf
export NIX_CONF_DIR = $(PWD)/nix
# Location of symlinks to derivations that should not be garbage collected
export _NIX_GCROOTS = /nix/var/nix/gcroots/per-user/$(USER)/status-react
# Defines which variables will be kept for Nix pure shell, use semicolon as divider
export _NIX_KEEP ?= TMPDIR,BUILD_ENV,STATUS_GO_SRC_OVERRIDE,NIMBUS_SRC_OVERRIDE

# Useful for Andoird release builds
TMP_BUILD_NUMBER := $(shell ./scripts/version/gen_build_no.sh | cut -c1-10)

# MacOS root is read-only, read nix/README.md for details
UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Darwin)
export NIX_IGNORE_SYMLINK_STORE=1
endif

#----------------
# Nix targets
#----------------

# WARNING: This has to be located right before the targets
ifdef IN_NIX_SHELL
SHELL := env bash
else
SHELL := ./nix/scripts/shell.sh
endif

shell: ##@prepare Enter into a pre-configured shell
ifndef IN_NIX_SHELL
	@ENTER_NIX_SHELL
else
	@echo "${YELLOW}Nix shell is already active$(RESET)"
endif

nix-repl: SHELL := /bin/sh
nix-repl: ##@nix Start an interactive Nix REPL
	nix repl default.nix

nix-gc-protected: SHELL := /bin/sh
nix-gc-protected:
	@echo -e "$(YELLOW)The following paths are protected:$(RESET)" && \
	ls -1 $(_NIX_GCROOTS) | sed 's/^/ - /'

nix-gc: export TARGET := nix
nix-gc: nix-gc-protected ##@nix Garbage collect all packages older than 20 days from /nix/store
	nix-store --gc

nix-clean: export TARGET := default
nix-clean: ##@nix Remove all status-react build artifacts from /nix/store
	nix/scripts/clean.sh

nix-purge: SHELL := /bin/sh
nix-purge: ##@nix Completely remove Nix setup, including /nix directory
	nix/scripts/purge.sh

nix-update-gradle: export TARGET := gradle
nix-update-gradle: ##@nix Update maven nix expressions based on current gradle setup
	nix/deps/gradle/generate.sh

nix-update-clojure: export TARGET := clojure
nix-update-clojure: ##@nix Update maven Nix expressions based on current clojure setup
	nix/deps/clojure/generate.sh

nix-update-gems: export TARGET := default
nix-update-gems: ##@nix Update Ruby gems in fastlane/Gemfile.lock and fastlane/gemset.nix
	fastlane/update.sh

nix-update-pods: export TARGET := ios
nix-update-pods: ##@nix Update CocoaPods in ios/Podfile.lock
	cd ios && pod update

#----------------
# General targets
#----------------

_fix-node-perms: SHELL := /bin/sh
_fix-node-perms: ##@prepare Fix permissions so that directory can be cleaned
	$(shell test -d node_modules && chmod -R 744 node_modules)
	$(shell test -d node_modules.tmp && chmod -R 744 node_modules.tmp)

_tmpdir-mk: SHELL := /bin/sh
_tmpdir-mk: ##@prepare Create a TMPDIR for temporary files
	@mkdir -p "$(TMPDIR)"
# Make sure TMPDIR exists every time make is called
-include _tmpdir-mk

_tmpdir-rm: SHELL := /bin/sh
_tmpdir-rm: ##@prepare Remove TMPDIR
	rm -fr "$(TMPDIR)"

# Remove directories and ignored files
clean: SHELL := /bin/sh
clean: _fix-node-perms _tmpdir-rm ##@prepare Remove all output folders
	git clean -dXf

# Remove directories, ignored and non-ignored files
purge: SHELL := /bin/sh
purge: _fix-node-perms _tmpdir-rm ##@prepare Remove all output folders
	git clean -dxf


watchman-clean: export TARGET := watchman
watchman-clean: ##@prepare Delete repo directory from watchman
	watchman watch-del $${STATUS_REACT_HOME}

pod-install: export TARGET := ios
pod-install: ##@prepare Run 'pod install' to install podfiles and update Podfile.lock
	cd ios && pod install; cd --

update-fleets: ##@prepare Download up-to-date JSON file with current fleets state
	curl -s https://fleets.status.im/ \
		| jq --indent 4 --sort-keys . \
		> resources/config/fleets.json

keystore: export TARGET := keytool
keystore: export KEYSTORE_PATH ?= $(HOME)/.gradle/status-im.keystore
keystore: ##@prepare Generate a Keystore for signing Android APKs
	@./scripts/generate-keystore.sh

fdroid-max-watches: SHELL := /bin/sh
fdroid-max-watches: ##@prepare Bump max_user_watches to avoid ENOSPC errors
	sysctl fs.inotify.max_user_watches=524288

fdroid-nix-dir: SHELL := /bin/sh
fdroid-nix-dir: ##@prepare Create /nix directory for F-Droid Vagrant builders
	mkdir -m 0755 /nix
	chown vagrant /nix

fdroid-fix-tmp: SHELL := /bin/sh
fdroid-fix-tmp: ##@prepare Fix TMPDIR permissions so Vagrant user is the owner
	chown -R vagrant "$(TMPDIR)"

fdroid-build-env: fdroid-max-watches fdroid-nix-dir fdroid-fix-tmp ##@prepare Setup build environment for F-Droud build

xcode-clean: SHELL := /bin/sh
xcode-clean: XCODE_HOME := $(HOME)/Library/Developer/Xcode
xcode-clean: ##@prepare Clean XCode derived data and archives
	rm -fr $(XCODE_HOME)/DerivedData/StatusIm-* $(XCODE_HOME)/Archives/*/StatusIm*

#----------------
# Release builds
#----------------
release: release-android release-ios ##@build Build release for Android and iOS

release-android: export BUILD_ENV ?= prod
release-android: export BUILD_TYPE ?= nightly
release-android: export BUILD_NUMBER ?= $(TMP_BUILD_NUMBER)
release-android: export KEYSTORE_PATH ?= $(HOME)/.gradle/status-im.keystore
release-android: export ANDROID_APK_SIGNED ?= true
release-android: export ANDROID_ABI_SPLIT ?= false
release-android: export ANDROID_ABI_INCLUDE ?= armeabi-v7a;arm64-v8a;x86
release-android: keystore ##@build Build release for Android
	scripts/release-android.sh

release-fdroid: export BUILD_ENV = prod
release-fdroid: export BUILD_TYPE = release
release-fdroid: export ANDROID_APK_SIGNED = false
release-fdroid: export ANDROID_ABI_SPLIT = false
release-fdroid: export ANDROID_ABI_INCLUDE = armeabi-v7a;arm64-v8a;x86;x86_64
release-fdroid: ##@build Build release for F-Droid
	echo "GOOGLE_FREE=1" >> .env.release
	scripts/release-android.sh

release-ios: export TARGET := ios
release-ios: export BUILD_ENV ?= prod
release-ios: watchman-clean ##@build Build release for iOS release
	@git clean -dxf -f target/ios && \
	$(MAKE) jsbundle-ios && \
	xcodebuild -workspace ios/StatusIm.xcworkspace -scheme StatusIm -configuration Release -destination 'generic/platform=iOS' -UseModernBuildSystem=N clean archive

jsbundle-android: SHELL := /bin/sh
jsbundle-android: export TARGET := android
jsbundle-android: export BUILD_ENV ?= prod
jsbundle-android: ##@jsbundle Compile JavaScript and Clojurescript into app directory
	# Call nix-build to build the 'targets.mobile.android.jsbundle' attribute and copy the.js files to the project root
	nix/scripts/build.sh targets.mobile.android.jsbundle && \
	mv result/* ./

jsbundle-ios: export TARGET := ios
jsbundle-ios: export BUILD_ENV ?= prod
jsbundle-ios: ##@jsbundle Compile JavaScript and Clojure into index.ios.js
	yarn shadow-cljs release mobile

#--------------
# status-go lib
#--------------

status-go-android: SHELL := /bin/sh
status-go-android: ##@status-go Compile status-go for Android app
	nix/scripts/build.sh targets.status-go.mobile.android

status-go-ios: SHELL := /bin/sh
status-go-ios: ##@status-go Compile status-go for iOS app
	nix/scripts/build.sh targets.status-go.mobile.ios

#--------------
# Watch, Build & Review changes
#--------------

run-clojure: export TARGET := clojure
run-clojure: ##@run Watch for and build Clojure changes for mobile
	yarn shadow-cljs watch mobile

run-metro: export TARGET := clojure
run-metro: ##@run Start Metro to build React Native changes
	@scripts/start-react-native.sh

run-re-frisk: export TARGET := clojure
run-re-frisk: ##@run Start re-frisk server
	yarn shadow-cljs run re-frisk-remote.core/start

# TODO: Migrate this to a Nix recipe, much the same way as nix/mobile/android/targets/release-android.nix
run-android: export TARGET := android
run-android: ##@run Build Android APK and start it on the device
	npx react-native run-android --appIdSuffix debug

SIMULATOR=
run-ios: export TARGET := ios
run-ios: ##@run Build iOS app and start it in a simulator/device
ifneq ("$(SIMULATOR)", "")
	npx react-native run-ios --simulator="$(SIMULATOR)"
else
	npx react-native run-ios
endif

#--------------
# Tests
#--------------

lint: export TARGET := clojure
lint: ##@test Run code style checks
	yarn clj-kondo --confg .clj-kondo/config.edn --lint src && \
	TARGETS=$$(git diff --diff-filter=d --cached --name-only src && echo src) && \
	clojure -Scp "$$CLASS_PATH" -m cljfmt.main check --indents indentation.edn $$TARGETS

lint-fix: export TARGET := clojure
lint-fix: ##@test Run code style checks and fix issues
	clojure -Scp "$$CLASS_PATH" -m cljfmt.main fix src --indents indentation.edn

test: export TARGET := clojure
test: ##@test Run tests once in NodeJS
	yarn shadow-cljs compile mocks && \
	yarn shadow-cljs compile test && \
	node --require ./test-resources/override.js target/test/test.js

coverage: ##@test Run tests once in NodeJS generating coverage
	@scripts/run-coverage.sh

#--------------
# Other
#--------------

geth-connect: export TARGET := android-env
geth-connect: ##@other Connect to Geth on the device
	adb forward tcp:8545 tcp:8545 && \
	build/bin/geth attach http://localhost:8545

android-clean: export TARGET := gradle
android-clean: ##@prepare Clean Gradle state
	git clean -dxf -f ./android/app/build; \
	[[ -d android/.gradle ]] && cd android && ./gradlew clean

android-ports: export TARGET := android-env
android-ports: ##@other Add proxies to Android Device/Simulator
	adb reverse tcp:8081 tcp:8081 && \
	adb reverse tcp:3449 tcp:3449 && \
	adb reverse tcp:4567 tcp:4567 && \
	adb forward tcp:5561 tcp:5561

android-devices: export TARGET := android-env
android-devices: ##@other Invoke adb devices
	adb devices

android-logcat: export TARGET := android-env
android-logcat: ##@other Read status-react logs from Android phone using adb
	adb logcat | grep -e RNBootstrap -e ReactNativeJS -e ReactNative -e StatusModule -e StatusNativeLogs -e 'F DEBUG   :' -e 'Go      :' -e 'GoLog   :' -e 'libc    :'

android-install: export TARGET := android-env
android-install: export BUILD_TYPE ?= release
android-install: ##@other Install APK on device using adb
	adb install result/app-$(BUILD_TYPE).apk

_list: SHELL := /bin/sh
_list:
	@$(MAKE) -pRrq -f $(lastword $(MAKEFILE_LIST)) : 2>/dev/null | awk -v RS= -F: '/^# File/,/^# Finished Make data base/ {if ($$1 !~ "^[#.]") {print $$1}}' | sort | egrep -v -e '^[^[:alnum:]]' -e '^$@$$'

#--------------
# REPLs
#--------------

repl-clojure: export TARGET := clojure
repl-clojure: ##@repl Start Clojure repl for mobile App
	yarn shadow-cljs cljs-repl mobile

repl-nix: nix-repl ##@repl Start an interactive Nix REPL

fdroid-pr: TARGET := android
fdroid-pr: GL_USERNAME ?= "${USERNAME}"
fdroid-pr: ##@fdroid Create F-Droid release PR
	scripts/fdroid-pr.sh

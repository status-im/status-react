# Description

This document describes how to update Status APK builds for the [F-Droid](https://f-droid.org/) Android application catalogue.

# Intro

In simplest terms F-Droid requires a YAML file that defines the steps necessary to create a universal unsigned APK build. This is achieved by submitting a new app versions into the `metadata/im.status.ethereum.yml` file in the [fdroiddata](https://gitlab.com/fdroid/fdroiddata) repository.

The app builds defined this way run on servers that generate the unsigned APKs using the [fdroidserver](https://gitlab.com/fdroid/fdroidserver) software. The [server setup](https://f-droid.org/en/docs/Build_Server_Setup/) is quite involved but is not necessary unless you want to run your own instance. Normally the applications defines in `fdroiddata` are built by servers maintained by [the F-Droid volunteers](https://f-droid.org/en/contribute/).

First release of Status app was merged in [fdroid/fdroiddata#7179](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/7179).

:warning: __WARNING__: Once changes are commited into `fdroiddata` repo they __cannot be changed__.

# Adding New Versions

There are two ways - automated and manual - described below.

## Automated

#### Requirements:

- GitLab account
- Forked copy of the [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) repo
- Link to the release published

#### Steps

1. First checkout the branch/commit you want to release
    ```
    git checkout release/1.12.x
    ```
2. Run the F-Droid PR script
    ```
    make fdroid-pr \
        USERNAME=cammellos \
        RELEASE_LINK=https://releases.status.im/StatusIm-Mobile-v1.12.0-cfb825-universal.apk
    ```

This will update the file, commit and push to the [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) repo. Then you can create a PR from the GitLab interface.

## Manual

You can find our configuration file at [`metadata/im.status.ethereum.yml`](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/im.status.ethereum.yml)

The file defines all the necessary metadata like `SourceCode`, `Website`, or `License`, but the most important key is `Builds`, which looks like this:
```yml
Builds:
  - versionName: 1.12.0
    versionCode: 2021022512
    commit: cfb825a11b61d312af8cb5d36686af540c31f481
    sudo:
      - cd build/im.status.ethereum
      - make fdroid-build-env
    init: nix/scripts/setup.sh
    output: result/app-release-unsigned.apk
    scanignore:
      - android/build.gradle
    scandelete:
      - ios
    build: make release-fdroid BUILD_NUMBER=$$VERCODE$$
```
It contains a list of objects defining each release of the application. In order to add a new release simply copy a previous release object and adjust the following values:

* `versionName` - String version like `1.12.0`.
* `versionCode` - Android `versionCode`. Normally it is generated from timestamp at build time.
* `commit` - Specific commit SHA1 from which the given release was built.

The `versionCode` should be the same as the one in build that was uploaded to Play Store.
It can be found in the build logs or by using:
```
 > make shell TARGET=android
 > apkanalyzer manifest version-code StatusIm-Mobile-v1.12.0.apk
2021022512
```

At the bottom of the file you should also update the following keys:

* `CurrentVersion` - Same as the new `versionName` added
* `CurrentVersionCode` - Same as the `versionCode` added

Then submit a merge request to the [fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata) repository.

:warning: __WARNING__: Currently GitLab PR builds will fail due to running as `root` instead of `vagrant` and failing to install Nix.

# Checking Builds

The simplest way to test if the app builds via F-Droid is to use the Docker image:
https://gitlab.com/fdroid/docker-executable-fdroidserver

Which is available under `registry.gitlab.com/fdroid/docker-executable-fdroidserver:latest`.

Because we use Nix to build the mobile app we need to slightly modify the image with this `Dockerfile`:
```Dockerfile
FROM registry.gitlab.com/fdroid/docker-executable-fdroidserver:latest
ARG BUILDER_UID=1000
ENV BUILDER_USER=vagrant
RUN useradd -u $BUILDER_UID $BUILDER_USER
RUN mkdir -m 0755 /nix /home/$BUILDER_USER \
 && chown -R $BUILDER_USER /nix /home/$BUILDER_USER
```
Build it using:
```
docker build --build-arg=BUILDER_UID=$UID -t statusteam/docker-executable-fdroidserver:latest .
```
Then clone the [fdroiddata](https://gitlab.com/fdroid/fdroiddata) and [fdroidserver](https://gitlab.com/fdroid/fdroidserver) repos and use it to test the build of a specific Status Android app version: `1.12.0`
```
git clone https://gitlab.com/fdroid/fdroidserver
git clone https://gitlab.com/fdroid/fdroiddata
docker run --rm \
  -u $UID:$GID -e USER=vagrant \
  -v $PWD/fdroiddata:/repo \
  -v $PWD/fdroidserver:/fdroidserver \
  statusteam/docker-executable-fdroidserver:latest \
  build im.status.ethereum
```
We have to create a user and specify the UID because Nix cannot run as `root` and that is the default user for the F-Droid Docker image. By adding our own user and setting the UID we also make it possible to mount folders like `fdroiddata` and `fdroidserver`.

You can specify a `--verbose` flag for `build` command for additional information.
You can provide `--storage-opt size=30G` flag if Docker complains about running out of space.

You should also run `lint` and `rewritemeta` for the App ID to verify and fix the YAML metadata formatting.

# Details

The original research was done in [#8512](https://github.com/status-im/status-react/issues/8512).

Normally F-Droid server wants to run Gradle itself, but we do not specify the `gradle` key in order to run `make release-fdroid` ourselves in `build` step. We also add `android/build.gradle` to `scanignore` to avoid F-Droid trying to use Gradle directly.

Once the PR is merged it may take a few days for the F-Droid server farm to build and deploy the new version to their site and app. You can look up the current state of builds [here](https://f-droid.org/wiki/index.php?title=Special:RecentChanges&days=7&from=&hidebots=0&hideanons=1&hideliu=1&limit=500) and look for your App ID and a `deploy` change after it.

# F-Droid Build

F-Droid requires submitted applications to be free of non Open Source software. For this reason we need to exclude certain Google libraries at build time.

This is achieved by setting `GOOGLE_FREE=1` in the `.env.release` file. This affects two things:

1. [Clojure code conditional](../src/status_im/acquisition/core.cljs) which stops use of `get-referrer`.
2. [Gradle build configuration](../android/app/build.gradle) by excluding Google libraries.

Both are affected by the `.env` file and both are required.

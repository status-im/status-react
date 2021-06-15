# Override some packages and utilities in 'pkgs'
# and make them available globally via callPackage.
#
# For more details see:
# - https://nixos.wiki/wiki/Overlays
# - https://nixos.org/nixos/nix-pills/callpackage-design-pattern.html

self: super:

let inherit (super) stdenv stdenvNoCC callPackage;
in rec {
  # Fix for MacOS
  mkShell = super.mkShell.override { stdenv = stdenvNoCC; };

  # Various utilities
  utils = callPackage ./tools/utils.nix { };
  lib = (super.lib or { }) // (import ./lib {
    inherit (super) lib;
    inherit (self) config;
  });

  # Project dependencies
  deps = {
    clojure = callPackage ./deps/clojure { };
    gradle = callPackage ./deps/gradle { };
    nodejs = callPackage ./deps/nodejs { };
    nodejs-patched = callPackage ./deps/nodejs-patched { };
    react-native = callPackage ./deps/react-native { };
  };

  # For parsing gradle.properties into an attrset
  gradlePropParser = callPackage ./tools/gradlePropParser.nix { };

  # Package version adjustments
  go = super.pkgs.go_1_14;
  gradle = super.pkgs.gradle_5;
  nodejs = super.pkgs.nodejs-12_x;
  openjdk = super.pkgs.openjdk8_headless;
  xcodeWrapper = callPackage ./pkgs/xcodeenv/compose-xcodewrapper.nix { } {
    version = "11.5";
    allowHigher = true;
  };

  # Android environement
  androidEnvCustom = callPackage ./pkgs/android-sdk { };
  androidPkgs = self.androidEnvCustom.pkgs;
  androidShell = self.androidEnvCustom.shell;

  # Custom packages
  aapt2 = callPackage ./pkgs/aapt2 { };
  gomobile = super.gomobile.override { androidPkgs = androidEnvCustom.compose; };
  patchMavenSources = callPackage ./pkgs/patch-maven-srcs { };
  goMavenResolver = callPackage ./pkgs/go-maven-resolver { };
}

{ lib, pkgs, deps, callPackage, mkShell
, status-go, androidPkgs, androidShell }:

let
  # For generating a temporary keystore for local development
  keystore = callPackage ./keystore.nix { };

  # Import a jsbundle compiled out of clojure codebase
  jsbundle = callPackage ./jsbundle { };

  # Import a patched version of watchman (important for sandboxed builds on macOS)
  watchmanFactory = callPackage ./watchman.nix { };

  # TARGETS
  release = callPackage ./release.nix {
    inherit keystore jsbundle status-go watchmanFactory;
  };

in {
  # TARGETS
  inherit keystore release jsbundle;

  shell = mkShell {
    buildInputs = with pkgs; [
      openjdk
      gradle
      lsof  # used in start-react-native.sh
      flock # used in nix/scripts/node_modules.sh
    ];

    inputsFrom = [
      release
      androidShell
    ];

    shellHook = ''
      export ANDROID_SDK_ROOT="${androidPkgs.sdk}"
      export ANDROID_NDK_ROOT="${androidPkgs.ndk}"

      export STATUS_NIX_MAVEN_REPO="${deps.gradle}"

      # required by some makefile targets
      export STATUS_GO_ANDROID_LIBDIR=${status-go}

      # check if node modules changed and if so install them
      $STATUS_REACT_HOME/nix/scripts/node_modules.sh ${deps.nodejs-patched}
    '';
  };
}

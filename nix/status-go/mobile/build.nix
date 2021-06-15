{ lib, stdenv, utils, callPackage, buildGo114Package
, go, androidPkgs, openjdk, gomobile, xcodeWrapper
# object with source attributes
, meta, source
, platform ? "android"
, architectures ? [ "arm64" "arm" "x86" ]
, goBuildFlags ? [ ]
, goBuildLdFlags ? [ ]
, outputFileName ? "status-go-${source.shortRev}-${platform}.aar" }:

let
  inherit (stdenv) isDarwin;
  inherit (lib)
    concatStrings concatStringsSep concatMapStrings optionalString
    getAttr attrValues makeBinPath optional;

  removeReferences = [ go ];
  removeExpr = refs: ''remove-references-to ${concatMapStrings (ref: " -t ${ref}") refs}'';

  # formatted for use with -target
  targetArchs = map (a: "${platform}/${a}") architectures;

in buildGo114Package {
  pname = source.repo;
  version = "${source.cleanVersion}-${source.shortRev}-${platform}";

  inherit meta;
  inherit (source) src goPackagePath;

  nativeBuildInputs = [ gomobile ]
    ++ optional (platform == "android") openjdk
    ++ optional isDarwin xcodeWrapper;

  # Fixes Cgo related build failures (see https://github.com/NixOS/nixpkgs/issues/25959 )
  hardeningDisable = [ "fortify" ];

  # Ensure XCode is present for iOS build, instead of failing at the end of the build
  preConfigure = optionalString (isDarwin && platform == "ios") utils.enforceXCodeAvailable;

  # Build mobile libraries
  preBuild = let
    NIX_GOWORKDIR = "$NIX_BUILD_TOP/go-build";
  in ''
    mkdir ${NIX_GOWORKDIR}

    export GO111MODULE=off
    export GOPATH=${gomobile}:$GOPATH
    export NIX_GOWORKDIR=${NIX_GOWORKDIR}
  '' + optionalString (platform == "android") ''
    export ANDROID_SDK_ROOT="${androidPkgs.sdk}"
    export ANDROID_NDK_HOME="${androidPkgs.ndk}"
    export PATH="${makeBinPath [ openjdk ]}:$PATH"
  '';

  # Build the Go library using gomobile for each of the configured platforms
  buildPhase = let
    ldFlags = [ "-extldflags=-Wl,--allow-multiple-definition" ] ++ goBuildLdFlags;
    CGO_LDFLAGS = concatStringsSep " " ldFlags;
  in ''
    runHook preBuild
    runHook renameImports

    echo -e "\nBuilding for targets: ${concatStringsSep "," targetArchs}\n"

    ${gomobile}/bin/gomobile bind \
      -target=${concatStringsSep "," targetArchs} \
      -ldflags="${CGO_LDFLAGS}" \
      ${optionalString (platform == "android") "-androidapi 23"} \
      ${optionalString (platform == "ios") "-iosversion=8.0"} \
      ${concatStringsSep " " goBuildFlags} \
      -o ${outputFileName} \
      ${source.goPackagePath}/mobile

    rm -rf $NIX_GOWORKDIR

    runHook postBuild
  '';

  # replace hardcoded paths to go package in /nix/store, otherwise Nix will fail the build
  fixupPhase = ''
    find $out -type f -exec ${removeExpr removeReferences} '{}' + || true
  '';

  installPhase = ''
    mkdir -p $out
    mv ${outputFileName} $out/
  '';

  outputs = [ "out" ];
}

{ stdenv, compose }:

#
# This derivation simply symlinks some stuff to get
# shorter paths as libexec/android-sdk is quite the mouthful.
# With this you can just do `androidPkgs.sdk` and `androidPkgs.ndk`.
#
stdenv.mkDerivation {
  name = "${compose.androidsdk.name}-mod";
  phases = [ "symlinkPhase" ];
  outputs = [ "out" "sdk" "ndk" ];
  symlinkPhase = ''
    ln -s ${compose.androidsdk} $out
    ln -s ${compose.androidsdk}/libexec/android-sdk $sdk
    ln -s ${compose.androidsdk}/libexec/android-sdk/ndk-bundle $ndk
  '';
}

#!/usr/bin/env bash
# Builds RenzoHub-Setup.exe from this Linux host (HANDOFF_renzo-hub_desktop-exe.md §7):
#   1. uber JAR with the WINDOWS Skiko natives (-PhubDesktopOs=windows)
#   2. a Windows JRE image, cross-linked with the local jlink against a
#      Windows JDK's jmods (downloaded once, cached)
#   3. an NSIS launcher exe + NSIS installer
#   4. Authenticode signing with osslsigncode (codesign.pfx)
set -euo pipefail

HUB="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${VERSION:-1.0.0}"
WORK="${WORK:-/tmp/renzohub-exe}"
OUT_DIR="${OUT_DIR:-/export/Main/Renzo-out}"
SIGN_DIR="/export/Main/Renzo-Apps/signing"
JDK_ZIP_URL="https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"
ICON="$HUB/hub-desktop/windows/renzohub.ico"
# libvlc for the built-in anime player (VlcRuntime finds <app>/vlc). Pinned:
# the hash is videolan's published .sha256 for this exact zip.
VLC_VER="3.0.24"
VLC_ZIP_URL="https://download.videolan.org/pub/videolan/vlc/$VLC_VER/win64/vlc-$VLC_VER-win64.zip"
VLC_SHA256="fcf30850371ad10c9373cc4f0f4501e7dee49e3e9ae9f20c72fb2661a1ca6323"

# The Windows jmods are Java 21 (class 65) — a 17 jlink can't read them, and
# which JDK is first on PATH varies by shell. Pin to a 21 jlink explicitly.
if [ -x /opt/jdk21/bin/jlink ]; then
  JLINK=/opt/jdk21/bin/jlink
else
  JLINK=jlink
fi

echo "── 1/5 uber jar (windows natives)"
(cd "$HUB" && ./gradlew -q :hub-desktop:packageUberJarForCurrentOS -PhubDesktopOs=windows)
JAR="$(ls "$HUB"/hub-desktop/build/compose/jars/*.jar | head -1)"
# NOT `grep -q`: with pipefail, -q's early exit SIGPIPEs unzip and fails the
# pipeline even on a match.
unzip -l "$JAR" | grep skiko-windows-x64.dll > /dev/null || { echo "jar lacks Windows natives"; exit 1; }

echo "── 2/5 windows JRE image"
mkdir -p "$WORK"
if [ ! -d "$WORK/win-jdk" ]; then
  curl -sL -o "$WORK/win-jdk.zip" "$JDK_ZIP_URL"
  (cd "$WORK" && unzip -q win-jdk.zip && mv jdk-* win-jdk)
fi
rm -rf "$WORK/jre"
"$JLINK" --module-path "$WORK/win-jdk/jmods" \
      --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.accessibility \
      --output "$WORK/jre" --no-header-files --no-man-pages --compress zip-6

echo "── 3/5 staging + launcher exe"
rm -rf "$WORK/stage"
mkdir -p "$WORK/stage"
cp "$JAR" "$WORK/stage/RenzoHub.jar"
cp -r "$WORK/jre" "$WORK/stage/jre"
# libvlc + plugins. Pruned of what an embedded player never loads (Qt GUI,
# visualisations, streaming/mux output, Lua scripts, remote control); the
# LGPL text ships alongside. plugins.dat is a cache — libvlc rebuilds it.
if [ ! -d "$WORK/vlc-$VLC_VER" ]; then
  curl -sL -o "$WORK/vlc.zip" "$VLC_ZIP_URL"
  echo "$VLC_SHA256  $WORK/vlc.zip" | sha256sum -c --quiet - || { echo "vlc zip checksum mismatch"; exit 1; }
  (cd "$WORK" && unzip -q vlc.zip)
fi
mkdir -p "$WORK/stage/vlc"
cp "$WORK/vlc-$VLC_VER/libvlc.dll" "$WORK/vlc-$VLC_VER/libvlccore.dll" "$WORK/stage/vlc/"
cp "$WORK/vlc-$VLC_VER/COPYING.txt" "$WORK/stage/vlc/COPYING.txt"
cp -r "$WORK/vlc-$VLC_VER/plugins" "$WORK/stage/vlc/plugins"
(cd "$WORK/stage/vlc/plugins" && rm -rf gui visualization services_discovery control stream_out \
  access_output mux lua video_splitter && rm -f plugins.dat)
makensis -DOUTDIR="$WORK/stage" -DICON="$ICON" "$HUB/hub-desktop/windows/renzohub-launcher.nsi" >/dev/null

echo "── 4/5 signing the launcher, building + signing the installer"
# The Hub's own identity (CN=Levitate Media), generated 2026-08-14 — the old
# codesign.pfx signs as "Renzo Shiori", which is the app this exe replaces.
PFX="$SIGN_DIR/hub-codesign.pfx"
PASS="$(cat "$SIGN_DIR/hub-codesign-password.txt")"
sign() {
  osslsigncode sign -pkcs12 "$PFX" -pass "$PASS" \
    -n "Renzo Hub" -i "https://www.renzo.net" -t http://timestamp.digicert.com \
    -in "$1" -out "$1.signed" && mv "$1.signed" "$1"
}
sign "$WORK/stage/RenzoHub.exe"
mkdir -p "$OUT_DIR"
makensis -DVERSION="$VERSION" -DSRC="$WORK/stage" -DICON="$ICON" \
  -DOUTFILE="$WORK/RenzoHub-Setup.exe" \
  "$HUB/hub-desktop/windows/renzohub-installer.nsi" >/dev/null
sign "$WORK/RenzoHub-Setup.exe"

echo "── 5/5 export"
cp "$WORK/RenzoHub-Setup.exe" "$OUT_DIR/RenzoHub-Setup.exe"
(cd "$OUT_DIR" && sha256sum RenzoHub-Setup.exe > RenzoHub-Setup.exe.sha256)
ls -la "$OUT_DIR/RenzoHub-Setup.exe"
echo "done: $OUT_DIR/RenzoHub-Setup.exe"

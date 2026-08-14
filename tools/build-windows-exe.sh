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
jlink --module-path "$WORK/win-jdk/jmods" \
      --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.accessibility \
      --output "$WORK/jre" --no-header-files --no-man-pages --compress zip-6

echo "── 3/5 staging + launcher exe"
rm -rf "$WORK/stage"
mkdir -p "$WORK/stage"
cp "$JAR" "$WORK/stage/RenzoHub.jar"
cp -r "$WORK/jre" "$WORK/stage/jre"
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

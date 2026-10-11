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
# libvlc for the built-in anime player (VlcRuntime finds <app>/vlc), from
# VideoLAN's own embedding package, which they publish as LGPL-2.1-or-later:
# the release binaries minus the plugins that wrap GPL libraries (x264,
# postproc, libdvdnav, libmad, faad2, the Qt GUI). NOT the vlc-*-win64.zip —
# that is the GPL application build. Pinned by the nupkg's sha256.
VLC_VER="3.0.24"
VLC_NUPKG_URL="https://api.nuget.org/v3-flatcontainer/videolan.libvlc.windows/$VLC_VER/videolan.libvlc.windows.$VLC_VER.nupkg"
VLC_SHA256="3da3f37ee2b2b10e5c788d830a3b513658c36521ea1b0f966ed86eb69f41ef3c"

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
# libvlc + plugins. Pruned of what an embedded player never loads (scripting,
# streaming/mux output, visualisations, discovery) and of the four plugins the
# package still carries under a GPL label; the guard below then refuses to
# ship ANY plugin whose embedded licence string says GPL.
VLC_PKG="$WORK/libvlc-$VLC_VER"
if [ ! -d "$VLC_PKG" ]; then
  curl -sL -o "$WORK/libvlc.nupkg" "$VLC_NUPKG_URL"
  echo "$VLC_SHA256  $WORK/libvlc.nupkg" | sha256sum -c --quiet - || { echo "libvlc nupkg checksum mismatch"; exit 1; }
  mkdir -p "$VLC_PKG" && unzip -q "$WORK/libvlc.nupkg" 'build/x64/*' -d "$VLC_PKG"
fi
mkdir -p "$WORK/stage/vlc"
cp -p "$VLC_PKG/build/x64/libvlc.dll" "$VLC_PKG/build/x64/libvlccore.dll" "$WORK/stage/vlc/"
cp -rp "$VLC_PKG/build/x64/plugins" "$WORK/stage/vlc/plugins"
(cd "$WORK/stage/vlc/plugins" && rm -rf lua services_discovery stream_out access_output mux visualization video_splitter \
  && rm -f codec/libx26410b_plugin.dll audio_filter/libheadphone_channel_mixer_plugin.dll \
     audio_filter/libdolby_surround_decoder_plugin.dll)
GPL_PLUGINS="$(grep -rla 'GNU General Public License, version 2 or later' "$WORK/stage/vlc/plugins" || true)"
[ -z "$GPL_PLUGINS" ] || { echo "GPL-licensed libvlc plugins would ship:"; echo "$GPL_PLUGINS"; exit 1; }
cp "$HUB/hub-desktop/windows/libvlc-LGPL-2.1.txt" "$WORK/stage/vlc/COPYING.LIB.txt"
cat > "$WORK/stage/vlc/NOTICE.txt" <<NOTICE
Renzo Hub's built-in video player uses libVLC $VLC_VER by VideoLAN,
licensed under the GNU Lesser General Public License, version 2.1 or later
(COPYING.LIB.txt). These are VideoLAN's unmodified binaries, from their
VideoLAN.LibVLC.Windows package. Source code:
https://download.videolan.org/pub/videolan/vlc/$VLC_VER/vlc-$VLC_VER.tar.xz
You may replace the files in this folder with your own build of libVLC.
NOTICE
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

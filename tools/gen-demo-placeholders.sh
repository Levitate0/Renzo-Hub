#!/usr/bin/env bash
# Placeholder art for the `demo` (store-listing) build type of Renzo Hub.
#
# The demo build swaps every image fetched from the server for one of these, so
# a Google Play screenshot carries no publisher artwork. They are simply the app
# icon centred on the app's own background colour — deliberately plain, so they
# read as "the app's own plate", not as fake cover art.
#
# Ported from tv-native/tools/gen-demo-placeholders.sh. The one thing that
# CHANGED in the move: the Hub has its own mark, so this renders from the Hub's
# adaptive-icon foreground rather than the old standalone Renzo icon. Re-run it
# whenever that mark changes or the plates will still show the previous brand.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../feature-renzo/src/demo/assets/placeholder"
# The Hub's own launcher foreground (transparent, largest density available).
ICON="$HERE/../app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png"
[ -f "$ICON" ] || ICON="$HERE/../app/src/main/res/drawable-xhdpi/ic_launcher_foreground.png"
BG="#0b0f14"   # RenzoColors.Background (renzo preset)

[ -f "$ICON" ] || { echo "missing icon source: $ICON" >&2; exit 1; }
mkdir -p "$OUT"

# plate <width> <height> <icon-px> <opacity%> <output>
plate() {
  local w=$1 h=$2 icon=$3 alpha=$4 out=$5
  convert -size "${w}x${h}" "xc:$BG" \
    \( "$ICON" -resize "${icon}x${icon}" -alpha set -channel A -evaluate multiply "0.${alpha}" +channel \) \
    -gravity center -composite \
    "$out"
}

echo "[1/5] poster 600x900 (2:3 cards, season strip)"
plate 600 900 320 95 "$OUT/poster.png"

echo "[2/5] banner 1920x1080 (detail-page hero)"
plate 1920 1080 520 90 "$OUT/banner.jpg"

echo "[3/5] thumb 480x270 (episode stills)"
plate 480 270 150 85 "$OUT/thumb.jpg"

echo "[4/5] preview 320x180 (scrub bar)"
plate 320 180 104 85 "$OUT/preview.jpg"

# Player controls and subtitles sit on top of this one, so it is dimmer.
echo "[5/5] video-cover 1920x1080 (over the video surface)"
plate 1920 1080 460 70 "$OUT/video-cover.jpg"

echo
identify "$OUT"/* | sed 's|.*/placeholder/||'
echo
du -sh "$OUT"

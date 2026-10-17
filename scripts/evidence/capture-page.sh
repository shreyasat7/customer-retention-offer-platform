#!/usr/bin/env bash
# Capture a web page (or local HTML file) to a retina PNG using headless Chrome.
# Usage: capture-page.sh <url> <output.png> [width] [height]
set -euo pipefail
URL="$1"; OUT="$2"; W="${3:-1400}"; H="${4:-1100}"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
PROFILE="$(mktemp -d)"
mkdir -p "$(dirname "$OUT")"
"$CHROME" --headless --disable-gpu --hide-scrollbars --no-first-run \
  --user-data-dir="$PROFILE" --force-device-scale-factor=2 \
  --virtual-time-budget=4000 --window-size="${W},${H}" \
  --screenshot="$OUT" "$URL" >/dev/null 2>&1 || true
rm -rf "$PROFILE"
[ -s "$OUT" ] && echo "captured: $OUT ($(du -h "$OUT" | cut -f1))" || { echo "FAILED: $OUT" >&2; exit 1; }

#!/usr/bin/env bash
# Render a REAL captured command log into a terminal-styled PNG (evidence for CLI steps).
# The log content is never edited - it is the verbatim output of the commands that were run.
# Usage: capture-terminal.sh <logfile> <output.png> "<window title>"
set -euo pipefail
LOG="$1"; OUT="$2"; TITLE="${3:-Terminal}"
TMP="$(mktemp -d)"; HTML="$TMP/term.html"
LINES=$(wc -l < "$LOG" | tr -d ' ')
HEIGHT=$(( LINES * 20 + 150 )); [ "$HEIGHT" -lt 320 ] && HEIGHT=320; [ "$HEIGHT" -gt 5200 ] && HEIGHT=5200
{
cat <<'HEAD'
<!doctype html><html><head><meta charset="utf-8"><style>
*{box-sizing:border-box}
body{margin:0;background:#0b1220;padding:22px;font:13px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace}
.win{background:#0f1729;border:1px solid #243049;border-radius:11px;overflow:hidden;
 box-shadow:0 18px 50px rgba(0,0,0,.5)}
.bar{display:flex;align-items:center;gap:8px;background:#1a2539;padding:10px 14px;border-bottom:1px solid #243049}
.dot{width:11px;height:11px;border-radius:50%}
.r{background:#ff5f57}.y{background:#febc2e}.g{background:#28c840}
.ttl{flex:1;text-align:center;color:#8c9bb5;font-size:12px;font-weight:600;letter-spacing:.2px}
pre{margin:0;padding:16px 18px;color:#d7e0ef;white-space:pre-wrap;word-break:break-word;
 font:13px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace}
b.p{color:#4ade80;font-weight:700}
b.c{color:#7dd3fc;font-weight:700}
b.ok{color:#4ade80}b.err{color:#fb7185}b.warn{color:#fbbf24}b.hi{color:#c4b5fd}
</style></head><body><div class="win"><div class="bar">
<span class="dot r"></span><span class="dot y"></span><span class="dot g"></span>
HEAD
printf '<span class="ttl">%s</span></div><pre>' "$TITLE"
# escape HTML, then colourise prompts/keywords - content itself is untouched
sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' "$LOG" \
 | sed -E \
   -e 's/^(\$ )(.*)$/<b class="p">$ <\/b><b class="c">\2<\/b>/' \
   -e 's/\b(BUILD SUCCESS|SUCCESS|PASSED|ok:|changed:|Finished: SUCCESS|UP|healthy|OK)\b/<b class="ok">\1<\/b>/g' \
   -e 's/\b(BUILD FAILURE|FAILURE|FAILED|ERROR|error|fatal:|failed=[1-9])\b/<b class="err">\1<\/b>/g' \
   -e 's/\b(WARNING|WARN|skipped:)\b/<b class="warn">\1<\/b>/g' \
   -e 's/^(=== .*)$/<b class="hi">\1<\/b>/'
printf '</pre></div></body></html>'
} > "$HTML"
"$(dirname "$0")/capture-page.sh" "file://$HTML" "$OUT" 1240 "$HEIGHT"
rm -rf "$TMP"

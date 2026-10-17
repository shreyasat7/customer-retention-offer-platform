#!/usr/bin/env bash
# Stop whatever is listening on the given port (default: local dev port 8070).
set -euo pipefail
. "$(dirname "$0")/env.sh"
PORT="${1:-$APP_LOCAL_PORT}"
PID="$(lsof -ti tcp:"$PORT" || true)"
if [ -n "$PID" ]; then kill "$PID" && echo "stopped pid $PID on port $PORT"; else echo "nothing listening on $PORT"; fi

#!/usr/bin/env bash
# Start the application locally for development on APP_LOCAL_PORT (8070).
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/env.sh
echo "Starting CROP on http://localhost:${APP_LOCAL_PORT} (JDK $(java -version 2>&1 | head -1 | cut -d'"' -f2))"
APP_PORT="${APP_LOCAL_PORT}" mvn spring-boot:run

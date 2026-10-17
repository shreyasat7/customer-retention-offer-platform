#!/usr/bin/env bash
# Stop the demonstration environment. Keeps all data and images.
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/env.sh
say() { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }

say "Application container, registry and interfaces";  docker stop crop-app registry registry-ui dozzle 2>/dev/null
say "Nginx";    brew services stop nginx >/dev/null 2>&1 && echo "    stopped"
say "Tomcat";   /opt/homebrew/opt/tomcat/libexec/bin/shutdown.sh >/dev/null 2>&1 && echo "    stopped"
say "Jenkins";  brew services stop jenkins-lts >/dev/null 2>&1 && echo "    stopped"
say "Local dev instance"; ./scripts/stop-local.sh 2>/dev/null
printf '\nAll stopped. Nothing was deleted - run scripts/demo-up.sh to bring it back.\n\n'

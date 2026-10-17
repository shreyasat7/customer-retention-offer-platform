#!/usr/bin/env bash
# One-screen status of every moving part.
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/env.sh

check() {  # check <label> <url>
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$2" 2>/dev/null)
  if [ "$code" = "200" ] || [ "$code" = "302" ] || [ "$code" = "401" ]; then
    printf '  \033[32m● UP  \033[0m %-28s %s\n' "$1" "$2"
  else
    printf '  \033[31m● DOWN\033[0m %-28s %s\n' "$1" "$2"
  fi
}

printf '\n\033[1mCustomer Retention Offer Platform — environment status\033[0m\n\n'
check "Application (proxy)"   "http://localhost:${PROXY_PORT}/actuator/health"
check "Application (container)" "http://localhost:${CONTAINER_PORT}/actuator/health"
check "Application (Tomcat)"  "http://localhost:${TOMCAT_PORT}/crop/actuator/health"
check "Jenkins"               "http://localhost:${JENKINS_PORT}/login"
check "Docker registry"       "http://localhost:${REGISTRY_PORT}/v2/"
check "Registry UI"           "http://localhost:${REGISTRY_UI_PORT}/"
check "Dozzle"                "http://localhost:8092/"

printf '\n\033[1mContainers\033[0m\n'
docker ps --format '  {{.Names}}\t{{.Image}}\t{{.Status}}' 2>/dev/null | column -t -s $'\t' || echo "  docker not reachable"

printf '\n\033[1mPublished image tags\033[0m\n'
curl -s --max-time 3 "http://localhost:${REGISTRY_PORT}/v2/crop/tags/list" 2>/dev/null | sed 's/^/  /' || echo "  registry not reachable"

printf '\n\033[1mLast Jenkins pipeline run\033[0m\n'
curl -s --max-time 3 -u admin:admin123 "http://localhost:${JENKINS_PORT}/job/crop-pipeline/lastBuild/api/json" 2>/dev/null \
  | python3 -I -c "import json,sys; d=json.load(sys.stdin); print(f\"  #{d['number']}  {d['result']}  {d.get('description','')}\")" 2>/dev/null \
  || echo "  Jenkins not reachable"
printf '\n'

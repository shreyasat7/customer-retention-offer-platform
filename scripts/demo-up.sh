#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  Bring the whole demonstration environment up, in dependency order.
#  Safe to re-run: every step checks before it acts.
# ---------------------------------------------------------------------------
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/env.sh

say()  { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }
ok()   { printf '    \033[32m✓\033[0m %s\n' "$*"; }
warn() { printf '    \033[33m!\033[0m %s\n' "$*"; }

wait_http() {   # wait_http <url> <label> [tries]
  local url="$1" label="$2" tries="${3:-30}"
  for _ in $(seq 1 "$tries"); do
    if curl -sf -o /dev/null --max-time 3 "$url"; then ok "$label  $url"; return 0; fi
    sleep 2
  done
  warn "$label did not come up at $url"; return 1
}

say "Docker Engine"
if ! docker info >/dev/null 2>&1; then
  open -a Docker
  for _ in $(seq 1 30); do docker info >/dev/null 2>&1 && break; sleep 3; done
fi
docker info >/dev/null 2>&1 && ok "Docker $(docker version --format '{{.Server.Version}}')" \
                             || { warn "Docker is not running - start Docker Desktop"; exit 1; }

say "Jenkins"
brew services start jenkins-lts >/dev/null 2>&1
wait_http "http://localhost:${JENKINS_PORT}/login" "Jenkins" 40

say "Apache Tomcat"
if curl -sf -o /dev/null --max-time 2 "http://localhost:${TOMCAT_PORT}/"; then
  ok "Tomcat already running"
else
  /opt/homebrew/opt/tomcat/libexec/bin/startup.sh >/dev/null 2>&1
  wait_http "http://localhost:${TOMCAT_PORT}/" "Tomcat"
fi
curl -sf -o /dev/null "http://localhost:${TOMCAT_PORT}/crop/actuator/health" \
  && ok "CROP on Tomcat  http://localhost:${TOMCAT_PORT}/crop" \
  || warn "crop.war is not deployed to Tomcat yet - run the Jenkins pipeline"

say "Docker registry and interfaces"
docker start registry registry-ui dozzle >/dev/null 2>&1
wait_http "http://localhost:${REGISTRY_PORT}/v2/"   "Registry API"
wait_http "http://localhost:${REGISTRY_UI_PORT}/"   "Registry UI "
wait_http "http://localhost:8092/"                  "Dozzle      "

say "Application node (Ansible)"
( cd ansible && ansible-playbook site.yml ) >/tmp/demo-up-ansible.log 2>&1
if tail -3 /tmp/demo-up-ansible.log | grep -q "failed=0"; then
  ok "node provisioned  ($(grep -oE 'changed=[0-9]+' /tmp/demo-up-ansible.log | tail -1))"
else
  warn "provisioning reported a problem - see /tmp/demo-up-ansible.log"
fi
wait_http "http://localhost:${CONTAINER_PORT}/actuator/health" "Container   "
wait_http "http://localhost:${PROXY_PORT}/actuator/health"     "Nginx proxy "

say "Everything is up"
cat <<URLS

  Application (nginx proxy)   http://localhost:${PROXY_PORT}
  Application (container)     http://localhost:${CONTAINER_PORT}
  Application (Tomcat WAR)    http://localhost:${TOMCAT_PORT}/crop
  Jenkins                     http://localhost:${JENKINS_PORT}          admin / admin123
  Docker registry UI          http://localhost:${REGISTRY_UI_PORT}
  Dozzle (Docker interface)   http://localhost:8092
  GitHub repository           https://github.com/shreyasat7/customer-retention-offer-platform

  Sign in as  agent / agent123   manager / manager123   admin / admin123

URLS

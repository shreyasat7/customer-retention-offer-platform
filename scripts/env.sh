#!/usr/bin/env bash
# Pin the toolchain. The default `mvn` on this machine runs on JDK 27; the project
# targets JDK 17, so every script and the Jenkinsfile source this file first.
export JAVA_HOME="${JAVA_HOME_OVERRIDE:-$(/usr/libexec/java_home -v 17 2>/dev/null)}"
export PATH="$JAVA_HOME/bin:$PATH"
export APP_LOCAL_PORT=8070      # mvn spring-boot:run
export APP_TEST_PORT=8095       # instance the Selenium suite runs against
export TOMCAT_PORT=9090         # WAR deployment target
export CONTAINER_PORT=8090      # docker container published port
export PROXY_PORT=8081          # nginx reverse proxy
export REGISTRY_PORT=5001       # local docker registry
export REGISTRY_UI_PORT=5002    # registry web interface
export JENKINS_PORT=8080

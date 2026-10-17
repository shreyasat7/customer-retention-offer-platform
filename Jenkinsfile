// =============================================================================
//  Customer Retention Offer Platform — delivery pipeline
//
//  Declarative pipeline, versioned with the code it builds, so a change to the
//  delivery process is reviewed exactly like a change to the application.
//
//  Checkout -> Build -> Unit tests -> Package -> Deploy to Tomcat -> Health check
// =============================================================================

pipeline {

    agent any

    // -------------------------------------------------------------- toolchain
    // The host's default `mvn` runs on JDK 27 while the project targets 17, so
    // both are pinned here rather than inherited from the agent's PATH (risk R4).
    tools {
        jdk   'jdk17'
        maven 'maven3'
    }

    // ------------------------------------------------------------- parameters
    // Environment settings are parameters, not hard-coded values, so the same
    // pipeline can target a different server or context path without an edit.
    parameters {
        string(name: 'TOMCAT_HOME',
               defaultValue: '/opt/homebrew/opt/tomcat/libexec',
               description: 'CATALINA_HOME of the Tomcat the WAR is deployed into')
        string(name: 'DEPLOY_PORT',
               defaultValue: '9090',
               description: 'HTTP port the deployment target listens on')
        string(name: 'APP_CONTEXT',
               defaultValue: 'crop',
               description: 'Context path the application is deployed under')
        string(name: 'SELENIUM_PORT',
               defaultValue: '8095',
               description: 'Port the throwaway instance under Selenium listens on')
        booleanParam(name: 'SKIP_DEPLOY',
               defaultValue: false,
               description: 'Build and test only — do not touch the server')
        string(name: 'DOCKER_REGISTRY',
               defaultValue: 'localhost:5001',
               description: 'Registry the versioned image is published to')
        string(name: 'IMAGE_NAME',
               defaultValue: 'crop',
               description: 'Image repository name inside the registry')
        string(name: 'CONTAINER_NAME',
               defaultValue: 'crop-app',
               description: 'Name of the deployed application container')
        string(name: 'CONTAINER_PORT',
               defaultValue: '8090',
               description: 'Host port the deployed container publishes on')
        booleanParam(name: 'SKIP_DOCKER',
               defaultValue: false,
               description: 'Skip the image build, registry push and container deploy')
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '25', artifactNumToKeepStr: '10'))
        timeout(time: 20, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    environment {
        ARTIFACT = 'target/crop.war'

        // launchd starts Jenkins with a minimal PATH, so the Docker CLI
        // (/usr/local/bin/docker, a symlink into Docker.app) is not on it.
        PATH = "/usr/local/bin:/opt/homebrew/bin:${env.PATH}"

        DOCKER_REGISTRY = "${params.DOCKER_REGISTRY ?: 'localhost:5001'}"
        IMAGE_NAME      = "${params.IMAGE_NAME ?: 'crop'}"
        CONTAINER_NAME  = "${params.CONTAINER_NAME ?: 'crop-app'}"
        CONTAINER_PORT  = "${params.CONTAINER_PORT ?: '8090'}"

        // The build number is the version. It is monotonic, unique and traceable
        // straight back to the Jenkins run that produced the image.
        IMAGE_TAG       = "${params.DOCKER_REGISTRY ?: 'localhost:5001'}/${params.IMAGE_NAME ?: 'crop'}:${env.BUILD_NUMBER}"
        IMAGE_LATEST    = "${params.DOCKER_REGISTRY ?: 'localhost:5001'}/${params.IMAGE_NAME ?: 'crop'}:latest"

        // Parameters only exist from the second build onward - the first run of a
        // parameterised job is what *defines* them. Resolving each one here with the
        // same default keeps the very first build, and any rebuild from an older
        // revision, working instead of failing on an unbound variable.
        TOMCAT_HOME = "${params.TOMCAT_HOME ?: '/opt/homebrew/opt/tomcat/libexec'}"
        DEPLOY_PORT = "${params.DEPLOY_PORT ?: '9090'}"
        APP_CONTEXT = "${params.APP_CONTEXT ?: 'crop'}"

        SELENIUM_PORT = "${params.SELENIUM_PORT ?: '8095'}"

        DEPLOY_URL = "http://localhost:${params.DEPLOY_PORT ?: '9090'}/${params.APP_CONTEXT ?: 'crop'}"
        HEALTH_URL = "http://localhost:${params.DEPLOY_PORT ?: '9090'}/${params.APP_CONTEXT ?: 'crop'}/actuator/health"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_SHA     = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    env.GIT_SUBJECT = sh(returnStdout: true, script: 'git log -1 --pretty=%s').trim()
                    env.GIT_BRANCH_NAME = sh(returnStdout: true, script: 'git rev-parse --abbrev-ref HEAD').trim()
                    currentBuild.description = "${env.GIT_SHA} · ${env.GIT_SUBJECT}"
                }
                echo "Building ${env.GIT_BRANCH_NAME} @ ${env.GIT_SHA} — ${env.GIT_SUBJECT}"
                sh 'java -version; mvn -v | head -2'
            }
        }

        stage('Build') {
            steps {
                sh 'mvn -B clean compile'
            }
        }

        stage('Unit tests') {
            steps {
                sh 'mvn -B test'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Package') {
            steps {
                sh 'mvn -B package -DskipTests'
                sh 'ls -lh target/crop.war'
            }
            post {
                success {
                    archiveArtifacts artifacts: "${env.ARTIFACT}", fingerprint: true
                }
            }
        }

        // ---------------------------------------------------------------------
        //  THE QUALITY GATE
        //
        //  `mvn verify` starts a throwaway instance on :8095, drives the five
        //  Selenium journeys against it in headless Chrome, and stops it again.
        //  It re-runs the unit tests as part of the standard lifecycle, which
        //  costs well under a second and keeps the command boringly ordinary.
        //
        //  If this stage fails the build is FAILURE and every stage below it is
        //  skipped - the deploy never happens. That is success criterion SC8.
        // ---------------------------------------------------------------------
        stage('Selenium regression gate') {
            steps {
                sh """
                    mvn -B verify \
                        -Dselenium.headless=true \
                        -Dselenium.port=${SELENIUM_PORT}
                """
            }
            post {
                always {
                    junit testResults: 'target/failsafe-reports/*.xml', allowEmptyResults: true

                    // the HTML report, published so a reviewer can read the run
                    sh 'mvn -B surefire-report:failsafe-report-only || true'
                    publishHTML(target: [
                        reportDir            : 'target/reports',
                        reportFiles          : 'failsafe.html',
                        reportName           : 'Selenium regression report',
                        keepAll              : true,
                        alwaysLinkToLastBuild: true,
                        allowMissing         : true
                    ])
                }
                failure {
                    // a failing UI test is useless without a picture of the page
                    archiveArtifacts artifacts: 'target/selenium-screenshots/*.png',
                                     allowEmptyArchive: true
                    echo 'Selenium gate FAILED - the deploy stage will not run.'
                }
            }
        }

        stage('Deploy to Tomcat') {
            when {
                expression { return !(params.SKIP_DEPLOY ?: false) }
            }
            steps {
                sh '''
                    set -eu
                    echo "Deploying to ${TOMCAT_HOME}/webapps/${APP_CONTEXT}.war"

                    # remove the previous deployment so Tomcat does a clean expand
                    rm -rf  "${TOMCAT_HOME}/webapps/${APP_CONTEXT}"
                    rm -f   "${TOMCAT_HOME}/webapps/${APP_CONTEXT}.war"

                    cp target/crop.war "${TOMCAT_HOME}/webapps/${APP_CONTEXT}.war"
                    ls -lh "${TOMCAT_HOME}/webapps/${APP_CONTEXT}.war"
                '''
            }
        }

        stage('Health check') {
            when {
                expression { return !(params.SKIP_DEPLOY ?: false) }
            }
            steps {
                sh '''
                    set -eu
                    echo "Waiting for ${HEALTH_URL}"
                    for attempt in $(seq 1 40); do
                        status=$(curl -s -o /tmp/health.json -w "%{http_code}" "${HEALTH_URL}" || true)
                        if [ "$status" = "200" ] && grep -q '"status":"UP"' /tmp/health.json; then
                            echo "Healthy after $((attempt * 3))s"
                            cat /tmp/health.json
                            exit 0
                        fi
                        sleep 3
                    done
                    echo "Application did not become healthy at ${HEALTH_URL}"
                    tail -60 "${TOMCAT_HOME}/logs/catalina.out" || true
                    exit 1
                '''
            }
        }

        // =====================================================================
        //  CONTAINER DELIVERY
        //  Only reachable once the Selenium gate has passed, so an image is
        //  never built from a revision that failed its tests.
        // =====================================================================

        stage('Build Docker image') {
            when { expression { return !(params.SKIP_DOCKER ?: false) } }
            steps {
                sh '''
                    set -eu
                    docker build -t "${IMAGE_TAG}" -t "${IMAGE_LATEST}" .
                    docker image inspect "${IMAGE_TAG}" \
                        --format 'built {{.Id}}  {{.Os}}/{{.Architecture}}  {{.Size}} bytes'
                '''
            }
        }

        stage('Publish to registry') {
            when { expression { return !(params.SKIP_DOCKER ?: false) } }
            steps {
                sh '''
                    set -eu
                    docker push "${IMAGE_TAG}"
                    docker push "${IMAGE_LATEST}"

                    echo "--- registry catalog ---"
                    curl -s "http://${DOCKER_REGISTRY}/v2/_catalog"
                    echo
                    echo "--- tags for ${IMAGE_NAME} ---"
                    curl -s "http://${DOCKER_REGISTRY}/v2/${IMAGE_NAME}/tags/list"
                    echo
                '''
            }
        }

        stage('Deploy container') {
            when { expression { return !(params.SKIP_DOCKER ?: false) } }
            steps {
                sh '''
                    set -eu

                    echo "Replacing ${CONTAINER_NAME} with ${IMAGE_TAG}"

                    # pull from the registry rather than reusing the local build, so the
                    # deploy exercises the artefact that was actually published
                    docker pull "${IMAGE_TAG}"

                    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true

                    # the named volume is NOT removed: the container is disposable,
                    # the data is not
                    docker run -d \
                        --name "${CONTAINER_NAME}" \
                        -p "${CONTAINER_PORT}:8080" \
                        -v crop-data:/app/data \
                        --restart unless-stopped \
                        --label "crop.build=${BUILD_NUMBER}" \
                        --label "crop.commit=${GIT_SHA}" \
                        "${IMAGE_TAG}"

                    docker ps --filter "name=${CONTAINER_NAME}" \
                        --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
                '''
            }
        }

        stage('Container health check') {
            when { expression { return !(params.SKIP_DOCKER ?: false) } }
            steps {
                sh '''
                    set -eu
                    URL="http://localhost:${CONTAINER_PORT}/actuator/health"
                    echo "Waiting for ${URL}"

                    for attempt in $(seq 1 40); do
                        state=$(docker inspect "${CONTAINER_NAME}" \
                                --format '{{.State.Health.Status}}' 2>/dev/null || echo unknown)
                        status=$(curl -s -o /tmp/chealth.json -w "%{http_code}" "${URL}" || true)

                        if [ "$status" = "200" ] && grep -q '"status":"UP"' /tmp/chealth.json; then
                            echo "Container healthy after $((attempt * 3))s (docker reports: ${state})"
                            cat /tmp/chealth.json; echo
                            exit 0
                        fi
                        sleep 3
                    done

                    echo "Container never became healthy"
                    docker logs --tail 80 "${CONTAINER_NAME}" || true
                    exit 1
                '''
            }
            post {
                failure {
                    echo 'Container health check FAILED - see Task 14 for the rollback procedure.'
                }
            }
        }
    }

    post {
        success {
            echo "SUCCESS — ${env.GIT_SHA} deployed to ${env.DEPLOY_URL}"
            echo "         container ${env.CONTAINER_NAME} running ${env.IMAGE_TAG} on http://localhost:${env.CONTAINER_PORT}"
        }
        failure {
            echo "FAILURE — ${env.GIT_SHA} was NOT deployed. The running server is untouched."
        }
        unstable {
            echo "UNSTABLE — ${env.GIT_SHA} was NOT deployed"
        }
        always {
            echo "Build #${env.BUILD_NUMBER} finished with status ${currentBuild.currentResult}"
        }
    }
}

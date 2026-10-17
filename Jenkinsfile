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
        booleanParam(name: 'SKIP_DEPLOY',
               defaultValue: false,
               description: 'Build and test only — do not touch the server')
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '25', artifactNumToKeepStr: '10'))
        timeout(time: 20, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    environment {
        ARTIFACT    = 'target/crop.war'
        DEPLOY_URL  = "http://localhost:${params.DEPLOY_PORT}/${params.APP_CONTEXT}"
        HEALTH_URL  = "http://localhost:${params.DEPLOY_PORT}/${params.APP_CONTEXT}/actuator/health"
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

        stage('Deploy to Tomcat') {
            when {
                expression { return !params.SKIP_DEPLOY }
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
                expression { return !params.SKIP_DEPLOY }
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
    }

    post {
        success {
            echo "SUCCESS — ${env.GIT_SHA} deployed to ${env.DEPLOY_URL}"
        }
        failure {
            echo "FAILURE — ${env.GIT_SHA} was not deployed"
        }
        always {
            echo "Build #${env.BUILD_NUMBER} finished with status ${currentBuild.currentResult}"
        }
    }
}

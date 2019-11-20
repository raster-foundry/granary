#!groovy

node {
    try {
        env.COMPOSE_PROJECT_NAME = "granary-${env.BRANCH_NAME}"

        stage('checkout') {
            checkout scm
        }

        stage('cibuild') {
            wrap([$class: 'AnsiColorBuildWrapper']) {
                sh './scripts/cibuild'
            }
        }

        env.GRANARY_SETTINGS_BUCKET = 'rasterfoundry-production-config-us-east-1'

        if (env.BRANCH_NAME == 'master' || env.BRANCH_NAME.startsWith('test/') || env.BRANCH_NAME.startsWith('release/') || env.BRANCH_NAME.startsWith('hotfix/')) {
            stage('cipublish') {
                // Decode the Quay credentials stored within Jenkins.
                withCredentials([[$class: 'StringBinding',
                                credentialsId: 'GRANARY_QUAY_USER',
                                variable: 'QUAY_USER'],
                                [$class: 'StringBinding',
                                credentialsId: 'GRANARY_QUAY_PASSWORD',
                                variable: 'QUAY_PASSWORD']]) {
                    wrap([$class: 'AnsiColorBuildWrapper']) {
                        sh './scripts/cipublish'
                    }
                }
            }

            // Plan and apply the current state of the staging infrastructure as
            // outlined by whatever branch of the `granary` repository passes
            // the conditional above (`master`, `test/*`, `release/*`,
            // `hotfix/*`).
            stage('infra') {
                // Use `git` to get the primary repository's current commmit SHA and
                // set it as the value of the `GIT_COMMIT` environment variable.
                env.GIT_COMMIT = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()

                wrap([$class: 'AnsiColorBuildWrapper']) {
                    sh 'docker-compose -f docker-compose.ci.yml run --rm terraform ./scripts/infra plan'
                    sh 'docker-compose -f docker-compose.ci.yml run --rm terraform ./scripts/infra apply'
                }

                def slackMessage = ":jenkins: *Granary (${env.BRANCH_NAME}) #${env.BUILD_NUMBER}*"
                slackMessage += "deployed revision <https://github.com/raster-foundry/granary/tree/${env.GIT_COMMIT}|${env.GIT_COMMIT}>"
                slackMessage += "\n<${env.BUILD_URL}|View Build>"
                slackSend channel: '#granary', color: 'good', message: slackMessage
            }
        }

        stage('notify') {
            if (currentBuild.currentResult == 'SUCCESS' && currentBuild.previousBuild?.result != 'SUCCESS') {
                def slackMessage = ":jenkins: *Granary (${env.BRANCH_NAME}) #${env.BUILD_NUMBER}*"
                if (env.CHANGE_TITLE) {
                    slackMessage += "\n${env.CHANGE_TITLE} - ${env.CHANGE_AUTHOR}"
                }
                slackMessage += "\n<${env.BUILD_URL}|View Build>"
                slackSend channel: '#granary', color: 'good', message: slackMessage
            }
        }

    } catch (err) {
        // Some exception was raised in the `try` block above. Assemble
        // an appropirate error message for Slack.
        def slackMessage = ":jenkins-angry: *Granary (${env.BRANCH_NAME}) #${env.BUILD_NUMBER}*"
        if (env.CHANGE_TITLE) {
            slackMessage += "\n${env.CHANGE_TITLE} - ${env.CHANGE_AUTHOR}"
        }
        slackMessage += "\n<${env.BUILD_URL}|View Build>"
        slackSend  channel: '#granary', color: 'danger', message: slackMessage

        // Re-raise the exception so that the failure is propagated to
        // Jenkins.
        throw err
    } finally {
        // Pass or fail, ensure that the services and networks
        // created by Docker Compose are torn down.
        sh 'docker-compose down -v'
    }
}

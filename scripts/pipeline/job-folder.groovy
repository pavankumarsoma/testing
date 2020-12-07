import jenkins.*
import hudson.*
import hudson.model.*
import groovy.json.JsonOutput

gitUrl = 'https://github.com/tr/ctdevops_ansible.git'
gitbranch = 'master'

office365ConnectorWebhooksName = ''
office365ConnectorWebhooksURL = ''

properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '15', artifactNumToKeepStr: '5', daysToKeepStr: '15', numToKeepStr: '5')), 
disableConcurrentBuilds(), 
gitLabConnection(''), 
// [$class: 'HudsonNotificationProperty', endpoints: [[buildNotes: '', urlInfo: [urlOrId: 'https://alert.victorops.com/integrations/dev/jenkins/20170920/d9c36d12-618c-4b1a-bf5a-836cca18f8ae', urlType: 'PUBLIC']]]], 
office365ConnectorWebhooks([[name: office365ConnectorWebhooksName, url: office365ConnectorWebhooksURL, notifyBackToNormal: true, notifyFailure: true, notifySuccess: true, notifyUnstable: true]]),
[$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false], 
        parameters([
            choiceParam(name: 'application', choices: 'acquire-ui\nacquisition-configuration-service\nacquisition-service\nauditing-service\nautotagger\nautotagger-service\nbtaxactiviti\nchart-service\nconversion-service\ndoc-metadata\ndocument-repository-service\necp-bridge-extract\necp-bridge-registrar\neditorial-workbench-ui\njudicial-content-authority\nlink-destination-authority\nmultimedia-service\nnotification-service\nrpx-gateway\ntrta-content-editor\ntigre-image-processor\nlogstash', description: 'Select Application', defaultValue: 'acquire-ui'),
            choiceParam(name: 'target', choices: 'c075yzxqed.int.thomsonreuters.com\nc800hubqed.int.thomsonreuters.com\nc557jvcqed.int.thomsonreuters.com\nc912mbnprod.int.thomsonreuters.com\nc415knjprod.int.thomsonreuters.com\nc094crmprod.int.thomsonreuters.com\nqed_app\nprod_app', description: 'Select domain or env', defaultValue: 'c075yzxqed.int.thomsonreuters.com'),
            choiceParam(name: 'method', choices: 'started\nstopped\nrestarted', defaultValue: 'restarted', description: 'Select method')
        ])
    ])

node('ctdeploy') {
  try {
    notifyBuild('STARTED')
  
    stage('Checkout') {
      git branch: gitbranch, credentialsId: 'github-user-pass', poll: false, url: gitUrl
    }

    stage('Status Service using Ansible') {
      ansiblePlaybookPath = "playbooks/status_environment_${params.method}.yml"
      sh '''${env.JOB_NAME}
${env.JOB_NAME}'''
    }
    
    stage('workspace cleanup') {
      step([$class: 'WsCleanup'])
    }
  } catch (e) {
    // If there was an exception thrown, the build failed
    currentBuild.result = "FAILED"
    throw e
  } finally {
    notifyBuild(currentBuild.result)
  }
}

def notifyBuild(String buildStatus = 'STARTED') {
  // build status of null means successful
  buildStatus = buildStatus ?: 'SUCCESS'

  // Default values
  def colorName = 'RED'
  def colorCode = '#FF0000'
  def subject = "${buildStatus}: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
  def summary = "${subject} (<${env.BUILD_URL}|Open>)"
  def details = """<p>STARTED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':</p>
    <p>Check console output at &QUOT;<a href='${env.BUILD_URL}'>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>&QUOT;</p>"""
  def content = '${JELLY_SCRIPT, template="mail.jelly"}'

  def jobname = env.JOB_NAME
  def appname = jobname.split("/")[1]

  // Override default values based on build status
  if (buildStatus == 'STARTED') {
    color = 'YELLOW'
    colorCode = '#FFFF00'
    //slackSend color: 'warning', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Deploy Started! <${env.BUILD_URL}|here> ", channel: "${slackNotificationChannel}"
  } else if (buildStatus == 'SUCCESS') {
    color = 'GREEN'
    colorCode = '#00FF00'
  } else {
    color = 'RED'
    colorCode = '#FF0000'
// notifyVictorops(entity_id: "${env.JOB_NAME}",message_type: "WARNING",state_message: "${appname} deploy  ${buildStatus} on ${params.target} Please check the Details available ${env.BUILD_URL}",BUILD_ID: "${params.version}")
  }

  emailext (
      subject: subject,
      body: content,
      mimeType: 'text/html',
      //to: 'TRTA_ContentTech_DevOps@thomsonreuters.com',
      to: 'pavankumar.soma@thomsonreuters.com',
      recipientProviders: [[$class: 'RequesterRecipientProvider']]
    )

}

/*
 * The controller's own URL. Without it the Jenkins CLI refuses to connect and links in
 * build notifications are wrong, so it is set as code rather than left to be clicked in.
 */
import jenkins.model.JenkinsLocationConfiguration

def location = JenkinsLocationConfiguration.get()
if (location.getUrl() != 'http://localhost:8080/') {
    location.setUrl('http://localhost:8080/')
    location.setAdminAddress('devops@crop.local')
    location.save()
    println '--> set Jenkins root URL'
}

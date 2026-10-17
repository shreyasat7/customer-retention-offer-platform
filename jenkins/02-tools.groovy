/*
 * Global tool configuration.
 *
 * The host's default `mvn` runs on JDK 27 while the project targets JDK 17, so the
 * controller pins both explicitly (project risk R4) instead of inheriting whatever
 * happens to be on PATH.
 */
import jenkins.model.*
import hudson.model.JDK
import hudson.tasks.Maven

def instance = Jenkins.get()

def jdkDescriptor = instance.getDescriptorByType(JDK.DescriptorImpl)
if (jdkDescriptor.getInstallations().length == 0) {
    jdkDescriptor.setInstallations(
        new JDK('jdk17', '/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home'))
    jdkDescriptor.save()
    println '--> registered JDK 17'
}

def mavenDescriptor = instance.getDescriptorByType(Maven.DescriptorImpl)
if (mavenDescriptor.getInstallations().length == 0) {
    mavenDescriptor.setInstallations(
        new Maven.MavenInstallation('maven3', '/opt/homebrew/opt/maven/libexec', []))
    mavenDescriptor.save()
    println '--> registered Maven 3'
}
instance.save()

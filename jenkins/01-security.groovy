/*
 * Jenkins security, configured as code rather than through the setup wizard so the
 * controller can be rebuilt from scratch reproducibly.
 *
 * Local demonstration instance only: it listens on 127.0.0.1 and holds no real data.
 */
import jenkins.model.*
import hudson.security.*
import jenkins.security.s2m.AdminWhitelistRule

def instance = Jenkins.get()

def realm = instance.getSecurityRealm()
if (!(realm instanceof HudsonPrivateSecurityRealm)) {
    realm = new HudsonPrivateSecurityRealm(false)
    instance.setSecurityRealm(realm)
    println '--> installed the local user database'
}
// Declared, not assumed: the admin account always ends up with the documented
// demonstration credential, so the controller can be rebuilt from an empty
// JENKINS_HOME without anyone having to click through the setup wizard.
realm.createAccount('admin', 'admin123')
println '--> ensured local admin account'

if (!(instance.getAuthorizationStrategy() instanceof FullControlOnceLoggedInAuthorizationStrategy)) {
    def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
    strategy.setAllowAnonymousRead(false)
    instance.setAuthorizationStrategy(strategy)
    println '--> signed-in users get full control, anonymous gets nothing'
}

instance.setNumExecutors(2)
instance.setSystemMessage('Customer Retention Offer Platform - CI/CD controller (local demonstration instance)')
instance.save()

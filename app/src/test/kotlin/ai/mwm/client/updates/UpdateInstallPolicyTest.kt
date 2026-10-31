package ai.mwm.client.updates

import org.junit.Assert.*
import org.junit.Test

class UpdateInstallPolicyTest {
    @Test fun passiveCheckNeverInstallsEvenWithVerifiedCachedApk() {
        assertFalse(shouldOpenUpdateInstaller(UpdateState(ready=true),true))
    }
    @Test fun explicitUpdateContinuesOnlyAfterVerificationAndWhileForeground() {
        val requested=UpdateState(installPending=true)
        assertFalse(shouldOpenUpdateInstaller(requested,true))
        assertFalse(shouldOpenUpdateInstaller(requested.copy(ready=true),false))
        assertTrue(shouldOpenUpdateInstaller(requested.copy(ready=true),true))
    }
    @Test fun busyFailedOrConsumedRequestsCannotLaunchInstaller() {
        val ready=UpdateState(ready=true,installPending=true)
        assertFalse(shouldOpenUpdateInstaller(ready.copy(busy=true),true))
        assertFalse(shouldOpenUpdateInstaller(ready.copy(error=true),true))
        assertFalse(shouldOpenUpdateInstaller(ready.copy(installPending=false),true))
    }
}

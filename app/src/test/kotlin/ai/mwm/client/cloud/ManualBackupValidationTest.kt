package ai.mwm.client.cloud

import android.app.Application
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class ManualBackupValidationTest {
    private fun main(space:String)=JSONObject().put("schema",1).put("last_shared_space",space)
        .put("state",JSONObject().put("preferences",JSONObject()))
        .put("private_task_notes",JSONObject()).put("personal_reminders",JSONObject())
    @Test fun sameSpaceBackupAndUnboundPrivateBackupAreAccepted() {
        AccountBackup.validateManualBackup("main",main("our-space"),"our-space")
        AccountBackup.validateManualBackup("main",main(""),"our-space")
    }
    @Test fun formerCoupleBackupIsRejectedBeforeWrites() {
        val failure=runCatching {AccountBackup.validateManualBackup("main",main("former-space"),"our-space")}.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("different shared space"))
    }
    @Test fun malformedMainAndFutureFormatsAreRejected() {
        assertTrue(runCatching {AccountBackup.validateManualBackup("main",main("").put("schema",2),"")}.isFailure)
        assertTrue(runCatching {AccountBackup.validateManualBackup("main",main("").put("state",JSONObject()),"")}.isFailure)
    }
    @Test fun badCareTimeAndWrongSpaceAreRejected() {
        val care=JSONObject().put("schema",1).put("space","ours").put("vault",JSONObject().put("checkTime",1440).put("headsTime",540))
        assertTrue(runCatching {AccountBackup.validateManualBackup("care",care,"ours")}.isFailure)
        care.getJSONObject("vault").put("checkTime",540)
        AccountBackup.validateManualBackup("care",care,"ours")
        assertTrue(runCatching {AccountBackup.validateManualBackup("care",care,"other")}.isFailure)
    }
    @Test fun restoreKeepsSharingConsentAndLiveSharedHistory() {
        fun data(consent:Boolean,day:Int)=JSONObject().put("space","ours").put("vault",JSONObject()
            .put("consent",consent).put("shareSpace","ours").put("pendingShare",false)
            .put("logs",JSONArray().put(day)).put("cycle",28).put("duration",5)
            .put("checkIn",true).put("categories",JSONArray().put("Mood")))
        val saved=data(false,1);val live=data(true,2)
        val result=privateCareRestore(saved,live).getJSONObject("vault")
        assertTrue(result.getBoolean("consent"))
        assertEquals(2,result.getJSONArray("logs").getInt(0))
        assertEquals(1,saved.getJSONObject("vault").getJSONArray("logs").getInt(0))
        live.getJSONObject("vault").put("consent",false)
        assertEquals(1,privateCareRestore(saved,live).getJSONObject("vault").getJSONArray("logs").getInt(0))
    }
    @Test fun partialFailureRestoresLocalDataAndDoesNotReportSuccess() {
        var main="before";var care="before"
        val failure=runCatching {restoreWithRollback(apply={main="backup";error("Care write failed")},rollback={main="before";care="before"})}.exceptionOrNull()
        assertEquals("Care write failed",failure?.message)
        assertEquals("before",main);assertEquals("before",care)
    }
    @Test fun recoveryFailureIsReportedRatherThanHidden() {
        val failure=runCatching {restoreWithRollback(apply={error("write failed")},rollback={error("disk full")})}.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("recovery could not finish"))
        assertEquals("disk full",failure?.suppressed?.single()?.message)
    }
}

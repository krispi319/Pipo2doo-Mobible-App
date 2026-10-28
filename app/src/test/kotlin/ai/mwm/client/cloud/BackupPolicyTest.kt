package ai.mwm.client.cloud
import org.junit.Assert.*
import org.junit.Test
class BackupPolicyTest {
 @Test fun cleanReinstallRestoresExistingAccount(){assertEquals("restore",AccountBackup.decision(false,false,true,0,5))}
 @Test fun newAccountUploadsOwnLocalData(){assertEquals("upload",AccountBackup.decision(false,true,true,0,0))}
 @Test fun initialCompetingPhoneDataRequiresConflictResolution(){assertEquals("conflict",AccountBackup.decision(false,true,true,0,5))}
 @Test fun newerRemoteRestoresOnlyWithoutLocalChanges(){assertEquals("restore",AccountBackup.decision(true,true,false,4,5));assertEquals("conflict",AccountBackup.decision(true,true,true,4,5))}
 @Test fun dirtyLocalDataUploadsAtKnownVersion(){assertEquals("upload",AccountBackup.decision(true,true,true,5,5))}
 @Test fun unchangedBackupIsCurrent(){assertEquals("current",AccountBackup.decision(true,true,false,5,5))}
 @Test fun missingRemoteNotSilentlyRecreatedAfterRestore(){assertEquals("conflict",AccountBackup.decision(true,true,false,5,0))}
}

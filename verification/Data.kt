package ai.mwm.client.data
import android.content.Context
import org.json.JSONObject
object AccountScope {
 fun current(c:Context)=c.owner
 fun key(uid:String)=uid
 fun preferencesName(c:Context)="main_${c.owner}"
}
class CareRepository(val c:Context) {
 companion object {
 val repos=mutableMapOf<Context,CareRepository>()
 fun get(c:Context)=repos.getOrPut(c){CareRepository(c)}
 }
 var data:JSONObject?=null
 fun hasBackupData()=data!=null
 fun exportBackup()=data ?: JSONObject().put("logs",org.json.JSONArray())
 fun restoreBackup(root:JSONObject){data=root}
}

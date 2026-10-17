package ai.mwm.client.cloud
import android.content.Context
import ai.mwm.client.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
fun main()=runBlocking {
 var checks=0
 fun verify(ok:Boolean,name:String){check(ok){name};checks++;println("PASS $name")}
 fun context(owner:String="alice")=Context(kotlin.io.path.createTempDirectory("pipo-backup").toFile(),owner)
 fun prefs(c:Context)=c.getSharedPreferences(AccountScope.preferencesName(c),0)
 fun state(c:Context,date:Int){prefs(c).edit().putString("state",JSONObject().put("preferences",JSONObject().put("togetherSince",date).put("myName","Kris")).toString()).commit()}
 val original=context();state(original,20000)
 val imageDir=File(original.noBackupFilesDir,"pictures_alice").apply{mkdirs()}
 val image=File(imageDir,"photo.jpg").apply{writeBytes(byteArrayOf(1,2,3,4))}
 val raw=JSONObject(prefs(original).getString("state","{}")!!);raw.getJSONObject("preferences").put("myPhoto",image.path)
 prefs(original).edit().putString("state",raw.toString()).putString("private_task_notes","{\"task\":\"secret\"}").putString("last_shared_space","old-space").commit()
 AccountBackup.syncMain(original)
 verify(SupabaseClient.rows["alicemain"]!!.getLong("version")==1L,"initial upload")
 val reinstalled=context()
 verify(AccountBackup.syncMain(reinstalled),"reinstall restores instead of uploading defaults")
 val restored=JSONObject(prefs(reinstalled).getString("state","{}")!!).getJSONObject("preferences")
 verify(restored.getInt("togetherSince")==20000 && restored.getString("myName")=="Kris","date and personal info survive")
 verify(File(restored.getString("myPhoto")).readBytes().contentEquals(byteArrayOf(1,2,3,4)),"local photo recreated on new install")
 verify(prefs(reinstalled).getString("private_task_notes","")=="{\"task\":\"secret\"}","private notes restored")
 verify(prefs(reinstalled).getString("last_shared_space","")=="old-space","space binding restored for partner isolation")
 AccountBackup.syncMain(reinstalled)
 verify(SupabaseClient.rows["alicemain"]!!.getLong("version")==1L,"restore hash stable; no redundant overwrite")
 val bob=context("bob");AccountBackup.syncMain(bob)
 verify(prefs(bob).getString("state",null)==null,"another account cannot restore Alice data")
 state(reinstalled,20001);AccountBackup.syncMain(reinstalled)
 verify(SupabaseClient.rows["alicemain"]!!.getLong("version")==2L,"new local edit uploads")
 AccountBackup.syncMain(original)
 verify(JSONObject(prefs(original).getString("state","{}")!!).getJSONObject("preferences").getInt("togetherSince")==20001,"unchanged second phone follows newer backup")
 state(original,20002);state(reinstalled,20003);AccountBackup.syncMain(reinstalled);AccountBackup.syncMain(original)
 verify(JSONObject(prefs(original).getString("state","{}")!!).getJSONObject("preferences").getInt("togetherSince")==20002,"concurrent local changes preserved")
 verify(AccountBackup.status.value["alice:main"]!!.startsWith("Not backed up"),"conflict blocks saved status")
 val offline=context("offline");state(offline,1);SupabaseClient.offline=true;AccountBackup.syncMain(offline);SupabaseClient.offline=false
 verify(AccountBackup.status.value["offline:main"]!!.startsWith("Not backed up"),"offline not reported as saved")
 val newer=context("newer");state(newer,1)
 SupabaseClient.afterWrite={state(newer,2)};AccountBackup.syncMain(newer)
 verify(AccountBackup.status.value["newer:main"]!!.startsWith("Not backed up"),"edit during upload stays pending")
 AccountBackup.syncMain(newer);verify(SupabaseClient.rows["newermain"]!!.getLong("version")==2L,"pending edit retried")
 val care=CareRepository.get(original);care.data=JSONObject().put("logs",org.json.JSONArray().put(JSONObject().put("note","support"))).put("cycle",30)
 AccountBackup.syncCare(original)
 val careNew=context();verify(AccountBackup.syncCare(careNew),"private care restores without partner sharing")
 verify(CareRepository.get(careNew).data!!.getInt("cycle")==30,"care cycle restored")
 verify(AccountBackup.decision(false,true,true,0,1)=="conflict","existing migration data not silently discarded")
 verify(AccountBackup.canonical(JSONObject("{\"b\":2,\"a\":1}"))==AccountBackup.canonical(JSONObject("{\"a\":1,\"b\":2}")),"canonical hashes independent of JSON key order")
 println("$checks checks passed (platform stubs; not an Android device test)")
}

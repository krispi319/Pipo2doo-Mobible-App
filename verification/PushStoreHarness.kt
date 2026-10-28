package ai.mwm.client.cloud
import android.content.Context
import java.nio.file.Files
import java.io.File
import org.json.*
fun main(){
 val dir=Files.createTempDirectory("pipo-queue").toFile();val store=PushWorkStore(Context(dir));var n=0
 fun test(value:Boolean){check(value);n++}
 fun data(id:String="12345678-1234-1234-1234-123456789abc")=mapOf("type" to "task_event","event_id" to id)
 store.add("owner-a",data());store.add("owner-a",data());val row=store.next("owner-a")!!
 test(JSONArray(File(dir,"pending_push_work.json").readText()).length()==1)
 test(row.getString("scope")=="owner-a")
 store.finish(row,true);test(store.pending());test(store.next("owner-a")==null)
 repeat(4){store.finish(row,true)};test(!store.pending())
 store.add("owner-a",data());test(store.next("owner-b")==null);test(!store.pending())
 store.add("owner-a",data());store.finish(store.next("owner-a")!!,false);test(!store.pending())
 repeat(140){store.add("owner-a",data("12345678-1234-1234-1234-"+it.toString().padStart(12,'0')))}
 test(JSONArray(File(dir,"pending_push_work.json").readText()).length()==128)
 val old=JSONArray().put(JSONObject().put("scope","owner-a").put("at",1));File(dir,"pending_push_work.json").writeText(old.toString());test(store.next("owner-a")==null);test(!store.pending())
 dir.deleteRecursively();println("$n push queue assertions passed with filesystem adapters (not an Android OS test).")
}

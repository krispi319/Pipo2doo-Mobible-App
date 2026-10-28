package ai.mwm.client.cloud

import ai.mwm.client.data.AccountScope
import android.app.job.*
import android.content.*
import android.os.Build
import android.util.AtomicFile
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*
import org.json.*
import java.io.File

internal fun transientPushFailure(e:Throwable):Boolean = e is java.io.IOException || e is TimeoutCancellationException ||
    (e is SupabaseFailure && (e.status==408 || e.status==429 || e.status>=500))
internal fun validPushData(data:Map<String,String>):Boolean {
    val type=data["type"] ?: return false
    val key=when(type){"task_event","partner_poke"->"event_id";"partner_note","partner_locket"->"record_id";else->return false}
    val id=data[key] ?: return false
    return if(type=="partner_poke")id.matches(Regex("[A-Za-z0-9_-]{1,100}")) else runCatching {java.util.UUID.fromString(id)}.isSuccess
}
/** Disk stores event IDs and account scope only; actual contents require a fresh authorized fetch. */
internal class PushWorkStore(context:Context) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"pending_push_work.json"))
    private fun load():List<JSONObject> = runCatching {val a=JSONArray(file.openRead().bufferedReader().use {it.readText()});(0 until a.length()).map {a.getJSONObject(it)}}.getOrDefault(emptyList())
    private fun save(rows:List<JSONObject>) {val stream=file.startWrite();try {stream.write(JSONArray(rows).toString().toByteArray());file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}}
    fun add(scope:String,data:Map<String,String>)=synchronized(lock) {
        require(validPushData(data));val type=data.getValue("type");val id=data["event_id"] ?: data.getValue("record_id")
        val rows=load().filter {it.optLong("at")>System.currentTimeMillis()-86_400_000}.toMutableList()
        if(rows.none {it.optString("scope")==scope && it.optString("type")==type && it.optString("id")==id})
            rows+=JSONObject().put("scope",scope).put("type",type).put("id",id).put("at",System.currentTimeMillis()).put("attempts",0).put("next",0)
        save(rows.takeLast(128))
    }
    fun next(scope:String):JSONObject?=synchronized(lock) {
        val rows=load().filter {it.optString("scope")==scope && it.optLong("at")>System.currentTimeMillis()-86_400_000}
        save(rows);rows.firstOrNull {it.optLong("next")<=System.currentTimeMillis()}
    }
    fun finish(row:JSONObject,retry:Boolean)=synchronized(lock) {
        val rows=load().toMutableList();val target=rows.firstOrNull {it.optString("scope")==row.optString("scope") && it.optString("type")==row.optString("type") && it.optString("id")==row.optString("id")} ?: return@synchronized
        val attempts=target.optInt("attempts")+1
        if(!retry || attempts>=5)rows.remove(target) else target.put("attempts",attempts).put("next",System.currentTimeMillis()+15_000L*(1L shl attempts))
        save(rows)
    }
    fun pending():Boolean=synchronized(lock){load().isNotEmpty()}
    companion object {private val lock=Any()}
}
class PushJobService:JobService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var active:Job?=null
    override fun onStartJob(params:JobParameters):Boolean {
        val ticket=synchronized(scheduleLock){running=true;++generation}
        active=scope.launch {
            var more=false
            try {
                val store=PushWorkStore(this@PushJobService);val began=System.currentTimeMillis()
                while(isActive && System.currentTimeMillis()-began<40_000) {
                    val account=AccountScope.current(this@PushJobService)
                    val row=store.next(account) ?: break
                    val type=row.getString("type");val id=row.getString("id")
                    val key=if(type in listOf("task_event","partner_poke"))"event_id" else "record_id"
                    val data=mapOf("type" to type,key to id)
                    currentCoroutineContext().ensureActive()
                    val delivery=AuthorizedPushDelivery(this@PushJobService)
                    delivery.receive(RemoteMessage.Builder("pipo2doo").setData(data).build())
                    currentCoroutineContext().ensureActive()
                    store.finish(row,delivery.retryNeeded)
                }
                more=store.pending()
            }catch(e:CancellationException){throw e}catch(_:Exception){more=true}
            finally {val alive=currentCoroutineContext().isActive;synchronized(scheduleLock){if(ticket==generation){running=false;if(alive)jobFinished(params,more || runCatching {PushWorkStore(this@PushJobService).pending()}.getOrDefault(true))}}}
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {synchronized(scheduleLock){generation++;running=false;active?.cancel()};return true}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
    companion object {
        private const val JOB_ID=14003
        private val scheduleLock=Any()
        private var running=false
        private var generation=0L
        fun enqueue(context:Context,data:Map<String,String>) {
            if(!validPushData(data))return
            try {synchronized(scheduleLock){
                val store=PushWorkStore(context);store.add(AccountScope.current(context),data)
                val scheduler=context.getSystemService(JobScheduler::class.java)
                // Existing pending/running job drains appended records, without canceling its current fetch.
                if(running)return
                fun job(expedited:Boolean):JobInfo {
                    val builder=JobInfo.Builder(JOB_ID,ComponentName(context,PushJobService::class.java)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                    if(Build.VERSION.SDK_INT>=31 && expedited)builder.setExpedited(true)
                    else builder.setBackoffCriteria(30_000,JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    return builder.build()
                }
                if(scheduler.schedule(job(true))==JobScheduler.RESULT_FAILURE)scheduler.schedule(job(false))
            }}catch(_:Exception){ /* App foreground refresh still fetches server content; no private contents were persisted. */ }
        }
    }
}

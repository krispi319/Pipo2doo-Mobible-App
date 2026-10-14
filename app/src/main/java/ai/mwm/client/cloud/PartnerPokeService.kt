package ai.mwm.client.cloud

import ai.mwm.client.MainActivity
import ai.mwm.client.R
import ai.mwm.client.data.PipoRepository
import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.TimeUnit
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONObject

/** No automatic system-display FCM payload. Every notification needs a fresh authorized read. */
class PartnerPokeService: FirebaseMessagingService() {
    private val tokenScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onDestroy() { tokenScope.cancel(); super.onDestroy() }
    override fun onNewToken(token: String) {
        // Never log or persist the token; register only for the authenticated consenting user.
        if(CloudRelease.ENABLED && FirebaseAuth.getInstance().currentUser!=null) tokenScope.launch {
            runCatching { PipoFirebase.get(this@PartnerPokeService).updateDevice(PipoRepository.get(this@PartnerPokeService).state.value.preferences,force=true) }
        }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        if(!CloudRelease.ENABLED || FirebaseAuth.getInstance().currentUser==null || message.data["type"]!="partner_poke" || !allowed(this)) return
        val p=PipoRepository.get(this).state.value.preferences
        if(!p.pokeOptIn) return
        val event=message.data["event_id"]?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,100}")) } ?: return
        try {
            val data=Tasks.await(FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("pipoApi").call(mapOf("action" to "validatePoke","eventId" to event)),8,TimeUnit.SECONDS).data as? Map<*,*> ?: return
            val task=data["taskId"] as? String ?: return
            val expires=(data["expiresAt"] as? Number)?.toLong() ?: return
            if(expires<=System.currentTimeMillis() || !allowed(this) || !PipoRepository.get(this).state.value.preferences.pokeOptIn) return
            synchronized(lock) {
                val cache=File(noBackupFilesDir,"poke_seen.json")
                val seen=runCatching {JSONObject(cache.readText())}.getOrDefault(JSONObject())
                val now=System.currentTimeMillis()
                seen.keys().asSequence().toList().forEach { if(seen.optLong(it)<now-86400000) seen.remove(it) }
                if(seen.has(event)) return
                seen.put(event,now)
                val atomic=android.util.AtomicFile(cache);val stream=atomic.startWrite()
                try {stream.write(seen.toString().toByteArray());atomic.finishWrite(stream)}catch(e: Exception){atomic.failWrite(stream);return}
                val intent=Intent(this,MainActivity::class.java).setData(Uri.parse("pipo2doo://partner-poke/$event")).putExtra("poke_event_id",event).putExtra("task_id",task)
                val pending=PendingIntent.getActivity(this,event.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_task_reminder).setContentTitle("A gentle Poke").setContentText("Open Pipo2doo to check your task.").setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setTimeoutAfter(expires-now).setContentIntent(pending).build()
                getSystemService(NotificationManager::class.java).notify("partner_poke",task.hashCode(),notification)
            }
        } catch(_: Exception) { /* Fail closed; no fresh authorization means no notification. */ }
    }
    companion object {
        const val CHANNEL="partner_pokes"
        private val lock=Any()
        fun channel(context: Context) { context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Partner pokes",NotificationManager.IMPORTANCE_DEFAULT).apply {description="Optional gentle nudges from your paired partner"}) }
        fun allowed(context: Context): Boolean { val manager=context.getSystemService(NotificationManager::class.java); return (Build.VERSION.SDK_INT<33 || androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance?.let {it!=NotificationManager.IMPORTANCE_NONE}==true }
        fun clear(context: Context) {val manager=context.getSystemService(NotificationManager::class.java);manager.activeNotifications.filter {it.tag=="partner_poke"}.forEach {manager.cancel(it.tag,it.id)} }
    }
}

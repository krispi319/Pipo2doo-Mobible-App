package ai.mwm.client.cloud

import ai.mwm.client.MainActivity
import ai.mwm.client.R
import ai.mwm.client.reminders.NotificationSounds
import ai.mwm.client.data.PipoRepository
import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.tasks.Tasks
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
        if(CloudRelease.ENABLED) tokenScope.launch {
            runCatching { PipoFirebase.get(this@PartnerPokeService).updateDevice(PipoRepository.get(this@PartnerPokeService).state.value.preferences,force=true) }
        }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        channel(this)
        if(message.data["type"]=="task_event") {TaskPushNotifications.receive(this,message.data["event_id"]);return}
        if(message.data["type"]=="partner_note") {receiveNote(message);return}
        if(message.data["type"]=="partner_locket") {receiveLocket(message);return}
        if(!CloudRelease.ENABLED || message.data["type"]!="partner_poke" || !allowed(this)) return
        val p=PipoRepository.get(this).state.value.preferences
        if(!p.pokeOptIn || quiet(p)) return
        val account=ai.mwm.client.data.AccountScope.current(this)
        val event=message.data["event_id"]?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,100}")) } ?: return
        try {
            val data=runBlocking {withTimeout(15_000) { PipoFirebase.get(this@PartnerPokeService).validatePoke(event) }}
            if(account!=ai.mwm.client.data.AccountScope.current(this) || ai.mwm.client.data.AccountScope.key(data["recipientUid"] as? String ?: "")!=account) return
            val task=data["taskId"] as? String ?: return
            val expires=(data["expiresAt"] as? Number)?.toLong() ?: return
            if(expires<=System.currentTimeMillis() || !allowed(this) || !PipoRepository.get(this).state.value.preferences.pokeOptIn) return
            val person=runBlocking {withTimeout(7000) {NotificationArtwork.partner(this@PartnerPokeService,data["senderUid"] as? String ?: error("Missing sender"))}}
            synchronized(lock) {
                val cache=File(noBackupFilesDir,"poke_seen_$account.json")
                val seen=runCatching {JSONObject(cache.readText())}.getOrDefault(JSONObject())
                val now=System.currentTimeMillis()
                seen.keys().asSequence().toList().forEach { if(seen.optLong(it)<now-86400000) seen.remove(it) }
                if(seen.has(event)) return
                seen.put(event,now)
                val atomic=android.util.AtomicFile(cache);val stream=atomic.startWrite()
                try {stream.write(seen.toString().toByteArray());atomic.finishWrite(stream)}catch(e: Exception){atomic.failWrite(stream);return}
                val intent=Intent(this,MainActivity::class.java).setData(Uri.parse("pipo2doo://partner-poke/$event")).putExtra("poke_event_id",event).putExtra("task_id",task).putExtra("account_scope",account)
                val pending=PendingIntent.getActivity(this,event.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification=NotificationArtwork.decorate(NotificationCompat.Builder(this,NotificationSounds.channelId(this,CHANNEL)),person,this).setSmallIcon(R.drawable.ic_pipo_notification).setContentTitle("${person.name} sent a Poke").setContentText("Open Pipo2doo to check your task.").setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setTimeoutAfter(expires-now).setContentIntent(pending).build()
                if(account!=ai.mwm.client.data.AccountScope.current(this) || !PipoRepository.get(this).state.value.preferences.pokeOptIn) return
                getSystemService(NotificationManager::class.java).notify("partner_poke",task.hashCode(),notification)
            }
        } catch(_: Exception) { /* Fail closed; no fresh authorization means no notification. */ }
    }
    private fun receiveLocket(message: RemoteMessage) {
        channel(this)
        val p=PipoRepository.get(this).state.value.preferences
        if(!CloudRelease.ENABLED || !allowed(this,LOCKET_CHANNEL) || !p.pokeOptIn || quiet(p)) return
        val account=ai.mwm.client.data.AccountScope.current(this)
        val record=message.data["record_id"]?.takeIf {runCatching {java.util.UUID.fromString(it)}.isSuccess} ?: return
        try {
            // Fetch metadata only; never download picture bytes in the FCM callback.
            val uid=runBlocking {withTimeout(5_000) {PipoFirebase.get(this@PartnerPokeService).validateLocket(record)}}
            if(ai.mwm.client.data.AccountScope.key(uid)!=account || account!=ai.mwm.client.data.AccountScope.current(this)) return
            val person=runBlocking {withTimeout(7000) {val sender=PipoFirebase.get(this@PartnerPokeService).locketSender(record);NotificationArtwork.partner(this@PartnerPokeService,sender)}}
            synchronized(lock) {
                val current=PipoRepository.get(this).state.value.preferences
                if(!current.pokeOptIn || quiet(current) || !allowed(this,LOCKET_CHANNEL)) return
                val seen=getSharedPreferences("locket_seen_$account",Context.MODE_PRIVATE)
                if(seen.contains(record)) return
                val now=System.currentTimeMillis()
                val edit=seen.edit()
                seen.all.forEach {(key,value)->if(value is Long && value<now-86_400_000L) edit.remove(key)}
                if(!edit.putLong(record,now).commit()) return
                val intent=Intent(this,MainActivity::class.java).setData(Uri.parse("pipo2doo://locket/$record")).putExtra("account_scope",account)
                val pending=PendingIntent.getActivity(this,record.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification=NotificationArtwork.decorate(NotificationCompat.Builder(this,NotificationSounds.channelId(this,LOCKET_CHANNEL)),person,this)
                    .setSmallIcon(R.drawable.ic_pipo_notification).setContentTitle("${person.name} shared a Locket picture")
                    .setContentText("Your partner shared a photo. Open Pipo2doo to see it.")
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH).setOnlyAlertOnce(true)
                    .setTimeoutAfter(86_400_000L).setContentIntent(pending).build()
                if(account==ai.mwm.client.data.AccountScope.current(this)) getSystemService(NotificationManager::class.java).notify("partner_locket",record.hashCode(),notification)
            }
        } catch(_: Exception) { /* Suppress notifications when current membership cannot be verified. */ }
    }
    private fun receiveNote(message: RemoteMessage) {
        val account=ai.mwm.client.data.AccountScope.current(this)
        val p=PipoRepository.get(this).state.value.preferences
        if(!CloudRelease.ENABLED || !allowed(this,MESSAGE_CHANNEL) || !p.pokeOptIn || quiet(p)) return
        val record=message.data["record_id"]?.takeIf {runCatching {java.util.UUID.fromString(it)}.isSuccess} ?: return
        try {
            val data=runBlocking {withTimeout(7000) {PipoFirebase.get(this@PartnerPokeService).validatePartnerNote(record)}}
            if(ai.mwm.client.data.AccountScope.key(data.getString("uid"))!=account || account!=ai.mwm.client.data.AccountScope.current(this)) return
            val space=data.getString("space")
            if(record in ai.mwm.client.data.ChatReadState.read(this,space,account)) return
            val person=runBlocking {withTimeout(7000) {NotificationArtwork.partner(this@PartnerPokeService,data.getString("sender"))}}
            synchronized(lock) {
                val current=PipoRepository.get(this).state.value.preferences
                if(account!=ai.mwm.client.data.AccountScope.current(this) || !current.pokeOptIn || quiet(current) || !allowed(this,MESSAGE_CHANNEL)) return
                if(record in ai.mwm.client.data.ChatReadState.read(this,space,account)) return
                val seen=getSharedPreferences("note_push_seen_$account",Context.MODE_PRIVATE)
                if(seen.contains(record)) return
                val intent=Intent(this,MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("pipo2doo://chat/$space"))
                    .putExtra("account_scope",account).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val shortcutId="chat_${account.take(16)}_$space"
                val shortcut=androidx.core.content.pm.ShortcutInfoCompat.Builder(this,shortcutId)
                    .setShortLabel(person.name ?: "Partner").setLongLived(true).setPersons(arrayOf(person)).setIntent(intent)
                    .setIcon(person.icon ?: androidx.core.graphics.drawable.IconCompat.createWithResource(this,R.drawable.ic_pipo_notification)).build()
                androidx.core.content.pm.ShortcutManagerCompat.pushDynamicShortcut(this,shortcut)
                val pending=PendingIntent.getActivity(this,space.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val text=when(data.getString("kind")) {
                    "Message" -> data.optString("message").ifBlank {"Sent you a picture"}
                    "CareSupport" -> "A little support: "+data.optString("message")
                    "Heart" -> "Loved your Locket picture ♡"
                    else -> data.getString("kind")+data.optString("message").takeIf {it.isNotBlank()}?.let {" · $it"}.orEmpty()
                }
                val self=androidx.core.app.Person.Builder().setName(current.myName.ifBlank {"Me"}).build()
                val notification=NotificationArtwork.decorate(NotificationCompat.Builder(this,NotificationSounds.channelId(this,MESSAGE_CHANNEL)),person,this)
                    .setSmallIcon(R.drawable.ic_pipo_notification)
                    .setStyle(NotificationCompat.MessagingStyle(self).addMessage(text,data.getLong("at"),person))
                    .setShortcutId(shortcutId).setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(pending)
                    .setPriority(NotificationCompat.PRIORITY_HIGH).setTimeoutAfter(86_400_000L).build()
                // Keep deduplication bounded. Messages remain in Supabase, not in notification preferences.
                val now=System.currentTimeMillis();val edit=seen.edit()
                seen.all.forEach {(key,value)->if(value is Long && value<now-86_400_000L) edit.remove(key)}
                if(!edit.putLong(record,now).commit()) return
                if(account==ai.mwm.client.data.AccountScope.current(this)) getSystemService(NotificationManager::class.java).notify("partner_note",record.hashCode(),notification)
            }
        } catch(_: Exception) { /* A private notification requires a fresh authorized record. */ }
    }
    companion object {
        private fun quiet(p: ai.mwm.client.data.Preferences): Boolean {
            if(!p.quietEnabled || p.quietStart==p.quietEnd) return false
            val t=java.time.LocalTime.now();val m=t.hour*60+t.minute
            return if(p.quietStart<p.quietEnd) m in p.quietStart until p.quietEnd else m>=p.quietStart || m<p.quietEnd
        }
        const val MESSAGE_CHANNEL="partner_messages"
        const val CHANNEL="partner_pokes"
        const val LOCKET_CHANNEL="partner_locket_photos"
        private val lock=Any()
        fun channel(context: Context) {
            val manager=context.getSystemService(NotificationManager::class.java)
            NotificationSounds.ensure(context,MESSAGE_CHANNEL,"Partner messages",NotificationManager.IMPORTANCE_HIGH,"Messages, thank-you and support notes from your partner")
            NotificationSounds.ensure(context,CHANNEL,"Partner pokes",NotificationManager.IMPORTANCE_DEFAULT,"Gentle nudges from your paired partner")
            NotificationSounds.ensure(context,LOCKET_CHANNEL,"Locket photos",NotificationManager.IMPORTANCE_HIGH,"Photos shared by your partner")
        }
        fun allowed(context: Context, channelId: String=CHANNEL): Boolean { val manager=context.getSystemService(NotificationManager::class.java); return (Build.VERSION.SDK_INT<33 || androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled() && manager.getNotificationChannel(NotificationSounds.channelId(context,channelId))?.importance?.let {it!=NotificationManager.IMPORTANCE_NONE}==true }
        fun clear(context: Context) {
            ai.mwm.client.data.TaskAlarmCache.clear(context)
            val manager=context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications.filter {it.tag=="task_event" || it.tag=="partner_poke" || it.tag=="partner_locket" || it.tag=="partner_note"}.forEach {manager.cancel(it.tag,it.id)}
            val shortcuts=androidx.core.content.pm.ShortcutManagerCompat.getDynamicShortcuts(context).map {it.id}.filter {it.startsWith("chat_")}
            if(shortcuts.isNotEmpty()) androidx.core.content.pm.ShortcutManagerCompat.removeLongLivedShortcuts(context,shortcuts)
        }
    }
}

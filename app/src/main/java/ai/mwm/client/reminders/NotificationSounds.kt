package ai.mwm.client.reminders

import ai.mwm.client.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri

/** Tone selection is per phone. Separate channels are required because Android channel sounds are immutable. */
object NotificationSounds {
    val choices=listOf("system" to "System sound","pi" to "Pi style","silent" to "Silent")
    private fun prefs(context: Context)=context.getSharedPreferences("pipo_notification_audio",Context.MODE_PRIVATE)
    fun selected(context: Context)=prefs(context).getString("tone","system").takeIf {key->choices.any {it.first==key}} ?: "system"
    fun label(key: String)=choices.firstOrNull {it.first==key}?.second ?: "System sound"
    fun channelId(context: Context,base: String)=channelIdFor(base,selected(context))
    fun channelIdFor(base: String,key: String)=if(key=="pi") base+"_pi_v1" else if(key=="silent") base+"_silent_v1" else base
    fun uri(context: Context,key: String=selected(context)): Uri? = when(key) {
        "pi"->Uri.parse("android.resource://${context.packageName}/raw/${context.resources.getResourceEntryName(R.raw.pi_style)}")
        "silent"->null
        else->RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    fun ensure(context: Context,base: String,name: String,importance: Int,description: String=""): String {
        val id=channelId(context,base)
        val manager=context.getSystemService(NotificationManager::class.java)
        if(manager.getNotificationChannel(id)!=null) return id
        val original=manager.getNotificationChannel(base)
        val channel=NotificationChannel(id,if(selected(context)=="system") name else "$name · ${label(selected(context))}",original?.importance ?: importance)
        channel.description=description
        channel.lockscreenVisibility=android.app.Notification.VISIBILITY_PRIVATE
        channel.setSound(uri(context),attributes)
        channel.enableVibration(original?.shouldVibrate() ?: false)
        if(original?.shouldVibrate()==true) channel.vibrationPattern=original.vibrationPattern
        manager.createNotificationChannel(channel)
        return id
    }
    fun select(context: Context,key: String) {
        require(choices.any {it.first==key})
        // Preserve restrictions on the channel the user was actually using before selecting another tone.
        val previous=selected(context)
        val manager=context.getSystemService(NotificationManager::class.java)
        val bases=listOf("pipo_ongoing_tasks","pipo_private_reminders","partner_pokes","partner_locket_photos","partner_messages")
        check(prefs(context).edit().putString("tone",key).commit()) {"Could not save notification sound."}
        bases.forEach {base->
            val old=manager.getNotificationChannel(channelIdFor(base,previous))
            if(old!=null && manager.getNotificationChannel(channelId(context,base))==null) {
                val channel=NotificationChannel(channelId(context,base),old.name.toString().substringBefore(" · ")+if(key=="system") "" else " · ${label(key)}",old.importance)
                channel.description=old.description;channel.lockscreenVisibility=old.lockscreenVisibility
                channel.setSound(uri(context,key),attributes);channel.enableVibration(old.shouldVibrate());channel.vibrationPattern=old.vibrationPattern
                manager.createNotificationChannel(channel)
            }
        }
    }
    private var player: MediaPlayer?=null
    @Synchronized fun stopPreview() {player?.release();player=null}
    @Synchronized fun preview(context: Context) {
        stopPreview()
        val source=uri(context) ?: return
        val next=MediaPlayer();player=next
        try {
            next.setAudioAttributes(attributes);next.setDataSource(context,source)
            next.setOnCompletionListener {if(player===it) stopPreview() else it.release()}
            next.setOnErrorListener {p,_,_->if(player===p) stopPreview() else p.release();true}
            next.setOnPreparedListener {if(player===it) it.start()}
            next.prepareAsync()
        } catch(_: Exception) {stopPreview()}
    }
}

package ai.mwm.client.reminders

import ai.mwm.client.MainActivity
import ai.mwm.client.R
import ai.mwm.client.data.*
import android.app.*
import android.content.*
import android.net.Uri
import androidx.core.app.NotificationCompat
import java.time.*
import java.util.concurrent.Executors
import org.json.JSONObject

/** Personal alarms use the last confirmed couple date, never task assignment or chat. */
object LoveDayReminders {
    const val WAKE="ai.mwm.client.LOVE_DAY_WAKE"
    private const val ID=945
    private fun channel(s:LoveCelebrationSettings)="pipo_love_days_s${if(s.sound)1 else 0}_h${if(s.haptics)1 else 0}"
    fun allowed(c:Context,s:LoveCelebrationSettings):Boolean {
        val manager=c.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && (android.os.Build.VERSION.SDK_INT<33 || androidx.core.content.ContextCompat.checkSelfPermission(c,android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED) && manager.getNotificationChannel(channel(s))?.importance!=NotificationManager.IMPORTANCE_NONE
    }
    private fun cache(c:Context)=c.getSharedPreferences("pipo_love_${AccountScope.current(c)}",0)
    private fun workspace(c:Context)=c.getSharedPreferences(AccountScope.preferencesName(c),0).getString("last_shared_space","").orEmpty()
    private fun pending(c:Context,account:String)=PendingIntent.getBroadcast(c,ID,
        Intent(c,LoveDayReceiver::class.java).setAction(WAKE).setData(Uri.parse("pipo2doo://love-wake/$account")).putExtra("account_scope",account),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun activate(c:Context) {
        val account=AccountScope.current(c)
        val registry=c.getSharedPreferences("pipo_love_alarm_owner",0)
        val old=registry.getString("account",null)
        if(old!=account) {
            old?.let {c.getSystemService(AlarmManager::class.java).cancel(pending(c,it))}
            c.getSystemService(NotificationManager::class.java).cancel(ID)
            registry.edit().putString("account",account).commit()
        }
    }
    @Synchronized fun sync(c:Context,p:Preferences) {
        activate(c)
        cache(c).edit().putString("snapshot",JSONObject().put("workspace",workspace(c)).put("started",p.started)
            .put("since",p.togetherSince).put("zone",p.clockZone).put("quiet",p.quietEnabled).put("quietStart",p.quietStart).put("quietEnd",p.quietEnd)
            .put("settings",p.loveCelebrations.toJson()).toString()).commit()
        deliver(c,p)
    }
    @Synchronized fun resume(c:Context) {
        activate(c)
        val p=runCatching {
            val j=JSONObject(cache(c).getString("snapshot",null) ?: return@runCatching null)
            if(j.optString("workspace")!=workspace(c))return@runCatching null
            Preferences(started=j.optBoolean("started"),togetherSince=j.getLong("since"),clockZone=j.optString("zone"),
                quietEnabled=j.optBoolean("quiet",true),quietStart=j.optInt("quietStart",1320),quietEnd=j.optInt("quietEnd",450),
                loveCelebrations=LoveCelebrationSettings.fromJson(j.optJSONObject("settings")))
        }.getOrNull()
        if(p!=null)deliver(c,p) else {c.getSystemService(AlarmManager::class.java).cancel(pending(c,AccountScope.current(c)));c.getSystemService(NotificationManager::class.java).cancel(ID)}
    }
    private fun deliver(c:Context,p:Preferences) {
        val alarm=c.getSystemService(AlarmManager::class.java)
        val wake=pending(c,AccountScope.current(c));alarm.cancel(wake)
        val manager=c.getSystemService(NotificationManager::class.java)
        if(!p.started || !p.loveCelebrations.reminders || AccountScope.current(c)=="locked") {manager.cancel(ID);return}
        val now=System.currentTimeMillis()
        val zone=clockZone(p.clockZone)
        val events=loveReminderEvents(p,now)
        if(events.isEmpty()){manager.cancel(ID);return}
        val channel=channel(p.loveCelebrations)
        if(manager.getNotificationChannel(channel)==null) {
            val channelPrefs=c.getSharedPreferences("pipo_love_channel",0)
            val previous=channelPrefs.getString("active",null)?.let {manager.getNotificationChannel(it)}
            val definition=NotificationChannel(channel,"Love-day celebrations",previous?.importance ?: NotificationManager.IMPORTANCE_DEFAULT)
            definition.lockscreenVisibility=Notification.VISIBILITY_PRIVATE
            definition.setSound(if(p.loveCelebrations.sound)android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION) else null,
                android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build())
            definition.enableVibration(p.loveCelebrations.haptics)
            manager.createNotificationChannel(definition)
        }
        c.getSharedPreferences("pipo_love_channel",0).edit().putString("active",channel).commit()
        if(allowed(c,p.loveCelebrations)) {
            val due=events.lastOrNull {it.at<=now && !cache(c).getBoolean("sent:${workspace(c)}:${it.key(p)}",false)}
            if(due!=null) {
                val open=PendingIntent.getActivity(c,ID,Intent(c,MainActivity::class.java).setData(Uri.parse("pipo2doo://love-day/${due.milestone.day}"))
                    .putExtra("account_scope",AccountScope.current(c)).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val text=if(due.advance)"Coming up: ${due.milestone.title}" else due.milestone.celebrationTitle
                manager.notify(ID,NotificationCompat.Builder(c,channel).setSmallIcon(R.drawable.ic_pipo_notification)
                    .setContentTitle("A little milestone for you two ♡").setContentText(text).setContentIntent(open)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).build())
                // Mark all overdue notices from this same day together, avoiding a burst on resume.
                val edit=cache(c).edit();events.filter {it.at<=now}.forEach {edit.putBoolean("sent:${workspace(c)}:${it.key(p)}",true)};edit.commit()
            }
        }
        val midnight=Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val next=minOf(events.firstOrNull {it.at>now}?.at ?: midnight,midnight)
        // Inexact, battery-friendly delivery; no exact-alarm permission or foreground service.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,wake)
    }
    fun wasShown(c:Context,p:Preferences,m:LoveMilestone)=cache(c).getBoolean("shown:${workspace(c)}:${p.togetherSince}:${m.day}",false)
    fun markShown(c:Context,p:Preferences,m:LoveMilestone){cache(c).edit().putBoolean("shown:${workspace(c)}:${p.togetherSince}:${m.day}",true).commit()}
}
class LoveDayReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action==LoveDayReminders.WAKE && intent.getStringExtra("account_scope")!=AccountScope.current(context))return
        val result=goAsync()
        executor.execute {try {LoveDayReminders.resume(context)} catch(_:Exception) { /* Retry from the next app resume; never log private notes. */ } finally {result.finish()}}
    }
    companion object {private val executor=Executors.newSingleThreadExecutor()}
}

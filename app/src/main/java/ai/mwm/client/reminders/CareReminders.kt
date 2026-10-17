package ai.mwm.client.reminders

import ai.mwm.client.MainActivity
import ai.mwm.client.R
import ai.mwm.client.data.CareRepository
import ai.mwm.client.data.CareState
import android.app.*
import android.content.*
import android.net.Uri
import androidx.core.app.NotificationCompat
import java.time.*
import java.util.concurrent.Executors

object CareReminders {
    private const val CHANNEL="pipo_private_reminders"
    private fun pending(context: Context,kind: Int)=PendingIntent.getBroadcast(context,kind,Intent(context,CareReminderReceiver::class.java).setAction("ai.mwm.client.PRIVATE_REMINDER").putExtra("account_scope",ai.mwm.client.data.AccountScope.current(context)).putExtra("kind",kind),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun clear(context: Context) {val alarm=context.getSystemService(AlarmManager::class.java);(0..2).forEach {alarm.cancel(pending(context,it))};context.getSystemService(NotificationManager::class.java).cancel(901)}
    fun sync(context: Context,state: CareState) { val alarm=context.getSystemService(AlarmManager::class.java); (0..2).forEach { alarm.cancel(pending(context,it)) };val manager=context.getSystemService(NotificationManager::class.java);NotificationSounds.ensure(context,CHANNEL,"Care reminders",NotificationManager.IMPORTANCE_DEFAULT);if(!TaskReminders.allowed(context) || manager.getNotificationChannel(NotificationSounds.channelId(context,CHANNEL))?.importance==NotificationManager.IMPORTANCE_NONE) return;val now=ZonedDateTime.now()
        fun at(day: LocalDate,minute: Int): Long { return day.atTime(minute/60,minute%60).atZone(now.zone).toInstant().toEpochMilli() }
        if(state.endReminder) state.starts.lastOrNull()?.takeIf { start -> state.logs.none {it.end&&it.day>=start} }?.let {start ->val time=at(LocalDate.ofEpochDay(start+state.duration-1),state.endTime);if(time>System.currentTimeMillis()) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pending(context,2))}
        if(state.checkIn) {var day=now.toLocalDate();if(at(day,state.checkTime)<=System.currentTimeMillis()) day=day.plusDays(1);alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at(day,state.checkTime),pending(context,0))};if(state.headsUp) state.nextStart()?.let { next -> val time=at(LocalDate.ofEpochDay(next-2),state.headsTime);if(time>System.currentTimeMillis()) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pending(context,1)) } }
    fun post(context: Context) { if(!TaskReminders.allowed(context)) return; val open=PendingIntent.getActivity(context,901,Intent(context,MainActivity::class.java).putExtra("account_scope",ai.mwm.client.data.AccountScope.current(context)).setData(Uri.parse("pipo2doo://private-reminder")).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);context.getSystemService(NotificationManager::class.java).notify(901,NotificationCompat.Builder(context,NotificationSounds.ensure(context,CHANNEL,"Care reminders",NotificationManager.IMPORTANCE_DEFAULT)).setSmallIcon(R.drawable.ic_pipo_notification).setLargeIcon(ai.mwm.client.cloud.NotificationArtwork.logo(context)).setContentTitle("Pipo2doo").setContentText("A little reminder").setContentIntent(open).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setAutoCancel(true).build()) }
}
class CareReminderReceiver: BroadcastReceiver() { override fun onReceive(context: Context,intent: Intent) { val result=goAsync();executor.execute { try { synchronized(CareRepository.get(context)) { if(intent.action=="ai.mwm.client.PRIVATE_REMINDER" && intent.getStringExtra("account_scope")!=ai.mwm.client.data.AccountScope.current(context))return@execute;val state=CareRepository.get(context).load();if(intent.action=="ai.mwm.client.PRIVATE_REMINDER" && (when(intent.getIntExtra("kind",0)) {0->state.checkIn;2->state.endReminder;else->state.headsUp})) CareReminders.post(context);CareReminders.sync(context,state) } } catch(_: Exception) { /* Never log private vault data. */ } finally {result.finish()} } };companion object {private val executor=Executors.newSingleThreadExecutor()} }

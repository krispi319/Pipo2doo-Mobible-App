package ai.mwm.client.cloud

import ai.mwm.client.MainActivity
import ai.mwm.client.R
import ai.mwm.client.data.*
import ai.mwm.client.reminders.NotificationSounds
import ai.mwm.client.reminders.TaskReminders
import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.*
import java.util.UUID

/** FCM carries an opaque event ID, never trusted task contents or a device-local clock time. */
object TaskPushNotifications {
    private const val CHANNEL = "task_assignments"
    private val lock = Any()
    fun receive(context: Context, event: String?) {
        if(!CloudRelease.ENABLED || event == null || runCatching {UUID.fromString(event)}.isFailure) return
        val account = AccountScope.current(context)
        try {
            val data = runBlocking {withTimeout(12_000) {PipoFirebase.get(context).validateTaskNotification(event)}}
            if(account != AccountScope.current(context) || AccountScope.key(data.getString("uid")) != account) return
            val repository = PipoRepository.get(context)
            val current = TaskAlarmCache.forAlarm(context, repository.state.value, repository.isRemote)
            val p = current.preferences
            if(!p.pokeOptIn) return
            val payload = data.getJSONObject("payload")
            val uid = data.getString("uid")
            val assigned = data.optString("assignedTo").takeUnless {it == "null" || it.isBlank()}
            val task = Task(id=data.getString("taskId"),title=payload.getString("title"),day=payload.getLong("day"),
                minute=payload.optInt("minute",-1).takeIf {it >= 0},scheduledAt=payload.optLong("scheduledAt"),scheduleZone=payload.optString("scheduleZone"),
                reminder=repository.personalReminder(data.getString("taskId"),data.optBoolean("enabled",true),data.optInt("offset")).first && data.optBoolean("enabled",true),
                offset=repository.personalReminder(data.getString("taskId"),data.optBoolean("enabled",true),data.optInt("offset")).second,
                status=TaskStatus.valueOf(payload.getString("status")),repeat=Repeat.valueOf(payload.getString("repeat")),
                owner=when(assigned) {null->Owner.Together;uid->Owner.Me;else->Owner.You}).inTaskZone(clockZone(p.clockZone))
            if(data.getString("kind") == "sync") {
                val remaining=current.tasks.filterNot {it.id==task.id}
                if(account == AccountScope.current(context)) TaskReminders.sync(context,current.copy(tasks=remaining+if(data.optBoolean("deleted") || task.done) emptyList() else listOf(task)))
                return
            }
            if(data.getString("kind") != "due" && account == AccountScope.current(context)) {
                TaskReminders.sync(context,current.copy(tasks=current.tasks.filterNot {it.id==task.id}+if(task.done) emptyList() else listOf(task)))
            }
            if(data.getString("kind") == "due") {
                if(task.done || !task.reminder) return
                // Shares TaskReminders' delivery key with the local alarm: one visible reminder, no double sound.
                if(account == AccountScope.current(context)) TaskReminders.sync(context,current.copy(tasks=current.tasks.filterNot {it.id==task.id}+task))
                return
            }
            NotificationSounds.ensure(context,CHANNEL,"Task assignments",NotificationManager.IMPORTANCE_HIGH,"New and reassigned tasks in your couple workspace")
            if(!PartnerPokeService.allowed(context,CHANNEL)) return
            val actor = data.optString("actor").takeUnless {it == "null" || it.isBlank()}
            val person = if(actor != null && actor != uid) runCatching {runBlocking {withTimeout(5000) {NotificationArtwork.partner(context,actor)}}}.getOrNull() else null
            synchronized(lock) {
                if(account != AccountScope.current(context)) return
                val seen = context.getSharedPreferences("task_events_$account",0)
                if(seen.contains(event)) return
                val intent = Intent(context,MainActivity::class.java).setAction("ai.mwm.client.OPEN_TASK")
                    .setData(Uri.parse("pipo2doo://task/${task.id}")).putExtra("task_id",task.id).putExtra("account_scope",account)
                val pending = PendingIntent.getActivity(context,event.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val title = if(data.getString("kind")=="assigned") "Task assignment updated" else "New task"
                val detail = "${task.title} · ${dayText(task.day)} · ${timeText(task.minute)}"
                val builder = NotificationCompat.Builder(context,NotificationSounds.channelId(context,CHANNEL))
                    .setSmallIcon(R.drawable.ic_pipo_notification).setContentTitle(title).setContentText(detail)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(detail)).setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending).setTimeoutAfter(86_400_000L)
                if(person != null) NotificationArtwork.decorate(builder,person,context) else builder.setLargeIcon(NotificationArtwork.logo(context))
                val now = LocalTime.now(clockZone(p.clockZone)); val minute = now.hour*60+now.minute
                val quiet = p.quietEnabled && p.quietStart != p.quietEnd && if(p.quietStart<p.quietEnd) minute in p.quietStart until p.quietEnd else minute>=p.quietStart || minute<p.quietEnd
                builder.setSilent(quiet)
                val edit=seen.edit();val cutoff=System.currentTimeMillis()-86_400_000L
                seen.all.forEach {(key,value)->if(value is Long && value<cutoff) edit.remove(key)}
                if(!edit.putLong(event,System.currentTimeMillis()).commit()) return
                context.getSystemService(NotificationManager::class.java).notify("task_event",event.hashCode(),builder.build())
            }
        } catch(_: Exception) { /* No fresh membership confirmation: no private notification. */ }
    }
}

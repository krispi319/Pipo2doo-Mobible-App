package ai.mwm.client.reminders

import ai.mwm.client.MainActivity
import ai.mwm.client.R
import ai.mwm.client.SparkApplication
import ai.mwm.client.data.*
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.*
import java.util.concurrent.Executors

object TaskReminders {
    const val CHANNEL = "pipo_ongoing_tasks"
    const val WAKE = "ai.mwm.client.REMINDERS_WAKE"
    const val COMPLETE = "ai.mwm.client.COMPLETE_TASK"
    const val DISMISS = "ai.mwm.client.DISMISS_REMINDER"
    private const val NOTIFICATION_ID = 501
    const val LIMITS = "Reminders stay ongoing until you complete the task, unless you dismiss or disable them in Android. Android 14 and newer allow individual dismissal. You can always turn notifications off or force-stop the app. Enable Alarms & reminders in Android for precise timing. Without that access, battery saving can delay delivery; after a force-stop, reopen Pipo2doo to restore scheduling."
    fun createChannel(context: Context) {
        NotificationSounds.ensure(context,CHANNEL,"Ongoing task reminders",NotificationManager.IMPORTANCE_DEFAULT,"Task reminders use your app timezone; quiet hours silence them without changing the scheduled time.")
    }
    fun allowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled() && manager.getNotificationChannel(NotificationSounds.channelId(context,CHANNEL))?.importance != NotificationManager.IMPORTANCE_NONE
    }
    private fun pending(context: Context, action: String, task: Task? = null): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(action).setData(Uri.parse("pipo2doo://reminder/$action/${task?.id ?: "all"}"))
            .putExtra("account_scope", AccountScope.current(context)).putExtra("task_id", task?.id).putExtra("reminder_key", task?.reminderKey), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun store(context: Context) = context.getSharedPreferences("pipo_reminder_delivery_${AccountScope.current(context)}", Context.MODE_PRIVATE)
    fun dismissed(context: Context, task: Task) { store(context).edit().putString("dismissed_${task.id}", task.reminderKey).commit() }
    private fun isQuiet(at: Long, p: Preferences): Boolean {
        if (!p.quietEnabled || p.quietStart == p.quietEnd) return false
        val time = Instant.ofEpochMilli(at).atZone(clockZone(p.clockZone)).toLocalTime()
        val minute = time.hour * 60 + time.minute
        return if(p.quietStart < p.quietEnd) minute in p.quietStart until p.quietEnd else minute >= p.quietStart || minute < p.quietEnd
    }
    private fun quietAdjusted(at: Long, p: Preferences): Long {
        if(!isQuiet(at, p)) return at
        val time = Instant.ofEpochMilli(at).atZone(clockZone(p.clockZone))
        var end = time.toLocalDate().atTime(p.quietEnd / 60, p.quietEnd % 60).atZone(time.zone)
        if(end.toInstant().toEpochMilli() <= at) end = end.plusDays(1)
        return end.toInstant().toEpochMilli()
    }
    /** Inexact OS alarms: no foreground service or invasive exact-alarm permissions. */
    @Synchronized fun sync(context: Context, state: PipoState, restore: Boolean = false) {
        createChannel(context)
        ai.mwm.client.data.TaskAlarmCache.save(context, state)
        val manager = context.getSystemService(NotificationManager::class.java)
        val alarm = context.getSystemService(AlarmManager::class.java)
        val wake = pending(context, WAKE)
        alarm.cancel(wake)
        val tasks = if(state.preferences.started) state.tasks.filter { it.isOwnReminder() } else emptyList()
        val active = manager.activeNotifications.filter { it.id == NOTIFICATION_ID }
        active.filter { notification -> tasks.none { it.id == notification.tag } }.forEach { manager.cancel(it.tag, NOTIFICATION_ID) }
        val delivery = store(context)
        // Disabled reminders and completed tasks can be explicitly re-enabled/reopened later.
        val edit = delivery.edit()
        delivery.all.keys.filter { key -> tasks.none { key == "delivered_${it.id}" || key == "dismissed_${it.id}" || key == "repeat_${it.id}" } }.forEach { edit.remove(it) }
        edit.commit()
        if (!allowed(context) || !state.preferences.started) return
        val now = System.currentTimeMillis()
        var nextWake: Long? = null
        tasks.forEach { task ->
            val present = active.any { it.tag == task.id }
            val delivered = delivery.getString("delivered_${task.id}", "") == task.reminderKey
            val dismissed = delivery.getString("dismissed_${task.id}", "") == task.reminderKey
            val baseAt = task.reminderAt(state.preferences.defaultReminderTime, clockZone(state.preferences.clockZone))
            val at = baseAt
            val repeats = state.preferences.style == "Paw nudge"
            val lastAlert = delivery.getLong("repeat_${task.id}", 0L)
            val repeatDue = repeats && delivered && now - lastAlert >= 15 * 60_000L
            if(at > now) {
                if(present) manager.cancel(task.id, NOTIFICATION_ID)
                if(!dismissed) nextWake = minOf(nextWake ?: Long.MAX_VALUE, at)
            } else if(!dismissed && (!delivered || present || restore || repeatDue)) {
                run {
                    val alert = !delivered || repeatDue
                    post(context, task, state, (present || delivered) && !repeatDue)
                    delivery.edit().putString("delivered_${task.id}", task.reminderKey).apply { if(alert) putLong("repeat_${task.id}",now) }.commit()
                }
            }
            if(repeats && !dismissed && at <= now) {
                val nextRepeat = maxOf(now + 1000, if(repeatDue || !delivered) now + 15 * 60_000L else lastAlert + 15 * 60_000L)
                nextWake = minOf(nextWake ?: Long.MAX_VALUE,nextRepeat)
            }
        }
        nextWake?.let {
            val at = maxOf(it, now + 1000)
            if(Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
                try { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, wake) }
                catch(_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, wake) }
            } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, wake)
        }
    }
    private fun post(context: Context, task: Task, state: PipoState, present: Boolean) {
        val open = Intent(context, MainActivity::class.java).setAction("ai.mwm.client.OPEN_TASK").setData(Uri.parse("pipo2doo://task/${task.id}")).putExtra("account_scope", AccountScope.current(context)).putExtra("task_id", task.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val content = PendingIntent.getActivity(context, task.id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val owner = when(task.owner) { Owner.Me -> state.preferences.myName; Owner.You -> state.preferences.partnerName; Owner.Together -> "Together" }
        val description = "$owner · ${dayText(task.day)} · ${timeText(task.minute)} · ${task.repeat.label}"
        val notification = NotificationCompat.Builder(context, NotificationSounds.ensure(context,CHANNEL,"Ongoing task reminders",NotificationManager.IMPORTANCE_DEFAULT))
            .setSmallIcon(R.drawable.ic_pipo_notification).setLargeIcon(ai.mwm.client.cloud.NotificationArtwork.logo(context)).setContentTitle(task.title).setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$description\n${task.note}\nComplete this task to clear its reminder."))
            .setSubText(state.preferences.style)
            .setContentIntent(content).setDeleteIntent(pending(context, DISMISS, task))
            .apply { if(!PipoRepository.get(context).isRemote && task.owner != Owner.You) addAction(R.drawable.ic_task_reminder, "Mark complete", pending(context, COMPLETE, task)) }
            .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(present).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setSilent(present || state.preferences.style == "Sticky note" || isQuiet(System.currentTimeMillis(), state.preferences))
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(task.id, NOTIFICATION_ID, notification) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        executor.execute {
            try {
                val repository = PipoRepository.get(context)
                synchronized(repository) {
                if(intent.action in listOf(TaskReminders.COMPLETE,TaskReminders.DISMISS,TaskReminders.WAKE) && intent.getStringExtra("account_scope")!=AccountScope.current(context))return@execute
                if(!repository.isRemote) repository.refreshDaily()
                val task = repository.state.value.tasks.find { it.id == intent.getStringExtra("task_id") }
                when(intent.action) {
                    TaskReminders.COMPLETE -> if(task != null && !repository.isRemote && task.owner != Owner.You && task.isOwnReminder() && repository.state.value.preferences.started && task.reminderKey == intent.getStringExtra("reminder_key")) {
                        repository.setStatus(task.id, TaskStatus.Done)
                        runCatching { SparkApplication.spark.analytics.logEvent(type = "task_completed_from_notification") }
                    }
                    TaskReminders.DISMISS -> if(task != null && task.reminderKey == intent.getStringExtra("reminder_key")) TaskReminders.dismissed(context, task)
                    else -> TaskReminders.sync(context, TaskAlarmCache.forAlarm(context, repository.state.value, repository.isRemote), restore = intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED)
                }
                }
            } finally { result.finish() }
        }
    }
    companion object { private val executor = Executors.newSingleThreadExecutor() }
}

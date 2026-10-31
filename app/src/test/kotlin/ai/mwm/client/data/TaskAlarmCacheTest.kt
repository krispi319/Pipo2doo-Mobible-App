package ai.mwm.client.data

import ai.mwm.client.reminders.TaskReminders
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class TaskAlarmCacheTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun prepare() {
        AccountScope.activate(context,"notification-test")
        context.getSharedPreferences(AccountScope.preferencesName(context),0).edit().putString("last_shared_space","notification-space").commit()
        TaskAlarmCache.clear(context)
    }

    @Test fun coldStartCacheContainsOnlyOwnAndTogetherReminders() {
        prepare()
        val task=Task(id="own",title="Reminder",day=25000,owner=Owner.Me,reminder=true)
        TaskAlarmCache.save(context,PipoState(tasks=listOf(task,task.copy(id="partner",owner=Owner.You),task.copy(id="together",owner=Owner.Together))))
        assertEquals(setOf("own","together"),TaskAlarmCache.forAlarm(context,PipoState(),true).tasks.map {it.id}.toSet())
    }

    @Test fun reassignmentClearsExistingNotificationAndColdStartCache() {
        prepare()
        val task=Task(id="reassigned",title="Reminder",day=25000,owner=Owner.Me,reminder=true)
        TaskAlarmCache.save(context,PipoState(tasks=listOf(task)))
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.notify(task.id,501,Notification.Builder(context,"test").setContentTitle("Old reminder").build())
        assertEquals(1,manager.activeNotifications.size)
        TaskReminders.sync(context,PipoState(tasks=listOf(task.copy(owner=Owner.You)),preferences=Preferences(started=true)))
        assertTrue(manager.activeNotifications.isEmpty())
        assertTrue(TaskAlarmCache.forAlarm(context,PipoState(),true).tasks.isEmpty())
    }
}

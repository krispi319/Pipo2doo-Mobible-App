package ai.mwm.client.data

import ai.mwm.client.reminders.LoveDayReminders
import android.app.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class LoveReminderDeliveryTest {
    private val c get()=RuntimeEnvironment.getApplication()
    private val manager get()=c.getSystemService(NotificationManager::class.java)
    private fun current()=Preferences(started=true,quietEnabled=false,togetherSince=LocalDate.now().toEpochDay()-100,
        loveCelebrations=LoveCelebrationSettings(anniversaries=false,reminders=true,minute=0,advanceDays=0,haptics=false))
    @Before fun reset() {
        AccountScope.activate(c,"love-test-a")
        c.getSharedPreferences("pipo_love_${AccountScope.current(c)}",0).edit().clear().commit()
        c.getSharedPreferences(AccountScope.preferencesName(c),0).edit().putString("last_shared_space","love-space-a").commit()
        manager.cancelAll()
    }
    @Test fun onceOnlyPerMilestoneAndResumeDoesNotRepostDismissedNotification() {
        val p=current();LoveDayReminders.sync(c,p)
        assertEquals(1,manager.activeNotifications.size)
        manager.cancelAll();LoveDayReminders.resume(c)
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun disablingLoveRemindersClearsOnlyLoveNotification() {
        val p=current();LoveDayReminders.sync(c,p)
        manager.notify("task",501,Notification.Builder(c,"task").setContentTitle("Task reminder").build())
        LoveDayReminders.sync(c,p.copy(loveCelebrations=p.loveCelebrations.copy(reminders=false)))
        assertEquals(listOf(501),manager.activeNotifications.map {it.id})
    }
    @Test fun disabledTaskChannelDoesNotDisableLoveDayReminders() {
        manager.createNotificationChannel(NotificationChannel("pipo_ongoing_tasks","Task reminders",NotificationManager.IMPORTANCE_NONE))
        LoveDayReminders.sync(c,current())
        assertEquals(1,manager.activeNotifications.size)
    }
    @Test fun accountAndWorkspaceChangesDoNotReuseOldDelivery() {
        LoveDayReminders.sync(c,current());assertEquals(1,manager.activeNotifications.size)
        AccountScope.activate(c,"love-test-b");LoveDayReminders.resume(c)
        assertTrue(manager.activeNotifications.isEmpty())
        AccountScope.activate(c,"love-test-a")
        c.getSharedPreferences(AccountScope.preferencesName(c),0).edit().putString("last_shared_space","different-space").commit()
        LoveDayReminders.resume(c);assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun savedSettingsSurviveRepositoryReloadWithoutChangingIdentityOrTasks() {
        val repo=PipoRepository.get(c);repo.activateAccount("");repo.restoreLocal()
        repo.change {it.copy(tasks=listOf(Task(id="preserved",title="Keep this task",repeat=Repeat.Once)))}
        val before=repo.state.value
        val settings=LoveCelebrationSettings(customDays=listOf(520,1000),effect="Glow",note="Only our note ♡",minute=670)
        repo.updateOwnPreferences {it.copy(loveCelebrations=settings)}
        repo.restoreLocal()
        assertEquals(settings,repo.state.value.preferences.loveCelebrations)
        assertEquals(before.preferences.myName,repo.state.value.preferences.myName)
        assertEquals(before.tasks.map {it.id},repo.state.value.tasks.map {it.id})
    }
}

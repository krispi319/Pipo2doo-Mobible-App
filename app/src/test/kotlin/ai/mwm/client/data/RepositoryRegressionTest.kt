package ai.mwm.client.data

import android.app.Application
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RepositoryRegressionTest {
    private lateinit var repository: PipoRepository
    @Before fun reset() {
        repository = PipoRepository.get(RuntimeEnvironment.getApplication())
        repository.activateAccount("")
        repository.restoreLocal()
        repository.change { PipoState(preferences=it.preferences) }
    }
    @Test fun cancellationPersistsAcrossReloadAndCannotBeReopened() {
        val task=Task(title="Keep in history")
        repository.saveTask(task)
        repository.cancelTask(task.id,TaskCancelScope.Occurrence,"actor","Not needed")
        repository.restoreLocal()
        val saved=repository.state.value.tasks.single {it.id==task.id}
        assertTrue(saved.cancelled);assertFalse(saved.done);assertEquals("actor",saved.cancelledBy)
        assertEquals("Not needed",saved.cancelReason);assertEquals(0L,saved.completedAt)
        try {repository.setStatus(task.id,TaskStatus.Todo);fail("Cancelled task reopened")}
        catch(_:IllegalArgumentException) { }
        assertFalse(repository.state.value.tasks.single {it.id==task.id}.isOwnReminder())
    }
    @Test fun soloCancellationClearsItsOngoingReminderAndPreservesOtherNotifications() {
        val context=RuntimeEnvironment.getApplication()
        val manager=context.getSystemService(android.app.NotificationManager::class.java)
        manager.cancelAll()
        repository.change {it.copy(preferences=it.preferences.copy(started=true,quietEnabled=false))}
        val task=Task(title="Reminder cancellation fixture",owner=Owner.Me,day=java.time.LocalDate.now().plusDays(1).toEpochDay())
        repository.saveTask(task)
        val notification=android.app.Notification.Builder(context,ai.mwm.client.reminders.TaskReminders.CHANNEL)
            .setContentTitle("Synthetic reminder").setSmallIcon(android.R.drawable.ic_dialog_info).build()
        manager.notify(task.id,501,notification)
        manager.notify("unrelated",999,notification)
        assertTrue(manager.activeNotifications.any {it.tag==task.id})
        repository.cancelTask(task.id,TaskCancelScope.Occurrence,"","Not needed")
        assertFalse(manager.activeNotifications.any {it.tag==task.id})
        assertTrue(manager.activeNotifications.any {it.tag=="unrelated" && it.id==999})
        manager.cancelAll()
    }
    @Test fun completeCreatesExactlyOneNextOccurrence() {
        val task = Task(title = "Daily", repeat = Repeat.Weekly)
        repository.saveTask(task)
        repository.setStatus(task.id, TaskStatus.Done)
        repository.setStatus(task.id, TaskStatus.Done)
        assertEquals(1, repository.state.value.tasks.count { it.parentId == task.id })
    }
    @Test fun savingDoneRecurringTaskAlsoCreatesNextOccurrence() {
        val task = Task(title = "Already done", repeat = Repeat.Weekly, status = TaskStatus.Done)
        repository.saveTask(task)
        assertEquals(1, repository.state.value.tasks.count { it.parentId == task.id })
    }
    @Test fun undoRemovesUntouchedNextOccurrence() {
        val task = Task(title = "Daily", repeat = Repeat.Weekly)
        repository.saveTask(task)
        repository.setStatus(task.id, TaskStatus.Done)
        repository.setStatus(task.id, TaskStatus.Todo)
        assertEquals(listOf(task.id), repository.state.value.tasks.map { it.id })
    }
    @Test fun undoPreservesEditedNextOccurrence() {
        val task = Task(title = "Daily", repeat = Repeat.Weekly)
        repository.saveTask(task)
        repository.setStatus(task.id, TaskStatus.Done)
        val child = repository.state.value.tasks.single { it.parentId == task.id }
        repository.saveTask(child.copy(title = "Keep this change"))
        repository.setStatus(task.id, TaskStatus.Todo)
        assertEquals("Keep this change", repository.state.value.tasks.single { it.id == child.id }.title)
        repository.setStatus(task.id, TaskStatus.Done)
        assertEquals(1, repository.state.value.tasks.count { it.parentId == task.id })
    }
    @Test fun undoPreservesStartedNextOccurrence() {
        val task = Task(title = "Daily", repeat = Repeat.Weekly)
        repository.saveTask(task)
        repository.setStatus(task.id, TaskStatus.Done)
        val child = repository.state.value.tasks.single { it.parentId == task.id }
        repository.setStatus(child.id, TaskStatus.Doing)
        repository.setStatus(task.id, TaskStatus.Todo)
        assertEquals(TaskStatus.Doing, repository.state.value.tasks.single { it.id == child.id }.status)
    }
    @Test fun dailyDaysExistBeforeCheckingAndSurviveUndo() {
        val task=Task(title="Independent daily",repeat=Repeat.Daily)
        repository.saveTask(task)
        val before=repository.state.value.tasks.map {it.id}
        assertEquals(DAILY_LOOKAHEAD_DAYS.toInt()+1,before.size)
        repository.setStatus(task.id,TaskStatus.Done)
        repository.setStatus(task.id,TaskStatus.Todo)
        assertEquals(before,repository.state.value.tasks.map {it.id})
        assertTrue(repository.state.value.tasks.all {!it.done})
    }
    @Test fun deleteAncestorPreservesAndReparentsFutureOccurrence() {
        val ancestor = Task(title = "Older")
        val parent = Task(title = "Parent", parentId = ancestor.id)
        val child = Task(title = "Future", parentId = parent.id)
        repository.saveTask(ancestor); repository.saveTask(parent); repository.saveTask(child)
        repository.deleteTask(parent.id)
        assertFalse(repository.state.value.tasks.any { it.id == parent.id })
        assertEquals(ancestor.id, repository.state.value.tasks.single { it.id == child.id }.parentId)
    }
    @Test fun deleteRootMakesNextOccurrenceIndependent() {
        val parent = Task(title = "Root")
        val child = Task(title = "Future", parentId = parent.id)
        repository.saveTask(parent); repository.saveTask(child)
        repository.deleteTask(parent.id)
        assertEquals("", repository.state.value.tasks.single().parentId)
    }
}

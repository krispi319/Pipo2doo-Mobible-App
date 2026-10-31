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

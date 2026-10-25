package ai.mwm.client.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TaskModelRegressionTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 10, 17).toEpochDay()
    private fun at(minute: Int) = LocalDate.ofEpochDay(day).atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()

    @Test fun anytimeUsesConfiguredReminderTime() {
        assertEquals(at(750), Task(day = day).reminderAt(750, zone))
    }
    @Test fun explicitDueTimeOverridesDefault() {
        assertEquals(at(1080) - 30 * 60_000L, Task(day = day, minute = 1080, offset = 30).reminderAt(750, zone))
    }
    @Test fun anytimeOffsetMatchesPreviewAndScheduler() {
        assertEquals(at(750) - 1500 * 60_000L, Task(day = day, offset = 1500).reminderAt(750, zone))
    }
    @Test fun legacyMorningOfReminderStillUsesNine() {
        assertEquals(at(540), Task(day = day, minute = 1080, offset = -1).reminderAt(750, zone))
    }
    @Test fun invalidDefaultIsClamped() {
        assertEquals(at(1439), Task(day = day).reminderAt(9000, zone))
    }
    @Test fun bothMembersCanReceiveTaskReminders() {
        assertTrue(Task(owner = Owner.You, reminder = true).isOwnReminder())
        assertTrue(Task(owner = Owner.Me, reminder = true).isOwnReminder())
        assertFalse(Task(owner = Owner.Me, reminder = true, status = TaskStatus.Done).isOwnReminder())
    }
    @Test fun onceHasNoNextOccurrence() {
        assertNull(nextOccurrence(Task(repeat = Repeat.Once)))
    }
    @Test fun repeatingOccurrenceGetsNewIdentityAndClearsCompletion() {
        val task = Task(day = LocalDate.now().plusDays(2).toEpochDay(), repeat = Repeat.Weekly, status = TaskStatus.Done, completedAt = 1, reminder = true, tags = listOf("tag"))
        val next = requireNotNull(nextOccurrence(task))
        assertNotEquals(task.id, next.id)
        assertEquals(task.id, next.parentId)
        assertEquals(task.day + 7, next.day)
        assertEquals(TaskStatus.Todo, next.status)
        assertEquals(0L, next.completedAt)
        assertEquals(task.tags, next.tags)
        assertTrue(next.reminder)
    }
}

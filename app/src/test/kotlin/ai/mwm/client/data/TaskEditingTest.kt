package ai.mwm.client.data

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TaskEditingTest {
    @Test fun quickAddUsesSelectedClockDayAcrossMidnight() {
        val instant=Instant.parse("2026-10-30T23:30:00Z").toEpochMilli()
        val berlin=quickTaskForSave("stable","  Tea  ",Owner.You,"home","Europe/Berlin",instant)
        val utc=quickTaskForSave("stable","  Tea  ",Owner.You,"home","UTC",instant)
        assertEquals(LocalDate.parse("2026-10-31").toEpochDay(),berlin.day)
        assertEquals(LocalDate.parse("2026-10-30").toEpochDay(),utc.day)
        assertEquals("stable",berlin.id);assertEquals("Tea",berlin.title);assertEquals(Owner.You,berlin.owner)
        assertNull(berlin.minute);assertTrue(berlin.reminder);assertEquals(Repeat.Once,berlin.repeat)
    }
    @Test fun retryCanKeepTheSameTaskIdentity() {
        val at=Instant.parse("2026-10-30T12:00:00Z").toEpochMilli()
        assertEquals(quickTaskForSave("stable","Tea",Owner.Me,"home","UTC",at),quickTaskForSave("stable","Tea",Owner.Me,"home","UTC",at))
    }
    @Test fun emptyDraftCannotBecomeAnUnnamedTask() {
        assertTrue(runCatching {quickTaskForSave("id","  ",Owner.Me,"home","UTC")}.isFailure)
        assertTrue(runCatching {quickTaskForSave("id","Tea",Owner.Me,"","UTC")}.isFailure)
    }
    @Test fun repeatHintsDescribeCurrentBehavior() {
        val prefs=Preferences(clockZone="Europe/Berlin")
        assertTrue(taskScheduleHint(Task(minute=720,repeat=Repeat.Weekly),prefs).contains("Completing it creates"))
        assertTrue(taskScheduleHint(Task(minute=720,repeat=Repeat.Daily),prefs).contains("Each date has its own checkbox"))
        assertTrue(taskScheduleHint(Task(minute=720),prefs).contains("Europe/Berlin"))
        assertFalse(taskScheduleHint(Task(reminder=false),prefs).contains("Nudge at"))
    }
}

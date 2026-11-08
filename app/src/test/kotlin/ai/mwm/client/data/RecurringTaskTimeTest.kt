package ai.mwm.client.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RecurringTaskTimeTest {
    private val zone=ZoneId.of("Europe/Berlin")
    private val day=LocalDate.of(2026,11,8).toEpochDay()
    private val now=LocalDate.ofEpochDay(day).atTime(12,0).atZone(zone).toInstant().toEpochMilli()
    private val root=Task(id="root",title="Task A",day=day-2,minute=1170,repeat=Repeat.Daily,note="Private")
        .withSavedSchedule(null,zone)
    private fun state()=PipoState(tasks=listOf(root),preferences=Preferences(clockZone=zone.id)).withDailyOccurrences(now)
    private fun assertTime(task:Task,minute:Int?) {
        assertEquals(minute,task.minute)
        if(minute==null) {assertEquals(0L,task.scheduledAt);assertTrue(task.scheduleZone.isBlank() || task.scheduleZone==zone.id)}
        else {
            assertEquals(zone.id,task.scheduleZone)
            val at=Instant.ofEpochMilli(task.scheduledAt).atZone(zone)
            assertEquals(task.day,at.toLocalDate().toEpochDay())
            assertEquals(minute,at.hour*60+at.minute)
        }
    }
    @Test fun allOpenChanges1930To1929AcrossExistingAndLaterGeneratedDates() {
        val s=state();val selected=s.tasks.first {it.id!=root.id}
        val changed=s.withTaskEdit(selected.copy(minute=1169),TaskEditScope.AllUnfinished,now)
        assertEquals(s.tasks.map {it.id to it.day},changed.tasks.map {it.id to it.day})
        changed.tasks.forEach {assertTime(it,1169)}
        val later=changed.withDailyOccurrences(now+40*86400000L)
        later.tasks.forEach {assertTime(it,1169)}
        assertEquals(later.tasks.size,later.tasks.map {it.day}.distinct().size)
    }
    @Test fun alreadyCorrectedOccurrenceCanReapplyTimeToMixedOpenCopies() {
        val initial=state();val selected=initial.tasks.first {it.id!=root.id}
            .copy(minute=1169).withSavedSchedule(null,zone)
        val s=initial.copy(tasks=initial.tasks.map {if(it.id==selected.id)selected else it})
        val changed=s.withTaskEdit(selected,TaskEditScope.AllUnfinished,now)
        changed.tasks.forEach {assertTime(it,1169)}
    }
    @Test fun futureThenAllOpenReachesBothSidesWithoutChangingPrivateFields() {
        val initial=state();val s=initial.copy(tasks=initial.tasks.map {it.copy(note="note:${it.id}",reminder=it.id==root.id,offset=30)})
        val selected=s.tasks.single {it.day==day+3}
        val future=s.withTaskEdit(selected.copy(minute=1169),TaskEditScope.Future,now)
        future.tasks.forEach {assertTime(it,if(it.day<selected.day)1170 else 1169)}
        val all=future.withTaskEdit(future.tasks.single {it.id==selected.id},TaskEditScope.AllUnfinished,now)
        all.tasks.forEach {after->
            assertTime(after,1169)
            val before=s.tasks.single {it.id==after.id}
            assertEquals(before.note,after.note);assertEquals(before.reminder,after.reminder);assertEquals(before.offset,after.offset)
        }
        all.withDailyOccurrences(now+40*86400000L).tasks.forEach {assertTime(it,1169)}
    }
    @Test fun historyAndUnrelatedSameNameTaskKeepTheirOriginalTimes() {
        val initial=state()
        val s=initial.copy(tasks=initial.tasks.map {when(it.day) {
            day+1->it.copy(status=TaskStatus.Done,completedAt=now)
            day+2->it.copy(status=TaskStatus.Done,cancelledAt=now,cancelledBy="member")
            else->it
        }}+root.copy(id="unrelated",repeat=Repeat.Once))
        val selected=s.tasks.first {it.id!=root.id && !it.closed && it.id!="unrelated"}
        val changed=s.withTaskEdit(selected.copy(minute=1169),TaskEditScope.AllUnfinished,now)
        for(before in s.tasks) {
            val after=changed.tasks.single {it.id==before.id}
            if(before.closed || before.id=="unrelated") {
                assertEquals(before.minute,after.minute);assertEquals(before.scheduledAt,after.scheduledAt)
                assertEquals(before.completedAt,after.completedAt);assertEquals(before.cancelledAt,after.cancelledAt)
            } else assertTime(after,1169)
        }
    }
    @Test fun weeklyParentChainsUpdateAndNextWeekUsesNewTime() {
        val a=root.copy(repeat=Repeat.Weekly)
        val b=a.copy(id="b",parentId=a.id,day=day+7).withSavedSchedule(null,zone)
        val s=PipoState(tasks=listOf(a,b),preferences=Preferences(clockZone=zone.id))
        val changed=s.withTaskEdit(b.copy(minute=1169),TaskEditScope.AllUnfinished,now)
        changed.tasks.forEach {assertTime(it,1169)}
        assertTime(nextOccurrence(changed.tasks.last(),now)!!,1169)
    }
    @Test fun anytimeModeChangesRemainOccurrenceOnlyAndFloatingSeriesCanStillRename() {
        val s=state()
        assertTrue(runCatching {s.withTaskEdit(s.tasks.first().copy(minute=null),TaskEditScope.AllUnfinished,now)}.isFailure)
        val floating=PipoState(tasks=listOf(root.copy(minute=null).withSavedSchedule(null,zone)),preferences=s.preferences).withDailyOccurrences(now)
        assertTrue(runCatching {floating.withTaskEdit(floating.tasks.first().copy(minute=1169),TaskEditScope.AllUnfinished,now)}.isFailure)
        val renamed=floating.withTaskEdit(floating.tasks.first().copy(title="Renamed"),TaskEditScope.AllUnfinished,now)
        renamed.withDailyOccurrences(now+40*86400000L).tasks.forEach {assertTime(it,null);assertEquals("Renamed",it.title);assertEquals("",it.scheduleZone)}
    }
    @Test fun timeEditUsesChosenAppClockAndPartnerSeesSameInstant() {
        val tokyo=ZoneId.of("Asia/Tokyo")
        val old=root.copy(minute=390).withSavedSchedule(null,tokyo).inTaskZone(zone)
        val s=PipoState(tasks=listOf(old),preferences=Preferences(clockZone=zone.id)).withDailyOccurrences(now)
        val changed=s.withTaskEdit(s.tasks.first().copy(minute=1169),TaskEditScope.AllUnfinished,now)
        changed.tasks.forEach {t->assertTime(t,1169);assertEquals(t.scheduledAt,t.inTaskZone(tokyo).scheduledAt)}
    }
    @Test fun springGapDoesNotShiftTheFutureTemplateTo0330() {
        val start=LocalDate.of(2027,3,28).toEpochDay()
        val at=LocalDate.ofEpochDay(start).atTime(0,0).atZone(zone).toInstant().toEpochMilli()
        val task=root.copy(day=start,minute=1170).withSavedSchedule(null,zone)
        val s=PipoState(tasks=listOf(task),preferences=Preferences(clockZone=zone.id)).withDailyOccurrences(at)
        val changed=s.withTaskEdit(task.copy(minute=150),TaskEditScope.AllUnfinished,at)
        assertEquals(210,changed.tasks.single {it.day==start}.minute)
        changed.tasks.filter {it.day>start}.forEach {assertTime(it,150)}
        changed.withDailyOccurrences(at+40*86400000L).tasks.filter {it.day>start}.forEach {assertTime(it,150)}
    }
}

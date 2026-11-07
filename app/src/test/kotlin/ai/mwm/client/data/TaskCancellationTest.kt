package ai.mwm.client.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TaskCancellationTest {
    private val day=LocalDate.of(2026,11,7).toEpochDay()
    private val now=LocalDate.ofEpochDay(day).atTime(12,0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val root=Task(id="root",title="Fix Pipo2doo",repeat=Repeat.Daily,day=day,minute=600)
    private val state=PipoState(tasks=listOf(root),preferences=Preferences(myName="Alex",clockZone="UTC")).withDailyOccurrences(now)
    @Test fun cancellingDailyRootKeepsNextDatesAndHistoryWithoutCredit() {
        val next=state.withTaskCancellation(root.id,TaskCancelScope.Occurrence,"alex","Not today",now)
        val cancelled=next.tasks.single {it.id==root.id}
        assertTrue(cancelled.cancelled);assertTrue(cancelled.closed);assertFalse(cancelled.done)
        assertEquals(0L,cancelled.completedAt);assertEquals("Not today",cancelled.cancelReason)
        assertFalse(cancelled.isOwnReminder());assertFalse(missedDeadline(cancelled,now))
        assertEquals(state.tasks.size,next.tasks.size)
        assertEquals(1,next.tasks.count {it.repeat==Repeat.Daily && it.seriesId.isBlank()})
        assertEquals(next.tasks.size,next.recurringFamily(root.id).size)
        assertTrue(next.withDailyOccurrences(now+30*86400000L).tasks.any {!it.closed && it.day>day+30})
        assertEquals(1,next.withDailyOccurrences(now).tasks.count {it.id==root.id})
    }
    @Test fun cancellingOneChildDoesNotCancelItsSiblingsOrRegenerateIt() {
        val child=state.tasks.first {it.day==day+2}
        val next=state.withTaskCancellation(child.id,TaskCancelScope.Occurrence,"alex","",now)
        assertTrue(next.tasks.single {it.id==child.id}.cancelled)
        assertEquals(1,next.tasks.count {it.cancelled})
        assertEquals(next,next.withDailyOccurrences(now))
        assertTrue(next.tasks.single {it.id==root.id}.repeat==Repeat.Daily)
    }
    @Test fun stopSeriesKeepsDoneAndOlderOpenDatesButNeverGeneratesAgain() {
        val selected=state.tasks.first {it.day==day+2}
        val completed=state.tasks.first {it.day==day+3}.copy(status=TaskStatus.Done,completedAt=now)
        val before=state.copy(tasks=state.tasks.map {if(it.id==completed.id)completed else it})
        val next=before.withTaskCancellation(selected.id,TaskCancelScope.Future,"alex","Stopped",now)
        assertEquals(completed.title,next.tasks.single {it.id==completed.id}.title)
        assertTrue(next.tasks.single {it.id==completed.id}.done)
        assertTrue(next.tasks.filter {it.day<selected.day}.none {it.closed})
        assertTrue(next.tasks.filter {it.day>=selected.day && it.id!=completed.id}.all {it.cancelled})
        assertEquals(before.tasks.size,next.tasks.size)
        assertEquals(next,next.withDailyOccurrences(now+60*86400000L))
    }
    @Test fun stoppingFromRootKeepsAllRecordsAndNoGenerators() {
        val next=state.withTaskCancellation(root.id,TaskCancelScope.Future,"alex","",now)
        assertTrue(next.tasks.all {it.cancelled && it.repeat==Repeat.Once})
        assertEquals(state.tasks.size,next.tasks.size)
        assertEquals(next,next.withDailyOccurrences(now+60*86400000L))
    }
    @Test fun weeklyCancellationCreatesExactlyOneLinkedSuccessor() {
        val weekly=root.copy(repeat=Repeat.Weekly)
        val next=PipoState(tasks=listOf(weekly)).withTaskCancellation(weekly.id,TaskCancelScope.Occurrence,"alex","",now)
        assertEquals(2,next.tasks.size)
        assertEquals(1,next.tasks.count {it.cancelled})
        assertEquals(2,next.recurringFamily(root.id).size)
        assertFalse(next.tasks.single {!it.cancelled}.closed)
    }
    @Test fun existingWeeklySuccessorIsNotDuplicated() {
        val weekly=root.copy(repeat=Repeat.Weekly)
        val child=weekly.copy(id="next",day=day+7,parentId=weekly.id)
        val next=PipoState(tasks=listOf(weekly,child)).withTaskCancellation(weekly.id,TaskCancelScope.Occurrence,"alex","",now)
        assertEquals(2,next.tasks.size);assertEquals(weekly.id,next.tasks.single {it.id==child.id}.parentId)
    }
    @Test fun cancellationDoesNotMatchUnrelatedTasksByTitle() {
        val extra=root.copy(id="unrelated")
        val next=state.copy(tasks=state.tasks+extra).withTaskCancellation(root.id,TaskCancelScope.Future,"alex","",now)
        assertEquals(extra,next.tasks.single {it.id==extra.id})
    }
    @Test fun cancelledTasksStayOutOfUpcomingAndPendingCompletionCannotReviveThem() {
        val next=state.withTaskCancellation(root.id,TaskCancelScope.Occurrence,"alex","",now)
        assertTrue(upcomingSections(next.tasks,day).flatMap {it.tasks}.none {it.id==root.id})
        val pending=PendingTaskBatch("test",listOf(PendingTaskStatus(taskId=root.id,status=TaskStatus.Todo,at=now)))
        assertTrue(withPendingTaskStatuses(next,pending,"test").tasks.single {it.id==root.id}.cancelled)
        assertEquals(TaskStatus.Done,withPendingTaskStatuses(next,pending,"test").tasks.single {it.id==root.id}.status)
    }
    @Test(expected=IllegalArgumentException::class) fun cannotCancelCompletedTask() {
        PipoState(tasks=listOf(root.copy(status=TaskStatus.Done))).withTaskCancellation(root.id,TaskCancelScope.Occurrence,"alex","",now)
    }
    @Test(expected=IllegalArgumentException::class) fun seriesEditsCannotChangeCancelledHistory() {
        val next=state.withTaskCancellation(root.id,TaskCancelScope.Occurrence,"alex","",now)
        next.withTaskEdit(next.tasks.single {it.id==root.id}.copy(title="Changed"),TaskEditScope.AllUnfinished,now)
    }
}

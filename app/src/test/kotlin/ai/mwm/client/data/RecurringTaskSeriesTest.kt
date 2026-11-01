package ai.mwm.client.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RecurringTaskSeriesTest {
    private val day=LocalDate.of(2026,10,31).toEpochDay()
    private val now=LocalDate.ofEpochDay(day).atTime(12,0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val root=Task(id="root",title="Fix pipo2doo",day=day-2,repeat=Repeat.Daily,note="Private")
    private fun daily()=PipoState(tasks=listOf(root),preferences=Preferences(clockZone="UTC")).withDailyOccurrences(now)

    @Test fun allUnfinishedIncludesOverdueAndPlannedButNotIdenticallyNamedUnrelatedTasks() {
        val other=root.copy(id="unrelated")
        val initial=daily();val state=initial.copy(tasks=initial.tasks+other)
        val selected=state.tasks.first {it.seriesId==root.id && it.day==day+3}
        val edited=state.withTaskEdit(selected.copy(title="Improve Pipo2doo"),TaskEditScope.AllUnfinished,now)
        assertTrue(edited.recurringFamily(root.id).all {it.title=="Improve Pipo2doo"})
        assertEquals(other,edited.tasks.single {it.id==other.id})
        assertEquals("Improve Pipo2doo",edited.withDailyOccurrences(now+30*86400000L).tasks.single {it.day==day+40 && it.seriesId==root.id}.title)
    }
    @Test fun alreadyCorrectedOccurrenceCanSyncItsTitleWithoutTypingItAgain() {
        val initial=daily();val selected=initial.tasks.single {it.day==day+3}.copy(title="Corrected",owner=Owner.You)
        val state=initial.copy(tasks=initial.tasks.map {if(it.id==selected.id)selected else it})
        val edited=state.withTaskEdit(selected,TaskEditScope.AllUnfinished,now)
        assertTrue(edited.tasks.all {it.title=="Corrected"})
        assertEquals(Owner.You,edited.tasks.single {it.id==selected.id}.owner)
        assertTrue(edited.tasks.filterNot {it.id==selected.id}.all {it.owner==Owner.Together})
    }
    @Test fun completedGeneratorKeepsItsContentWhileNewOccurrencesUseCorrectedTitle() {
        val state=daily().let {it.copy(tasks=it.tasks.map {t->if(t.id==root.id)t.copy(status=TaskStatus.Done,completedAt=now) else t})}
        val selected=state.tasks.first {!it.done}
        val edited=state.withTaskEdit(selected.copy(title="Corrected"),TaskEditScope.AllUnfinished,now)
        val history=edited.tasks.single {it.id==root.id}
        assertEquals(root.title,history.title);assertTrue(history.done);assertEquals(now,history.completedAt)
        assertEquals("Private",history.note)
        assertEquals("Corrected",edited.withDailyOccurrences(now+30*86400000L).tasks.single {it.day==day+40}.title)
    }
    @Test fun stoppedOccurrenceStillOffersSeriesEditing() {
        val state=daily().let {it.copy(tasks=it.tasks.map {t->if(t.day==day+1)t.copy(repeat=Repeat.Once) else t})}
        val selected=state.tasks.single {it.day==day+1}
        assertTrue(state.hasRecurrence(selected.id))
        assertTrue(state.withTaskEdit(selected.copy(title="Corrected"),TaskEditScope.AllUnfinished,now).tasks.all {it.title=="Corrected"})
        val future=state.withTaskEdit(selected.copy(title="Future correction"),TaskEditScope.Future,now)
        assertEquals(Repeat.Once,future.tasks.single {it.id==selected.id}.repeat)
    }
    @Test fun futureSplitRetainsLinksSoLaterAllUnfinishedReachesBothSides() {
        val state=daily();val selected=state.tasks.single {it.day==day+3}
        val split=state.withTaskEdit(selected.copy(title="Future only"),TaskEditScope.Future,now)
        assertEquals(state.tasks.size,split.recurringFamily(selected.id).size)
        val all=split.withTaskEdit(split.tasks.single {it.id==selected.id}.copy(title="Whole series"),TaskEditScope.AllUnfinished,now)
        assertTrue(all.tasks.all {it.title=="Whole series"})
    }
    @Test fun legacyWeeklyParentChainsIncludeEarlierUnfinishedCopies() {
        val a=root.copy(repeat=Repeat.Weekly)
        val b=a.copy(id="b",parentId=a.id,day=day+7)
        val c=b.copy(id="c",parentId=b.id,day=day+14)
        assertTrue(PipoState(tasks=listOf(a,b,c)).withTaskEdit(c.copy(title="Corrected"),TaskEditScope.AllUnfinished,now).tasks.all {it.title=="Corrected"})
    }
    @Test fun wholeSeriesDeletionKeepsCompletedHistoryAndNeverGeneratesAgain() {
        val state=daily().let {it.copy(tasks=it.tasks.map {t->if(t.day==day+2 || t.id==root.id)t.copy(status=TaskStatus.Done,completedAt=now) else t})}
        val deleted=state.withTaskDeletion(state.tasks.first {!it.done}.id,TaskDeleteScope.Series,now)
        assertEquals(2,deleted.tasks.size);assertTrue(deleted.tasks.all {it.done})
        assertEquals(state.tasks.filter {it.done}.map {it.id to it.title},deleted.tasks.map {it.id to it.title})
        assertEquals(deleted.tasks,deleted.withDailyOccurrences(now+50*86400000L).tasks)
    }
    @Test fun deletingPendingRootReparentsCompletedChildrenBeforeRemovingIt() {
        val state=daily().let {it.copy(tasks=it.tasks.map {t->if(t.day==day+2)t.copy(status=TaskStatus.Done,completedAt=now) else t})}
        val deleted=state.withTaskDeletion(root.id,TaskDeleteScope.Series,now)
        assertEquals(1,deleted.tasks.size)
        assertTrue(deleted.tasks.single().parentId.isBlank());assertTrue(deleted.tasks.single().seriesId.isBlank())
        assertEquals(deleted,deleted.withDailyOccurrences(now+50*86400000L))
    }
    @Test fun deletingOnlyTemplatePromotesNextOccurrenceAndContinuesSeries() {
        val initial=daily();val once=initial.tasks.first {it.id!=root.id}.copy(repeat=Repeat.Once)
        val state=initial.copy(tasks=initial.tasks.map {if(it.id==once.id)once else it})
        val deleted=state.withTaskDeletion(root.id,TaskDeleteScope.Occurrence,now)
        assertEquals(state.tasks.size-1,deleted.tasks.size)
        assertEquals(1,deleted.tasks.count {it.repeat==Repeat.Daily && it.seriesId.isBlank()})
        assertTrue(deleted.withDailyOccurrences(now+40*86400000L).tasks.any {it.day==day+50})
        assertFalse(deleted.tasks.any {it.id==root.id})
        assertEquals(Repeat.Once,deleted.tasks.single {it.id==once.id}.repeat)
    }
    @Test fun deletingFutureKeepsEarlierUnfinishedAndAllCompletedButStopsRepeats() {
        val state=daily().let {it.copy(tasks=it.tasks.map {t->if(t.day==day+7)t.copy(status=TaskStatus.Done,completedAt=now) else t})}
        val deleted=state.withTaskDeletion(state.tasks.single {it.day==day+2}.id,TaskDeleteScope.Future,now)
        assertTrue(deleted.tasks.all {it.day<day+2 || it.done})
        assertEquals(deleted.tasks,deleted.withDailyOccurrences(now+40*86400000L).tasks)
    }
    @Test fun deleteNeverMatchesUnrelatedTaskByTitle() {
        val other=root.copy(id="unrelated")
        val state=daily().let {it.copy(tasks=it.tasks+other)}
        assertEquals(listOf(other),state.withTaskDeletion(root.id,TaskDeleteScope.Series,now).tasks)
    }
    @Test fun allCompletedSeriesCanStillBeStoppedWithoutDeletingHistory() {
        val state=daily().let {it.copy(tasks=it.tasks.map {t->t.copy(status=TaskStatus.Done,completedAt=now)})}
        val stopped=state.withTaskDeletion(root.id,TaskDeleteScope.Series,now)
        assertEquals(state.tasks.size,stopped.tasks.size)
        assertTrue(stopped.tasks.all {it.done})
        assertEquals(stopped,stopped.withDailyOccurrences(now+50*86400000L))
    }
    @Test fun renamingStoppedHistoryDoesNotRestartDailyGeneration() {
        val state=daily().let {it.copy(tasks=it.tasks.map {t->t.copy(status=TaskStatus.Done,completedAt=now)})}
        val stopped=state.withTaskDeletion(root.id,TaskDeleteScope.Series,now)
        val selected=stopped.tasks.first()
        val edited=stopped.withTaskEdit(selected.copy(title="History correction"),TaskEditScope.AllUnfinished,now)
        assertEquals(stopped,edited)
        assertEquals(edited,edited.withDailyOccurrences(now+50*86400000L))
    }
    @Test fun aCustomizedOccurrenceTimeDoesNotBecomeTheFutureTemplateTime() {
        val state=PipoState(tasks=listOf(root.copy(minute=390)),preferences=Preferences(clockZone="UTC")).withDailyOccurrences(now)
        val custom=state.tasks.single {it.day==day+3}.copy(minute=600)
        val changed=state.copy(tasks=state.tasks.map {if(it.id==custom.id)custom else it})
            .withTaskEdit(custom.copy(title="Corrected"),TaskEditScope.Future,now)
        assertEquals(600,changed.tasks.single {it.id==custom.id}.minute)
        assertEquals(390,changed.withDailyOccurrences(now+30*86400000L).tasks.single {it.day==day+40}.minute)
    }
    @Test fun aNewTemplateKeepsOriginalTimezoneAndWallClockAcrossDst() {
        val tokyo=ZoneId.of("Asia/Tokyo");val berlin=ZoneId.of("Europe/Berlin")
        val anchor=LocalDate.of(2026,10,24).atTime(6,30).atZone(tokyo)
        val history=root.copy(day=anchor.toLocalDate().toEpochDay(),minute=390,scheduledAt=anchor.toInstant().toEpochMilli(),scheduleZone=tokyo.id,
            status=TaskStatus.Done,completedAt=now).inTaskZone(berlin)
        val changed=PipoState(tasks=listOf(history),preferences=Preferences(clockZone=berlin.id))
            .withTaskEdit(history.copy(title="Corrected"),TaskEditScope.AllUnfinished,now)
        val next=changed.tasks.single {!it.done}
        assertEquals(tokyo.id,next.scheduleZone)
        assertEquals(LocalTime.of(6,30),Instant.ofEpochMilli(next.scheduledAt).atZone(tokyo).toLocalTime())
        assertEquals(history.title,changed.tasks.single {it.done}.title)
    }
    @Test fun renamingDoesNotAdoptACustomOccurrenceAssigneeAsTheFutureDefault() {
        val base=PipoState(tasks=listOf(root.copy(owner=Owner.Me)),preferences=Preferences(clockZone="UTC")).withDailyOccurrences(now)
        val selected=base.tasks.first {it.id!=root.id}.copy(owner=Owner.You)
        val state=base.copy(tasks=base.tasks.map {when(it.id) {root.id->it.copy(status=TaskStatus.Done,completedAt=now);selected.id->selected;else->it}})
        val edited=state.withTaskEdit(selected.copy(title="Corrected"),TaskEditScope.AllUnfinished,now)
        assertEquals(Owner.You,edited.tasks.single {it.id==selected.id}.owner)
        assertEquals(Owner.Me,edited.withDailyOccurrences(now+30*86400000L).tasks.single {it.day==day+40}.owner)
    }
}

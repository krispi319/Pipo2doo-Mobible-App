package ai.mwm.client.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RecurringTaskEditingTest {
    private val day=LocalDate.of(2026,10,31).toEpochDay()
    private val now=LocalDate.ofEpochDay(day).atTime(12,0).atZone(ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()
    private val root=Task(id="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",title="Watre plants",day=day,repeat=Repeat.Daily,note="Private")
    private fun daily()=PipoState(tasks=listOf(root),preferences=Preferences(clockZone="Europe/Berlin")).withDailyOccurrences(now)

    @Test fun singleChildCorrectionLeavesOtherDatesAndTemplateAlone() {
        val s=daily();val child=s.tasks.single {it.day==day+1}
        val edited=s.withTaskEdit(child.copy(title="Water plants"),TaskEditScope.Occurrence,now)
        assertEquals(root,edited.tasks.first())
        assertEquals("Water plants",edited.tasks.single {it.id==child.id}.title)
        assertEquals("Watre plants",edited.withDailyOccurrences(now+30*86400000L).tasks.single {it.day==day+40}.title)
    }
    @Test fun singleTemplateCorrectionDoesNotChangeFutureGeneratedTitles() {
        val s=daily();val edited=s.withTaskEdit(root.copy(title="Water plants"),TaskEditScope.Occurrence,now)
        assertEquals("Water plants",edited.tasks.single {it.id==root.id}.title)
        assertEquals(1,edited.tasks.count {it.repeat==Repeat.Daily && it.seriesId.isBlank()})
        assertEquals("Watre plants",edited.withDailyOccurrences(now+30*86400000L).tasks.single {it.day==day+40}.title)
        assertEquals(s.tasks.map {it.id}.toSet(),edited.tasks.map {it.id}.toSet())
    }
    @Test fun futureCorrectionUpdatesExistingAndNewDatesWithoutDuplicates() {
        val s=daily();val child=s.tasks.single {it.day==day+3}
        val edited=s.withTaskEdit(child.copy(title="Water plants"),TaskEditScope.Future,now)
        assertTrue(edited.tasks.filter {it.day<child.day}.all {it.title==root.title})
        assertTrue(edited.tasks.filter {it.day>=child.day}.all {it.title=="Water plants"})
        val expanded=edited.withDailyOccurrences(now+30*86400000L)
        assertEquals("Water plants",expanded.tasks.single {it.day==day+40}.title)
        assertEquals(expanded.tasks.size,expanded.tasks.map {it.id}.distinct().size)
        assertEquals(expanded.tasks.size,expanded.tasks.map {it.day}.distinct().size)
    }
    @Test fun futureCorrectionPreservesCompletedHistoryAndPrivateDetails() {
        val initial=daily();val s=initial.copy(tasks=initial.tasks.map {if(it.day==day+5)it.copy(status=TaskStatus.Done,completedAt=now) else it.copy(note="note:${it.day}",offset=15)})
        val selected=s.tasks.single {it.day==day+2};val done=s.tasks.single {it.done}
        val edited=s.withTaskEdit(selected.copy(title="Water plants",owner=Owner.You,listId="garden",tags=listOf("green")),TaskEditScope.Future,now)
        assertEquals(done.copy(seriesId=selected.id,parentId=selected.id),edited.tasks.single {it.id==done.id})
        assertEquals(1,edited.withDailyOccurrences(now).tasks.count {it.day==done.day})
        for(before in s.tasks.filter {it.day>=selected.day && !it.done}) {
            val after=edited.tasks.single {it.id==before.id}
            assertEquals("Water plants",after.title);assertEquals(Owner.You,after.owner)
            assertEquals("garden",after.listId);assertEquals(listOf("green"),after.tags)
            assertEquals(before.day,after.day);assertEquals(before.note,after.note);assertEquals(before.offset,after.offset)
        }
    }
    @Test fun templateFutureCorrectionKeepsDatesAndIds() {
        val s=daily();val edited=s.withTaskEdit(root.copy(title="Water plants"),TaskEditScope.Future,now)
        assertTrue(edited.tasks.all {it.title=="Water plants"})
        assertEquals(s.tasks.map {it.id to it.day},edited.tasks.map {it.id to it.day})
    }
    @Test fun futureScopeRejectsUnimplementedScheduleAndPrivateChanges() {
        for(edit in listOf(root.copy(day=day+1),root.copy(minute=600),root.copy(repeat=Repeat.Once),root.copy(note="Changed"),root.copy(reminder=false))) {
            assertTrue(runCatching {daily().withTaskEdit(edit,TaskEditScope.Future,now)}.isFailure)
        }
    }
    @Test fun singleDateCanStillBeMovedAndFutureDaysKeepOriginalSchedule() {
        val s=daily();val edited=s.withTaskEdit(root.copy(day=day+4),TaskEditScope.Occurrence,now)
        assertEquals(day+4,edited.tasks.single {it.id==root.id}.day)
        assertEquals(day+1,edited.tasks.single {it.id==dailyOccurrenceId(root.id,day+1)}.day)
    }
    @Test fun untouchedFutureCustomTitlesAreNotResetByAnAssigneeChange() {
        val s=daily().let {it.copy(tasks=it.tasks.map {t->if(t.day==day+5)t.copy(title="Custom") else t})}
        val edited=s.withTaskEdit(root.copy(owner=Owner.You),TaskEditScope.Future,now)
        assertEquals("Custom",edited.tasks.single {it.day==day+5}.title)
        assertTrue(edited.tasks.all {it.owner==Owner.You})
    }
    @Test fun weeklyFutureCorrectionReachesDescendantsButNotCompletedDates() {
        val a=root.copy(repeat=Repeat.Weekly,status=TaskStatus.Done,completedAt=now)
        val b=a.copy(id="b",parentId=a.id,day=day+7,status=TaskStatus.Todo,completedAt=0)
        val c=b.copy(id="c",parentId=b.id,day=day+14)
        val s=PipoState(tasks=listOf(a,b,c))
        val edited=s.withTaskEdit(b.copy(title="Water plants"),TaskEditScope.Future,now)
        assertEquals(a,edited.tasks.first());assertEquals("Water plants",edited.tasks.last().title)
        assertEquals("Water plants",nextOccurrence(edited.tasks.last(),now)!!.title)
    }
    @Test fun weeklySingleCorrectionRetainsOriginalDetailsForNextTask() {
        val a=root.copy(repeat=Repeat.Weekly)
        val s=PipoState(tasks=listOf(a))
        val edited=s.withTaskEdit(a.copy(title="Water plants"),TaskEditScope.Occurrence,now)
        assertEquals("Water plants",edited.tasks.first().title)
        assertEquals(root.title,edited.tasks.single {it.parentId==a.id}.title)
    }
    @Test fun singleRootWithoutMaterializedFutureStillPreservesTemplate() {
        val s=PipoState(tasks=listOf(root),preferences=Preferences(clockZone="Europe/Berlin"))
        val edited=s.withTaskEdit(root.copy(title="Water plants"),TaskEditScope.Occurrence,now).withDailyOccurrences(now)
        assertEquals("Watre plants",edited.tasks.single {it.day==day+2}.title)
    }
}

package ai.mwm.client.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DailyOccurrencesTest {
 private val zone=ZoneId.of("Europe/Berlin")
 private val today=LocalDate.of(2026,10,24).toEpochDay()
 private val now=LocalDate.ofEpochDay(today).atTime(12,0).atZone(zone).toInstant().toEpochMilli()
 private fun root(day:Long=today)=Task(id="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",title="Daily care",day=day,minute=1080,repeat=Repeat.Daily,reminder=true)
 private fun state(t:Task=root())=PipoState(tasks=listOf(t),preferences=Preferences(clockZone=zone.id))
 @Test fun missedYesterdayDoesNotBlockTodayOrTomorrow(){val s=state(root(today-1)).withDailyOccurrences(now);assertTrue(s.tasks.any {it.day==today});assertTrue(s.tasks.any {it.day==today+1});assertFalse(s.tasks.first().done)}
 @Test fun createsOnlyRollingWindow(){val s=state().withDailyOccurrences(now);assertEquals(22,s.tasks.size);assertEquals(today+21,s.tasks.maxOf {it.day})}
 @Test fun idempotentAndStableIds(){val s=state().withDailyOccurrences(now);assertEquals(s,s.withDailyOccurrences(now));assertEquals(dailyOccurrenceId(root().id,today+1),s.tasks.single {it.day==today+1}.id)}
 @Test fun completedRootStillRepeats(){val s=state(root().copy(status=TaskStatus.Done,completedAt=now)).withDailyOccurrences(now);assertTrue(s.tasks.any {it.day==today+1 && !it.done})}
 @Test fun completedDayDoesNotChangeOtherDates(){val s=state().withDailyOccurrences(now);val next=s.tasks.single {it.day==today+1};val done=s.copy(tasks=s.tasks.map {if(it.id==next.id)it.copy(status=TaskStatus.Done,completedAt=now) else it}).withDailyOccurrences(now);assertTrue(done.tasks.single {it.id==next.id}.done);assertFalse(done.tasks.single {it.day==today+2}.done);assertEquals(s.tasks.size,done.tasks.size)}
 @Test fun existingHistoryAndOldChainArePreserved(){val a=root(today-2).copy(status=TaskStatus.Done);val b=a.copy(id="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",day=today-1,parentId=a.id);val c=b.copy(id="cccccccc-cccc-cccc-cccc-cccccccccccc",day=today,parentId=b.id,status=TaskStatus.Todo);val s=state(a).copy(tasks=listOf(a,b,c)).withDailyOccurrences(now);assertEquals(a,s.tasks.first());assertEquals(a.id,s.tasks.single {it.id==c.id}.seriesId);assertEquals(1,s.tasks.count {it.day==today});assertTrue(s.tasks.single {it.id==b.id}.done)}
 @Test fun generatedChildrenDoNotBecomeNewTemplates(){val s=state().withDailyOccurrences(now);assertEquals(1,s.tasks.count {it.seriesId.isBlank()});assertTrue(s.tasks.drop(1).all {it.parentId==root().id});assertEquals(22,s.withDailyOccurrences(now).tasks.size)}
 @Test fun weekliesAndOneOffsUnchanged(){for(repeat in listOf(Repeat.Once,Repeat.Weekly)){val s=state(root().copy(repeat=repeat));assertEquals(s,s.withDailyOccurrences(now))}}
 @Test fun skippedDateDoesNotReappear(){val s=state().withDailyOccurrences(now);val removed=s.tasks.single {it.day==today+1};val skipped=s.copy(tasks=s.tasks.filterNot {it.id==removed.id},skippedDaily=setOf(removed.id));assertEquals(skipped,skipped.withDailyOccurrences(now))}
 @Test fun deletedRootStopsExpansion(){val s=state().withDailyOccurrences(now);val removed=s.copy(tasks=s.tasks.drop(1).map {it.copy(parentId="")});assertEquals(removed,removed.withDailyOccurrences(now+30*86400000L))}
 @Test fun noInventedHistoricalMisses(){val s=state(root(today-100)).withDailyOccurrences(now);assertFalse(s.tasks.any {it.day in today-99 until today});assertTrue(s.tasks.any {it.day==today})}
 @Test fun futureStartNotBroughtForward(){val s=state(root(today+5)).withDailyOccurrences(now);assertTrue(s.tasks.all {it.day>=today+5})}
 @Test fun timezoneAndAutumnOverlapKeepSharedInstant(){val r=root().copy(minute=150).withSavedSchedule(null,zone);val s=state(r).withDailyOccurrences(now);val next=s.tasks.single {it.seriesId==r.id && it.day==today+1};assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(),next.scheduledAt);assertEquals(150,next.minute);val india=next.inTaskZone(ZoneId.of("Asia/Kolkata"));assertEquals(next.scheduledAt,india.scheduledAt);assertEquals(360,india.minute)}
 @Test fun springGapMovesForward(){val day=LocalDate.of(2026,3,28).toEpochDay();val at=LocalDate.ofEpochDay(day).atTime(12,0).atZone(zone).toInstant().toEpochMilli();val r=root(day).copy(minute=150).withSavedSchedule(null,zone);val s=state(r).withDailyOccurrences(at);assertEquals(210,s.tasks.single {it.day==day+1}.minute)}
 @Test fun changedAndMovedOccurrenceIsNotOverwritten(){val s=state().withDailyOccurrences(now);val next=s.tasks.single {it.day==today+1};val edited=s.copy(tasks=s.tasks.map {if(it.id==next.id)it.copy(day=today+3,title="Changed") else it});assertEquals(edited,edited.withDailyOccurrences(now))}
 @Test fun originalRemindersAndPrivateNotesCarriedLocally(){val r=root().copy(reminder=false,offset=15,note="Private");val s=state(r).withDailyOccurrences(now);assertTrue(s.tasks.all {!it.reminder && it.offset==15 && it.note=="Private"})}
 @Test fun stopsWhenRepeatChangedToOnce(){val s=state(root(today-2)).withDailyOccurrences(now);val stopped=s.copy(tasks=s.tasks.map {if(it.id==root().id)it.copy(repeat=Repeat.Once) else it});assertEquals(stopped,stopped.withDailyOccurrences(now+30*86400000L))}
}

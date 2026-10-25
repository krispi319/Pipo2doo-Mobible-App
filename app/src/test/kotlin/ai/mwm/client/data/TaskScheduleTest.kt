package ai.mwm.client.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TaskScheduleTest {
 private val berlin=ZoneId.of("Europe/Berlin")
 private val india=ZoneId.of("Asia/Kolkata")
 private fun task(date:String="2026-10-23",minute:Int=1080)=Task(day=LocalDate.parse(date).toEpochDay(),minute=minute).withSavedSchedule(null,berlin)
 @Test fun sameInstantDifferentLocalTime() {
  val mine=task();val his=mine.inTaskZone(india)
  assertEquals(1080,mine.minute);assertEquals(1290,his.minute)
  assertEquals(mine.dueAt(),his.dueAt());assertEquals(mine.reminderAt(),his.reminderAt())
 }
 @Test fun crossesMidnight() {
  val mine=task(minute=1380);val his=mine.inTaskZone(india)
  assertEquals(mine.day+1,his.day);assertEquals(150,his.minute)
 }
 @Test fun viewingDoesNotChangeWirePayload() {
  val mine=task();val his=mine.inTaskZone(india)
  assertEquals(mine.canonicalSchedule(),his.canonicalSchedule())
 }
 @Test fun editingTitleDoesNotMoveTime() {
  val mine=task();val his=mine.inTaskZone(india)
  val changed=his.copy(title="New title").withSavedSchedule(his,india)
  assertEquals(mine.scheduledAt,changed.scheduledAt);assertEquals("Europe/Berlin",changed.scheduleZone)
 }
 @Test fun partnerCanEditTimeInOwnZone() {
  val his=task().inTaskZone(india)
  val changed=his.copy(minute=1350).withSavedSchedule(his,india)
  assertEquals(his.scheduledAt+3600000,changed.scheduledAt);assertEquals("Asia/Kolkata",changed.scheduleZone)
 }
 @Test fun legacyTaskIsNotSilentlyMigrated() {
  val old=Task(day=20000,minute=720)
  assertEquals(0,old.copy(title="Rename").withSavedSchedule(old,berlin).scheduledAt)
  assertTrue(old.copy(minute=721).withSavedSchedule(old,berlin).scheduledAt>0)
 }
 @Test fun explicitlySelectingLegacyTimeAnchorsIt() {
  val old=Task(day=LocalDate.parse("2026-10-23").toEpochDay(),minute=1080)
  val chosen=old.withSavedSchedule(null,berlin)
  assertTrue(chosen.withSavedSchedule(old,berlin).scheduledAt>0)
 }
 @Test fun anytimeIsDateOnly() {
  val mine=task().copy(minute=null).withSavedSchedule(task(),berlin)
  assertEquals(0,mine.scheduledAt);assertEquals("",mine.scheduleZone);assertEquals(mine.day,mine.inTaskZone(india).day)
 }
 @Test fun offsetsMatchAcrossZones() {
  val mine=task().copy(offset=30);val his=mine.inTaskZone(india)
  assertEquals(mine.dueAt()-1800000,mine.reminderAt());assertEquals(mine.reminderAt(),his.reminderAt())
 }
 @Test fun reminderKeyIgnoresDisplayZone() {val mine=task();assertEquals(mine.reminderKey,mine.inTaskZone(india).reminderKey)}
 @Test fun remindersIncludeBothAssignees() {for(owner in Owner.entries) assertTrue(task().copy(owner=owner).isOwnReminder());assertFalse(task().copy(status=TaskStatus.Done).isOwnReminder());assertFalse(task().copy(reminder=false).isOwnReminder())}
 @Test fun recurrencePreservesAnchorAcrossDaylightSaving() {
  val mine=task("2026-10-24",1080).copy(repeat=Repeat.Daily)
  val next=nextOccurrence(mine,Instant.parse("2026-10-24T17:00:00Z").toEpochMilli())!!
  assertEquals(1080,next.minute);assertEquals(25*3600000L,next.scheduledAt-mine.scheduledAt)
  assertEquals(1350,next.inTaskZone(india).minute)
 }
 @Test fun weeklySkipsOverdueDates() {
  val old=task("2026-09-01",1080).copy(repeat=Repeat.Weekly)
  val now=Instant.parse("2026-10-23T20:00:00Z").toEpochMilli()
  val next=nextOccurrence(old,now)!!
  assertTrue(next.scheduledAt>now);assertEquals(DayOfWeek.TUESDAY,LocalDate.ofEpochDay(next.day).dayOfWeek)
 }
 @Test fun springGapResolvesConsistently() {
  val mine=task("2026-03-29",150)
  assertEquals(210,mine.minute);assertEquals(mine.dueAt(),mine.inTaskZone(india).dueAt())
 }
}

package ai.mwm.client.data
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class CalendarHistoryTest {
 private val zone=ZoneId.of("UTC")
 private val date=LocalDate.of(2026,10,17)
 private fun stamp(hour:Int)=date.atTime(hour,0).atZone(zone).toInstant().toEpochMilli()
 @Test fun lateCompletionHasSeparateDueAndCompletionDays() {
  val t=Task(day=date.toEpochDay(),minute=720,status=TaskStatus.Done,completedAt=stamp(13)+86_400_000)
  assertEquals(date.toEpochDay()+1,completionDay(t,zone))
  assertTrue(missedDeadline(t,stamp(14)+86_400_000,zone))
 }
 @Test fun allDayTasksHaveUntilMidnight() {
  val t=Task(day=date.toEpochDay(),minute=null)
  assertFalse(missedDeadline(t,stamp(23),zone))
  assertTrue(missedDeadline(t,stamp(0)+86_400_000,zone))
 }
 @Test fun unknownDateDoesNotInventCompletionHistory() {assertNull(completionDay(Task(status=TaskStatus.Done,completedAt=0),zone))}
 @Test fun newTasksStartWithNudge() {assertTrue(Task().reminder);assertFalse(Task(reminder=false).reminder)}
 @Test fun partnerClockCanHaveLocalOverride() {
  val p=Preferences(partnerClockZone="Asia/Tokyo",partnerClockOverride="Europe/Berlin")
  assertEquals("Europe/Berlin",partnerClockZone(p)?.id)
 }
}

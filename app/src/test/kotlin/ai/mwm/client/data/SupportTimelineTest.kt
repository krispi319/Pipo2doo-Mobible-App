package ai.mwm.client.data
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class SupportTimelineTest {
 private fun note(id:String,at:String,kind:String="CareSupport")=Appreciation(id=id,kind=kind,message="Rest today ♡",at=Instant.parse(at).toEpochMilli())
 @Test fun eachViewUsesSelectedTimezone(){val n=note("a","2026-10-28T23:30:00Z");assertEquals(LocalDate.parse("2026-10-28").toEpochDay(),supportDay(n.at,ZoneId.of("UTC")));assertEquals(LocalDate.parse("2026-10-29").toEpochDay(),supportDay(n.at,ZoneId.of("Asia/Kolkata")))}
 @Test fun tomorrowStartsEmptyWithoutDeletingYesterday(){val n=note("a","2026-10-28T12:00:00Z");val day=LocalDate.parse("2026-10-28").toEpochDay();assertEquals(listOf(n),supportForDay(listOf(n),day,ZoneId.of("UTC")));assertTrue(supportForDay(listOf(n),day+1,ZoneId.of("UTC")).isEmpty());assertEquals(1,supportTimeline(listOf(n),ZoneId.of("UTC")).size)}
 @Test fun chronologicalAndDuplicateSafe(){val a=note("a","2026-10-28T12:00:00Z");val b=note("b","2026-10-28T11:00:00Z");val c=note("c","2026-10-29T12:00:00Z");assertEquals(listOf(listOf(c),listOf(b,a)),supportTimeline(listOf(a,c,b,a),ZoneId.of("UTC")).map {it.entries})}
 @Test fun historyRetainsMoreThanPreviewLimit(){val list=(0..40).map {Appreciation(id="$it",kind="CareSupport",message="Note $it",at=1000L+it)};assertEquals(41,supportTimeline(list,ZoneId.of("UTC")).sumOf {it.entries.size})}
 @Test fun missingTimestampNotInvented(){val n=Appreciation(kind="CareSupport",message="Old",at=0);assertNull(supportTimeline(listOf(n),ZoneId.of("UTC")).single().day);assertNull(supportDay(-1,ZoneId.of("UTC")))}
 @Test fun nonSupportNeverAppears(){assertTrue(supportTimeline(listOf(note("a","2026-10-28T12:00:00Z","Message")),ZoneId.of("UTC")).isEmpty())}
}

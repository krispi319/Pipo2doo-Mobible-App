package ai.mwm.client.data

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class LoveMilestonesTest {
    private fun day(s:String)=LocalDate.parse(s).toEpochDay()
    private val base=Preferences(started=true,togetherSince=day("2025-02-14"),clockZone="Europe/Berlin",loveCelebrations=LoveCelebrationSettings(reminders=true))
    @Test fun dayZeroAndNextMilestoneMatchTheApprovedExample() {
        assertEquals(625L,day("2026-11-01")-base.togetherSince)
        val next=loveMilestones(base,day("2026-11-01")).first()
        assertEquals(700L,next.daysTogether);assertEquals(day("2027-01-15"),next.day)
        assertEquals(75L,next.day-day("2026-11-01"))
        assertTrue(loveMilestones(base,base.togetherSince).all {it.day>base.togetherSince})
    }
    @Test fun milestoneOnTodayRemainsVisibleAndTomorrowAdvances() {
        val date=base.togetherSince+700
        assertEquals(date,loveMilestones(base,date).first().day)
        assertTrue(loveMilestones(base,date+1).all {it.day>date})
    }
    @Test fun leapDayAnniversaryUsesFebruary28AndDayCountUsesCalendarDays() {
        val p=base.copy(togetherSince=day("2024-02-29"),loveCelebrations=base.loveCelebrations.copy(hundreds=false))
        val m=loveMilestones(p,day("2025-01-01")).first()
        assertEquals(day("2025-02-28"),m.day);assertEquals(1,m.years);assertEquals(365L,m.daysTogether)
    }
    @Test fun customAndAnniversaryOnSameDateProduceOneCelebration() {
        val p=base.copy(loveCelebrations=base.loveCelebrations.copy(hundreds=false,customDays=listOf(365,365)))
        val list=loveMilestones(p,base.togetherSince,4)
        assertEquals(1,list.count {it.day==base.togetherSince+365})
        assertEquals(1,list.first().years)
    }
    @Test fun allDisabledHasNoEventsAndCustomOnlyIsSorted() {
        val none=base.copy(loveCelebrations=base.loveCelebrations.copy(hundreds=false,anniversaries=false))
        assertTrue(loveMilestones(none,none.togetherSince).isEmpty())
        assertEquals(listOf(520L,1000L),loveMilestones(none.copy(loveCelebrations=none.loveCelebrations.copy(customDays=listOf(1000,520))),none.togetherSince).map {it.daysTogether})
    }
    @Test fun notificationTimesAreLocalAndAdvanceNoticeIsSeparate() {
        val p=base.copy(quietEnabled=false,loveCelebrations=base.loveCelebrations.copy(anniversaries=false,minute=540,advanceDays=3))
        val now=LocalDate.ofEpochDay(p.togetherSince+95).atStartOfDay(clockZone(p.clockZone)).toInstant().toEpochMilli()
        val events=loveReminderEvents(p,now).filter {it.milestone.daysTogether==100L}
        assertEquals(2,events.size)
        assertEquals(listOf(97L,100L),events.map {Instant.ofEpochMilli(it.at).atZone(clockZone(p.clockZone)).toLocalDate().toEpochDay()-p.togetherSince})
        assertTrue(events.all {Instant.ofEpochMilli(it.at).atZone(clockZone(p.clockZone)).hour==9})
        assertNotEquals(events[0].key(p),events[1].key(p))
    }
    @Test fun reminderTimeDuringDstGapMovesToNextValidTime() {
        val p=base.copy(togetherSince=day("2026-03-28"),quietEnabled=false,loveCelebrations=LoveCelebrationSettings(hundreds=false,anniversaries=false,customDays=listOf(1),reminders=true,minute=150,advanceDays=0))
        val event=loveReminderEvents(p,Instant.parse("2026-03-28T10:00:00Z").toEpochMilli()).single()
        assertEquals(Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(),event.at)
    }
    @Test fun quietHoursDeferToMorningAcrossMidnight() {
        val at=ZonedDateTime.of(2026,11,1,23,0,0,0,clockZone(base.clockZone)).toInstant().toEpochMilli()
        val adjusted=Instant.ofEpochMilli(loveAfterQuiet(at,base)).atZone(clockZone(base.clockZone))
        assertEquals(LocalDate.of(2026,11,2),adjusted.toLocalDate());assertEquals(LocalTime.of(7,30),adjusted.toLocalTime())
        assertFalse(loveIsQuiet(adjusted.toInstant().toEpochMilli(),base))
    }
    @Test fun reminderOptOutAndStoppedWorkspaceDoNotSchedule() {
        assertTrue(loveReminderEvents(base.copy(loveCelebrations=base.loveCelebrations.copy(reminders=false)),System.currentTimeMillis()).isEmpty())
        assertTrue(loveReminderEvents(base.copy(started=false),System.currentTimeMillis()).isEmpty())
    }
    @Test fun configRoundTripsAndOldSettingsRemainCompatible() {
        val settings=LoveCelebrationSettings(customDays=listOf(520,1000),reminders=true,minute=1234,advanceDays=7,effect="Hearts",sound=true,haptics=false,note="Our little note ♡")
        assertEquals(settings,LoveCelebrationSettings.fromJson(settings.toJson()))
        assertEquals(LoveCelebrationSettings(),LoveCelebrationSettings.fromJson(null))
        assertEquals(listOf(520,1000),settings.copy(customDays=listOf(1000,520,1000)).validated().customDays)
    }
    @Test fun newPartnerDoesNotInheritOldLoveNoteOrMilestones() {
        val old=base.copy(loveCelebrations=base.loveCelebrations.copy(note="For former partner",customDays=listOf(520)))
        assertEquals(LoveCelebrationSettings(),old.startNewCouple(day("2026-11-01")).loveCelebrations)
    }
    @Test(expected=IllegalArgumentException::class) fun invalidCustomMilestoneCannotSave() {LoveCelebrationSettings(customDays=listOf(0)).validated()}
}

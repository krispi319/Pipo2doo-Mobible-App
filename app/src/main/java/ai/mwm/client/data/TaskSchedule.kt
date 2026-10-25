package ai.mwm.client.data

import java.time.*

/** Timed tasks have one instant; day/minute remain the viewer's local presentation.
 * Old tasks deliberately remain floating until their date or time is explicitly edited.
 * An Anytime task remains a date-only task, never an invented shared appointment. */
fun Task.inTaskZone(zone: ZoneId): Task {
    if(scheduledAt <= 0 || minute == null) return copy(displayZone = zone.id)
    val local = Instant.ofEpochMilli(scheduledAt).atZone(zone)
    return copy(day = local.toLocalDate().toEpochDay(), minute = local.hour * 60 + local.minute, displayZone = zone.id)
}

fun Task.withSavedSchedule(previous: Task?, zone: ZoneId): Task {
    if(minute == null) return copy(scheduledAt = 0, scheduleZone = "", displayZone = zone.id)
    val displayedPrevious = previous?.inTaskZone(zone)
    val changed = previous == null || day != displayedPrevious?.day || minute != displayedPrevious.minute || (scheduledAt > 0 && previous.scheduledAt <= 0)
    if(!changed) return copy(scheduledAt = previous.scheduledAt, scheduleZone = previous.scheduleZone, displayZone = zone.id)
    val local = LocalDate.ofEpochDay(day).atTime(minute / 60, minute % 60)
    // The platform resolves a spring gap forwards and an autumn overlap to its earlier offset.
    val instant = local.atZone(zone).toInstant().toEpochMilli()
    return copy(scheduledAt = instant, scheduleZone = zone.id, displayZone = zone.id).inTaskZone(zone)
}

/** Stable wire representation; viewing in another zone must never mutate the task. */
fun Task.canonicalSchedule(): Task = if(scheduledAt > 0 && minute != null)
    inTaskZone(clockZone(scheduleZone)).copy(displayZone = "") else copy(displayZone = "")

fun PipoState.inTaskZone(): PipoState {
    val zone = clockZone(preferences.clockZone)
    return copy(tasks = tasks.map { it.inTaskZone(zone) })
}

package ai.mwm.client.data

import java.time.*
fun clockZone(selection: String): ZoneId = runCatching {ZoneId.of(selection)}.getOrElse {ZoneId.systemDefault()}
fun partnerClockZone(p: Preferences): ZoneId? = (p.partnerClockOverride.ifBlank {p.partnerClockZone}).takeIf {it.isNotBlank()}?.let {runCatching {ZoneId.of(it)}.getOrNull()}
fun completionDay(task: Task,zone: ZoneId=ZoneId.systemDefault()): Long? =
    if(task.done && task.completedAt>0) Instant.ofEpochMilli(task.completedAt).atZone(zone).toLocalDate().toEpochDay() else null
fun missedDeadline(task: Task,now: Long=System.currentTimeMillis(),zone: ZoneId=ZoneId.systemDefault()): Boolean {
    val due=if(task.minute!=null) task.dueAt(zone) else LocalDate.ofEpochDay(task.day).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    if(now<due) return false
    return if(task.done) task.completedAt>0 && if(task.minute==null) task.completedAt>=due else task.completedAt>due else true
}

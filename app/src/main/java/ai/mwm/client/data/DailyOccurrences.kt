package ai.mwm.client.data

import java.time.*
import java.util.UUID

const val DAILY_LOOKAHEAD_DAYS = 21L
fun dailyOccurrenceId(series: String, day: Long): String =
    UUID.nameUUIDFromBytes("pipo-daily:$series:$day".toByteArray(Charsets.UTF_8)).toString()

/** Preserve old completion chains as one series; generated days are siblings, not deep chains. */
fun normalizeDailySeries(tasks: List<Task>): List<Task> {
    val byId=tasks.associateBy {it.id}
    return tasks.map {task ->
        if(task.repeat!=Repeat.Daily || task.seriesId.isNotBlank()) task else {
            var root=task;val seen=mutableSetOf(task.id)
            while(true) {
                val parent=byId[root.parentId] ?: break
                if(parent.repeat!=Repeat.Daily || !seen.add(parent.id)) break
                root=parent
            }
            if(root.id==task.id) task else task.copy(seriesId=root.seriesId.ifBlank {root.id},parentId=root.seriesId.ifBlank {root.id})
        }
    }
}

/** Local workspaces only. Shared occurrences are materialized by the server, never guessed by a viewer. */
fun PipoState.withDailyOccurrences(now: Long = System.currentTimeMillis()): PipoState {
    val normalized=normalizeDailySeries(tasks)
    val result=normalized.toMutableList();val ids=normalized.mapTo(mutableSetOf()) {it.id}.apply {addAll(skippedDaily)}
    for(root in normalized.filter {it.repeat==Repeat.Daily && it.seriesId.isBlank()}) {
        val zone=if(root.scheduledAt>0 && root.minute!=null) clockZone(root.scheduleZone) else clockZone(preferences.clockZone)
        val anchor=if(root.scheduledAt>0 && root.minute!=null) Instant.ofEpochMilli(root.scheduledAt).atZone(zone) else null
        val first=anchor?.toLocalDate()?.toEpochDay() ?: root.day
        val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        val occupied=normalized.filter {it.seriesId==root.id}.mapTo(mutableSetOf()) {child ->
            if(child.scheduledAt>0 && child.minute!=null) Instant.ofEpochMilli(child.scheduledAt).atZone(zone).toLocalDate().toEpochDay() else child.day
        }
        for(day in maxOf(first+1,today)..(today+DAILY_LOOKAHEAD_DAYS)) {
            val id=dailyOccurrenceId(root.id,day)
            if(day in occupied || !ids.add(id)) continue
            val instant=anchor?.let {LocalDate.ofEpochDay(day).atTime(it.toLocalTime()).atZone(zone).toInstant().toEpochMilli()} ?: 0L
            result+=root.copy(id=id,day=day,scheduledAt=instant,status=TaskStatus.Todo,completedAt=0,
                parentId=root.id,seriesId=root.id,events=listOf(TaskEvent("Daily occurrence",now))).inTaskZone(clockZone(preferences.clockZone))
        }
    }
    return if(result==tasks) this else copy(tasks=result)
}

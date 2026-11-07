package ai.mwm.client.data

enum class TaskEditScope { Occurrence, Future, AllUnfinished }

/** A recurring edit changes shared details without rewriting completed history or personal notes. */
fun PipoState.withTaskEdit(task: Task, scope: TaskEditScope, now: Long = System.currentTimeMillis()): PipoState {
    val old=tasks.find {it.id==task.id}
    require(old?.cancelled!=true) {"Cancelled tasks stay in History."}
    val zone=clockZone(preferences.clockZone)
    val saved=task.withSavedSchedule(old,zone).copy(title=task.title.trim(),note=task.note.trim(),
        completedAt=if(task.done) {if(old?.done==true)old.completedAt else now} else 0,
        events=(old?.events ?: emptyList())+TaskEvent(if(old==null) "Created this task" else "Edited this task",now))
    require(saved.title.isNotBlank()) {"Task title is required."}
    if(old==null) return copy(tasks=tasks+saved+if(saved.done && saved.repeat==Repeat.Weekly) listOfNotNull(nextOccurrence(saved,now)) else emptyList())
    if(scope!=TaskEditScope.Occurrence)return withRecurringDetails(old,saved,scope,now)
    var result=tasks.map {if(it.id==saved.id)saved else it}
    fun details(t:Task)=t.copy(title=if(old.title!=saved.title)saved.title else t.title,
        owner=if(old.owner!=saved.owner)saved.owner else t.owner,
        listId=if(old.listId!=saved.listId)saved.listId else t.listId,
        tags=if(old.tags!=saved.tags)saved.tags else t.tags)
    if(old.repeat==Repeat.Daily) {
        val rootId=old.seriesId.ifBlank {old.id}
        val root=tasks.find {it.id==rootId} ?: error("The daily series is unavailable. Refresh before editing.")
        val family=tasks.filter {it.id==rootId || it.seriesId==rootId}
        if(old.id==rootId && (details(old)!=old || old.day!=saved.day || old.minute!=saved.minute || old.repeat!=saved.repeat || old.note!=saved.note || old.reminder!=saved.reminder || old.offset!=saved.offset)) {
            // Editing the template alone must not silently change dates generated later.
            val future=family.filter {it.id!=old.id && it.day>old.day && !it.closed}.minByOrNull {it.day}
                ?: old.copy(id=dailyOccurrenceId(old.id,old.day+1),day=old.day+1,scheduledAt=0,
                    status=TaskStatus.Todo,completedAt=0,events=listOf(TaskEvent("Daily occurrence",now)))
                    .withSavedSchedule(null,zone)
            val nextRoot=future.copy(seriesId="",parentId=root.parentId)
            if(result.none {it.id==nextRoot.id}) result=result+nextRoot
            result=result.map {t->when {
                t.id==nextRoot.id -> nextRoot
                t.id==old.id -> saved.copy(seriesId=nextRoot.id,parentId=nextRoot.id)
                t.seriesId==rootId -> t.copy(seriesId=nextRoot.id,parentId=nextRoot.id)
                else -> t
            }}
        }
    } else if(old.repeat==Repeat.Weekly && details(old)!=old && tasks.none {it.parentId==old.id}) {
        // Keep the original weekly details for the next occurrence, even before completion.
        nextOccurrence(old,now)?.let {result=result+it}
    }
    val next=if(saved.done && !old.done && saved.repeat==Repeat.Weekly) nextOccurrence(saved,now) else null
    return copy(tasks=result+if(next!=null && result.none {it.parentId==saved.id})listOf(next) else emptyList())
}

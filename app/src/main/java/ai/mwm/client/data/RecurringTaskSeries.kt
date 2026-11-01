package ai.mwm.client.data

import java.time.Instant

enum class TaskDeleteScope { Occurrence, Future, Series }

/** Follow saved recurrence links, never titles: separate tasks may have identical names. */
fun PipoState.recurringFamily(id:String):List<Task> {
    val ids=tasks.mapTo(hashSetOf()) {it.id}
    val edges=hashMapOf<String,MutableSet<String>>()
    fun link(a:String,b:String) {if(b.isNotBlank()) {edges.getOrPut(a){hashSetOf()}.add(b);edges.getOrPut(b){hashSetOf()}.add(a)}}
    tasks.forEach {t->link(t.id,t.seriesId);if(t.parentId in ids)link(t.id,t.parentId)}
    val seen=hashSetOf(id);val queue=ArrayDeque<String>();queue.add(id)
    while(queue.isNotEmpty())edges[queue.removeFirst()].orEmpty().forEach {if(seen.add(it))queue.add(it)}
    return tasks.filter {it.id in seen}
}

fun PipoState.hasRecurrence(id:String)=recurringFamily(id).let {it.size>1 || it.any {t->t.repeat!=Repeat.Once || t.seriesId.isNotBlank()}}

private fun PipoState.nextDailyTemplate(source:Task,day:Long,now:Long):Task {
    val next=source.copy(id=newId(),day=day,seriesId="",parentId="",status=TaskStatus.Todo,completedAt=0,
        events=listOf(TaskEvent("Daily occurrence",now)))
    if(source.scheduledAt>0 && source.minute!=null) {
        val instant=Instant.ofEpochMilli(source.scheduledAt).atZone(clockZone(source.scheduleZone))
            .plusDays(day-source.day).toInstant().toEpochMilli()
        return next.copy(scheduledAt=instant).inTaskZone(clockZone(preferences.clockZone))
    }
    return next.copy(scheduledAt=0).withSavedSchedule(null,clockZone(preferences.clockZone))
}

internal fun PipoState.withRecurringDetails(old:Task,edited:Task,scope:TaskEditScope,now:Long):PipoState {
    require(hasRecurrence(old.id)) {"This task has no linked recurring series."}
    require(old.day==edited.day && old.minute==edited.minute && old.repeat==edited.repeat && old.note==edited.note && old.reminder==edited.reminder && old.offset==edited.offset) {
        "Series edits change the title, assignee, list and tags. Use Only this occurrence for dates, times, repeat settings, reminders or private notes."
    }
    val unchangedShared=old.title==edited.title && old.owner==edited.owner && old.listId==edited.listId && old.tags==edited.tags
    val syncCurrentTitle=scope==TaskEditScope.AllUnfinished && unchangedShared
    fun details(t:Task)=t.copy(title=if(old.title!=edited.title || syncCurrentTitle)edited.title else t.title,
        owner=if(old.owner!=edited.owner)edited.owner else t.owner,
        listId=if(old.listId!=edited.listId)edited.listId else t.listId,
        tags=if(old.tags!=edited.tags)edited.tags else t.tags)
    if(unchangedShared && !syncCurrentTitle)return this
    val family=recurringFamily(old.id);val ids=family.mapTo(hashSetOf()) {it.id}
    val boundary=if(scope==TaskEditScope.AllUnfinished)Long.MIN_VALUE else old.day
    var result=tasks.map {if(it.id in ids && !it.done && it.day>=boundary)details(it) else it}
    if(family.any {it.repeat==Repeat.Daily && it.seriesId.isBlank()}) {
        // A stopped/edited occurrence can still belong to a daily series. Keep its linkage.
        val source=family.first {it.repeat==Repeat.Daily && it.seriesId.isBlank()}
        val intendedTemplate=details(source)
        val template=result.filter {it.id in ids && !it.done && it.day>=boundary && it.repeat==Repeat.Daily && it.minute==source.minute && it.scheduleZone==source.scheduleZone &&
            it.title==intendedTemplate.title && it.owner==intendedTemplate.owner && it.listId==intendedTemplate.listId && it.tags==intendedTemplate.tags}
            .let {pending->if(scope==TaskEditScope.Future)pending.firstOrNull {it.id==old.id} ?: pending.minByOrNull {it.day} else pending.minByOrNull {it.day}}
            ?: details(nextDailyTemplate(source,maxOf(Instant.ofEpochMilli(now).atZone(clockZone(preferences.clockZone)).toLocalDate().toEpochDay()+1,family.maxOf {it.day}+1),now))
        val externalParent=family.firstOrNull {it.parentId !in ids}?.parentId.orEmpty()
        val root=template.copy(repeat=Repeat.Daily,seriesId="",parentId=externalParent)
        if(result.none {it.id==root.id})result=result+root
        result=result.map {t->when {
            t.id==root.id -> root
            t.id !in ids -> t
            t.repeat==Repeat.Daily && t.seriesId.isBlank() -> t.copy(repeat=Repeat.Once,seriesId=root.id,parentId=root.id)
            else -> t.copy(seriesId=root.id,parentId=root.id)
        }}
    }
    return copy(tasks=result)
}

fun PipoState.taskDeletionTargets(id:String,scope:TaskDeleteScope):Set<String> {
    val selected=tasks.find {it.id==id} ?: return emptySet()
    if(scope==TaskDeleteScope.Occurrence)return setOf(id)
    return recurringFamily(id).filter {!it.done && (scope==TaskDeleteScope.Series || it.day>=selected.day)}.mapTo(hashSetOf()) {it.id}
}

/** Series deletion keeps completed content and stops templates before the server can expand again. */
fun PipoState.withTaskDeletion(id:String,scope:TaskDeleteScope,now:Long=System.currentTimeMillis()):PipoState {
    if(tasks.none {it.id==id})return this
    val family=recurringFamily(id);val familyIds=family.mapTo(hashSetOf()) {it.id}
    val deleted=taskDeletionTargets(id,scope)
    var result=tasks.filterNot {it.id in deleted}
    val outsideParent=family.firstOrNull {it.parentId !in familyIds}?.parentId.orEmpty()
    val removedTemplate=family.firstOrNull {it.id in deleted && it.repeat==Repeat.Daily && it.seriesId.isBlank()}
    if(scope==TaskDeleteScope.Occurrence && removedTemplate!=null) {
        val template=result.filter {it.id in familyIds && !it.done && it.repeat==Repeat.Daily && it.day>removedTemplate.day && it.minute==removedTemplate.minute && it.scheduleZone==removedTemplate.scheduleZone}.minByOrNull {it.day}
            ?: nextDailyTemplate(removedTemplate,maxOf(Instant.ofEpochMilli(now).atZone(clockZone(preferences.clockZone)).toLocalDate().toEpochDay()+1,family.maxOf {it.day}+1),now)
        val root=template.copy(seriesId="",parentId=outsideParent,repeat=Repeat.Daily)
        if(result.none {it.id==root.id})result=result+root
        result=result.map {t->when {t.id==root.id->root;t.id in familyIds->t.copy(seriesId=root.id,parentId=root.id);else->t}}
    } else {
        val survivors=result.filter {it.id in familyIds}
        val anchor=survivors.firstOrNull {it.parentId !in familyIds || it.parentId in deleted} ?: survivors.firstOrNull()
        result=result.map {t->if(t.id !in familyIds)t else {
            val stop=scope!=TaskDeleteScope.Occurrence && (t.repeat==Repeat.Weekly || t.repeat==Repeat.Daily && t.seriesId.isBlank())
            t.copy(repeat=if(stop)Repeat.Once else t.repeat,
                parentId=if(t.id==anchor?.id && t.parentId in deleted)outsideParent else if(t.parentId in deleted)anchor?.id.orEmpty() else t.parentId,
                seriesId=if(t.id==anchor?.id && t.seriesId in deleted)"" else if(t.seriesId in deleted)anchor?.id.orEmpty() else t.seriesId)
        }}
        // A surviving daily anchor must not become a new generator after its parent is removed.
        if(scope!=TaskDeleteScope.Occurrence && anchor!=null)result=result.map {if(it.id==anchor.id && it.repeat==Repeat.Daily && it.seriesId.isBlank())it.copy(repeat=Repeat.Once) else it}
    }
    return copy(tasks=result,skippedDaily=skippedDaily+deleted.filter {key->family.any {it.id==key && it.seriesId.isNotBlank()}})
}

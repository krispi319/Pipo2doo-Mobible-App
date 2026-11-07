package ai.mwm.client.data

enum class TaskCancelScope { Occurrence, Future }

/** Retain records as terminal history while reusing the proven series re-anchoring rules. */
fun PipoState.withTaskCancellation(id:String,scope:TaskCancelScope,actor:String,reason:String,now:Long=System.currentTimeMillis()):PipoState {
    val selected=tasks.find {it.id==id} ?: error("This task is no longer available.")
    require(!selected.closed) {"This task is already finished. Refresh and try again."}
    val clean=reason.trim()
    require(clean.length<=200) {"Keep the reason within 200 characters."}
    val deleteScope=if(scope==TaskCancelScope.Occurrence)TaskDeleteScope.Occurrence else TaskDeleteScope.Future
    // Weekly repeats need a successor even when this date is skipped instead of completed.
    val prepared=if(scope==TaskCancelScope.Occurrence && selected.repeat==Repeat.Weekly && tasks.none {it.parentId==id})
        copy(tasks=tasks+listOfNotNull(nextOccurrence(selected,now))) else this
    val targets=if(scope==TaskCancelScope.Occurrence)setOf(id) else prepared.taskDeletionTargets(id,deleteScope)
    val removed=prepared.withTaskDeletion(id,deleteScope,now)
    val remaining=if(scope==TaskCancelScope.Occurrence && selected.repeat==Repeat.Weekly)
        removed.copy(tasks=removed.tasks.map {t->if(prepared.tasks.any {it.id==t.id && it.parentId==id})t.copy(parentId=id) else t}) else removed
    val family=prepared.recurringFamily(id).mapTo(hashSetOf()) {it.id}
    val newRoot=remaining.tasks.firstOrNull {it.id in family && it.repeat==Repeat.Daily && it.seriesId.isBlank()}
        ?: remaining.tasks.firstOrNull {it.id !in tasks.map {t->t.id} && it.repeat==Repeat.Daily}
    val cancelled=prepared.tasks.filter {it.id in targets}.map {task->
        task.copy(status=TaskStatus.Done,completedAt=0,repeat=Repeat.Once,
            seriesId=if(task.id==selected.id && newRoot!=null)newRoot.id else task.seriesId,
            parentId=if(task.id==selected.id && newRoot!=null)newRoot.id else task.parentId,
            cancelledAt=now,cancelledBy=actor,cancelledByName=preferences.myName,cancelReason=clean,
            events=task.events+TaskEvent("Cancelled by ${preferences.myName}"+if(clean.isBlank())"" else " · $clean",now))
    }
    // Keep the skipped occurrence ID occupied; both local and server generators must leave it alone.
    return remaining.copy(tasks=remaining.tasks+cancelled)
}

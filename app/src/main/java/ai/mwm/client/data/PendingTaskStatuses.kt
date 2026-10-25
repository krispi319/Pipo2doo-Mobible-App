package ai.mwm.client.data

/** An intent is separate from confirmed task data. Server writes alone create repeats/history. */
data class PendingTaskStatus(val id: String = newId(), val taskId: String, val status: TaskStatus, val at: Long)
data class PendingTaskBatch(val scope: String = "", val items: List<PendingTaskStatus> = emptyList())

/** Last tap wins visually, while the persisted queue preserves write order. */
fun withPendingTaskStatuses(state: PipoState, pending: PendingTaskBatch, currentScope: String): PipoState {
    if(currentScope.isBlank() || pending.scope != currentScope || pending.items.isEmpty()) return state
    val latest = pending.items.associateBy {it.taskId}
    return state.copy(tasks=state.tasks.map {task -> latest[task.id]?.let {intent ->
        task.copy(status=intent.status, completedAt=if(intent.status==TaskStatus.Done) {
            if(task.done && task.completedAt>0) task.completedAt else intent.at
        } else 0)
    } ?: task})
}

/** Acknowledging one save must never discard newer taps or another task's pending save. */
fun PendingTaskBatch.acknowledge(scope: String, operation: String): PendingTaskBatch =
    if(this.scope != scope) this else copy(items=items.filterNot {it.id==operation})

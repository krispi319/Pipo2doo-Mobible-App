package ai.mwm.client.cloud

import ai.mwm.client.data.*

fun roleLabel(role: String) = when(role) { "user_1" -> "User 1"; "user_2" -> "User 2"; else -> "Choose a role" }
private fun ready(c: PartnerConnection) = c.online && !c.busy && c.uid.isNotBlank() && c.role in listOf("user_1","user_2")
// Assignment controls responsibility and display, not either member's editing access.
fun canEditList(@Suppress("UNUSED_PARAMETER") list: SharedList, c: PartnerConnection): Boolean = if(!c.connected) c.uid.isBlank() || c.authenticated else ready(c)
/** Check every actual ancestor, not just the visual list. Cycles and missing UUID parents fail closed. */
fun taskAuthorized(task: Task, state: PipoState, @Suppress("UNUSED_PARAMETER") uid: String): Boolean {
    val visited=mutableSetOf<String>()
    var current: Task?=task
    while(current!=null) {
        val node=current
        if(!visited.add(node.id) || visited.size>64)return false
        if(node.parentId.isBlank())break
        current=state.tasks.find {it.id==node.parentId} ?: return false
    }
    return true
}
fun canEditTask(task: Task, state: PipoState, c: PartnerConnection): Boolean = if(!c.connected) c.uid.isBlank() || c.authenticated else ready(c) && taskAuthorized(task,state,c.uid)

/** Atomic client preflight for all old/new trees. The server remains the final authority. */
fun requireSharedPermissions(old: PipoState, next: PipoState, c: PartnerConnection) {
    fun assigned(owner: Owner): String = when(owner) {Owner.Me->c.uid;Owner.You->c.partnerUid.also {require(it.isNotBlank()) {"Connect your partner before assigning to them."}};Owner.Together->""}
    val canonical=next.copy(tasks=next.tasks.map {it.copy(assigneeUid=assigned(it.owner))},lists=next.lists.map {it.copy(assigneeUid=assigned(it.owner))})
    old.tasks.filter {before->next.tasks.find {it.id==before.id}!=before}.forEach {require(taskAuthorized(it,old,c.uid)) {"This task has an invalid parent chain. Refresh and try again."}}
    // Both members may change assignments and destinations; validate the parent chain.
    canonical.tasks.filter {after->old.tasks.find {it.id==after.id}!=next.tasks.find {it.id==after.id}}.forEach {after->
        val before=old.tasks.find {it.id==after.id}
        require(taskAuthorized(after,canonical,c.uid)) {"This task has an invalid parent chain. Choose a valid parent."}
        if(before!=null) require(taskAuthorized(before,old,c.uid)) {"This task has an invalid parent chain. Refresh and try again."}
    }
    old.notes.filter {before->next.notes.find {it.id==before.id}!=before}.forEach {require(it.owner!=Owner.You) {"Only the sender can change their thank-you note."}}
}

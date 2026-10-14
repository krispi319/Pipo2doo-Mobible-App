package ai.mwm.client.data

/** Conservative upgrade: old examples had random IDs, but were created as one batch.
 * Require that batch provenance as well as the exact bundled content. A task created
 * or edited by a user is kept, even when it uses a bundled list or example title. */
internal fun removeBundledExamples(state: PipoState): PipoState {
    val demo = legacyDemoState()
    fun matches(t: Task, d: Task): Boolean = t.title == d.title && t.owner == d.owner &&
        t.listId == d.listId && t.minute == d.minute && t.note == d.note &&
        t.tags == d.tags && t.repeat == d.repeat && t.parentId.isBlank()
    val candidates = state.tasks.filter { t ->
        t.events.firstOrNull()?.text == "Created this task" &&
            t.events.none { it.text == "Edited this task" } && demo.tasks.any { matches(t, it) }
    }
    val batch = candidates.map { it.events.first().at }.sorted()
    // At least five original examples with creation timestamps within one second
    // identifies the original bundled batch, never just a similarly named task.
    val seedTimes = batch.filter { at -> batch.count { kotlin.math.abs(it - at) <= 1000L } >= 5 }
    val removed = candidates.filter { t -> seedTimes.any { kotlin.math.abs(t.events.first().at - it) <= 1000L } }.map { it.id }.toMutableSet()
    var found = true
    while(found) {
        val descendants = state.tasks.filter { it.parentId in removed && it.id !in removed && it.events.none { e -> e.text == "Edited this task" } }
        found = descendants.isNotEmpty()
        removed.addAll(descendants.map { it.id })
    }
    val tasks = state.tasks.filterNot { it.id in removed }
    // Do not remove a list containing real tasks or a list the user customized.
    val lists = state.lists.filterNot { list -> demo.lists.any { it == list } && tasks.none { it.listId == list.id } }
    val tags = state.tags.filterNot { tag -> demo.tags.any { it == tag } && tasks.none { tag.id in it.tags } }
    return state.copy(tasks=tasks, lists=lists, tags=tags, notes=state.notes.filterNot { it.sample })
}

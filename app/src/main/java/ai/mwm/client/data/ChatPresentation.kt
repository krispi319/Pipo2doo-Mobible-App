package ai.mwm.client.data

/** Internal relation IDs must never appear as a human-facing task caption. */
fun chatContext(note: Appreciation,state: PipoState): String? {
    if(note.kind=="Heart") return "Locket picture"
    if(note.kind=="CareSupport") return "Care & support"
    val reference=note.taskTitle.trim()
    if(reference.isBlank()) return null
    if(runCatching {java.util.UUID.fromString(reference)}.isSuccess) return state.tasks.find {it.id==reference}?.title?.takeIf {it.isNotBlank()}
    return reference
}

package ai.mwm.client.data

/** Sending is local; Sent requires a server save; Read requires the recipient's receipt. */
data class ChatPending(val note: Appreciation,val state: String="Sending",val error: String?=null)
fun chatDelivery(note: Appreciation,pending: ChatPending?,readIds: Set<String>,shared: Boolean): String = when {
    pending?.state=="Sending" -> "Sending"
    pending?.state=="Failed" -> "Failed · Retry"
    !shared -> "Saved here"
    note.id in readIds -> "Seen"
    else -> "Sent"
}
fun mergeChatNotes(server: List<Appreciation>,pending: Map<String,ChatPending>): List<Appreciation> =
    (server+pending.values.filter {p->server.none {it.id==p.note.id}}.map {it.note}).sortedWith(compareBy<Appreciation> {it.at}.thenBy {it.id})

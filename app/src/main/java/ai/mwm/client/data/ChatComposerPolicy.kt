package ai.mwm.client.data

/** A submission owns a draft by identity and content, never by cursor movement. */
internal data class ChatComposerDraft(val id:String,val kind:String,val text:String,val picture:String="",val replyTo:String="") {
    fun matches(note:Appreciation):Boolean = id==note.id && kind==note.kind && text.trim()==note.message && picture==note.picture && replyTo==note.replyTo
}

/** On failure, restore only the empty replacement draft created by that submission.
 * New typing, attachment, reply or message-style edits must survive an older response. */
internal fun shouldRestoreFailedChatDraft(current:ChatComposerDraft,clearedId:String?):Boolean =
    clearedId!=null && current.id==clearedId && current.text.isEmpty() && current.picture.isEmpty() && current.replyTo.isEmpty()

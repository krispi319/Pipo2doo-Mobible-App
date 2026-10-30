package ai.mwm.client.data

import org.json.JSONArray
import org.json.JSONObject

internal data class StoredChatSend(val pending:ChatPending,val submittedDraft:ChatComposerDraft?=null,val clearedId:String?=null)
internal data class ChatConversationData(val draft:ChatComposerDraft,val outbox:Map<String,StoredChatSend> = emptyMap()) {
    val pending get()=outbox.mapValues {it.value.pending}
    /** An authoritative server row confirms the ID even when its upload response was lost. */
    fun confirmed(messages:List<Appreciation>):ChatConversationData {
        val ids=messages.filter {it.owner==Owner.Me}.mapTo(mutableSetOf()){it.id}
        val matching=outbox.values.firstOrNull {it.pending.note.id in ids && it.submittedDraft==draft}
        return copy(draft=if(matching!=null)ChatComposerDraft(newId(),draft.kind,"") else draft,
            outbox=outbox.filterKeys {it !in ids})
    }
    fun interrupted():ChatConversationData {
        var restored=draft
        val next=outbox.mapValues {(_,send)->
            if(send.pending.state!="Sending")send else {
                if(send.submittedDraft!=null && shouldRestoreFailedChatDraft(restored,send.clearedId))restored=send.submittedDraft
                send.copy(pending=send.pending.copy(state="Failed",error="Send was interrupted. Retry to confirm the same message."))
            }
        }
        return copy(draft=restored,outbox=next)
    }
}
internal object ChatConversationCodec {
    fun encode(owner:String,space:String,data:ChatConversationData):JSONObject = JSONObject().put("schema",1).put("owner",owner).put("space",space)
        .put("draft",draft(data.draft)).put("outbox",JSONArray(data.outbox.values.map {s->
            val n=s.pending.note
            JSONObject().put("id",n.id).put("kind",n.kind).put("message",n.message).put("at",n.at).put("task",n.taskTitle).put("picture",n.picture).put("reply",n.replyTo)
                .put("state",s.pending.state).put("error",s.pending.error ?: JSONObject.NULL).put("submitted",s.submittedDraft?.let(::draft) ?: JSONObject.NULL).put("cleared",s.clearedId ?: JSONObject.NULL)
        }))
    fun decode(owner:String,space:String,json:JSONObject):ChatConversationData {
        require(json.getInt("schema")==1 && json.getString("owner")==owner && json.getString("space")==space){"Draft belongs to another account or space."}
        val list=json.getJSONArray("outbox");require(list.length()<=128)
        val items=(0 until list.length()).map {i->val s=list.getJSONObject(i)
            val note=Appreciation(id=s.getString("id"),kind=s.getString("kind"),message=s.getString("message"),owner=Owner.Me,at=s.getLong("at"),taskTitle=s.getString("task"),picture=s.getString("picture"),replyTo=s.getString("reply"))
            require(s.getString("state") in listOf("Sending","Sent","Failed"));java.util.UUID.fromString(note.id)
            StoredChatSend(ChatPending(note,s.getString("state"),s.optString("error").takeUnless {it=="null" || it.isBlank()}),s.optJSONObject("submitted")?.let(::readDraft),s.optString("cleared").takeUnless {it=="null" || it.isBlank()})
        }
        require(items.map {it.pending.note.id}.distinct().size==items.size)
        return ChatConversationData(readDraft(json.getJSONObject("draft")),items.associateBy {it.pending.note.id})
    }
    private fun draft(d:ChatComposerDraft)=JSONObject().put("id",d.id).put("kind",d.kind).put("text",d.text).put("picture",d.picture).put("reply",d.replyTo)
    private fun readDraft(j:JSONObject)=ChatComposerDraft(j.getString("id"),j.getString("kind"),j.getString("text"),j.getString("picture"),j.getString("reply")).also {java.util.UUID.fromString(it.id)}
}

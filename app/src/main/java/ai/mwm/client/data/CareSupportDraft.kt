package ai.mwm.client.data

/** A retry is the same note; new text after submission becomes a separate draft. */
data class CareSupportDraft(val ownerKey:String,val id:String=newId(),val message:String="",val sentAt:Long=0L) {
    fun edit(value:String):CareSupportDraft {
        val bounded=value.take(500)
        return if(sentAt>0 && bounded.trim()!=message.trim()) CareSupportDraft(ownerKey,message=bounded)
        else copy(message=bounded)
    }
    fun prepare(owner:String,now:Long):CareSupportDraft {
        check(owner==ownerKey) {"Account or partner changed. Reopen Care before sending."}
        require(message.trim().length in 1..500) {"Write a support note first."}
        require(sentAt>0 || now>0) {"The support note needs a valid date."}
        return if(sentAt>0)this else copy(sentAt=now)
    }
    fun acknowledge(owner:String,sentId:String):CareSupportDraft =
        if(owner==ownerKey && sentId==id)CareSupportDraft(ownerKey) else this
}

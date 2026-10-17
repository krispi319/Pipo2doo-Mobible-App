package ai.mwm.client.data

/** Shared with the server allowlist. One reaction per person on each message. */
val CHAT_EMOJIS=listOf("❤️","🥰","😘","😂","😊","🥺","😍","💕","🫶","👍","🎉","😢","🐱","🌸","✨","💖","🤗","😴","🙈","😎","🤭","😭","😮","💪","🙏","🔥","💯","🌷","☀️","🌙","🍀","🧸","☕","🍰","🎂","💌")
data class ChatReaction(val messageId:String,val userId:String,val emoji:String)
data class EmojiInsertion(val text:String,val cursor:Int)

/** UTF-16 limit without leaving a dangling half of a surrogate pair. */
fun limitChatText(text:String,limit:Int=240):String {
    if(text.length<=limit) return text
    val end=if(limit>0 && text[limit-1].isHighSurrogate() && text[limit].isLowSurrogate()) limit-1 else limit
    return text.substring(0,end.coerceAtLeast(0))
}
fun insertChatEmoji(text:String,start:Int,end:Int,emoji:String):EmojiInsertion? {
    require(emoji in CHAT_EMOJIS)
    fun boundary(index:Int,forward:Boolean):Int {
        val i=index.coerceIn(0,text.length)
        return if(i>0 && i<text.length && text[i-1].isHighSurrogate() && text[i].isLowSurrogate()) i+(if(forward) 1 else -1) else i
    }
    val a=boundary(minOf(start,end),false)
    val b=boundary(maxOf(start,end),true)
    val result=text.substring(0,a)+emoji+text.substring(b)
    return if(result.length>240) null else EmojiInsertion(result,a+emoji.length)
}
fun nextChatReaction(current:String?,chosen:String):String {
    require(chosen in CHAT_EMOJIS)
    return if(current==chosen) "" else chosen
}

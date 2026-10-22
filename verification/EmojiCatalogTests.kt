import ai.mwm.client.data.*
fun main() {
 var n=0
 fun test(ok:Boolean) {check(ok);n++}
 test(CHAT_EMOJI_CHOICES.size==157)
 test(CHAT_EMOJI_CHOICES.map {it.glyph}.distinct().size==157)
 test(CHAT_EMOJIS.all {e->CHAT_EMOJI_CHOICES.any {it.glyph==e}})
 test(emojiPickerChoices(true).map {it.glyph}.toSet()==CHAT_EMOJIS.toSet())
 test(emojiPickerChoices(false).size>emojiPickerChoices(true).size)
 test(CHAT_EMOJI_CHOICES.map {it.category}.distinct()==listOf("People","Love","Nature","Food","Celebrate"))
 test(insertChatEmoji("Hi ",3,3,"😀")==EmojiInsertion("Hi 😀",5))
 test(insertChatEmoji("replace",0,7,"🥳")==EmojiInsertion("🥳",2))
 test(runCatching {insertChatEmoji("",0,0,"bad")}.isFailure)
 test(runCatching {nextChatReaction(null,"😀")}.isFailure)
 test(nextChatReaction("❤️","❤️")=="")
 test(nextChatReaction(null,"🥰")=="🥰")
 println("$n catalogue, insertion and reaction-compatibility checks passed")
}

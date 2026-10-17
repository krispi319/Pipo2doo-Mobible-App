import ai.mwm.client.data.*
fun main() {
 var n=0
 fun test(ok:Boolean) {check(ok);n++}
 test(CHAT_EMOJIS.distinct().size==36)
 test(insertChatEmoji("Hi there",2,2,"😊")==EmojiInsertion("Hi😊 there",4))
 test(insertChatEmoji("Hello Kris",6,10,"❤️")==EmojiInsertion("Hello ❤️",8))
 test(insertChatEmoji("Hello Kris",10,6,"❤️")==EmojiInsertion("Hello ❤️",8))
 test(insertChatEmoji("a😊b",2,2,"🐱")==EmojiInsertion("a🐱b",3))
 test(insertChatEmoji("x".repeat(239),239,239,"😊")==null)
 test(insertChatEmoji("x".repeat(238),238,238,"😊")?.text?.length==240)
 test(insertChatEmoji("x".repeat(240),0,2,"😊")?.text?.length==240)
 test(limitChatText("x".repeat(239)+"😊")=="x".repeat(239))
 test(limitChatText("x".repeat(238)+"😊z")=="x".repeat(238)+"😊")
 test(limitChatText("cute 🥰")=="cute 🥰")
 test(nextChatReaction("❤️","❤️")=="")
 test(nextChatReaction("❤️","😊")=="😊")
 test(nextChatReaction(null,"😊")=="😊")
 test(runCatching {nextChatReaction(null,"bad")}.isFailure)
 println("$n emoji and reaction logic checks passed")
}

package ai.mwm.client.data
fun main() {
 val n=Appreciation(id="one",kind="Message",message="Hi",at=1)
 var checks=0
 fun verify(ok:Boolean,label:String){check(ok){label};checks++;println("PASS $label")}
 verify(chatDelivery(n,ChatPending(n),emptySet(),true)=="Sending","optimistic bubble is Sending")
 verify(chatDelivery(n,ChatPending(n,"Sent"),emptySet(),true)=="Sent","server save is Sent")
 verify(chatDelivery(n,null,setOf("one"),true)=="Read","partner receipt is Read")
 verify(chatDelivery(n,null,setOf("other"),true)=="Sent","another message receipt cannot mark this one read")
 verify(chatDelivery(n,ChatPending(n,"Failed"),emptySet(),true)=="Failed · Retry","failure has retry status")
 verify(chatDelivery(n,null,setOf("one"),false)=="Saved here","local notes never claim partner delivery")
 verify(mergeChatNotes(listOf(n),mapOf("one" to ChatPending(n))).size==1,"server and optimistic ID deduplicate")
 verify(mergeChatNotes(emptyList(),mapOf("one" to ChatPending(n))).single().id=="one","pending message appears immediately")
 val two=n.copy(id="two",at=2)
 verify(mergeChatNotes(listOf(two),mapOf("one" to ChatPending(n))).map {it.id}==listOf("one","two"),"pending and server messages remain ordered")
 verify(chatDelivery(n,ChatPending(n),setOf("one"),true)=="Sending","receipt cannot prematurely complete current send")
 println("$checks delivery checks passed")
}

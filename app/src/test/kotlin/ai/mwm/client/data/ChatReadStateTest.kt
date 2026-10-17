package ai.mwm.client.data
import org.junit.Assert.*
import org.junit.Test
class ChatReadStateTest {
    @Test fun onlyIncomingMessagesAndThanksCount() {
        val notes=listOf(
            Appreciation(id="message",kind="Message",message="Hi",owner=Owner.You,at=1),
            Appreciation(id="thanks",kind="Thank you",message="",owner=Owner.You,at=1),
            Appreciation(id="support",kind="CareSupport",message="Here",owner=Owner.You),
            Appreciation(kind="Message",message="Own",owner=Owner.Me),
            Appreciation(kind="Locket",message="Photo",owner=Owner.You),
            Appreciation(kind="Thank you",message="Sample",owner=Owner.You,sample=true))
        assertEquals(3,ChatReadState.unread(notes,emptySet()))
        assertEquals(2,ChatReadState.unread(notes,setOf("message"))) // Same timestamp does not swallow a second note.
        assertEquals(0,ChatReadState.unread(notes,setOf("message","thanks","support")))
        assertEquals(0,ChatReadState.unread(emptyList(),setOf("old-deleted-note")))
    }
}

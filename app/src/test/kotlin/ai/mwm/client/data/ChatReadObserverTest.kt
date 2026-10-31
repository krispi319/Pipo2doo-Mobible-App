package ai.mwm.client.data

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class ChatReadObserverTest {
    private val context get()=RuntimeEnvironment.getApplication()

    @Test fun openingConversationClearsLargeBadgeAndNewMessagesCountAgain() {
        AccountScope.activate(context,"")
        val space=newId()
        val notes=(1..150).map {Appreciation(id="message:$it",kind="Message",message="Hello",owner=Owner.You)}
        assertEquals(150,ChatReadState.unread(notes,ChatReadState.read(context,space)))
        ChatReadState.mark(context,space,ChatReadState.incoming(notes).map {it.id}.toSet())
        val read=ChatReadState.read(context,space)
        assertEquals(0,ChatReadState.unread(notes,read))
        assertEquals(1,ChatReadState.unread(notes+Appreciation(id="new",kind="Message",message="New",owner=Owner.You),read))
        assertEquals(read,ChatReadState.read(context,space))
        assertTrue(ChatReadState.read(context,newId()).isEmpty())
    }

    @Test fun markersNeverCrossAccountOrSpaceBoundaries() {
        AccountScope.activate(context,"first")
        val first=AccountScope.current(context);val space=newId()
        ChatReadState.mark(context,space,setOf("first-read"),first)
        AccountScope.activate(context,"second")
        assertTrue(ChatReadState.read(context,space).isEmpty())
        ChatReadState.mark(context,space,setOf("stale"),first)
        assertTrue(ChatReadState.read(context,space).isEmpty())
        AccountScope.activate(context,"first")
        assertEquals(setOf("first-read"),ChatReadState.read(context,space))
        assertTrue(ChatReadState.read(context,newId()).isEmpty())
        AccountScope.activate(context,"")
    }
}

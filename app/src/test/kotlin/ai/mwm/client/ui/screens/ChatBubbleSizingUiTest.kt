package ai.mwm.client.ui.screens

import ai.mwm.client.cloud.*
import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.*
import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[28],application=Application::class,qualifiers="w360dp-h800dp-xxhdpi")
class ChatBubbleSizingUiTest {
    @get:Rule val compose=createComposeRule()
    private val p=Preferences(myName="Alex",partnerName="Sam",started=true)
    private fun show(notes:List<Appreciation>,paired:Boolean=false) {
        val connection=if(paired)PartnerConnection(uid="me",partnerUid="partner",spaceId="chat-sizing",role="user_1",partnerRole="user_2",online=true,authenticated=true) else PartnerConnection()
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides connection) {MyApplicationTheme {
            Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {CuteChatScreen(PipoState(notes=notes,preferences=p),"",{}, {},{_,_,_,_,_,_,done->done(null)})}
        }}}
        compose.waitForIdle()
    }
    @Test fun shortMessagesShrinkLongMessagesWrapAndBothSidesKeepAlignment() {
        val long="Let us get dinner together after work. I can pick up the groceries on my way home and we can cook something nice."
        show(listOf(Appreciation(id="short-in",kind="Message",message="Hi",owner=Owner.You,at=1),
            Appreciation(id="long-out",kind="Message",message=long,owner=Owner.Me,at=2),
            Appreciation(id="short-out",kind="Message",message="See you soon",owner=Owner.Me,at=3)),paired=true)
        val incoming=compose.onNodeWithTag("chat-bubble:short-in").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val outgoing=compose.onNodeWithTag("chat-bubble:short-out").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val wide=compose.onNodeWithTag("chat-bubble:long-out").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val text=compose.onNodeWithText("Hi").fetchSemanticsNode().boundsInRoot
        val density=compose.density.density
        assertTrue(incoming.width<wide.width*.5f)
        assertTrue(outgoing.width<wide.width)
        assertTrue(incoming.width-text.width<=26f*density)
        assertTrue(wide.height>incoming.height)
        assertEquals(wide.right,outgoing.right,1f)
        assertTrue(incoming.left<outgoing.left)
        assertTrue(wide.width<=(360f-32f)*.87f*density+1f)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v147-chat-content-sized.png")
    }
    @Test fun repliesAndEmojiStayCompactInLocalMode() {
        show(listOf(Appreciation(id="original",kind="Message",message="Tea?",owner=Owner.You,at=10),
            Appreciation(id="reply",kind="Message",message="Yes",owner=Owner.Me,replyTo="original",at=11),
            Appreciation(id="emoji",kind="Message",message="❤️",owner=Owner.You,at=12)))
        val reply=compose.onNodeWithTag("chat-bubble:reply").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val emoji=compose.onNodeWithTag("chat-bubble:emoji").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(reply.width<240f*compose.density.density)
        assertTrue(emoji.width<140f*compose.density.density)
        compose.onAllNodesWithText("Reply").onFirst().assertExists()
        compose.onNodeWithContentDescription("Send message").assertExists()
    }
}

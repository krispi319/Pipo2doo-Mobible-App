package ai.mwm.client.ui.screens

import ai.mwm.client.cloud.*
import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.*
import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
class ChatVisibilityUiTest {
    @get:Rule val compose=createComposeRule()
    private val connection=PartnerConnection(uid="me",partnerUid="partner",spaceId="preview",role="user_1",online=true,authenticated=true,
        chatPresence=ChatPresence(visible=false,partnerOnline=true,known=true))
    private val state=PipoState(preferences=Preferences(myName="Alex",partnerName="Sam",myPhoto="asset:profile_mina",partnerPhoto="asset:profile_partner",started=true))

    @Test fun badgeObservesReadMarkersWithoutAnotherNetworkRefresh() {
        compose.setContent {
            val context=LocalContext.current
            val space=remember {newId()}
            val notes=remember {(1..150).map {Appreciation(id="m$it",kind="Message",message="Hello",owner=Owner.You)}}
            val read by remember(space) {ChatReadState.observe(context,space)}.collectAsState(initial=emptySet())
            androidx.compose.foundation.layout.Column {
                Text("Unread: ${ChatReadState.unread(notes,read)}")
                Button({ChatReadState.mark(context,space,notes.map {it.id}.toSet())}) {Text("Open conversation")}
            }
        }
        compose.onNodeWithText("Unread: 150").assertExists()
        compose.onNodeWithText("Open conversation").performClick()
        compose.waitUntil(3000) {compose.onAllNodesWithText("Unread: 0").fetchSemanticsNodes().size==1}
    }

    @Test fun chatSeparatesPartnerOnlineFromOwnInvisibleSetting() {
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides connection) {MyApplicationTheme {
            Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {CuteChatScreen(state,"",{}, {}, {_,_,_,_,_,_,done->done(null)})}
        }}}
        compose.onNodeWithText("Online").assertExists()
        compose.onNodeWithText("You: Invisible").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/chat-presence.png")
    }

    @Test fun settingsVisibilitySwitchSubmitsExplicitChoice() {
        var choice:Boolean?=null
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides connection) {MyApplicationTheme {
            SettingsScreen(state,true,{}, {}, {}, {}, {},onChatVisibility={choice=it})
        }}}
        compose.onNodeWithText("Connected to shared space").assertExists()
        compose.onNodeWithText("Refresh").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Show online status"))
        compose.onNodeWithContentDescription("Show online status").assertIsOff().performClick()
        compose.runOnIdle {assertEquals(true,choice)}
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/chat-visibility-settings.png")
    }
}

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
class LocketLayoutUiTest {
    @get:Rule val compose=createComposeRule()
    private val state=PipoState(preferences=Preferences(myName="Alex",partnerName="Sam",myPhoto="asset:profile_mina",partnerPhoto="asset:profile_partner",clockZone="Europe/Berlin",partnerClockZone="Asia/Ho_Chi_Minh",started=true),
        tasks=listOf(Task(id="dinner",title="Plan our dinner",minute=1080)),
        notes=listOf(Appreciation(id="photo",kind="Locket",message="Our weekend",at=2000,picture="asset:demo_travel"),Appreciation(id="partner-photo",kind="Locket",message="Home together",at=1000,picture="asset:demo_balcony",owner=Owner.You)))
    @Test fun photosAppearAboveTasksAndGalleryAndCollapseStillWork() {
        val request=mutableStateOf<String?>(null)
        var consumed=0
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides PartnerConnection(uid="me",partnerUid="partner",spaceId="ours",online=true,authenticated=true)) {MyApplicationTheme {
            Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {TodayScreen(state,{}, {},onReminders={},locketRequest=request.value,onLocketOpened={consumed++;request.value=null})}
        }}}
        val locket=compose.onNodeWithText("Our Locket").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val tasks=compose.onNodeWithText("Still to do").fetchSemanticsNode().boundsInRoot
        val clocks=compose.onNodeWithContentDescription("Set Alex clock timezone").fetchSemanticsNode().boundsInRoot
        assertTrue(locket.top>clocks.bottom);assertTrue(locket.bottom<tasks.top)
        compose.onNodeWithContentDescription("Share a locket picture").assertIsEnabled()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v145-locket-top.png")
        compose.onNodeWithContentDescription("View memories").performClick()
        compose.onNodeWithText("Our weekend").assertExists()
        compose.onNodeWithText("Share my picture").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Hide Locket").performClick()
        compose.onNodeWithContentDescription("View memories").assertDoesNotExist()
        compose.runOnIdle {request.value="photo"}
        compose.onNodeWithText("Our weekend").assertExists()
        compose.runOnIdle {assertEquals(1,consumed)}
    }
}

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
@Config(sdk=[28],application=Application::class,qualifiers="w360dp-h700dp-xxhdpi")
class CareSlimHeaderUiTest {
    @get:Rule val compose=createComposeRule()
    @Composable private fun Screen(connection:PartnerConnection,shared:CareConnection=CareConnection(),onRefresh:()->Unit={}) {
        CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides connection) {
            MyApplicationTheme {Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {
                CareScreen(CareState(),"Pi","Pi's home",null,true,{}, {}, {}, {}, {}, {},shared=shared,onRefresh=onRefresh)
            }}
        }
    }
    @Test fun soloUsesCompactSwitcherAndKeepsPartnerRecordsPrivate() {
        var refreshes=0
        compose.setContent {Screen(PartnerConnection(),onRefresh={refreshes++})}
        compose.onNodeWithText("Your cycle").assertIsDisplayed()
        compose.onNodeWithContentDescription("Refresh care records").performClick()
        assertEquals(1,refreshes)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v145-care-slim-solo.png")
        compose.onNodeWithContentDescription("Choose care profile").performClick()
        compose.onNodeWithContentDescription("View partner care records").performClick()
        compose.onNodeWithText("Give them a little space").assertExists()
        compose.onNodeWithText("Their cycle").assertDoesNotExist()
        compose.onNodeWithContentDescription("Choose care profile").performClick()
        compose.onNodeWithContentDescription("View my care records").performClick()
        compose.onNodeWithText("Your cycle").assertIsDisplayed()
        compose.onNodeWithText("A little heads-up").assertExists()
    }
    @Test fun bothPairedRolesCanSwitchOnlyToSharedPartnerRecords() {
        val connection=mutableStateOf(PartnerConnection(uid="a",partnerUid="b",spaceId="ours",role="user_1",online=true,authenticated=true))
        compose.setContent {Screen(connection.value,CareConnection(spaceId="ours",partner=CareState(),online=true))}
        for(role in listOf("user_1","user_2")) {
            compose.runOnIdle {connection.value=connection.value.copy(role=role)}
            compose.onNodeWithContentDescription("Choose care profile").performClick()
            compose.onNodeWithContentDescription("View partner care records").performClick()
            compose.onNodeWithText("Their cycle").assertIsDisplayed()
            compose.onNodeWithText("Pi's home's records · You can follow along and send support").assertExists()
            compose.onNodeWithContentDescription("Choose care profile").performClick()
            compose.onNodeWithContentDescription("View my care records").performClick()
            compose.onNodeWithText("Your cycle").assertIsDisplayed()
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v145-care-slim-paired.png")
        compose.runOnIdle {connection.value=connection.value.copy(online=false)}
        compose.onNodeWithContentDescription("Choose care profile").performClick()
        compose.onNodeWithContentDescription("View partner care records").performClick()
        compose.onNodeWithText("Their cycle").assertDoesNotExist()
    }
    @Test fun longProfileNamesRemainAccessibleInPicker() {
        val name="A very long personal display name"
        val partner="A partner with a very long display name"
        val selected=mutableStateOf(false)
        compose.setContent {MyApplicationTheme {ApprovedDesign {
            CareSlimHeader(name,partner,selected.value,{selected.value=it},{},"Your cycle","A little care, every day")
        }}}
        compose.onNodeWithContentDescription("Choose care profile").assertIsDisplayed().performClick()
        compose.onNodeWithText(partner).assertIsDisplayed().performClick()
        assertTrue(selected.value)
        compose.onNodeWithContentDescription("Choose care profile").assertIsDisplayed()
        compose.onNodeWithContentDescription("Refresh care records").assertIsDisplayed()
    }
    @Test fun unpairedCloudAccountUsesSameHeaderWithoutExposingStalePartnerRecords() {
        compose.setContent {Screen(PartnerConnection(uid="solo",spaceId="ours",role="user_1",online=true,authenticated=true),
            CareConnection(spaceId="ours",partner=CareState(),online=true))}
        compose.onNodeWithText("Your cycle").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose care profile").performClick()
        compose.onNodeWithContentDescription("View partner care records").performClick()
        compose.onNodeWithText("Their cycle").assertDoesNotExist()
        compose.onNodeWithText("Give them a little space").assertExists()
    }
}

package ai.mwm.client.ui.screens

import ai.mwm.client.cloud.*
import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.*
import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[28],application=Application::class,qualifiers="w360dp-h800dp-xxhdpi")
class CompactSettingsUiTest {
    @get:Rule val compose=createComposeRule()
    private val preferences=Preferences(myName="Pi",partnerName="Pi's home",myPhoto="asset:profile_mina",partnerPhoto="asset:profile_partner",togetherSince=LocalDate.now().minusDays(55).toEpochDay(),started=true)
    @Test fun accountHeaderKeepsLiveDetailsEqualButtonsAndMilestonesAction() {
        var details=0;var checks=0
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides PartnerConnection(uid="me",partnerUid="partner",spaceId="ours",role="user_1",partnerRole="user_2",online=true,authenticated=true,togetherDateKnown=true,accountEmail="pi@example.com",message="Partner connected · Synced")) {
            MyApplicationTheme {Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {
                SettingsScreen(PipoState(preferences=preferences),true,{}, {}, {}, {}, {},onPair={details++},onCheck={checks++})
            }}
        }}
        compose.onNodeWithText("Signed in as pi@example.com").assertIsDisplayed()
        compose.onNodeWithText("Partner connected · Synced").assertIsDisplayed()
        val left=compose.onNode(hasText("Connection details") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val right=compose.onNode(hasText("Check setup") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertEquals(left.width,right.width,.1f);assertEquals(left.height,right.height,.1f)
        compose.onNodeWithText("Connection details").performClick();compose.onNodeWithText("Check setup").performClick()
        assertEquals(1,details);assertEquals(1,checks)
        val avatar=compose.onNodeWithContentDescription("My settings avatar").fetchSemanticsNode().boundsInRoot
        assertEquals(108f*compose.density.density,avatar.width,1f)
        compose.onNodeWithContentDescription("Partner photo is read-only").assertHasNoClickAction()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v145-us-compact-applied.png")
        compose.onNodeWithContentDescription("Open our milestones").performClick()
        compose.onNodeWithText("Our milestones").assertIsDisplayed()
    }
    @Test fun soloProfileKeepsPhotoEditAndDoesNotInventRoles() {
        var edits=0;var opened=0
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides PartnerConnection()) {
            MyApplicationTheme {ApprovedDesign {CompactSettingsProfiles(preferences,55,onEditPhoto={edits++},onMilestones={opened++})}}
        }}
        compose.onNodeWithContentDescription("Change my photo").performClick();assertEquals(1,edits)
        compose.onNodeWithText("User 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open our milestones").performClick();assertEquals(1,opened)
        compose.onNodeWithText("Days",substring=true).assertExists()
    }
    @Test fun narrowProfilesKeepLargeCountAndLongNamesInsideCard() {
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true) {MyApplicationTheme {ApprovedDesign {
            Box(Modifier.width(264.dp)) {CompactSettingsProfiles(preferences.copy(myName="A much longer personal display name",partnerName="A much longer partner display name"),45000,onEditPhoto={})}
        }}}}
        val mine=compose.onNodeWithContentDescription("My settings avatar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val partner=compose.onNodeWithContentDescription("Partner settings avatar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(mine.right<partner.left)
        compose.onNodeWithText("45000").assertIsDisplayed()
        compose.onNodeWithText("A much longer partner display name").assertExists()
    }
}

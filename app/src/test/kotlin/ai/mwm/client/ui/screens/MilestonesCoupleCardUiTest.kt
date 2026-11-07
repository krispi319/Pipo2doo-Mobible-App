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
@Config(sdk=[28],application=Application::class,qualifiers="w360dp-h650dp-xxhdpi")
class MilestonesCoupleCardUiTest {
    @get:Rule val compose=createComposeRule()
    private val p=Preferences(myName="Pi",partnerName="Pi's home",myPhoto="asset:profile_mina",partnerPhoto="asset:profile_partner",togetherSince=LocalDate.now().minusDays(55).toEpochDay())
    @Test fun actualMilestonesPageKeepsComputedDateAndOwnPhotoEdit() {
        var edits=0
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides PartnerConnection(uid="a",partnerUid="b",spaceId="ours",role="user_1",partnerRole="user_2",online=true)) {
            MyApplicationTheme {ApprovedDesign {Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {
                LoveMilestonesScreen(p,{}, {},{edits++},{})
            }}}
        }}
        compose.onNodeWithText("55").assertIsDisplayed()
        compose.onNodeWithText("Together since ${dayText(p.togetherSince)}").assertIsDisplayed()
        compose.onNodeWithContentDescription("Change my photo").performClick()
        assertEquals(1,edits)
        compose.onNodeWithContentDescription("Partner photo is read-only").assertHasNoClickAction()
        val size=compose.onNodeWithContentDescription("My milestones avatar").fetchSemanticsNode().boundsInRoot
        assertEquals(108f*compose.density.density,size.width,1f)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v145-milestones-larger-avatars.png")
    }
    @Test fun bothPairedRolesAndSoloUseSameCardWithoutInventedRoles() {
        val connection=mutableStateOf(PartnerConnection())
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides connection.value) {
            MyApplicationTheme {ApprovedDesign {MilestonesCoupleCard(p,55,{})}}
        }}
        compose.onNodeWithText("Pi").assertExists()
        compose.onNodeWithText("Pi's home").assertExists()
        compose.onNodeWithText("User 1").assertDoesNotExist()
        for(role in listOf("user_1","user_2")) {
            compose.runOnIdle {connection.value=PartnerConnection(uid="me",partnerUid="partner",spaceId="ours",role=role,partnerRole=if(role=="user_1")"user_2" else "user_1",online=true)}
            compose.onNodeWithText("User 1").assertExists()
            compose.onNodeWithText("User 2").assertExists()
            compose.onNodeWithContentDescription("Partner photo is read-only").assertHasNoClickAction()
        }
    }
    @Test fun narrowCardKeepsBothPhotosAndLongNamesWithinBounds() {
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true) {MyApplicationTheme {ApprovedDesign {
            Box(Modifier.width(288.dp)) {MilestonesCoupleCard(p.copy(myName="A longer personal display name",partnerName="A much longer partner display name"),45000,{})}
        }}}}
        val mine=compose.onNodeWithContentDescription("My milestones avatar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val partner=compose.onNodeWithContentDescription("Partner milestones avatar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(mine.right<partner.left)
        assertTrue(mine.width>=96f*compose.density.density-1f)
        compose.onNodeWithText("45000").assertIsDisplayed()
        compose.onNodeWithText("A much longer partner display name").assertExists()
    }
}

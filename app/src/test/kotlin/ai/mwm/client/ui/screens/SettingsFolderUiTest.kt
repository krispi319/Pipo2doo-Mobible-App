package ai.mwm.client.ui.screens

import ai.mwm.client.cloud.*
import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.*
import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[28],application=Application::class,qualifiers="w360dp-h800dp-xxhdpi")
class SettingsFolderUiTest {
    @get:Rule val compose=createComposeRule()
    private val state=PipoState(preferences=Preferences(myName="Alex",partnerName="Sam",myPhoto="asset:profile_mina",partnerPhoto="asset:profile_partner",togetherSince=LocalDate.of(2025,2,14).toEpochDay(),clockZone="Europe/Berlin",partnerClockZone="Asia/Ho_Chi_Minh",started=true))
    private val connection=PartnerConnection(spaceId="sample-space",uid="sample-me",partnerUid="sample-partner",online=true,authenticated=true,togetherDateKnown=true,accountEmail="alex@example.com",message="Your shared space is connected",lastSyncedAt=1793525400000L)

    @Test fun folderKeepsDraftAndSaveAcrossHardwareBack() {
        lateinit var back:OnBackPressedDispatcher
        var savedName:String?=null
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides connection) {MyApplicationTheme {
            back=LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {
                SettingsScreen(state,true,{}, {}, {}, {}, {}, {},onSaveName={name,done->savedName=name;done(null)})
            }
        }}}
        compose.onNodeWithText("Shared space").assertDoesNotExist()
        compose.onNodeWithContentDescription("Change my photo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Partner photo is read-only").assertIsDisplayed()
        val details=compose.onNode(hasText("Connection details") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val setup=compose.onNode(hasText("Check setup") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertEquals(details.width,setup.width,.1f)
        assertEquals(details.height,setup.height,.1f)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v145-settings-main.png")
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Shared space").assertIsDisplayed()
        compose.onNodeWithText("My profile").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v145-settings-folder.png")
        compose.onNodeWithContentDescription("My name").performTextClearance()
        compose.onNodeWithContentDescription("My name").performTextInput("Alex New")
        compose.runOnIdle {back.onBackPressed()}
        compose.onNodeWithText("Shared space").assertDoesNotExist()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithContentDescription("My name").assertTextEquals("Alex New")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {assertEquals("Alex New",savedName)}
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Us & settings").assertIsDisplayed()
    }

    @Test fun existingSectionsAndCareActionRemainAccessible() {
        var careOpened=false
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides connection) {MyApplicationTheme {
            Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {
                SettingsScreen(state,true,{}, {}, {}, {}, {},onCare={careOpened=true})
            }
        }}}
        compose.onNodeWithText("Settings").performClick()
        listOf("Device preferences","Our timezones","Gentle reminders","Chat visibility","Notifications","Period & care").forEach {label->
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        compose.onNodeWithText("Period & care").performClick()
        compose.runOnIdle {assertTrue(careOpened)}
    }
}

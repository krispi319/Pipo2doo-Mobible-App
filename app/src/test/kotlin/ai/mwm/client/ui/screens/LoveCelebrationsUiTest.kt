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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
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
class LoveCelebrationsUiTest {
    @get:Rule val compose=createComposeRule()
    private val p=Preferences(myName="Alex",partnerName="Sam",myPhoto="asset:profile_mina",partnerPhoto="asset:profile_partner",started=true,togetherSince=LocalDate.now().minusDays(625).toEpochDay())
    @Test fun counterOpensMilestonesAndLoveSettingsRetainDraftAfterFailedSave() {
        var saved:LoveCelebrationSettings?=null
        var attempts=0
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true) {MyApplicationTheme {Surface(Modifier.fillMaxSize()) {
            SettingsScreen(PipoState(preferences=p),true,{}, {}, {}, {}, {},onLoveSave={s,done->saved=s;attempts++;done(if(attempts==1)"Save failed · Retry" else null)})
        }}}}
        compose.onNodeWithContentDescription("Open our milestones").performClick()
        compose.onNodeWithText("Our milestones").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v145-love-milestones.png")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Celebration settings"))
        compose.onNodeWithText("Celebration settings").performClick()
        compose.onNodeWithText("Custom").assertIsDisplayed()
        compose.onNodeWithText("Custom").performClick()
        compose.onNodeWithContentDescription("Custom love days").performSemanticsAction(SemanticsActions.SetText){it(AnnotatedString("0, 520"))}
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Use up to 20 whole numbers from 1 to 365250.").assertExists()
        compose.onNodeWithContentDescription("Custom love days").performSemanticsAction(SemanticsActions.SetText){it(AnnotatedString("520, 1000"))}
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Look & feel").performClick()
        compose.onNodeWithText("Hearts").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Your celebration note · Optional"))
        compose.onNodeWithContentDescription("Celebration note").performSemanticsAction(SemanticsActions.SetText){it(AnnotatedString("Always you ♡"))}
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v145-love-options.png")
        compose.onNodeWithText("Preview").performClick()
        compose.onNodeWithText("Always you ♡").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Hearts").assertIsSelected()
        compose.onNodeWithContentDescription("Celebration note").assertTextEquals("Always you ♡")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Save failed · Retry").assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {assertEquals(2,attempts);assertEquals(listOf(520,1000),saved?.customDays);assertEquals("Hearts",saved?.effect);assertEquals("Always you ♡",saved?.note)}
        compose.onNodeWithText("Our milestones").assertIsDisplayed()
    }
    @Test fun celebrationTabsRetainReminderEffectAndNoteAcrossNavigation() {
        var saved:LoveCelebrationSettings?=null
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true) {MyApplicationTheme {
            LoveCelebrationOptions(p,{}, {},{settings,done->saved=settings;done(null)},{},false)
        }}}
        compose.onNodeWithText("Our next celebration").assertIsDisplayed()
        compose.onNodeWithText("Reminders").performClick()
        compose.onNodeWithContentDescription("Love-day reminders").performClick()
        compose.onNodeWithText("Enable notifications").assertExists()
        compose.onNodeWithText("Look & feel").performClick()
        compose.onNodeWithText("Soft glow").performClick()
        compose.onNodeWithContentDescription("Celebration note").performSemanticsAction(SemanticsActions.SetText){it(AnnotatedString("Our next adventure"))}
        compose.onNodeWithText("Celebrate",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Custom").assertIsDisplayed()
        compose.onNodeWithText("Reminders").performClick()
        compose.onNodeWithContentDescription("Love-day reminders").assertIsOn()
        compose.onNodeWithText("Look & feel").performClick()
        compose.onNodeWithText("Soft glow").assertIsSelected()
        compose.onNodeWithContentDescription("Celebration note").assertTextEquals("Our next adventure")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {assertTrue(saved!!.reminders);assertEquals("Glow",saved!!.effect);assertEquals("Our next adventure",saved!!.note)}
    }
    @Test fun actualCelebrationUsesSelectedEffectAndLiteralNote() {
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true) {MyApplicationTheme {ApprovedDesign {
            Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {LoveCelebrationDay(p.copy(loveCelebrations=LoveCelebrationSettings(note="I choose YOU ♡",effect="Cats")),LoveMilestone(p.togetherSince+700,700)) {}}
        }}}}
        compose.onNodeWithText("I choose YOU ♡").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v145-love-day.png")
    }
    @Test fun automaticMilestoneShowsOnceAndDoesNotReopenAfterDismissal() {
        val context=org.robolectric.RuntimeEnvironment.getApplication()
        context.getSharedPreferences("pipo_love_${AccountScope.current(context)}",0).edit().clear().commit()
        var current by mutableStateOf(p.copy(togetherSince=LocalDate.now().minusDays(100).toEpochDay()))
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true) {MyApplicationTheme {
            LoveCelebrationAutoHost(current,true,"auto-test")
        }}}
        compose.onNodeWithText("Lovely ♡").assertIsDisplayed().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle {current=current.copy(myName="Alex New")}
        compose.onNode(isDialog()).assertDoesNotExist()
    }
}

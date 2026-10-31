package ai.mwm.client.ui.screens

import ai.mwm.client.cloud.LocalPartnerConnection
import ai.mwm.client.cloud.PartnerConnection
import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.MyApplicationTheme
import ai.mwm.client.ui.theme.AppTheme
import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertFalse
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[28],application=Application::class,qualifiers="w360dp-h800dp-xxhdpi")
class AssigneeAvatarPreviewTest {
    @get:Rule val compose=createComposeRule()
    private val preferences=Preferences(myName="Alexandra Catherine",partnerName="Christopher Alexander",
        myPhoto="asset:profile_mina",partnerPhoto="asset:profile_partner")
    private val connection=PartnerConnection(uid="me",partnerUid="partner",spaceId="preview",role="user_1",online=true,authenticated=true,partnerAvatar=2)
    private val task=Task(id="task",title="Water the plants",owner=Owner.Me,listId="home",minute=390,reminder=true)
    private val state=PipoState(tasks=listOf(task),lists=listOf(SharedList(id="home",name="Home")),preferences=preferences)

    @Test fun taskDetailUsesAvatarsAndStillChangesAssignment() {
        compose.setContent {CompositionLocalProvider(LocalPartnerConnection provides connection) {MyApplicationTheme {
            var selected by remember {mutableStateOf(task)}
            Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {
                TaskDetailScreen(selected,state,{}, {}, {}, {_,edited,done->selected=edited;done(null)}, {done->done(null)}, {})
            }
        }}}
        compose.onNodeWithText("Whose is it?").assertIsDisplayed()
        compose.onNodeWithContentDescription("Assign to ${preferences.myName}").assertIsSelected()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/assignee-detail.png")
        compose.onNodeWithContentDescription("Assign to ${preferences.partnerName}").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Assign to Together").performClick().assertIsSelected()
    }

    @Test fun taskEditorUsesAvatarsWithoutLongNamesInsideCircles() {
        compose.setContent {CompositionLocalProvider(LocalPartnerConnection provides connection) {MyApplicationTheme {
            TaskEditor(task,state,null,{}, {_,_,done->done(null)}, {_,done->done(null)}, {}, {}, {})
        }}}
        compose.onNodeWithContentDescription("Assign to ${preferences.myName}").assertIsSelected()
        compose.onNodeWithContentDescription("Assign to ${preferences.partnerName}").performClick().assertIsSelected()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/assignee-editor.png")
    }

    @Test fun presetsAndEmptyProfilesRemainSelectable() {
        val p=preferences.copy(myPhoto="",partnerPhoto="",avatar=0)
        compose.setContent {CompositionLocalProvider(LocalPartnerConnection provides connection.copy(partnerAvatar=-1)) {MyApplicationTheme {
            var selected by remember {mutableStateOf(Owner.Me)}
            TaskOwnerCircles(selected,p) {selected=it}
        }}}
        compose.onNodeWithContentDescription("Assign to ${p.partnerName}").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Assign to Together").performClick().assertIsSelected()
    }

    @Test @Config(qualifiers="w320dp-h800dp-xxhdpi") fun timeChipFitsLargerText() {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalPartnerConnection provides connection,LocalDensity provides Density(density.density,1.5f)) {MyApplicationTheme {
                Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {
                    TaskDetailScreen(task,state,{}, {}, {}, {_,_,done->done(null)}, {done->done(null)}, {})
                }
            }}
        }
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("By 6:30 AM").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertFalse(layouts.single().hasVisualOverflow)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/assignee-large-text.png")
    }
}

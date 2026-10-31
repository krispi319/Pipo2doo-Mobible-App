package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.MyApplicationTheme
import androidx.compose.runtime.*
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
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[28],application=Application::class,qualifiers="w360dp-h800dp-xxhdpi")
class TaskReleaseUiTest {
    @get:Rule val compose=createComposeRule()
    private val list=SharedList(id="home",name="Home")
    private val state=PipoState(lists=listOf(list),preferences=Preferences(myName="Alex",partnerName="Sam",started=true))

    @Test fun compactTodayKeepsTasksVisibleAndLocketExpandable() {
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true) {MyApplicationTheme {HomeScreen()}}}
        compose.onNodeWithText("Still to do").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show couple overview").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v142-today.png")
    }

    @Test fun quickAddDraftSurvivesMoreOptionsAndSaves() {
        var saved:Task?=null
        compose.setContent {MyApplicationTheme {
            TaskEditor(null,state,null,{}, {task,_,done->saved=task;done(null)}, {_,done->done(null)}, {}, {}, {})
        }}
        compose.onNodeWithText("What needs doing?").performTextInput("Buy oat milk")
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v142-quick-add.png")
        compose.onNodeWithText("More options").performClick()
        compose.onNodeWithText("Buy oat milk").assertExists()
        compose.onNodeWithText("Save task").performClick()
        compose.runOnIdle {assertEquals("Buy oat milk",saved?.title);assertEquals("home",saved?.listId)}
    }

    @Test fun repeatScopeIsPassedToSave() {
        val task=Task(title="Watre plants",repeat=Repeat.Daily,listId=list.id)
        var savedScope:TaskEditScope?=null
        compose.setContent {MyApplicationTheme {
            TaskEditor(task,state.copy(tasks=listOf(task)),null,{}, {_,scope,done->savedScope=scope;done(null)}, {_,done->done(null)}, {}, {}, {})
        }}
        compose.onNodeWithText("This and future occurrences").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v142-repeat-editor.png")
        compose.onNodeWithText("Save task").performClick()
        compose.runOnIdle {assertEquals(TaskEditScope.Future,savedScope)}
    }
}

package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.MyApplicationTheme
import android.app.Application
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[28],application=Application::class,qualifiers="w360dp-h800dp-xxhdpi")
class RecurringSeriesUiTest {
    @get:Rule val compose=createComposeRule()
    private val root=Task(id="root",title="Fix pipo2doo",repeat=Repeat.Daily,listId="home")
    private val child=root.copy(id="child",seriesId=root.id,parentId=root.id,day=root.day+1,repeat=Repeat.Once)
    private val history=root.copy(id="history",seriesId=root.id,parentId=root.id,day=root.day-1,status=TaskStatus.Done,completedAt=1)
    private val state=PipoState(tasks=listOf(root,child,history,root.copy(id="unrelated",repeat=Repeat.Once)),
        lists=listOf(SharedList(id="home",name="Home")),preferences=Preferences(myName="Alex",partnerName="Sam",myPhoto="asset:profile_mina",partnerPhoto="asset:profile_partner",started=true))

    private fun verifyOccurrenceRecovery(change:()->Unit,expected:(Task)->Boolean) {
        var attempts=0;var closed=0;var savedScope:TaskEditScope?=null
        var result=state
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true) {MyApplicationTheme {
            TaskEditor(child,state,null,{closed++},{task,scope,done->
                attempts++;savedScope=scope
                if(attempts==1)done("Offline · Retry")
                else {result=state.withTaskEdit(task,scope);done(null)}
            },{_,done->done(null)}, {}, {}, {})
        }}}
        compose.onNodeWithContentDescription("All unfinished occurrences · 2").performClick()
        change()
        compose.onNodeWithText("Save task").performClick()
        compose.onNodeWithText("Save changes to this task?").assertIsDisplayed()
        compose.runOnIdle {assertEquals(0,attempts)}
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithContentDescription("All unfinished occurrences · 2").assertIsSelected()
        compose.onNodeWithText("Save task").performClick()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/v146-save-occurrence-recovery.png")
        compose.onNodeWithText("Save this task").performClick()
        compose.onNodeWithText("Offline · Retry").assertIsDisplayed()
        compose.onNodeWithContentDescription("Only this occurrence").assertIsSelected()
        compose.runOnIdle {assertEquals(0,closed);assertEquals(TaskEditScope.Occurrence,savedScope)}
        compose.onNodeWithText("Save task").performClick()
        compose.runOnIdle {
            assertEquals(2,attempts);assertEquals(1,closed)
            assertTrue(expected(result.tasks.single {it.id==child.id}))
            assertEquals(state.tasks.filter {it.id!=child.id},result.tasks.filter {it.id!=child.id})
        }
    }
    @Test fun seriesDateChangeCanSaveOneTaskAfterConfirmationAndRetry() = verifyOccurrenceRecovery(
        change={compose.onNodeWithText("Today").performScrollTo().performClick()},
        expected={it.day==root.day}
    )
    @Test fun seriesReminderChangeCanSaveOneTaskAfterConfirmationAndRetry() = verifyOccurrenceRecovery(
        change={compose.onNodeWithContentDescription("Gentle reminder").performScrollTo().performClick()},
        expected={!it.reminder}
    )
    @Test fun seriesRepeatChangeCanSaveOneTaskAfterConfirmationAndRetry() = verifyOccurrenceRecovery(
        change={compose.onNodeWithText("Weekly").performScrollTo().performClick()},
        expected={it.repeat==Repeat.Weekly}
    )

    @Test fun existingOnceOccurrenceOffersWholeSeriesEditAndPassesItsScope() {
        var scope:TaskEditScope?=null
        var submitted:Task?=null
        var latest by mutableStateOf(child)
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true) {MyApplicationTheme {
            TaskEditor(latest,state.copy(tasks=state.tasks.map {if(it.id==latest.id)latest else it}),null,{}, {task,selected,done->submitted=task;scope=selected;done(null)}, {_,done->done(null)}, {}, {}, {})
        }}}
        compose.onNodeWithContentDescription("All unfinished occurrences · 2").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("About series edits").performClick()
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithContentDescription("All unfinished occurrences · 2").assertIsSelected()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextClearance()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Improve Pipo2doo")
        compose.runOnIdle {latest=child.copy(owner=Owner.You)}
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v144-series-edit.png")
        compose.onNodeWithText("Save task").performClick()
        compose.runOnIdle {assertEquals(TaskEditScope.AllUnfinished,scope);assertEquals(Owner.You,submitted?.owner);assertEquals("Improve Pipo2doo",submitted?.title)}
    }

    @Test fun seriesDeleteShowsCountKeepsSelectionAfterFailureAndAllowsRetry() {
        var attempts=0;var requested:TaskDeleteScope?=null
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true) {MyApplicationTheme {
            TaskDetailScreen(root,state,{}, {}, {}, {_,_,done->done(null)}, {done->done("Unexpected single delete")}, {},onDeleteSeries={scope,done->
                requested=scope;attempts++;done(if(attempts==1)"Network unavailable · Retry" else null)
            })
        }}}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Delete task"))
        compose.onNodeWithText("Delete task").performClick()
        compose.onNodeWithContentDescription("Entire series").performClick().assertIsSelected()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {assertEquals(0,attempts)}
        compose.onNodeWithText("Delete task").performClick()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/v144-series-delete.png")
        compose.onNodeWithText("Delete 2 tasks").performClick()
        compose.onNodeWithText("Network unavailable · Retry").assertIsDisplayed()
        compose.onNodeWithContentDescription("Entire series").assertIsSelected()
        compose.onNodeWithText("Delete 2 tasks").performClick()
        compose.runOnIdle {assertEquals(TaskDeleteScope.Series,requested);assertEquals(2,attempts)}
        compose.onNodeWithText("Delete series?").assertDoesNotExist()
    }
}

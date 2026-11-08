package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.MyApplicationTheme
import android.app.Application
import android.app.TimePickerDialog
import android.content.DialogInterface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[28],application=Application::class,qualifiers="w360dp-h800dp-xxhdpi")
class RecurringTimeUiTest {
    @get:Rule val compose=createComposeRule()
    private fun verifyTimePickerSave(scope:TaskEditScope,alreadyCorrected:Boolean) {
        val today=LocalDate.now().toEpochDay()
        val root=Task(id="root",title="Task A",day=today,minute=1170,repeat=Repeat.Daily,listId="home")
        val child=root.copy(id="child",day=today+1,parentId=root.id,seriesId=root.id,minute=if(alreadyCorrected)1169 else 1170)
        val later=child.copy(id="later",day=today+2,minute=1170)
        val history=child.copy(id="history",day=today-1,status=TaskStatus.Done,completedAt=1,minute=1170)
        val s=PipoState(tasks=listOf(root,child,later,history),lists=listOf(SharedList(id="home",name="Home")))
        var attempts=0;var closed=0;var result=s;var submittedScope:TaskEditScope?=null
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true) {MyApplicationTheme {
            TaskEditor(child,s,null,{closed++},{task,selected,done->
                attempts++;submittedScope=selected
                if(attempts==1)done("Offline · Retry")
                else {result=s.withTaskEdit(task,selected);done(null)}
            },{_,done->done(null)}, {}, {}, {})
        }}}
        compose.onNodeWithContentDescription(if(scope==TaskEditScope.AllUnfinished)"All unfinished occurrences · 3" else "This and future occurrences").performClick()
        compose.onAllNodesWithText(timeText(child.minute)).onFirst().performScrollTo().performClick()
        compose.runOnIdle {
            val picker=ShadowDialog.getLatestDialog() as TimePickerDialog
            picker.updateTime(19,29)
            picker.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.onNodeWithText("Save task").performClick()
        compose.onNodeWithText("Save changes to this task?").assertDoesNotExist()
        compose.onNodeWithText("Offline · Retry").assertIsDisplayed()
        compose.onNodeWithText("Save task").performClick()
        compose.runOnIdle {
            assertEquals(2,attempts);assertEquals(1,closed);assertEquals(scope,submittedScope)
            assertEquals(1169,result.tasks.single {it.id==child.id}.minute)
            assertEquals(1169,result.tasks.single {it.id==later.id}.minute)
            assertEquals(if(scope==TaskEditScope.AllUnfinished)1169 else 1170,result.tasks.single {it.id==root.id}.minute)
            assertEquals(1170,result.tasks.single {it.id==history.id}.minute)
        }
    }
    @Test fun allOpenTimePickerUpdatesEveryUnfinishedOccurrenceAndRetainsScopeAfterFailure() = verifyTimePickerSave(TaskEditScope.AllUnfinished,false)
    @Test fun allOpenCanResyncAnAlreadyCorrectedTimeThroughThePicker() = verifyTimePickerSave(TaskEditScope.AllUnfinished,true)
    @Test fun fromHereTimePickerLeavesEarlierOpenTasksUnchanged() = verifyTimePickerSave(TaskEditScope.Future,false)
}

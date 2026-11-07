package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.cloud.LocalPartnerConnection
import ai.mwm.client.cloud.PartnerConnection
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[28],application=Application::class,qualifiers="w360dp-h640dp-xxhdpi")
class TaskCancellationUiTest {
    @get:Rule val compose=createComposeRule()
    private val task=Task(id="task",title="Fix Pipo2doo",repeat=Repeat.Daily)
    private val state=PipoState(tasks=listOf(task),preferences=Preferences(myName="Alex",partnerName="Sam"))
    @Test fun soloModesShowCancelForOwnTogetherAndOtherAssignments() {
        val current=mutableStateOf(task)
        val connection=mutableStateOf(PartnerConnection())
        var saves=0
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides connection.value,LocalTaskCancel provides {_,_,_,_->saves++}) {
            MyApplicationTheme {Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {
                Column(Modifier.padding(20.dp)) {TaskActionPills(current.value,state.copy(tasks=listOf(current.value)))}
            }}
        }}
        val soloModes=listOf(PartnerConnection(),PartnerConnection(uid="solo",authenticated=true),
            PartnerConnection(uid="solo",spaceId="own-space",role="user_1",authenticated=true,online=true),
            PartnerConnection(uid="solo",spaceId="own-space",role="user_2",authenticated=true,online=true))
        for(mode in soloModes)for(owner in Owner.entries) {
            compose.runOnIdle {connection.value=mode;current.value=task.copy(owner=owner)}
            compose.onNodeWithText("Cancel").assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText("Cancel task?").assertIsDisplayed()
            compose.onNodeWithText("Keep").performClick()
            if(owner==Owner.Me)compose.onNodeWithText("Poke").assertDoesNotExist()
        }
        assertEquals(0,saves)
    }
    @Test fun soloCancellationSavesReloadsAndMovesFromTodayToHistory() {
        val repository=PipoRepository.get(RuntimeEnvironment.getApplication())
        repository.activateAccount("");repository.restoreLocal()
        repository.change {PipoState(preferences=it.preferences.copy(myName="Solo",started=true))}
        val solo=Task(title="Solo cancellation fixture",owner=Owner.Me,repeat=Repeat.Once)
        repository.saveTask(solo)
        val history=mutableStateOf(false)
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides PartnerConnection(),
            LocalTaskCancel provides {item,scope,reason,result->repository.cancelTask(item.id,scope,"",reason);result(null)}) {
            MyApplicationTheme {Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {
                val actual=repository.state.collectAsState().value
                if(history.value)ProgressScreen(actual,{}, {}) else TodayScreen(actual,{}, {},onReminders={})
            }}
        }}
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(solo.title))
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Cancellation reason").performTextInput("No longer needed")
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText(solo.title).assertDoesNotExist()
        compose.runOnIdle {
            repository.restoreLocal()
            val saved=repository.state.value.tasks.single {it.id==solo.id}
            assertTrue(saved.cancelled);assertFalse(saved.done);assertFalse(saved.isOwnReminder())
            assertEquals("No longer needed",saved.cancelReason)
            history.value=true
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Cancelled by Solo"))
        compose.onNodeWithText(solo.title).assertExists()
        compose.onNodeWithContentDescription("Cancelled").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v145-solo-cancel-history.png")
    }
    @Test fun pairedMembersSeeCancelForEveryAssigneeAndCanReviewWithoutCancelling() {
        val current=mutableStateOf(task)
        val connection=mutableStateOf(PartnerConnection(spaceId="ours",uid="a",partnerUid="b",role="user_1",online=true,authenticated=true))
        var saves=0
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides connection.value,LocalTaskCancel provides {_,_,_,_->saves++}) {
            MyApplicationTheme {Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {
                Column(Modifier.padding(20.dp)) {
                    val pairedState=state.copy(tasks=listOf(current.value))
                    TaskRow(current.value,pairedState,{}, {},aligned=false) {TaskActionPills(current.value,pairedState)}
                }
            }}
        }}
        for(role in listOf("user_1","user_2")) for(owner in Owner.entries) {
            compose.runOnIdle {
                connection.value=connection.value.copy(role=role,uid=if(role=="user_1")"a" else "b",partnerUid=if(role=="user_1")"b" else "a")
                current.value=task.copy(owner=owner)
            }
            compose.onNodeWithText("Cancel").assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText("Cancel task?").assertIsDisplayed()
            compose.onNodeWithText("Keep").performClick()
        }
        assertEquals(0,saves)
    }
    @Test fun pillsMatchAndKeepDismissesWithoutSaving() {
        var calls=0
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalTaskCancel provides {_,_,_,_->calls++}) {MyApplicationTheme {
            Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {Column(Modifier.padding(20.dp)) {TaskRow(task,state,{}, {},aligned=false) {TaskActionPills(task,state)}}}
        }}}
        val poke=compose.onNodeWithText("Poke").fetchSemanticsNode().boundsInRoot
        val cancel=compose.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot
        assertEquals(poke.height,cancel.height,0.1f);assertTrue(cancel.left>poke.right)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v145-cancel-task-card.png")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("This date").assertExists()
        compose.onNodeWithText("Stop series").assertExists()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/v145-cancel-dialog.png")
        compose.onNodeWithText("Keep").performClick()
        compose.onNodeWithText("Cancel task?").assertDoesNotExist()
        assertEquals(0,calls)
    }
    @Test fun scopeReasonAndFailureStayReviewableUntilConfirmedSave() {
        var result:((String?)->Unit)?=null
        var selected:TaskCancelScope?=null
        var reason=""
        compose.setContent {CompositionLocalProvider(LocalTaskCancel provides {_,scope,note,done->selected=scope;reason=note;result=done}) {MyApplicationTheme {
            TaskCancellationDialog(task,state,{})
        }}}
        compose.onNodeWithText("Stop series").performClick()
        compose.onNodeWithContentDescription("Cancellation reason").performTextInput("No longer needed")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(TaskCancelScope.Future,selected);assertEquals("No longer needed",reason)
        compose.onNodeWithText("Saving…").assertExists()
        compose.runOnIdle {result?.invoke("Offline · Try again")}
        compose.onNodeWithText("Offline · Try again").assertExists()
        compose.onNodeWithText("Cancel").assertIsEnabled()
    }
    @Test fun cancelledHistoryHasSignAndDoesNotShowTaskActions() {
        val cancelled=task.copy(status=TaskStatus.Done,cancelledAt=1000,cancelledByName="Alex")
        compose.setContent {MyApplicationTheme {Column {CancelledHistoryRow(cancelled,state,{});TaskActionPills(cancelled,state)}}}
        compose.onNodeWithContentDescription("Cancelled").assertExists()
        compose.onNodeWithText("Cancelled by Alex").assertExists()
        compose.onNodeWithText("Poke").assertDoesNotExist()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }
    @Test fun progressRetainsCompletedRowsBesideCancellationHistory() {
        var opened=""
        val now=System.currentTimeMillis()
        val cancelled=task.copy(status=TaskStatus.Done,cancelledAt=now,cancelledByName="Alex")
        val done=Task(id="done",title="Water the plants",owner=Owner.You,status=TaskStatus.Done,completedAt=now-1000)
        compose.setContent {MyApplicationTheme {Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {
            ProgressScreen(state.copy(tasks=listOf(cancelled,done)),{opened=it},{})
        }}}
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Cancelled by Alex"))
        compose.onNodeWithText("History").assertExists()
        compose.onNodeWithContentDescription("Cancelled").assertExists()
        compose.onNodeWithText("Done by Sam").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v145-cancel-progress-history.png")
        compose.onNodeWithText("Water the plants").performClick()
        assertEquals("done",opened)
    }
}

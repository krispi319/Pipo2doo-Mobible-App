package ai.mwm.client.ui.screens

import ai.mwm.client.cloud.*
import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.*
import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w360dp-h800dp-xxhdpi")
class TaskDetailSaveUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun failedAssignmentShowsRetryAndKeepsTheIntendedChange() {
        var response:((String?)->Unit)?=null
        var attempts=0
        var intended:Task?=null
        val original=Task(id="task",title="Plants",owner=Owner.Me,listId="home")
        val prefs=Preferences(myName="Alex",partnerName="Sam")
        val connection=PartnerConnection(uid="me",partnerUid="partner",spaceId="space",role="user_1",authenticated=true,online=true)
        compose.setContent {CompositionLocalProvider(LocalPartnerConnection provides connection) {MyApplicationTheme {
            var task by remember {mutableStateOf(original)}
            TaskDetailScreen(task,PipoState(tasks=listOf(task),preferences=prefs),{}, {}, {}, {base,edit,done->
                attempts++;intended=edit
                response={failure->if(failure==null)task=mergeTaskDetailEdit(task,base,edit);done(failure)}
            }, {done->done(null)}, {})
        }}}
        compose.onNodeWithContentDescription("Assign to Sam").performClick()
        compose.onNodeWithText("Saving change…").assertExists()
        compose.runOnIdle {assertEquals(Owner.You,intended?.owner);response!!("Offline · Please reconnect")}
        compose.onNodeWithText("Offline · Please reconnect").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle {assertEquals(2,attempts);assertEquals(Owner.You,intended?.owner);response!!(null)}
        compose.onNodeWithText("Saved to shared space").assertExists()
        compose.onNodeWithContentDescription("Assign to Sam").assertIsSelected()
    }
}

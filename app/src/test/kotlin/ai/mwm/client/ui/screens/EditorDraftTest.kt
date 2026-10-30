package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test

class EditorDraftTest {
    private val scope=object:SaverScope {override fun canBeSaved(value:Any)=true}
    @Test fun completeTaskDraftSurvivesRecreationWithSameId() {
        val task=Task(id="stable-id",title="Dinner 🌷",owner=Owner.You,day=20756,minute=1110,listId="shared-list",note="Nhớ mua trà\nTwo lines",tags=listOf("tea, coffee","🌿"),repeat=Repeat.Daily,reminder=true,offset=30,status=TaskStatus.Todo,assigneeUid="partner-id",scheduledAt=1793377800000L,scheduleZone="Europe/Berlin",displayZone="Asia/Kolkata",seriesId="daily-series")
        val saved=with(TaskDraftSaver){scope.save(task)}!!
        assertEquals(task,TaskDraftSaver.restore(saved))
    }
    @Test fun untimedTaskDraftKeepsAnytimeAndNoNudge() {
        val task=Task(id="stable-id",title="Rest",minute=null,reminder=false,repeat=Repeat.Once)
        assertEquals(task,TaskDraftSaver.restore(with(TaskDraftSaver){scope.save(task)}!!))
    }
    @Test fun selectedListPicturesKeepExactPathsAndOrder() {
        val pictures=listOf("/private/Pictures/nhớ mua trà.jpg","cloud:owner/photo.jpg")
        assertEquals(pictures,ListPicturesSaver.restore(with(ListPicturesSaver){scope.save(pictures)}!!))
    }
    @Test fun removingEveryPictureStaysEmptyAfterRecreation() {
        val saved=with(ListPicturesSaver){scope.save(emptyList())}
        assertNotNull(saved)
        assertEquals(emptyList<String>(),ListPicturesSaver.restore(saved!!))
    }
}

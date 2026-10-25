package ai.mwm.client.data

import org.junit.Assert.*
import org.junit.Test

class PendingTaskStatusTest {
    private val scope="account:space:one"
    private val a=Task(id="a",title="A",repeat=Repeat.Daily)
    private val b=Task(id="b",title="B")
    private val confirmed=PipoState(tasks=listOf(a,b),lists=listOf(SharedList(id="list",name="Home")),tags=listOf(Tag("tag","Tag")))
    private fun intent(task:String,status:TaskStatus=TaskStatus.Done,id:String=task+status.name)=PendingTaskStatus(id,task,status,1234)
    private fun batch(vararg items:PendingTaskStatus)=PendingTaskBatch(scope,items.toList())
    @Test fun checkboxChangesBeforeNetworkConfirmation() {
        val shown=withPendingTaskStatuses(confirmed,batch(intent("a")),scope)
        assertTrue(shown.tasks.first().done)
        assertFalse(confirmed.tasks.first().done)
    }
    @Test fun severalCheckboxesDoNotWaitForEachOther() {
        val shown=withPendingTaskStatuses(confirmed,batch(intent("a"),intent("b")),scope)
        assertTrue(shown.tasks.all {it.done})
    }
    @Test fun rapidReopenWinsVisuallyButRetainsWriteOrder() {
        val q=batch(intent("a"),intent("a",TaskStatus.Todo))
        assertFalse(withPendingTaskStatuses(confirmed,q,scope).tasks.first().done)
        assertEquals(listOf(TaskStatus.Done,TaskStatus.Todo),q.items.map {it.status})
    }
    @Test fun acknowledgingFirstTapKeepsLaterTap() {
        val q=batch(intent("a"),intent("a",TaskStatus.Todo))
        val next=q.acknowledge(scope,q.items.first().id)
        assertEquals(1,next.items.size)
        assertEquals(TaskStatus.Todo,next.items.single().status)
    }
    @Test fun failedTaskRollsBackWithoutRollingBackOtherTasks() {
        val q=batch(intent("a"),intent("b"))
        val remaining=q.acknowledge(scope,q.items.first().id)
        val shown=withPendingTaskStatuses(confirmed,remaining,scope)
        assertFalse(shown.tasks[0].done)
        assertTrue(shown.tasks[1].done)
    }
    @Test fun staleAcknowledgementCannotRemoveNewerOperation() {
        val q=batch(intent("a",id="new"))
        assertEquals(q,q.acknowledge(scope,"old"))
    }
    @Test fun otherAccountCannotSeePendingChecks() {
        assertEquals(confirmed,withPendingTaskStatuses(confirmed,batch(intent("a")),"another-account:space:one"))
    }
    @Test fun otherPartnerSpaceCannotSeePendingChecks() {
        assertEquals(confirmed,withPendingTaskStatuses(confirmed,batch(intent("a")),"account:space:two"))
    }
    @Test fun wrongScopeCannotAcknowledgeOperation() {
        val q=batch(intent("a"))
        assertEquals(q,q.acknowledge("other",q.items.first().id))
    }
    @Test fun deletedTaskIsNeverResurrected() {
        val current=confirmed.copy(tasks=listOf(b))
        assertEquals(current,withPendingTaskStatuses(current,batch(intent("a")),scope))
    }
    @Test fun pendingCheckDoesNotCreateRepeatsOrHistory() {
        val shown=withPendingTaskStatuses(confirmed,batch(intent("a")),scope)
        assertEquals(2,shown.tasks.size)
        assertEquals(a.events,shown.tasks.first().events)
        assertEquals(a.parentId,shown.tasks.first().parentId)
    }
    @Test fun committedServerStateReplacesOverlayAfterAcknowledgement() {
        val q=batch(intent("a"));val saved=confirmed.copy(tasks=listOf(a.copy(status=TaskStatus.Done,completedAt=1500),b))
        assertEquals(saved,withPendingTaskStatuses(saved,q.acknowledge(scope,q.items.first().id),scope))
    }
    @Test fun unrelatedDataRemainsIdentical() {
        val shown=withPendingTaskStatuses(confirmed,batch(intent("a")),scope)
        assertSame(confirmed.preferences,shown.preferences)
        assertSame(confirmed.lists,shown.lists)
        assertSame(confirmed.tags,shown.tags)
        assertSame(confirmed.notes,shown.notes)
    }
}

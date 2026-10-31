package ai.mwm.client.data

import org.junit.Assert.*
import org.junit.Test

class TaskDetailEditTest {
    private val base=Task(id="task",title="Plants",owner=Owner.Me,day=20000,minute=390,note="Private",tags=listOf("one"))
    @Test fun retryOnlyReappliesTheRequestedField() {
        val current=base.copy(title="Partner corrected title",status=TaskStatus.Done,completedAt=123,listId="garden",tags=listOf("two"))
        val saved=mergeTaskDetailEdit(current,base,base.copy(owner=Owner.You))
        assertEquals(current.copy(owner=Owner.You),saved)
    }
    @Test fun scheduleAndPersonalEditsRetainNewerSharedData() {
        val current=base.copy(title="New title",owner=Owner.Together)
        assertEquals(current.copy(minute=600),mergeTaskDetailEdit(current,base,base.copy(minute=600)))
        assertEquals(current.copy(reminder=false),mergeTaskDetailEdit(current,base,base.copy(reminder=false)))
        assertEquals(current.copy(note="Updated note"),mergeTaskDetailEdit(current,base,base.copy(note="Updated note")))
    }
    @Test fun changingTimeToAnytimeIsPreserved() {
        assertNull(mergeTaskDetailEdit(base,base,base.copy(minute=null)).minute)
    }
    @Test fun aDifferentTaskCannotReceiveTheRetry() {
        assertTrue(runCatching {mergeTaskDetailEdit(base.copy(id="other"),base,base.copy(owner=Owner.You))}.isFailure)
    }
}

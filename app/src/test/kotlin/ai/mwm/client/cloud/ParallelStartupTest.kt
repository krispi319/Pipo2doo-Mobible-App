package ai.mwm.client.cloud

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ParallelStartupTest {
    @Test fun bothReadsStartBeforeEitherCompletesAndRetainReminderValues()=runBlocking {
        val recordsStarted=CompletableDeferred<Unit>()
        val remindersStarted=CompletableDeferred<Unit>()
        val result=withTimeout(2000) {
            withParallelReminders(loadRecords={
                recordsStarted.complete(Unit);remindersStarted.await();"tasks"
            },loadReminders={
                remindersStarted.complete(Unit);recordsStarted.await();mapOf("task" to (false to 15))
            })
        }
        assertEquals("tasks",result.first)
        assertEquals(false to 15,result.second["task"])
    }
    @Test fun reminderReadFailureRetainsExistingEmptyFallback()=runBlocking {
        val result=withParallelReminders(loadRecords={"tasks"},loadReminders={error("Offline")})
        assertEquals("tasks",result.first);assertTrue(result.second.isEmpty())
    }
    @Test fun mandatoryRecordFailureCancelsReminderRead()=runBlocking {
        val started=CompletableDeferred<Unit>()
        var canceled=false
        val failure=runCatching {withParallelReminders(loadRecords={started.await();error("Membership denied")},loadReminders={
            started.complete(Unit)
            try {awaitCancellation()}finally {canceled=true}
        })}.exceptionOrNull()
        assertEquals("Membership denied",failure?.message);assertTrue(canceled)
    }
    @Test fun cancellationIsNotReportedAsSuccessfulReminderFallback()=runBlocking {
        val failure=runCatching {withParallelReminders(loadRecords={"tasks"},loadReminders={throw CancellationException("Stopped")})}.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }
}

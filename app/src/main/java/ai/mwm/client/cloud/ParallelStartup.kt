package ai.mwm.client.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Both reads begin together; records remain mandatory and reminder fallback stays unchanged. */
internal suspend fun <T> withParallelReminders(
    loadRecords:suspend ()->T,
    loadReminders:suspend ()->Map<String,Pair<Boolean,Int>>
):Pair<T,Map<String,Pair<Boolean,Int>>> = coroutineScope {
    val reminders=async {
        try {loadReminders()}catch(e:CancellationException) {throw e}catch(_:Exception) {emptyMap()}
    }
    val records=async {loadRecords()}
    records.await() to reminders.await()
}

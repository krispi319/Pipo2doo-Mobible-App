package ai.mwm.client.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Pending IDs/statuses only; no task titles, photos, tokens, or partner notes.
 * Account + workspace partitioning prevents replay into a different couple. */
class TaskStatusOutbox(context: Context) {
    private val directory=context.noBackupFilesDir
    private val mutable=MutableStateFlow(PendingTaskBatch())
    val updates: StateFlow<PendingTaskBatch> = mutable
    private fun file(scope: String)=AtomicFile(File(directory,"task_status_outbox_${AccountScope.key(scope)}.json"))
    private fun read(scope: String): PendingTaskBatch {
        if(scope.isBlank()) return PendingTaskBatch()
        val source=file(scope)
        if(!source.baseFile.exists()) return PendingTaskBatch(scope)
        val rows=JSONArray(source.openRead().bufferedReader().use {it.readText()})
        return PendingTaskBatch(scope,(0 until rows.length()).map {i -> val row=rows.getJSONObject(i)
            PendingTaskStatus(row.getString("id"),row.getString("task"),TaskStatus.valueOf(row.getString("status")),row.getLong("at"))
        })
    }
    private fun persist(batch: PendingTaskBatch) {
        if(batch.scope.isBlank()) return
        val target=file(batch.scope)
        if(batch.items.isEmpty()) {target.delete();return}
        val rows=JSONArray(batch.items.map {JSONObject().put("id",it.id).put("task",it.taskId).put("status",it.status.name).put("at",it.at)})
        val stream=target.startWrite()
        try {stream.write(rows.toString().toByteArray());target.finishWrite(stream)}
        catch(e: Exception) {target.failWrite(stream);throw e}
    }
    @Synchronized fun activate(scope: String) {
        if(mutable.value.scope != scope) mutable.value=read(scope)
    }
    @Synchronized fun enqueue(scope: String, task: String, status: TaskStatus) {
        activate(scope)
        require(scope.isNotBlank()) {"Wait for your workspace to reconnect."}
        if(mutable.value.items.lastOrNull {it.taskId==task}?.status==status) return
        val next=mutable.value.copy(items=mutable.value.items+PendingTaskStatus(taskId=task,status=status,at=System.currentTimeMillis()))
        // A tap only stages memory here; the I/O worker persists before sending it.
        mutable.value=next
    }
    @Synchronized fun first(scope: String): PendingTaskStatus? = mutable.value.takeIf {it.scope==scope}?.items?.firstOrNull()
    /** Called only by the single I/O worker, never while holding the UI's memory lock. */
    fun flush(scope: String) {
        while(true) {
            val snapshot=synchronized(this) {mutable.value.takeIf {it.scope==scope}} ?: return
            persist(snapshot)
            if(synchronized(this) {mutable.value==snapshot || mutable.value.scope!=scope}) return
        }
    }
    fun acknowledge(scope: String, id: String) {
        val next=synchronized(this) {
            val before=if(mutable.value.scope==scope) mutable.value else read(scope)
            val after=before.acknowledge(scope,id)
            if(mutable.value.scope==scope) mutable.value=after
            after
        }
        if(synchronized(this) {mutable.value.scope==scope}) flush(scope) else persist(next)
    }
}

package ai.mwm.client.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest

/** Device-local partition, never a role or an authorization credential. Legacy data stays local. */
object AccountScope {
    private val mutable = MutableStateFlow("local")
    val changes = mutable.asStateFlow()
    @Volatile private var loaded = false
    @Synchronized fun current(context: Context): String {
        if (!loaded) {
            val session = File(context.noBackupFilesDir, "supabase_session.bin")
            mutable.value = if (session.exists()) File(context.noBackupFilesDir, "pipo_account_scope").takeIf { it.exists() }?.readText()?.takeIf { it.matches(Regex("[a-f0-9]{64}")) } ?: "locked" else "local"
            loaded = true
        }
        return mutable.value
    }
    fun key(uid: String): String = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
    @Synchronized fun activate(context: Context, uid: String): Boolean {
        current(context)
        val next = if(uid.isBlank()) "local" else key(uid)
        if (next == mutable.value) return false
        val marker = File(context.noBackupFilesDir, "pipo_account_scope")
        if (uid.isBlank()) marker.delete() else marker.writeText(next)
        mutable.value = next
        return true
    }
    fun preferencesName(context: Context) = when(val owner=current(context)) { "local" -> "pipo_local_v2"; else -> "pipo_account_$owner" }
}

package ai.mwm.client.cloud

import android.content.Context
import android.util.Base64
import ai.mwm.client.data.AccountScope
import ai.mwm.client.data.CareRepository
import ai.mwm.client.reminders.NotificationSounds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Private account backup, not partner sharing. No passwords, tokens or device keys leave this phone. */
object AccountBackup {
    private val lock=Mutex()
    private val messages=MutableStateFlow<Map<String,String>>(emptyMap())
    val status=messages.asStateFlow()
    @Synchronized private fun label(owner: String,section: String,value: String) {
        messages.value=messages.value+("$owner:$section" to value)
    }
    fun pending(c: Context,section: String) {
        val owner=AccountScope.current(c)
        if(owner !in listOf("local","locked")) label(owner,section,"Changes waiting for account backup")
    }
    private fun digest(value: ByteArray)=MessageDigest.getInstance("SHA-256").digest(value).joinToString("") {"%02x".format(it)}
    /** Sort object keys so wire formatting never creates a false local edit. */
    internal fun canonical(value: Any?): String = when(value) {
        null,JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",","{","}") {JSONObject.quote(it)+":"+canonical(value.get(it))}
        is JSONArray -> (0 until value.length()).joinToString(",","[","]") {canonical(value.get(it))}
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
    internal fun decision(initialized: Boolean,hasLocal: Boolean,localChanged: Boolean,base: Long,remote: Long): String = when {
        !initialized && remote>0 && hasLocal -> "conflict"
        !initialized && remote>0 -> "restore"
        initialized && remote!=base && localChanged -> "conflict"
        initialized && remote!=base -> if(remote>0) "restore" else "conflict"
        !initialized || localChanged -> "upload"
        else -> "current"
    }
    private fun mainPrefs(c: Context)=c.getSharedPreferences(AccountScope.preferencesName(c),Context.MODE_PRIVATE)
    private fun captureMain(c: Context): JSONObject {
        val p=mainPrefs(c)
        return JSONObject().put("schema",1)
            .put("state",JSONObject(p.getString("state","{}") ?: "{}"))
            .put("private_task_notes",JSONObject(p.getString("private_task_notes","{}") ?: "{}"))
            .put("personal_reminders",JSONObject(p.getString("personal_reminders","{}") ?: "{}"))
            .put("last_shared_space",p.getString("last_shared_space",""))
            .put("tone",NotificationSounds.selected(c))
    }
    private fun restoreMain(c: Context,data: JSONObject) {
        check(data.getInt("schema")==1) {"Install a newer app to read this backup."}
        check(mainPrefs(c).edit().putString("state",data.getJSONObject("state").toString())
            .putString("private_task_notes",data.getJSONObject("private_task_notes").toString())
            .putString("personal_reminders",data.getJSONObject("personal_reminders").toString())
            .putString("last_shared_space",data.optString("last_shared_space")).commit()) {"Could not restore account settings."}
        NotificationSounds.select(c,data.optString("tone","system"))
    }
    private fun careMarker(c: Context)=c.getSharedPreferences("care_space_${AccountScope.current(c)}",Context.MODE_PRIVATE)
    private fun captureCare(c: Context)=JSONObject().put("schema",1)
        .put("vault",CareRepository.get(c).exportBackup()).put("space",careMarker(c).getString("space",""))
    private fun restoreCare(c: Context,data: JSONObject) {
        check(data.getInt("schema")==1) {"Install a newer app to read this backup."}
        CareRepository.get(c).restoreBackup(data.getJSONObject("vault"))
        check(careMarker(c).edit().putString("space",data.optString("space")).commit())
    }
    /** Local pictures are embedded privately; cloud pictures keep their existing references. */
    private fun pack(c: Context,data: JSONObject): JSONObject {
        val images=JSONObject()
        val directory=File(c.noBackupFilesDir,"pictures_${AccountScope.current(c)}").canonicalFile
        fun visit(value: Any?): Any = when(value) {
            is JSONObject -> JSONObject().also { out -> value.keys().forEach {out.put(it,visit(value.get(it)))} }
            is JSONArray -> JSONArray().also {out -> (0 until value.length()).forEach {out.put(visit(value.get(it)))} }
            is String -> if(value.startsWith(directory.path+"/")) {
                val image=File(value).canonicalFile
                require(image.parentFile==directory && image.isFile && image.length() in 1..5_242_880) {"A local picture could not be backed up. Choose it again before uninstalling."}
                val bytes=image.readBytes();val hash=digest(bytes)
                images.put(hash,Base64.encodeToString(bytes,Base64.NO_WRAP));"backup-image:$hash"
            } else value
            else -> value ?: JSONObject.NULL
        }
        val out=JSONObject().put("format",1).put("data",visit(data)).put("images",images)
        require(out.toString().toByteArray().size<=18_000_000) {"Account backup exceeds 18 MB. Keep this installation; backup needs more storage support."}
        return out
    }
    private fun unpack(c: Context,payload: JSONObject): JSONObject {
        check(payload.getInt("format")==1) {"Install a newer app to restore this backup."}
        val directory=File(c.noBackupFilesDir,"pictures_${AccountScope.current(c)}").apply {mkdirs()}
        val images=payload.getJSONObject("images")
        fun visit(value: Any?): Any = when(value) {
            is JSONObject -> JSONObject().also {out -> value.keys().forEach {out.put(it,visit(value.get(it)))} }
            is JSONArray -> JSONArray().also {out -> (0 until value.length()).forEach {out.put(visit(value.get(it)))} }
            is String -> if(value.startsWith("backup-image:")) {
                val hash=value.removePrefix("backup-image:")
                require(hash.matches(Regex("[a-f0-9]{64}")))
                val bytes=Base64.decode(images.getString(hash),Base64.DEFAULT)
                require(bytes.size in 1..5_242_880 && digest(bytes)==hash) {"Backup picture verification failed."}
                val image=File(directory,"restored_$hash.jpg")
                if(!image.exists()) {
                    val atomic=android.util.AtomicFile(image);val stream=atomic.startWrite()
                    try {stream.write(bytes);atomic.finishWrite(stream)} catch(e: Exception) {atomic.failWrite(stream);throw e}
                }
                image.absolutePath
            } else value
            else -> value ?: JSONObject.NULL
        }
        return visit(payload.getJSONObject("data")) as JSONObject
    }
    suspend fun syncMain(c: Context): Boolean = sync(c,"main",{mainPrefs(c).contains("state")},{captureMain(c)},{restoreMain(c,it)})
    suspend fun syncCare(c: Context): Boolean = sync(c,"care",{CareRepository.get(c).hasBackupData()},{captureCare(c)},{restoreCare(c,it)})
    private suspend fun sync(c: Context,section: String,hasLocal: ()->Boolean,capture: ()->JSONObject,restore: (JSONObject)->Unit): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            val owner=AccountScope.current(c)
            if(owner in listOf("local","locked")) return@withLock false
            fun same()=AccountScope.current(c)==owner
            try {
                val client=SupabaseClient(c)
                val uid=client.uid()
                check(AccountScope.key(uid)==owner && same()) {"Account changed. Backup paused."}
                val metadata=c.getSharedPreferences("pipo_backup_${owner}_$section",Context.MODE_PRIVATE)
                fun snapshot(): JSONObject = synchronized(CareRepository.get(c)) {
                    check(same()) {"Account changed. Backup paused."}
                    pack(c,capture())
                }
                val localExists=synchronized(CareRepository.get(c)) {check(same());hasLocal()}
                val packed=snapshot()
                val hash=digest(canonical(packed).toByteArray())
                val remote=(client.request("/rest/v1/account_backups?user_id=eq.$uid&section=eq.$section&select=version") as JSONArray).optJSONObject(0)
                check(same()) {"Account changed. Backup paused."}
                val version=remote?.getLong("version") ?: 0L
                val action=decision(metadata.contains("version"),localExists,hash!=metadata.getString("hash",""),metadata.getLong("version",0),version)
                when(action) {
                    "conflict" -> error("Backup conflict: another phone has different data. Local data kept; do not uninstall. Use your original phone until this is resolved.")
                    "restore" -> {
                        val download=(client.request("/rest/v1/account_backups?user_id=eq.$uid&section=eq.$section&version=eq.$version&select=payload") as JSONArray).optJSONObject(0)
                            ?: error("Backup changed during restore. Please retry.")
                        check(same()) {"Account changed. Restore paused."}
                        // A foreground edit made during the GET must never be overwritten by restoration.
                        check(hash==digest(canonical(snapshot()).toByteArray())) {"Data changed during restore. Retry backup."}
                        val restoredHash=synchronized(CareRepository.get(c)) {
                            check(same()) {"Account changed. Restore paused."}
                            check(hash==digest(canonical(snapshot()).toByteArray())) {"Data changed during restore. Retry backup."}
                            val restored=unpack(c,download.getJSONObject("payload"))
                            restore(restored)
                            digest(canonical(snapshot()).toByteArray())
                        }
                        check(metadata.edit().putLong("version",version).putString("hash",restoredHash).commit())
                    }
                    "upload" -> {
                        val reply=client.rpc("save_account_backup",JSONObject().put("p_section",section).put("p_payload",packed).put("p_expected",version)) as JSONObject
                        check(same()) {"Account changed. Backup response ignored."}
                        // Keep the hash of the uploaded snapshot: a newer local edit stays dirty for the next pass.
                        check(metadata.edit().putLong("version",reply.getLong("version")).putString("hash",hash).commit())
                        check(hash==digest(canonical(snapshot()).toByteArray())) {"New changes are waiting for backup. Tap Back up now again."}
                    }
                }
                label(owner,section,"Saved to your account · "+LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
                action=="restore"
            } catch(e: CancellationException) {throw e} catch(e: Exception) {
                if(same()) label(owner,section,"Not backed up · "+(e.message ?: "Check your connection and retry."))
                false
            }
        }
    }
}

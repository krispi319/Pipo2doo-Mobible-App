package ai.mwm.client.data

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ai.mwm.client.cloud.PipoFirebase
import ai.mwm.client.cloud.NoMembership
import ai.mwm.client.cloud.connectionError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import ai.mwm.client.reminders.CareReminders

data class DayLog(val day: Long, val start: Boolean = false, val end: Boolean = false, val flow: String = "None", val cramps: String = "None", val mood: Int? = null, val cravings: Set<String> = emptySet(), val symptoms: Set<String> = emptySet(), val note: String = "")
data class CareState(val logs: List<DayLog> = emptyList(), val cycle: Int = 28, val duration: Int = 5, val checkIn: Boolean = false, val headsUp: Boolean = false, val checkTime: Int = 1200, val headsTime: Int = 540, val consent: Boolean = false, val categories: Set<String> = setOf("Mood", "Cravings", "Symptoms"), val history: Boolean = false, val includeNotes: Boolean = false, val endReminder: Boolean = false, val endTime: Int = 540, val shareSpace: String = "", val pendingShare: Boolean = false) {
    val starts get() = logs.filter { it.start }.map { it.day }.sorted()
    val intervals get() = starts.zipWithNext().filter { (a,b) -> logs.any { it.end && it.day in a until b } }.map { (a,b) -> (b-a).toInt() }.filter { it in 10..120 }.takeLast(6)
    val estimateCycle: Int get() { val values = intervals.sorted(); return if(values.size < 3) cycle else if(values.size % 2 == 1) values[values.size/2] else (values[values.size/2-1]+values[values.size/2])/2 }
    fun nextStart(today: Long = LocalDate.now().toEpochDay()): Long? { var next = starts.lastOrNull()?.plus(estimateCycle) ?: return null; while(next < today) next += estimateCycle; return next }
    fun predicted(day: Long): Boolean { val last = starts.lastOrNull() ?: return false; val delta = day-last; val occurrence=last+(delta/estimateCycle)*estimateCycle; val ended=logs.any { it.end && it.day in occurrence..day }; return delta >= 0 && !ended && delta % estimateCycle < duration && logs.none { it.day == day && (it.flow != "None" || it.start) } }
}

/** Separate vault: no backup, task sync, analytics or log statements. Fail closed if unreadable. */
class CareRepository private constructor(private val context: Context) {
    init { AccountScope.current(context) }
    private val file: File get() = File(context.noBackupFilesDir, if(AccountScope.current(context)=="local") "care_vault.bin" else "care_vault_${AccountScope.current(context)}.bin")
    /** Bind local care to a couple; keep the old encrypted vault as an archive on partner change. */
    @Synchronized fun forSpace(space: String): CareState {
        java.util.UUID.fromString(space)
        val owner=AccountScope.current(context)
        val marker=context.getSharedPreferences("care_space_$owner",Context.MODE_PRIVATE)
        val current=load()
        val previous=marker.getString("space",null) ?: current.shareSpace
        val next=current.forCouple(previous,space)
        if(next!==current) {
            val source=if(file.exists()) file else File(file.path+".bak")
            if(source.exists()) {
                val archive=File(file.path+".space_"+AccountScope.key(previous))
                if(!archive.exists()) source.copyTo(archive,overwrite=false)
            }
            save(next)
        }
        check(marker.edit().putString("space",space).commit()) {"Could not open care for this couple."}
        return next
    }
    private fun key(): SecretKey { val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }; (store.getKey("pipo_care_v1", null) as? SecretKey)?.let { return it }; return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply { init(KeyGenParameterSpec.Builder("pipo_care_v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()) }.generateKey() }
    @Synchronized fun load(): CareState { if(!file.exists() && !File(file.path+".bak").exists()) return CareState(); val bytes=android.util.AtomicFile(file).openRead().use { it.readBytes() }; require(bytes.size>12); val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))); val root=JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)); fun set(obj: JSONObject,name: String): Set<String> { val a=obj.optJSONArray(name) ?: return emptySet(); return (0 until a.length()).map { a.getString(it) }.toSet() }; val a=root.optJSONArray("logs") ?: JSONArray(); return CareState(logs=(0 until a.length()).map { val l=a.getJSONObject(it); DayLog(l.getLong("day"),l.optBoolean("start"),l.optBoolean("end"),l.optString("flow","None"),l.optString("cramps","None"),l.optInt("mood",-1).takeIf { it in 0..4 },set(l,"cravings"),set(l,"symptoms"),l.optString("note")) },cycle=root.optInt("cycle",28).coerceIn(10,120),duration=root.optInt("duration",5).coerceIn(1,14),checkIn=root.optBoolean("checkIn"),headsUp=root.optBoolean("headsUp"),checkTime=root.optInt("checkTime",1200),headsTime=root.optInt("headsTime",540),consent=root.optBoolean("consent"),categories=set(root,"categories"),history=root.optBoolean("history"),includeNotes=root.optBoolean("includeNotes"),endReminder=root.optBoolean("endReminder"),endTime=root.optInt("endTime",540),shareSpace=root.optString("shareSpace"),pendingShare=root.optBoolean("pendingShare")) }
    @Synchronized fun save(state: CareState) { val root=exportState(state); val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key()); val atomic=android.util.AtomicFile(file); val out=atomic.startWrite(); try { out.write(cipher.iv+cipher.doFinal(root.toString().toByteArray(Charsets.UTF_8)));atomic.finishWrite(out) } catch(e: Exception) {atomic.failWrite(out);throw e};ai.mwm.client.cloud.AccountBackup.pending(context,"care");CareReminders.sync(context,state) }
    @Synchronized fun hasBackupData(): Boolean = file.exists() || File(file.path+".bak").exists()
    @Synchronized fun exportBackup(): JSONObject = exportState(load())
    private fun exportState(state: CareState): JSONObject = JSONObject().put("logs",JSONArray(state.logs.map { l -> JSONObject().put("day",l.day).put("start",l.start).put("end",l.end).put("flow",l.flow).put("cramps",l.cramps).put("mood",l.mood ?: -1).put("cravings",JSONArray(l.cravings.toList())).put("symptoms",JSONArray(l.symptoms.toList())).put("note",l.note) })).put("cycle",state.cycle).put("duration",state.duration).put("checkIn",state.checkIn).put("headsUp",state.headsUp).put("checkTime",state.checkTime).put("headsTime",state.headsTime).put("consent",state.consent).put("categories",JSONArray(state.categories.toList())).put("history",state.history).put("includeNotes",state.includeNotes).put("endReminder",state.endReminder).put("endTime",state.endTime).put("shareSpace",state.shareSpace).put("pendingShare",state.pendingShare)
    @Synchronized fun restoreBackup(root: JSONObject) {
        val cloud=CareCodec.decode(root)
        save(cloud.copy(checkIn=root.optBoolean("checkIn"),headsUp=root.optBoolean("headsUp"),
            checkTime=root.optInt("checkTime",1200),headsTime=root.optInt("headsTime",540),
            consent=root.optBoolean("consent"),history=root.optBoolean("history"),includeNotes=root.optBoolean("includeNotes"),
            categories=root.optJSONArray("categories")?.let { a -> (0 until a.length()).map {a.getString(it)}.toSet() } ?: setOf("Mood","Cravings","Symptoms"),
            endReminder=root.optBoolean("endReminder"),endTime=root.optInt("endTime",540),
            shareSpace=root.optString("shareSpace"),pendingShare=root.optBoolean("pendingShare")))
    }
    companion object { @Volatile private var instance: CareRepository?=null; fun get(context: Context): CareRepository = instance ?: synchronized(this) { instance ?: CareRepository(context.applicationContext).also { instance=it } } }
}
/** Only care logs and cycle settings cross the network. Device reminders stay local. */
object CareCodec {
    fun encode(s: CareState)=JSONObject().put("cycle",s.cycle).put("duration",s.duration).put("logs",JSONArray(s.logs.map {l->JSONObject().put("day",l.day).put("start",l.start).put("end",l.end).put("flow",l.flow).put("cramps",l.cramps).put("mood",l.mood ?: -1).put("cravings",JSONArray(l.cravings.toList())).put("symptoms",JSONArray(l.symptoms.toList())).put("note",l.note)}))
    fun decode(p: JSONObject): CareState {
        fun strings(o: JSONObject,k: String): Set<String> {val a=o.optJSONArray(k) ?: JSONArray();return (0 until a.length()).map {a.getString(it)}.toSet()}
        val logs=p.optJSONArray("logs") ?: JSONArray()
        return CareState(cycle=p.optInt("cycle",28).coerceIn(10,120),duration=p.optInt("duration",5).coerceIn(1,14),logs=(0 until logs.length()).map {i->val l=logs.getJSONObject(i);DayLog(l.getLong("day"),l.optBoolean("start"),l.optBoolean("end"),l.optString("flow","None"),l.optString("cramps","None"),l.optInt("mood",-1).takeIf {it in 0..4},strings(l,"cravings"),strings(l,"symptoms"),l.optString("note"))})
    }
}
data class CareConnection(val spaceId: String="",val enabled: Boolean=false,val version: Long=0,val partner: CareState?=null,val online: Boolean=false,val busy: Boolean=false,val status: String="Connect your partner to share care.", val conflict: Boolean=false)
class CareViewModel(app: Application): AndroidViewModel(app) {
    private val repository=CareRepository.get(app)
    private val api=PipoFirebase.get(app)
    private val lock=Mutex()
    private val mutable=MutableStateFlow<CareState?>(null)
    val state: StateFlow<CareState?> = mutable
    private val remote=MutableStateFlow(CareConnection())
    val connection: StateFlow<CareConnection> = remote
    private val errors=MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = errors
    private var loadedOwner=""
    private var polling: Job?=null
    init {viewModelScope.launch(Dispatchers.IO) {
        AccountScope.changes.collect {owner->lock.withLock {
            loadedOwner="";mutable.value=null;remote.value=CareConnection();errors.value=null
            try {val local=repository.load();if(owner==AccountScope.current(app)) {mutable.value=local;loadedOwner=owner;CareReminders.sync(app,local)}} catch(_: Exception) {errors.value="Care records could not be opened. Your saved records have not been replaced."}
        }}
    }}
    private fun same(owner: String)=owner==loadedOwner && owner==AccountScope.current(getApplication())
    fun resume() {if(polling?.isActive==true)return;polling=viewModelScope.launch(Dispatchers.IO) {while(isActive) {lock.withLock {refreshLocked()};delay(8000)}}}
    fun pause() {polling?.cancel();polling=null}
    fun refresh() {viewModelScope.launch(Dispatchers.IO) {lock.withLock {refreshLocked()}}}
    private suspend fun refreshLocked() {
        val owner=loadedOwner
        if(owner.isBlank() || owner=="local" || !same(owner)) return
        try {
            val restored=ai.mwm.client.cloud.AccountBackup.syncCare(getApplication())
            if(!same(owner)) return
            if(restored) mutable.value=repository.load()
            val snapshot=api.careSnapshot()
            if(!same(owner) || AccountScope.key(snapshot.getString("uid"))!=owner)return
            val sid=snapshot.getString("spaceId");val uid=snapshot.getString("uid");val rows=snapshot.getJSONArray("entries")
            val own=(0 until rows.length()).map {rows.getJSONObject(it)}.firstOrNull {it.getString("user_id")==uid}
            val partner=(0 until rows.length()).map {rows.getJSONObject(it)}.firstOrNull {it.getString("user_id")!=uid && it.optBoolean("enabled")}
            val local=repository.forSpace(sid)
            if(!same(owner)) return
            val enabled=own?.optBoolean("enabled")==true
            val pending=local.pendingShare && local.shareSpace==sid && enabled
            val conflict=pending && (remote.value.conflict || remote.value.spaceId!=sid || remote.value.version!=(own?.optLong("version") ?: 0))
            val next=if(enabled && !pending) {val cloud=CareCodec.decode(own!!.getJSONObject("payload"));local.copy(logs=cloud.logs,cycle=cloud.cycle,duration=cloud.duration,consent=true,shareSpace=sid,pendingShare=false)}
                else local.copy(consent=enabled,shareSpace=if(enabled) sid else "",pendingShare=pending)
            if(same(owner)) {
                if(next!=local) repository.save(next)
                mutable.value=next
                remote.value=CareConnection(sid,enabled,own?.optLong("version") ?: 0,partner?.let {CareCodec.decode(it.getJSONObject("payload"))},true,false,if(pending) "Changes saved on this phone. Retry sharing to publish them." else if(enabled) "Shared with your partner · Only you can edit your records" else "Share your records when you want your partner to follow along.",conflict)
            }
        } catch(e: CancellationException) {throw e} catch(e: NoMembership) {if(same(owner)) remote.value=CareConnection()} catch(e: Exception) {if(same(owner)) remote.value=remote.value.copy(online=false,partner=null,busy=false,status=connectionError(e))}
    }
    fun update(transform: (CareState)->CareState) {
        val owner=AccountScope.current(getApplication())
        viewModelScope.launch(Dispatchers.IO) {lock.withLock {
            if(!same(owner))return@withLock
            val current=mutable.value ?: return@withLock
            val proposed=transform(current)
            val dataChanged=proposed.logs!=current.logs || proposed.cycle!=current.cycle || proposed.duration!=current.duration
            val next=proposed.copy(pendingShare=current.pendingShare || (dataChanged && current.consent && current.shareSpace==remote.value.spaceId))
            try {repository.save(next);mutable.value=next;errors.value=null;if(next.pendingShare && remote.value.online && !remote.value.conflict) publishLocked(owner,true)} catch(e: CancellationException) {throw e} catch(_: Exception) {errors.value="Care could not be saved. Please try again."}
        }}
    }
    fun setSharing(enabled: Boolean) {
        val owner=AccountScope.current(getApplication())
        viewModelScope.launch(Dispatchers.IO) {lock.withLock {
            if(!same(owner))return@withLock
            // Refresh the version before an explicit publish/retry, preserving this phone's draft.
            val draft=mutable.value ?: return@withLock
            refreshLocked()
            if(!same(owner))return@withLock
            if(enabled) mutable.value=draft.copy(pendingShare=true,shareSpace=remote.value.spaceId)
            publishLocked(owner,enabled)
        }}
    }
    private suspend fun publishLocked(owner: String,enabled: Boolean) {
        val current=mutable.value ?: return
        val c=remote.value
        if(!c.online || c.spaceId.isBlank()) {errors.value="Connect to your shared space before changing Care sharing.";return}
        remote.value=c.copy(busy=true)
        try {
            val result=api.saveCareShare(c.spaceId,enabled,CareCodec.encode(current),c.version)
            if(!same(owner))return
            val next=current.copy(consent=enabled,shareSpace=if(enabled)c.spaceId else "",pendingShare=false)
            repository.save(next);mutable.value=next;errors.value=null
            remote.value=c.copy(enabled=enabled,version=result.getLong("version"),busy=false,conflict=false,status=if(enabled) "Shared with your partner · Only you can edit your records" else "Sharing stopped · Your records stay on this phone")
        } catch(e: CancellationException) {throw e} catch(e: Exception) {if(same(owner)) {remote.value=remote.value.copy(busy=false,conflict=true,status="Your saved logs are safe on this phone. Sharing needs a retry.");errors.value=connectionError(e)}}
    }
    fun save(log: DayLog)=update {it.copy(logs=it.logs.filterNot {old->old.day==log.day}+log)}
    fun delete(day: Long)=update {it.copy(logs=it.logs.filterNot {l->l.day==day})}
    fun clear()=update {it.copy(logs=emptyList(),checkIn=false,headsUp=false,endReminder=false)}
    fun clearError() {errors.value=null}
}

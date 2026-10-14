package ai.mwm.client.data

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
data class CareState(val logs: List<DayLog> = emptyList(), val cycle: Int = 28, val duration: Int = 5, val checkIn: Boolean = false, val headsUp: Boolean = false, val checkTime: Int = 1200, val headsTime: Int = 540, val consent: Boolean = false, val categories: Set<String> = setOf("Mood", "Cravings", "Symptoms"), val history: Boolean = false, val includeNotes: Boolean = false, val endReminder: Boolean = false, val endTime: Int = 540) {
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
    private fun key(): SecretKey { val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }; (store.getKey("pipo_care_v1", null) as? SecretKey)?.let { return it }; return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply { init(KeyGenParameterSpec.Builder("pipo_care_v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()) }.generateKey() }
    @Synchronized fun load(): CareState { if(!file.exists() && !File(file.path+".bak").exists()) return CareState(); val bytes=android.util.AtomicFile(file).openRead().use { it.readBytes() }; require(bytes.size>12); val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))); val root=JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)); fun set(obj: JSONObject,name: String): Set<String> { val a=obj.optJSONArray(name) ?: return emptySet(); return (0 until a.length()).map { a.getString(it) }.toSet() }; val a=root.optJSONArray("logs") ?: JSONArray(); return CareState(logs=(0 until a.length()).map { val l=a.getJSONObject(it); DayLog(l.getLong("day"),l.optBoolean("start"),l.optBoolean("end"),l.optString("flow","None"),l.optString("cramps","None"),l.optInt("mood",-1).takeIf { it in 0..4 },set(l,"cravings"),set(l,"symptoms"),l.optString("note")) },cycle=root.optInt("cycle",28).coerceIn(10,120),duration=root.optInt("duration",5).coerceIn(1,14),checkIn=root.optBoolean("checkIn"),headsUp=root.optBoolean("headsUp"),checkTime=root.optInt("checkTime",1200),headsTime=root.optInt("headsTime",540),consent=root.optBoolean("consent"),categories=set(root,"categories"),history=root.optBoolean("history"),includeNotes=root.optBoolean("includeNotes"),endReminder=root.optBoolean("endReminder"),endTime=root.optInt("endTime",540)) }
    @Synchronized fun save(state: CareState) { val root=JSONObject().put("logs",JSONArray(state.logs.map { l -> JSONObject().put("day",l.day).put("start",l.start).put("end",l.end).put("flow",l.flow).put("cramps",l.cramps).put("mood",l.mood ?: -1).put("cravings",JSONArray(l.cravings.toList())).put("symptoms",JSONArray(l.symptoms.toList())).put("note",l.note) })).put("cycle",state.cycle).put("duration",state.duration).put("checkIn",state.checkIn).put("headsUp",state.headsUp).put("checkTime",state.checkTime).put("headsTime",state.headsTime).put("consent",state.consent).put("categories",JSONArray(state.categories.toList())).put("history",state.history).put("includeNotes",state.includeNotes).put("endReminder",state.endReminder).put("endTime",state.endTime); val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key()); val atomic=android.util.AtomicFile(file); val out=atomic.startWrite(); try { out.write(cipher.iv+cipher.doFinal(root.toString().toByteArray(Charsets.UTF_8)));atomic.finishWrite(out) } catch(e: Exception) {atomic.failWrite(out);throw e};CareReminders.sync(context,state) }
    companion object { @Volatile private var instance: CareRepository?=null; fun get(context: Context): CareRepository = instance ?: synchronized(this) { instance ?: CareRepository(context.applicationContext).also { instance=it } } }
}
class CareViewModel(app: Application): AndroidViewModel(app) {
    private val repository=CareRepository.get(app)
    private val mutable=MutableStateFlow<CareState?>(null)
    val state: StateFlow<CareState?> = mutable
    @Volatile private var loadedOwner: String?=null
    private val errors=MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = errors
    init { viewModelScope.launch(Dispatchers.IO) {
        AccountScope.changes.collect { owner -> synchronized(repository) {
            loadedOwner=null;mutable.value=null;errors.value=null
            runCatching { repository.load() }.onSuccess { if(AccountScope.current(app)==owner){loadedOwner=owner;mutable.value=it;CareReminders.sync(app,it)} }.onFailure { errors.value="Your private vault could not be opened. No records were changed." }
        } }
    } }
    fun update(transform: (CareState)->CareState) {
        val owner=AccountScope.current(getApplication())
        viewModelScope.launch(Dispatchers.IO) { synchronized(repository) {
            if(owner!=AccountScope.current(getApplication()) || loadedOwner!=owner)return@synchronized
            val current=mutable.value ?: return@synchronized
            runCatching { val next=transform(current);repository.save(next);mutable.value=next }.onFailure { errors.value="Your private change could not be saved. Please try again." }
        } }
    }
    fun save(log: DayLog) = update { it.copy(logs=it.logs.filterNot { old -> old.day==log.day }+log) }
    fun delete(day: Long)=update { it.copy(logs=it.logs.filterNot { l->l.day==day }) }
    fun clear()=update { CareState(cycle=it.cycle,duration=it.duration) }
    fun clearError() { errors.value=null }
}

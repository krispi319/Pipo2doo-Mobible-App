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

/** Separate encrypted vault, with private account backup. Fail closed if unreadable. */
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
    @Synchronized fun save(state: CareState) { val root=exportState(state); val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key()); val atomic=android.util.AtomicFile(file); val out=atomic.startWrite(); try { out.write(cipher.iv+cipher.doFinal(root.toString().toByteArray(Charsets.UTF_8)));atomic.finishWrite(out) } catch(e: Exception) {atomic.failWrite(out);throw e};ai.mwm.client.cloud.AccountBackup.pending(context,"care");runCatching {CareReminders.sync(context,state)} }
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
data class CareNotice(val id:String=newId(),val message:String)
class CareViewModel(app: Application): AndroidViewModel(app) {
    private val repository=CareRepository.get(app)
    private val api=PipoFirebase.get(app)
    private val lock=Mutex()
    private val refreshLock=Mutex()
    private val publishLock=Mutex()
    private val mutable=MutableStateFlow<CareState?>(null)
    val state:StateFlow<CareState?> = mutable
    private val remote=MutableStateFlow(CareConnection())
    val connection:StateFlow<CareConnection> = remote
    private val errors=MutableStateFlow<String?>(null)
    val error:StateFlow<String?> = errors
    private val notices=MutableStateFlow<CareNotice?>(null)
    val notice:StateFlow<CareNotice?> = notices
    @Volatile private var loadedOwner=""
    private var polling:Job?=null
    private var backup:Job?=null
    private var backupAt=0L
    private val publishWake=kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    init {
        viewModelScope.launch(Dispatchers.IO) {
            AccountScope.changes.collect {owner->lock.withLock {
                loadedOwner="";mutable.value=null;remote.value=CareConnection();errors.value=null;notices.value=null;backupAt=0
                try {val local=repository.load();if(owner==AccountScope.current(app)) {mutable.value=local;loadedOwner=owner;runCatching {CareReminders.sync(app,local)}}}
                catch(_:Exception){errors.value="Care records could not be opened. Your saved records have not been replaced."}
            }}
        }
        viewModelScope.launch(Dispatchers.IO) {
            for(signal in publishWake) {
                val owner=loadedOwner
                val c=remote.value
                if(same(owner) && mutable.value?.pendingShare==true && c.enabled && c.online && !c.conflict) publish(owner,true)
            }
        }
    }
    private fun same(owner:String)=owner.isNotBlank() && owner==loadedOwner && owner==AccountScope.current(getApplication())
    fun resume(){if(polling?.isActive==true)return;polling=viewModelScope.launch(Dispatchers.IO){while(isActive){refreshRemote();delay(8000)}}}
    fun pause(){polling?.cancel();polling=null}
    fun refresh(){viewModelScope.launch(Dispatchers.IO){refreshRemote()}}
    fun backupNow(){val owner=loadedOwner;viewModelScope.launch(Dispatchers.IO){backup?.join();if(same(owner) && owner!="local")backupLater(owner,true)}}
    private fun backupLater(owner:String,force:Boolean=false) {
        if(backup?.isActive==true || (!force && System.currentTimeMillis()-backupAt<60_000))return
        backupAt=System.currentTimeMillis()
        backup=viewModelScope.launch(Dispatchers.IO) {
            try {
                val restored=ai.mwm.client.cloud.AccountBackup.syncCare(getApplication())
                if(restored)lock.withLock {if(same(owner)) {
                    val saved=repository.load();val live=mutable.value
                    val merged=if(remote.value.online && live!=null) saved.copy(logs=live.logs,cycle=live.cycle,duration=live.duration,shareSpace=live.shareSpace,pendingShare=live.pendingShare) else saved
                    repository.save(merged);mutable.value=merged
                }}
            }catch(e:CancellationException){throw e}catch(_:Exception){ /* Backup status carries the failure; local Care remains visible. */ }
        }
    }
    /** Network waits never hold the local edit lock. Pending local logs cannot be replaced by a refresh. */
    private suspend fun refreshRemote(preserveOwn:Boolean=false) {
        val owner=loadedOwner
        if(!same(owner) || owner=="local" || !refreshLock.tryLock())return
        try {
            // On a new phone, restore private Care controls before importing server logs.
            // Otherwise that first import would look like competing unsaved local data.
            if(!repository.hasBackupData()) {
                val restored=ai.mwm.client.cloud.AccountBackup.syncCare(getApplication())
                if(restored)lock.withLock {if(same(owner))mutable.value=repository.load()}
            }
            if(!same(owner))return
            val snapshot=api.careSnapshot()
            lock.withLock {
                if(!same(owner) || AccountScope.key(snapshot.getString("uid"))!=owner)return@withLock
                val sid=snapshot.getString("spaceId");val uid=snapshot.getString("uid");val rows=snapshot.getJSONArray("entries")
                val entries=(0 until rows.length()).map {rows.getJSONObject(it)}
                val own=entries.firstOrNull {it.getString("user_id")==uid}
                val partner=entries.firstOrNull {it.getString("user_id")!=uid && it.optBoolean("enabled")}
                val version=own?.optLong("version") ?: 0
                if(remote.value.spaceId==sid && version<remote.value.version)return@withLock
                val local=repository.forSpace(sid)
                val enabled=own?.optBoolean("enabled")==true
                val pending=local.pendingShare && local.shareSpace==sid
                val conflict=pending && (remote.value.conflict || remote.value.spaceId!=sid || remote.value.version!=version)
                val next=if(enabled && !pending && !preserveOwn) {
                    val cloud=CareCodec.decode(own!!.getJSONObject("payload"))
                    local.copy(logs=cloud.logs,cycle=cloud.cycle,duration=cloud.duration,consent=true,shareSpace=sid,pendingShare=false)
                } else local.copy(consent=enabled,shareSpace=if(enabled || pending)sid else "",pendingShare=pending)
                if(next!=local)repository.save(next)
                mutable.value=next
                remote.value=CareConnection(sid,enabled,version,partner?.let {CareCodec.decode(it.getJSONObject("payload"))},true,false,
                    if(pending)"Saved on this phone · Sharing pending" else if(enabled)"Shared with your partner · Only you can edit your records" else "Share your records when you want your partner to follow along.",conflict)
            }
            if(same(owner))backupLater(owner)
        }catch(e:CancellationException){throw e}
        catch(e:NoMembership){lock.withLock {if(same(owner))remote.value=CareConnection()};if(same(owner))backupLater(owner)}
        catch(e:Exception){lock.withLock {if(same(owner))remote.value=remote.value.copy(online=false,partner=null,busy=false,status=connectionError(e))}}
        finally {refreshLock.unlock()}
    }
    fun update(transform:(CareState)->CareState)=updateWithNotice(transform,"Care settings saved ✓")
    private fun updateWithNotice(transform:(CareState)->CareState,message:String) {
        val owner=AccountScope.current(getApplication())
        viewModelScope.launch(Dispatchers.IO) {lock.withLock {
            if(!same(owner)){errors.value="Your account is still opening. Please retry saving this draft.";return@withLock}
            val current=mutable.value
            if(current==null){errors.value="Care is still opening. Please retry saving this draft.";return@withLock}
            try {
                val proposed=transform(current)
                val changed=proposed.logs!=current.logs || proposed.cycle!=current.cycle || proposed.duration!=current.duration
                val next=proposed.copy(pendingShare=current.pendingShare || (changed && current.consent && current.shareSpace.isNotBlank()))
                repository.save(next);mutable.value=next;errors.value=null
                notices.value=CareNotice(message=if(next.pendingShare)"Saved on this phone · Sharing pending" else message)
                if(next.pendingShare)publishWake.trySend(Unit)
            }catch(e:CancellationException){throw e}
            catch(_:Exception){errors.value="Day log could not be saved. Your draft is still here; tap Save to retry.";notices.value=CareNotice(message="Save failed · Your draft was kept")}
        }}
    }
    fun setSharing(enabled:Boolean) {
        val owner=AccountScope.current(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            refreshRemote(preserveOwn=true)
            publish(owner,enabled)
        }
    }
    private suspend fun publish(owner:String,enabled:Boolean)=publishLock.withLock {
        val sent=lock.withLock {
            if(!same(owner))return@withLock null
            val current=mutable.value ?: return@withLock null
            val c=remote.value
            if(!c.online || c.spaceId.isBlank()){errors.value="Saved on this phone. Reconnect before changing Care sharing.";return@withLock null}
            remote.value=c.copy(busy=true)
            current to c
        } ?: return@withLock
        try {
            val result=api.saveCareShare(sent.second.spaceId,enabled,CareCodec.encode(sent.first),sent.second.version)
            lock.withLock {
                if(!same(owner) || remote.value.spaceId!=sent.second.spaceId)return@withLock
                val current=mutable.value ?: return@withLock
                val unchanged=CareCodec.encode(current).toString()==CareCodec.encode(sent.first).toString()
                val next=current.copy(consent=enabled,shareSpace=if(enabled)sent.second.spaceId else "",pendingShare=enabled && !unchanged)
                repository.save(next);mutable.value=next;errors.value=null
                remote.value=remote.value.copy(enabled=enabled,version=result.getLong("version"),busy=false,conflict=false,
                    status=if(next.pendingShare)"Saved on this phone · Sharing pending" else if(enabled)"Shared with your partner · Only you can edit your records" else "Sharing stopped · Your records stay on this phone")
                if(next.pendingShare)publishWake.trySend(Unit)
                else if(enabled)notices.value=CareNotice(message="Day log saved · Shared with your partner ✓")
            }
        }catch(e:CancellationException){throw e}
        catch(e:Exception){lock.withLock {if(same(owner)) {
            val current=mutable.value
            if(enabled && current!=null){val pending=current.copy(pendingShare=true,shareSpace=sent.second.spaceId);repository.save(pending);mutable.value=pending}
            remote.value=remote.value.copy(busy=false,conflict=true,status="Saved on this phone · Sharing needs a retry")
            errors.value="Your log is saved on this phone. Sharing could not finish: "+connectionError(e)
        }}}
        finally {lock.withLock {if(same(owner))remote.value=remote.value.copy(busy=false)}}
    }
    fun save(log:DayLog)=updateWithNotice({it.copy(logs=it.logs.filterNot {old->old.day==log.day}+log)},"Day log saved ✓")
    fun delete(day:Long)=updateWithNotice({it.copy(logs=it.logs.filterNot {l->l.day==day})},"Day log deleted")
    fun clear()=updateWithNotice({it.copy(logs=emptyList(),checkIn=false,headsUp=false,endReminder=false)},"Day logs cleared")
    fun clearError(){errors.value=null}
    fun clearNotice(id:String){if(notices.value?.id==id)notices.value=null}
}

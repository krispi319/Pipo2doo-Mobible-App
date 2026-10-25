package ai.mwm.client.cloud

import ai.mwm.client.data.*
import android.content.Context
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** No demo imports, no health serialization, no persistent partner-data cache. */
data class PartnerConnection(val busy: Boolean=false, val spaceId: String="", val uid: String="", val partnerUid: String="", val generation: String="", val code: String="", val expiresAt: Long=0, val message: String="Local mode · Sign in to connect with your partner", val online: Boolean=false, val pokeCooldowns: Map<String,Long> = emptyMap(), val needsAuth: Boolean=false, val pairingMessage: String="", val retryAt: Long=0, val authenticated: Boolean=false, val role: String="", val partnerRole: String="", val selectedRole: String="", val accountEmail: String="", val partnerAvatar: Int = -1,val togetherDateVersion:Long=0,val togetherDateKnown:Boolean=false) {
    val connected get() = spaceId.isNotBlank()
    val paired get() = connected && partnerUid.isNotBlank()
}
val LocalPartnerConnection = androidx.compose.runtime.staticCompositionLocalOf { PartnerConnection() }
val LocalPartnerNote = androidx.compose.runtime.staticCompositionLocalOf<(String,String,String,String) -> Unit> { {_,_,_,_->} }
val LocalPartnerPoke = androidx.compose.runtime.staticCompositionLocalOf<(Task) -> Unit> { {} }
fun connectionError(e: Throwable): String = when(e) {
    is FirebaseFunctionsException -> when(e.code) {
        FirebaseFunctionsException.Code.NOT_FOUND -> "Code or server unavailable. Check the code, or ask for a fresh one."
        FirebaseFunctionsException.Code.ABORTED -> "Changed on another phone. Refresh and try again; your edit was not saved."
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "Please wait before trying again."
        FirebaseFunctionsException.Code.UNAVAILABLE -> "Offline · Change not sent. Reconnect and try again."
        else -> e.message ?: "Partner service unavailable. Try again."
    }
    is FirebaseAuthException -> "Sign-in could not complete. Check your connection and retry."
    is SignInRequired -> "Sign in to Supabase to generate a code or join your partner."
    is SupabaseFailure -> when {
        e.reason == "sender_not_configured" -> "Poke sender setup is missing on the server. Configure FCM_SERVICE_ACCOUNT_JSON in Supabase Edge Function secrets."
        e.reason == "sender_auth_failed" -> "Firebase rejected the sending key. Replace FCM_SERVICE_ACCOUNT_JSON in Supabase with an active service-account key, then retry."
        e.reason == "40001" -> "Changed on another phone. Refresh and try again; your edit was not saved."
        e.status == 429 -> "Too many attempts. Please wait before trying again."
        e.reason == "email_not_confirmed" -> "Confirm your email first, then sign in."
        e.reason == "invalid_credentials" -> "Email or password is incorrect. Please try again."
        else -> e.message ?: "Supabase request failed. Please try again."
    }
    is java.io.IOException -> "Offline · Supabase could not be reached. Reconnect and try again."
    else -> e.message ?: "Connection failed. Your change was not sent."
}
class PartnerSync(private val context: Context, private val repository: PipoRepository, private val scope: CoroutineScope) {
    private val api=PipoFirebase.get(context)
    private val lock=Mutex()
    private val mutable=MutableStateFlow(PartnerConnection())
    val state: StateFlow<PartnerConnection> = mutable.asStateFlow()
    private var taskVersions=mapOf<String,Long>()
    private var listVersions=mapOf<String,Long>()
    private var tagVersions=mapOf<String,Long>()
    private var polling: Job?=null
    private var pendingAction: Pair<String,String>?=null
    init {mutable.value=PartnerConnection(selectedRole=roleStore().getString("chosen_role","") ?: "",message=if(repository.isRemote) "Restoring authenticated shared space…" else "Local mode · Sign in to connect with your partner")}
    private fun roleStore()=context.getSharedPreferences("pipo_role_${AccountScope.current(context)}",Context.MODE_PRIVATE)
    fun chooseRole(role: String) {
        if(mutable.value.busy || mutable.value.connected)return
        require(role in listOf("user_1","user_2"))
        roleStore().edit().putString("chosen_role",role).commit()
        mutable.value=mutable.value.copy(selectedRole=role,pairingMessage=if(role=="user_1") "Host selected · User 1. Share your code with the Joiner." else "Joiner selected · User 2. Enter the Host’s code.")
    }
    private suspend fun activateIdentity() {
        val (uid,email)=api.identity()
        if(mutable.value.uid!=uid) {
            val chosen=mutable.value.selectedRole
            repository.activateAccount(uid)
            taskVersions=emptyMap();listVersions=emptyMap();tagVersions=emptyMap()
            mutable.value=PartnerConnection(busy=mutable.value.busy,uid=uid,accountEmail=email,authenticated=true,selectedRole=chosen,needsAuth=mutable.value.needsAuth,pairingMessage=mutable.value.pairingMessage)
            if(chosen.isNotBlank())roleStore().edit().putString("chosen_role",chosen).commit()
        } else mutable.value=mutable.value.copy(accountEmail=email,authenticated=true)
    }
    fun dismissAuth() {pendingAction=null;mutable.value=mutable.value.copy(needsAuth=false)}
    fun authenticate(email: String,password: String,signup: Boolean) {
        if(mutable.value.busy)return
        mutable.value=mutable.value.copy(busy=true,pairingMessage="Signing in to Supabase…")
        scope.launch {lock.withLock {
            try {
                val signedIn=api.authenticate(email,password,signup)
                if(!signedIn) {mutable.value=mutable.value.copy(pairingMessage="Check your email to confirm your account, then sign in.");return@withLock}
                mutable.value=mutable.value.copy(needsAuth=false,authenticated=true,pairingMessage="Signed in · Choose Create space or Join")
                refresh()
            } catch(e: CancellationException) {throw e} catch(e: Exception) {mutable.value=mutable.value.copy(pairingMessage=connectionError(e))}
            finally {mutable.value=mutable.value.copy(busy=false)}
        }
        if(!mutable.value.needsAuth) {val pending=pendingAction;pendingAction=null;if(pending!=null)connect(pending.first,pending.second)}
        }
    }
    fun signOut() {if(mutable.value.busy)return;scope.launch {lock.withLock {runCatching {api.revokeDevice()};api.signOut();repository.activateAccount("");repository.restoreLocal();taskVersions=emptyMap();listVersions=emptyMap();tagVersions=emptyMap();pendingAction=null;mutable.value=PartnerConnection(message="Signed out · Your local workspace is preserved");PartnerPokeService.clear(context)}}}
    fun check() {if(mutable.value.busy)return;scope.launch {lock.withLock {mutable.value=mutable.value.copy(busy=true);try {api.probe();refresh()} catch(e: CancellationException){throw e} catch(e: Exception){mutable.value=mutable.value.copy(message=connectionError(e),needsAuth=e is SignInRequired)} finally {mutable.value=mutable.value.copy(busy=false)}}}}
    fun connect(action: String, code: String="") {
        if(mutable.value.busy)return
        mutable.value=mutable.value.copy(busy=true,pairingMessage="Contacting Supabase…")
        scope.launch {lock.withLock {
            try {
                activateIdentity() // Verify and partition by authenticated UID before reading personal state.
                val chosen=mutable.value.role.ifBlank {mutable.value.selectedRole}
                check(chosen in listOf("user_1","user_2")){"Choose User 1 or User 2 before creating or joining a space."}
                if(action=="join") {
                    val reply=api.joinSpace(code,chosen)
                    when(reply["status"]) {
                        "connected" -> {mutable.value=mutable.value.copy(code="",expiresAt=0,retryAt=0);refresh();mutable.value=mutable.value.copy(pairingMessage="Partner joined · Shared space loaded")}
                        "role_conflict" -> error("Your partner already uses ${roleLabel(chosen)}. Choose ${roleLabel(reply["available_role"] as? String ?: "")} and join again. The code is still valid.")
                        "role_required" -> error("Choose User 1 or User 2 before joining.")
                        "rate_limited" -> error("Too many joining attempts. Wait 15 minutes before trying again.")
                        "invalid_or_unavailable" -> error("Code expired, already used, or unavailable. If you have your own space, leave it before joining another.")
                        else -> error("Unexpected pairing response. Nobody was connected.")
                    }
                } else {
                    check(action in listOf("create","regenerate")){"Unknown connection action"}
                    api.createSpace(chosen) // Idempotent; establishes required membership ONLY for creator/regenerator.
                    refresh()
                    check(!mutable.value.paired){"Your partner is already connected. No invitation is needed."}
                    val previous=mutable.value
                    // A lost HTTP reply may mean the old code was invalidated; never keep it shareable in that case.
                    mutable.value=previous.copy(code="",expiresAt=0,pairingMessage="Generating server code…")
                    val reply=api.issueCode()
                    when(reply["status"]) {
                        "issued" -> {
                            val issued=reply["code"] as? String ?: error("Server returned no invitation code")
                            check(issued.matches(Regex("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}"))){"Server returned an invalid invitation code"}
                            val expiry=java.time.OffsetDateTime.parse(reply["expires_at"] as String).toInstant().toEpochMilli()
                            check(expiry>System.currentTimeMillis()){"Server returned an expired code. Check this phone's clock."}
                            check(reply["space_id"]==mutable.value.spaceId){"Invitation belongs to a different space"}
                            mutable.value=mutable.value.copy(code=issued,expiresAt=expiry,online=true,retryAt=System.currentTimeMillis()+30000,pairingMessage="New code issued · Valid for 10 minutes. Any previous code no longer works.")
                        }
                        "cooldown" -> mutable.value=mutable.value.copy(code=previous.code,expiresAt=previous.expiresAt,retryAt=System.currentTimeMillis()+30000,pairingMessage="Please wait up to 30 seconds before regenerating. No new code was issued.")
                        else -> error("Unexpected code response. Please try again.")
                    }
                }
                // No automatic profile import: sharing a minimal display profile is an explicit Settings opt-in.
            } catch(e: CancellationException){throw e} catch(e: Exception) {
                if(e is SignInRequired) {pendingAction=action to code;mutable.value=mutable.value.copy(needsAuth=true)}
                mutable.value=mutable.value.copy(pairingMessage=connectionError(e),message=connectionError(e))
            } finally {mutable.value=mutable.value.copy(busy=false)}
        }}
    }
    fun resume() {
        if(polling?.isActive==true)return
        polling=scope.launch {while(isActive){lock.withLock {
            if(api.hasSession()) try {refresh()} catch(e: CancellationException){throw e} catch(e: Exception){mutable.value=mutable.value.copy(online=false,message=connectionError(e));if(e is SignInRequired){PartnerPokeService.clear(context);repository.activateAccount("");repository.restoreLocal();taskVersions=emptyMap();listVersions=emptyMap();tagVersions=emptyMap();mutable.value=PartnerConnection(message=connectionError(e))}}
        };delay(6000)}}
    }
    fun pause(){polling?.cancel();polling=null}
    private fun maps(value: Any?): List<Map<String,Any?>> { @Suppress("UNCHECKED_CAST") return (value as? List<*>)?.mapNotNull {it as? Map<String,Any?>} ?: emptyList() }
    private fun n(r: Map<String,Any?>,key: String,default: Long=0)=(r[key] as? Number)?.toLong() ?: default
    private fun owner(r: Map<String,Any?>,uid: String)=when(r["assigneeUid"]){null->Owner.Together;uid->Owner.Me;else->Owner.You}
    private suspend fun refresh() {
        activateIdentity()
        if(AccountBackup.syncMain(context)) repository.restoreLocal()
        val r=try {api.snapshot()} catch(e: NoMembership) {PartnerPokeService.clear(context);repository.restoreLocal();taskVersions=emptyMap();listVersions=emptyMap();tagVersions=emptyMap();mutable.value=mutable.value.copy(spaceId="",partnerUid="",togetherDateKnown=false,togetherDateVersion=0,partnerAvatar=-1,role="",partnerRole="",code="",expiresAt=0,online=true,authenticated=true,message="Signed in · Create a space or join your partner's code");return}
        val uid=r["uid"] as String;val partner=r["partnerUid"] as? String ?: "";val sid=r["spaceId"] as String;val generation=r["generation"] as String
        val role=r["role"] as String;val partnerRole=r["partnerRole"] as String
        check(uid!=partner){"You and your partner must sign in with separate accounts."}
        val ts=maps(r["tasks"]);val ls=maps(r["lists"]);val tagRows=maps(r["tags"]);val profiles=maps(r["profiles"]);val own=profiles.find {it["uid"]==uid};val other=profiles.find {it["uid"]==partner}
        taskVersions=ts.associate {it["id"] as String to n(it,"version")};listVersions=ls.associate {it["id"] as String to n(it,"version")};tagVersions=tagRows.associate {it["id"] as String to n(it,"version")}
        repository.bindSharedSpace(sid)
        val current=repository.state.value
        val savedReminders=try {api.taskReminderSettings(sid)} catch(e: CancellationException) {throw e} catch(_: Exception) {emptyMap()}
        fun reminder(id: String, series: String): Pair<Boolean,Int> {
            val inherited=savedReminders[id] ?: savedReminders[series] ?: if(series.isNotBlank()) repository.personalReminder(series) else (true to 0)
            return repository.personalReminder(id,inherited.first,inherited.second)
        }
        val tasks=ts.map {t->Task(id=t["id"] as String,title=t["title"] as String,owner=owner(t,uid),assigneeUid=t["assigneeUid"] as? String ?: "",scheduledAt=n(t,"scheduledAt"),scheduleZone=t["scheduleZone"] as? String ?: "",day=n(t,"day"),minute=(t["minute"] as? Number)?.toInt(),listId=t["listId"] as String,note=repository.privateNote(t["id"] as String),tags=(t["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),repeat=Repeat.valueOf(t["repeat"] as String),status=TaskStatus.valueOf(t["status"] as String),completedAt=n(t,"completedAt"),parentId=t["parentId"] as? String ?: "",seriesId=t["seriesId"] as? String ?: "",reminder=reminder(t["id"] as String,t["seriesId"] as? String ?: "").first,offset=reminder(t["id"] as String,t["seriesId"] as? String ?: "").second)}
        val lists=ls.map {l->SharedList(id=l["id"] as String,name=l["name"] as String,emoji=l["emoji"] as String,pinned=l["pinned"]==true,owner=owner(l,uid),assigneeUid=l["assigneeUid"] as? String ?: "",pictures=(l["pictures"] as? List<*>)?.filterIsInstance<String>() ?: emptyList())}
        val notes=maps(r["notes"]).map {v->Appreciation(id=v["id"] as String,kind=v["kind"] as String,message=v["message"] as String,owner=if(v["sender"]==uid)Owner.Me else Owner.You,at=n(v,"at"),taskTitle=v["taskTitle"] as? String ?: "",picture=v["picture"] as? String ?: "",replyTo=v["replyTo"] as? String ?: "")}
        val together=maps(listOfNotNull(r["togetherDate"])).singleOrNull()
        val p=restoreSharedProfile(current.preferences,own,other,if(partner.isBlank()) "Waiting for partner" else roleLabel(partnerRole)).copy(started=true,code=mutable.value.code.ifBlank {"------"},codeExpiresAt=mutable.value.expiresAt,togetherSince=together?.let {n(it,"since_day")} ?: current.preferences.togetherSince)
        val profileNotice=try {api.ensureProfile(p);""} catch(e: CancellationException) {throw e} catch(_: Exception) {" · Profile update waiting to retry"}
        // Legacy task tags contained names rather than IDs; keep them readable until migrated.
        val savedTags=tagRows.map {Tag(it["id"] as String,it["name"] as String)}
        val legacyTags=tasks.flatMap {it.tags}.distinct().filter {id -> savedTags.none {it.id==id} }.map {Tag(it,it)}
        repository.importRemote(current.copy(tasks=tasks,lists=lists,notes=notes,tags=savedTags+legacyTags,preferences=p))
        try {api.syncTaskReminders(sid,tasks)} catch(e: CancellationException) {throw e} catch(_: Exception) {
            // Task save and synchronization remain available while background-reminder setup retries.
        }
        runCatching {api.updateDevice(p)}.onFailure { error ->
            if(error is CancellationException) throw error
            // Task synchronization stays available while push setup retries on the next refresh.
        }
        mutable.value=mutable.value.copy(togetherDateVersion=together?.let {n(it,"version")} ?: 0,togetherDateKnown=together!=null,role=role,partnerRole=partnerRole,selectedRole=role,spaceId=sid,uid=uid,partnerUid=partner,generation=generation,partnerAvatar=(other?.get("avatar") as? Number)?.toInt() ?: -1,authenticated=true,online=true,message=(if(partner.isBlank()) "Space created · Waiting for your partner" else "Partner connected · Synced")+profileNotice,code=if(partner.isBlank())mutable.value.code else "",expiresAt=if(partner.isBlank())mutable.value.expiresAt else 0)
    }
    /** Network-first edits: rejected/conflicting/offline writes never masquerade as synced. */
    suspend fun mutate(showBusy: Boolean = true, block: () -> Unit)=lock.withLock {
        check(!mutable.value.busy){"Please wait for the current account operation."}
        if(!mutable.value.connected){check(!repository.isRemote){"Shared space is still reconnecting. Wait for a fresh server response."};block();return@withLock}
        check(mutable.value.online){"Offline · Change not sent. Reconnect and try again."}
        val old=repository.state.value;val next=repository.preview(block);val c=mutable.value
        if(showBusy) mutable.value=c.copy(busy=true,message="Saving to shared space…")
        try {
            require(next.preferences.partnerName==old.preferences.partnerName && next.preferences.partnerPhoto==old.preferences.partnerPhoto){"Only your partner can edit their profile."}
            requireSharedPermissions(old,next,c)
            fun depth(task: Task): Int {var t=task;var d=0;val seen=mutableSetOf<String>();while(t.parentId.isNotBlank() && seen.add(t.id) && d<64) {t=old.tasks.find {it.id==t.parentId} ?: break;d++};return d}
            for(l in next.lists.filter {it !in old.lists}){val pictures=l.pictures.map {api.preparePhoto(it)};api.saveList(l.copy(pictures=pictures),c.uid,c.partnerUid.ifBlank {null},listVersions[l.id] ?: 0)}
            val renamedOrAdded=next.tags.filter {tag -> old.tags.find {it.id==tag.id}!=tag}
            // Legacy tags have non-UUID IDs: retain their name IDs until the user replaces them.
            for(tag in renamedOrAdded) api.saveTag(tag,tagVersions[tag.id] ?: 0)
            fun sharedTask(t: Task)=t.canonicalSchedule().copy(note="",reminder=false,offset=0,events=emptyList())
            for(t in next.tasks.filter {t->old.tasks.find {it.id==t.id}?.let {sharedTask(it)==sharedTask(t)}!=true}){api.saveTask(t,c.uid,c.partnerUid.ifBlank {null},taskVersions[t.id] ?: 0)}
            for(t in old.tasks.filter {before->next.tasks.none {it.id==before.id}}.sortedByDescending(::depth))api.deleteTask(t.id,taskVersions[t.id] ?: 0)
            for(l in old.lists.filter {before->next.lists.none {it.id==before.id}})api.deleteList(l.id,listVersions[l.id] ?: 0)
            for(tag in old.tags.filter {before -> next.tags.none {it.id==before.id} && tagVersions.containsKey(before.id)})api.deleteTag(tag.id,tagVersions[tag.id] ?: 0)
            for(note in old.notes.filter {before->next.notes.none {it.id==before.id}})api.call("deleteNote",mapOf("id" to note.id))
            var locketNotice=""
            for(note in next.notes.filter {it !in old.notes}) {
                api.call("saveNote",mapOf("id" to note.id,"value" to mapOf("kind" to note.kind,"message" to note.message,"taskTitle" to note.taskTitle,"picture" to if(note.picture.isBlank())"" else api.preparePhoto(note.picture))))
                if(note.kind=="Locket") {
                    // A saved photo stays saved even if the optional push sender is unavailable.
                    val status=try {api.notifyLocket(note.id)} catch(e: CancellationException) {throw e} catch(_: Exception) {"failed"}
                    if(status !in listOf("provider_accepted","already_sent")) locketNotice=when(status) {
                        "recipient_unavailable" -> "Picture shared · Partner notifications are off or in quiet hours."
                        "no_device" -> "Picture shared · Your partner needs to enable notifications on their phone."
                        else -> "Picture shared · Phone notification could not be confirmed."
                    }
                } else {
                    val status=try {api.notifyPartnerNote(note.id)} catch(e: CancellationException) {throw e} catch(_: Exception) {"failed"}
                    if(status !in listOf("provider_accepted","already_sent")) locketNotice="Note shared · Phone notification could not be confirmed. Check your partner's notification and quiet-hour settings."
                }
            }
            val p=next.preferences
            if(p.myName!=old.preferences.myName || p.avatar!=old.preferences.avatar || p.myPhoto!=old.preferences.myPhoto || p.clockZone!=old.preferences.clockZone) api.ensureProfile(p)
            repository.savePrivateNotes(next.tasks)
            repository.savePersonalReminders(next.tasks)
            // Personal reminders/preferences are local; collections are replaced only by server results.
            repository.change {it.copy(preferences=next.preferences,tasks=it.tasks.map {t->next.tasks.find {it.id==t.id}?.let {edit->t.copy(reminder=edit.reminder,offset=edit.offset)} ?: t},tags=it.tags)}
            refresh()
            if(locketNotice.isNotBlank()) mutable.value=mutable.value.copy(message=locketNotice)
        } catch(e: Exception){runCatching {refresh()};mutable.value=mutable.value.copy(message="Shared update interrupted. Refresh to review any saved changes. ${connectionError(e)}");throw e} finally {if(showBusy) mutable.value=mutable.value.copy(busy=false)}
    }
    suspend fun setTogetherDate(day:Long,expectedVersion:Long,expectedAccount:String)=lock.withLock {
        require(validTogetherDate(day)) {"Choose a date from 1900 through today."}
        check(AccountScope.current(context)==expectedAccount) {"Account changed. Reopen settings."}
        val c=mutable.value
        check(!c.busy) {"Please wait for the current update."}
        if(!c.connected) {
            check(!repository.isRemote) {"Shared space is reconnecting. Try again when online."}
            repository.updateOwnPreferences {it.copy(togetherSince=day)}
            return@withLock
        }
        check(c.online) {"Reconnect before setting your shared date."}
        mutable.value=c.copy(busy=true)
        try {
            val saved=api.saveTogetherDate(c.uid,c.spaceId,day,expectedVersion)
            check(AccountScope.current(context)==expectedAccount && mutable.value.spaceId==c.spaceId) {"Partner changed. Reopen settings."}
            repository.change {it.copy(preferences=it.preferences.copy(togetherSince=n(saved,"since_day")))}
            mutable.value=mutable.value.copy(togetherDateKnown=true,togetherDateVersion=n(saved,"version"),message="Together date saved · Shared with your partner")
        } catch(e:CancellationException) {throw e} catch(e:Exception) {
            runCatching {refresh()}
            throw e
        } finally {mutable.value=mutable.value.copy(busy=false)}
    }
    suspend fun sendChatNote(note: Appreciation,expectedAccount: String,expectedPartner: String) = lock.withLock {
        check(AccountScope.current(context)==expectedAccount && mutable.value.partnerUid==expectedPartner) {"Account or partner changed. Reopen chat."}
        val c=mutable.value
        if(!c.connected) {
            check(!repository.isRemote) {"Shared chat is reconnecting. Please wait."}
            repository.change {current->current.copy(notes=current.notes.filterNot {it.id==note.id}+note)}
            return@withLock
        }
        check(c.paired && c.online) {"Reconnect to your partner before sending."}
        val saved=api.saveChatNote(note,c.uid,c.spaceId)
        check(AccountScope.current(context)==expectedAccount && mutable.value.spaceId==c.spaceId) {"Partner space changed."}
        val current=repository.state.value
        repository.importRemote(current.copy(notes=current.notes.filterNot {it.id==saved.id}+saved))
        // A saved message is Sent before optional push delivery. Push failure never retracts it.
        scope.launch(Dispatchers.IO) {
            if(AccountScope.current(context)==expectedAccount && mutable.value.spaceId==c.spaceId) {
                try {api.notifyPartnerNote(saved.id)} catch(e: CancellationException) {throw e} catch(_: Exception) { }
            }
        }
    }
    suspend fun poke(task: Task): Map<String,Any?> = lock.withLock {
        check(mutable.value.paired && mutable.value.online) {"Connect your partner before sending a Poke."}
        check(!task.done && task.owner!=Owner.Me) {"Choose an unfinished task for your partner."}
        check(!mutable.value.busy) {"Please wait for the current update."}
        val owner=AccountScope.current(context)
        val store=context.getSharedPreferences("pipo_poke_requests_$owner",0)
        val oldAt=store.getLong("at_${task.id}",0)
        val nonce=if(System.currentTimeMillis()-oldAt<300_000) store.getString(task.id,null) else null
        val request=nonce ?: newId().also {store.edit().putString(task.id,it).putLong("at_${task.id}",System.currentTimeMillis()).commit()}
        mutable.value=mutable.value.copy(busy=true)
        try {
            val reply=api.poke(task.id,taskVersions[task.id] ?: 0,request)
            check(owner==AccountScope.current(context)) {"Account changed. Poke response ignored."}
            if(reply["status"] in listOf("provider_accepted","failed","canceled","no_device","recipient_unavailable","unavailable","cooldown")) store.edit().remove(task.id).remove("at_${task.id}").commit()
            if(reply["status"]=="cooldown") {
                val until=(reply["next_allowed_at"] as? String)?.let {runCatching {java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli()}.getOrNull()} ?: (System.currentTimeMillis()+900_000)
                mutable.value=mutable.value.copy(pokeCooldowns=mutable.value.pokeCooldowns+(task.id to until))
            }
            reply
        } finally {mutable.value=mutable.value.copy(busy=false)}
    }
    fun leave(){ if(mutable.value.busy)return; if(!mutable.value.connected){repository.updateOwnPreferences {it.copy(started=false)};return};scope.launch {lock.withLock {try{api.leaveSpace();PartnerPokeService.clear(context);repository.restoreLocal();repository.updateOwnPreferences {it.copy(started=false)};mutable.value=mutable.value.copy(spaceId="",partnerUid="",togetherDateKnown=false,togetherDateVersion=0,partnerAvatar=-1,role="",partnerRole="",code="",expiresAt=0,retryAt=0,message="Space closed · Account-private data preserved");taskVersions=emptyMap();listVersions=emptyMap();tagVersions=emptyMap();pendingAction=null;runCatching {api.revokeDevice()}}catch(e: Exception){mutable.value=mutable.value.copy(message=connectionError(e))}}} }
}

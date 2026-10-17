package ai.mwm.client.data

import ai.mwm.client.SparkApplication
import ai.mwm.client.reminders.TaskReminders
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
private inline fun <reified T : Enum<T>> enumValue(name: String, fallback: T): T = enumValues<T>().firstOrNull { it.name == name } ?: fallback

/** Shared by the UI and notification receiver; changes are serialized and saved before publishing. */
class PipoRepository private constructor(private val context: Context) {
    private var prefs = context.getSharedPreferences(AccountScope.preferencesName(context), Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load())
    val state: StateFlow<PipoState> = mutable
    init {
        if(java.io.File(context.noBackupFilesDir,"pipo_partner_session").exists()) {
            // Await a fresh server snapshot after process death; never show demo data as shared.
            mutable.value=mutable.value.copy(tasks=emptyList(),lists=emptyList(),tags=emptyList(),notes=emptyList(),preferences=mutable.value.preferences.copy(myPhoto="",partnerName="Partner",partnerPhoto=""))
        }
        // Preserve legacy workspaces verbatim; account partitions never import or delete them.
    }
    private fun load(): PipoState {
        val raw = prefs.getString("state", null) ?: return if(AccountScope.current(context)=="local") seedState() else PipoState(preferences=Preferences(myName="Me",partnerName="Partner",myPhoto="",partnerPhoto=""))
        return runCatching {
            val root = JSONObject(raw)
            val p = root.optJSONObject("preferences") ?: JSONObject()
            PipoState(
                tasks = root.optJSONArray("tasks")?.objects()?.map { t -> Task(
                    id = t.getString("id"), title = t.optString("title"), owner = enumValue(t.optString("owner"), Owner.Together),
                    day = t.optLong("day", java.time.LocalDate.now().toEpochDay()), minute = t.optInt("minute", -1).takeIf { it in 0..1439 },
                    listId = t.optString("listId", "home"), note = t.optString("note"), tags = t.optJSONArray("tags")?.strings() ?: emptyList(),
                    repeat = enumValue(t.optString("repeat"), Repeat.Once), reminder = t.optBoolean("reminder"), offset = t.optInt("offset"),
                    status = enumValue(t.optString("status"), TaskStatus.Todo), completedAt = t.optLong("completedAt"), parentId = t.optString("parentId"),
                    events = t.optJSONArray("events")?.objects()?.map { TaskEvent(it.optString("text"), it.optLong("at"), enumValue(it.optString("owner"), Owner.Me)) } ?: emptyList()
                ) } ?: emptyList(),
                lists = root.optJSONArray("lists")?.objects()?.map { SharedList(it.getString("id"), it.getString("name"), it.optString("emoji", "🧺"), it.optBoolean("pinned"), enumValue(it.optString("owner"), Owner.Together), it.optJSONArray("pictures")?.strings() ?: emptyList()) } ?: emptyList(),
                tags = root.optJSONArray("tags")?.objects()?.map { Tag(it.getString("id"), it.getString("name")) } ?: emptyList(),
                notes = root.optJSONArray("notes")?.objects()?.map { Appreciation(it.getString("id"), it.getString("kind"), it.optString("message"), enumValue(it.optString("owner"), Owner.Me), it.optLong("at"), it.optString("taskTitle"), it.optBoolean("sample", it.optString("id").startsWith("sample_") || it.optString("message") in listOf("You even got the oat milk I like.", "Pipo's litter box has never smelled so innocent.")), it.optString("picture")) } ?: emptyList(),
                preferences = Preferences(p.optString("myName", "User 1"), p.optString("partnerName", "User 2"), p.optString("code", "K4T9PB"), p.optBoolean("started"), p.optInt("avatar", -1).let { if(root.optInt("version") < 4 && it == 0 && p.optString("myPhoto").isBlank()) -1 else it }, p.optBoolean("quietEnabled", true), p.optInt("quietStart", 1320), p.optInt("quietEnd", 450), p.optString("style", "Soft purr"), p.optBoolean("sound", true), p.optBoolean("haptics", true), p.optString("myPhoto"), p.optString("partnerPhoto"), p.optBoolean("pokeOptIn"), p.optString("themeMode", "System"), p.optString("language", "en"), p.optLong("codeExpiresAt"), p.optLong("togetherSince", java.time.LocalDate.of(2023,2,14).toEpochDay()), p.optInt("defaultReminderTime",750).coerceIn(0,1439), p.optBoolean("keepResetHistory",true), p.optBoolean("shareProfile",false))
            )
        }.getOrElse { if(AccountScope.current(context)=="local") seedState() else PipoState(preferences=Preferences(myName="Me",partnerName="Partner")) }
    }
    private val remoteMarker=java.io.File(context.noBackupFilesDir,"pipo_partner_session")
    private var remote = remoteMarker.exists()
    internal val isRemote: Boolean get() = remote
    private var staged: PipoState? = null
    @Synchronized internal fun preview(block: () -> Unit): PipoState {
        check(staged == null)
        staged = mutable.value
        return try { block(); requireNotNull(staged) } finally { staged = null }
    }
    @Synchronized internal fun activateAccount(uid: String) {
        val changed=synchronized(CareRepository.get(context)) {AccountScope.activate(context,uid)}
        if(changed) {
            remoteMarker.delete(); remote=false
            prefs=context.getSharedPreferences(AccountScope.preferencesName(context),Context.MODE_PRIVATE)
            ai.mwm.client.reminders.CareReminders.clear(context)
            mutable.value=load()
            TaskReminders.sync(context,mutable.value)
        }
    }
    /** A new couple is a new workspace. Never import the previous couple's local helpers. */
    @Synchronized internal fun bindSharedSpace(space: String) {
        java.util.UUID.fromString(space)
        val previous=prefs.getString("last_shared_space","") ?: ""
        if(previous==space) return
        val edit=prefs.edit().putString("last_shared_space",space)
        if(previous.isNotBlank()) {
            val old=mutable.value.preferences
            val p=old.startNewCouple(java.time.LocalDate.now().toEpochDay())
            val stored=JSONObject(prefs.getString("state","{}") ?: "{}")
            val own=(stored.optJSONObject("preferences") ?: JSONObject()).put("myName",p.myName).put("avatar",p.avatar).put("started",p.started).put("togetherSince",p.togetherSince).put("partnerName",p.partnerName).put("partnerPhoto","").put("myPhoto",p.myPhoto).put("shareProfile",false).put("code","------").put("codeExpiresAt",0)
            stored.put("preferences",own).put("tasks",JSONArray()).put("lists",JSONArray()).put("notes",JSONArray()).put("tags",JSONArray())
            edit.putString("state",stored.toString()).remove("private_task_notes").remove("personal_reminders")
            check(edit.commit()) {"Could not open the new couple workspace."}
            mutable.value=PipoState(preferences=p)
            ai.mwm.client.cloud.PartnerPokeService.clear(context)
            TaskReminders.sync(context,mutable.value)
        } else check(edit.commit()) {"Could not remember your couple workspace."}
    }
    @Synchronized internal fun savePrivateNotes(tasks: List<Task>) {
        val values=JSONObject(prefs.getString("private_task_notes","{}") ?: "{}")
        tasks.forEach { values.put(it.id,it.note) }
        check(prefs.edit().putString("private_task_notes",values.toString()).commit())
    }
    internal fun privateNote(id: String): String = runCatching {JSONObject(prefs.getString("private_task_notes","{}") ?: "{}").optString(id)}.getOrDefault("")
    @Synchronized internal fun savePersonalReminders(tasks: List<Task>) {
        val values=JSONObject()
        tasks.forEach {values.put(it.id,JSONObject().put("enabled",it.reminder).put("offset",it.offset))}
        check(prefs.edit().putString("personal_reminders",values.toString()).commit())
    }
    internal fun personalReminder(id: String): Pair<Boolean,Int> {
        val item=runCatching {JSONObject(prefs.getString("personal_reminders","{}") ?: "{}").optJSONObject(id)}.getOrNull()
        return (item?.optBoolean("enabled") ?: false) to (item?.optInt("offset") ?: 0)
    }
    @Synchronized internal fun importRemote(next: PipoState) { remoteMarker.writeText("active"); remote = true; mutable.value = next; TaskReminders.sync(context,next) }
    @Synchronized internal fun restoreLocal() { remoteMarker.delete(); remote=false; mutable.value=load(); TaskReminders.sync(context,mutable.value) }
    @Synchronized internal fun change(transform: (PipoState) -> PipoState) {
        val current = staged ?: mutable.value
        val next = transform(current)
        requireOwnPreferencesUpdate(current.preferences, next.preferences)
        if(staged != null) { staged=next; return }
        val p = next.preferences
        val json = JSONObject().put("version", 4).put("tasks", JSONArray(next.tasks.map { t -> JSONObject()
            .put("id", t.id).put("title", t.title).put("owner", t.owner.name).put("day", t.day).put("minute", t.minute ?: -1)
            .put("listId", t.listId).put("note", t.note).put("tags", JSONArray(t.tags)).put("repeat", t.repeat.name)
            .put("reminder", t.reminder).put("offset", t.offset).put("status", t.status.name).put("completedAt", t.completedAt).put("parentId", t.parentId)
            .put("events", JSONArray(t.events.map { JSONObject().put("text", it.text).put("at", it.at).put("owner", it.owner.name) })) }))
            .put("lists", JSONArray(next.lists.map { JSONObject().put("id", it.id).put("name", it.name).put("emoji", it.emoji).put("pinned", it.pinned).put("owner", it.owner.name).put("pictures", JSONArray(it.pictures)) }))
            .put("tags", JSONArray(next.tags.map { JSONObject().put("id", it.id).put("name", it.name) }))
            .put("notes", JSONArray(next.notes.map { JSONObject().put("id", it.id).put("kind", it.kind).put("message", it.message).put("owner", it.owner.name).put("at", it.at).put("taskTitle", it.taskTitle).put("sample", it.sample).put("picture", it.picture) }))
            .put("preferences", JSONObject().put("myName", p.myName).put("partnerName", p.partnerName).put("code", p.code).put("started", p.started).put("avatar", p.avatar).put("quietEnabled", p.quietEnabled).put("quietStart", p.quietStart).put("quietEnd", p.quietEnd).put("style", p.style).put("sound", p.sound).put("haptics", p.haptics).put("myPhoto", p.myPhoto).put("partnerPhoto", p.partnerPhoto).put("pokeOptIn", p.pokeOptIn).put("themeMode", p.themeMode).put("language", p.language).put("codeExpiresAt", p.codeExpiresAt).put("togetherSince", p.togetherSince).put("defaultReminderTime", p.defaultReminderTime).put("keepResetHistory",p.keepResetHistory).put("shareProfile",p.shareProfile))
        val persisted = if(remote) {
            // Never persist partner task/photo data into the local demo workspace.
            val local=JSONObject(prefs.getString("state", "{}") ?: "{}")
            // Account-owned preferences only; never write remote identity into legacy local mode.
            val own=json.getJSONObject("preferences").put("partnerName","Partner").put("partnerPhoto","").put("code","------").put("codeExpiresAt",0)
            local.put("preferences",own).put("version",4)
        } else json
        check(prefs.edit().putString("state", persisted.toString()).commit()) { "Could not save changes" }
        ai.mwm.client.cloud.AccountBackup.pending(context,"main")
        mutable.value = next
        TaskReminders.sync(context, next)
    }
    /** The local session owns only My profile. Partner data is read-only here.
     * Any future sync import must use a separately authenticated partner-owned path. */
    fun updateOwnPreferences(transform: (Preferences) -> Preferences) = change { current ->
        current.copy(preferences = transform(current.preferences))
    }
    fun saveTask(task: Task) = change { s ->
        val old = s.tasks.find { it.id == task.id }
        val saved = task.copy(title = task.title.trim(), note = task.note.trim(), completedAt = if(task.done) { if(old?.done == true) old.completedAt else System.currentTimeMillis() } else 0L, events = (old?.events ?: emptyList()) + TaskEvent(if(old == null) "Created this task" else "Edited this task"))
        val tasks = if(old == null) s.tasks + saved else s.tasks.map { if(it.id == saved.id) saved else it }
        val next = if(saved.done && old?.done != true) nextOccurrence(saved) else null
        s.copy(tasks = tasks + if(next != null && tasks.none { it.parentId == saved.id }) listOf(next) else emptyList())
    }
    fun setStatus(id: String, status: TaskStatus) = change { s ->
        val task = s.tasks.find { it.id == id } ?: return@change s
        if(task.status == status) return@change s
        if(status == TaskStatus.Done) {
            val done = task.copy(status = status, completedAt = System.currentTimeMillis(), events = task.events + TaskEvent("Ticked it off", owner = task.owner))
            val next = nextOccurrence(task)
            s.copy(tasks = s.tasks.map { if(it.id == id) done else it } + if(next != null && s.tasks.none { it.parentId == id }) listOf(next) else emptyList())
        } else {
            // Undo removes only an untouched next occurrence, never a completed occurrence.
            val tasks = if(task.done) s.tasks.filterNot { child ->
                child.parentId == id && child.status == TaskStatus.Todo && child.events.size == 1 &&
                    child.events.first().text.startsWith("Next ") && s.tasks.none { it.parentId == child.id }
            } else s.tasks
            s.copy(tasks = tasks.map { if(it.id == id) it.copy(status = status, completedAt = 0, events = it.events + TaskEvent(if(task.done) "Reopened this task" else "Started this task")) else it })
        }
    }
    fun deleteTask(id: String) = change { state ->
        val deleted = state.tasks.find { it.id == id } ?: return@change state
        // Keep future occurrences and their history editable after deleting an ancestor.
        state.copy(tasks = state.tasks.filterNot { it.id == id }.map {
            if(it.parentId == id) it.copy(parentId = deleted.parentId) else it
        })
    }
    companion object {
        @Volatile private var instance: PipoRepository? = null
        fun get(context: Context): PipoRepository = instance ?: synchronized(this) { instance ?: PipoRepository(context.applicationContext).also { instance = it } }
    }
}

class PipoViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PipoRepository.get(app)
    val state = repository.state
    private val errorState = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = errorState
    private val sync = ai.mwm.client.cloud.PartnerSync(app, repository, viewModelScope)
    val connection = sync.state
    private fun act(after: () -> Unit = {}, block: () -> Unit) {
        val account=AccountScope.current(getApplication())
        viewModelScope.launch(Dispatchers.IO) { runCatching {
            sync.mutate {check(account==AccountScope.current(getApplication())){"Account changed. Reopen this screen before editing."};block()};after()
        }.onFailure { errorState.value = ai.mwm.client.cloud.connectionError(it) } }
    }
    fun authenticate(email: String, password: String, signup: Boolean) = sync.authenticate(email,password,signup)
    fun dismissAuth() = sync.dismissAuth()
    fun signOut() = sync.signOut()
    fun chooseRole(role: String) = sync.chooseRole(role)
    fun connect(action: String, code: String = "") = sync.connect(action,code)
    fun checkConnection() = sync.check()
    fun resumeConnection() = sync.resume()
    fun pauseConnection() = sync.pause()
    fun leaveConnection() = sync.leave()
    fun poke(task: Task) { viewModelScope.launch { runCatching { sync.poke(task) }.onFailure {errorState.value=ai.mwm.client.cloud.connectionError(it)}.onSuccess { reply ->
        errorState.value=when(reply["status"]) {
            "provider_accepted" -> "Poke sent to the notification provider. Phone delivery is not confirmed."
            "no_device" -> "Your partner needs to enable Receive pokes and allow Android notifications."
            "recipient_unavailable" -> "Your partner has pokes off or is in quiet hours."
            "cooldown" -> "Please wait 15 minutes between partner pokes."
            "unavailable", "canceled" -> "This task is no longer available for a Poke."
            "unknown", "pending", "sending", "queued" -> "Poke status is pending. Delivery is not confirmed."
            else -> "Poke was not sent. Check notification setup and retry."
        }
    } } }
    fun clearError() { errorState.value = null }
    fun event(name: String) { runCatching { SparkApplication.spark.analytics.logEvent(type = name) } }
    fun save(task: Task) = act(after={event("task_saved")}) { repository.saveTask(task) }

    /** Editor callback always arrives on Main; it closes only if the remote write succeeded. */
    fun saveWithResult(task: Task, onResult: (String?) -> Unit) {
        val account=AccountScope.current(getApplication())
        viewModelScope.launch {
            val failure=try {
                withContext(Dispatchers.IO) {
                    sync.mutate {
                        check(account==AccountScope.current(getApplication())){"Account changed. Reopen this task before saving."}
                        repository.saveTask(task)
                    }
                }
                event("task_saved")
                null
            } catch(e: CancellationException) { throw e }
              catch(e: Exception) { ai.mwm.client.cloud.connectionError(e) }
            if(failure!=null) errorState.value=failure
            onResult(failure)
        }
    }
    fun status(id: String, value: TaskStatus) = act(after={if(value == TaskStatus.Done) event("task_completed")}) { repository.setStatus(id, value) }
    fun toggle(task: Task) = status(task.id, if(task.done) TaskStatus.Todo else TaskStatus.Done)
    fun delete(id: String) = act { repository.deleteTask(id) }
    fun preferences(update: (Preferences) -> Preferences) = act { repository.updateOwnPreferences(update) }
    fun resetWorkspace() = act {repository.change {it.copy(tasks=emptyList(),lists=emptyList(),tags=emptyList(),notes=if(it.preferences.keepResetHistory) it.notes else emptyList())};event("workspace_reset_local")}
    fun start() = act(after={event("onboarding_completed")}) { repository.change { it.copy(preferences = it.preferences.copy(started = true)) } }
    fun setCode(code: String) = act { repository.change { it.copy(preferences = it.preferences.copy(code = code)) } }
    fun regenerate() = sync.connect("regenerate")
    fun saveList(list: SharedList) = act { repository.change { s -> s.copy(lists = if(s.lists.any { it.id == list.id }) s.lists.map { if(it.id == list.id) list.copy(name = list.name.trim()) else it } else s.lists + list.copy(name = list.name.trim())) } }
    fun saveListWithResult(list: SharedList, onResult: (String?) -> Unit) = mutationWithResult(onResult) {
        repository.change { s ->
            require(list.name.trim().isNotBlank()) { "List name is required." }
            val saved = list.copy(name = list.name.trim())
            s.copy(lists = if(s.lists.any { it.id == list.id }) s.lists.map { if(it.id == list.id) saved else it } else s.lists + saved)
        }
    }
    fun deleteList(id: String) = act { repository.change { s -> s.copy(lists = s.lists.filterNot { it.id == id }, tasks = s.tasks.filterNot { it.listId == id }) } }
    private fun updateTag(tag: Tag) = repository.change { s ->
        val name=tag.name.trim()
        require(name.isNotBlank()) {"Tag name is required."}
        require(s.tags.none {it.id!=tag.id && it.name.equals(name,true)}) {"That tag already exists."}
        val existing=s.tags.find {it.id==tag.id}
        // Older shared tasks stored tag names as IDs. Migrate to a UUID on rename.
        val validId=runCatching {java.util.UUID.fromString(tag.id)}.isSuccess
        val saved=tag.copy(id=if(validId)tag.id else newId(),name=name)
        s.copy(
            tags=if(existing==null) s.tags+saved else s.tags.map {if(it.id==tag.id)saved else it},
            tasks=if(existing!=null && !validId) s.tasks.map {it.copy(tags=it.tags.map {id->if(id==tag.id)saved.id else id})} else s.tasks
        )
    }
    fun saveTag(tag: Tag) = act { updateTag(tag) }

    /** Wait for the shared-tag write before selecting the tag in a new task. */
    fun saveTagWithResult(tag: Tag, onResult: (String?) -> Unit) = mutationWithResult(onResult) { updateTag(tag) }
    fun deleteTagWithResult(id: String, onResult: (String?) -> Unit) = mutationWithResult(onResult) {
        repository.change { s -> s.copy(tags = s.tags.filterNot { it.id == id }, tasks = s.tasks.map { it.copy(tags = it.tags - id) }) }
    }
    private fun mutationWithResult(onResult: (String?) -> Unit, change: () -> Unit) {
        val account=AccountScope.current(getApplication())
        viewModelScope.launch {
            val failure=try {
                withContext(Dispatchers.IO) {
                    sync.mutate {
                        check(account==AccountScope.current(getApplication())){"Account changed. Reopen this screen before saving."}
                        change()
                    }
                }
                null
            } catch(e: CancellationException) { throw e }
              catch(e: Exception) { ai.mwm.client.cloud.connectionError(e) }
            if(failure!=null)errorState.value=failure
            onResult(failure)
        }
    }
    fun deleteTag(id: String) = act { repository.change { s -> s.copy(tags = s.tags.filterNot { it.id == id }, tasks = s.tasks.map { it.copy(tags = it.tags - id) }) } }
    fun sendSupport(message: String,onResult: (String?)->Unit) {
        val account=AccountScope.current(getApplication())
        val target=connection.value.partnerUid
        viewModelScope.launch {
            val failure=try {
                withContext(Dispatchers.IO) {
                    check(connection.value.paired && message.trim().length in 1..500) {"Connect your partner and write a support note first."}
                    sync.mutate {
                        check(account==AccountScope.current(getApplication()) && target==connection.value.partnerUid) {"Account or partner changed. Reopen Care before sending."}
                        repository.change {it.copy(notes=it.notes+Appreciation(kind="CareSupport",message=message.trim(),taskTitle=target))}
                    }
                }
                null
            } catch(e: kotlinx.coroutines.CancellationException) {throw e} catch(e: Exception) {ai.mwm.client.cloud.connectionError(e)}
            onResult(failure)
        }
    }
    fun sendChatNote(id: String,kind: String,message: String,taskTitle: String,picture: String,onResult: (String?)->Unit) {
        val account=AccountScope.current(getApplication())
        val target=connection.value.partnerUid
        viewModelScope.launch {
            val failure=try {
                require(kind in listOf("Message","Thank you","You're the best","Purr-fect")) {"Choose a message type."}
                require(message.trim().length<=240 && (kind!="Message" || message.isNotBlank() || picture.isNotBlank())) {"Write a message or choose a picture."}
                withContext(Dispatchers.IO) {sync.sendChatNote(Appreciation(id=id,kind=kind,message=message.trim(),taskTitle=taskTitle,picture=picture),account,target)}
                null
            } catch(e: CancellationException) {throw e} catch(e: Exception) {ai.mwm.client.cloud.connectionError(e)}
            onResult(failure)
        }
    }
    fun thank(kind: String, message: String, taskTitle: String = "", picture: String = "") = act(after={event(if(connection.value.connected) "appreciation_shared" else "appreciation_saved_local")}) { repository.change { it.copy(notes = it.notes + Appreciation(kind = kind, message = message.trim(), taskTitle = taskTitle,picture=picture)) } }
    fun deleteNote(id: String) = act { repository.change { s -> s.copy(notes = s.notes.filterNot { it.id == id }) } }
    fun syncReminders() = act { TaskReminders.sync(getApplication(), state.value, restore = true) }
    fun testReminder() = act {
        val now = java.time.LocalDateTime.now()
        repository.saveTask(Task(title = "Check your reminder", owner = Owner.Me, day = now.toLocalDate().toEpochDay(), minute = now.hour * 60 + now.minute, listId = state.value.lists.firstOrNull()?.id ?: "home", reminder = true, note = "Complete this task here or in the notification to clear it."))
        event("reminder_test_created")
    }
}

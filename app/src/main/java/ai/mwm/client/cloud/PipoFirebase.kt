package ai.mwm.client.cloud

import ai.mwm.client.data.*
import android.content.Context
import org.json.*
import java.time.OffsetDateTime
import java.util.UUID

/** Firebase is retained ONLY as a disabled FCM transport, never for pairing or shared data. */
object CloudRelease { const val ENABLED = true }
class NoMembership: Exception("No active shared space. Create a space or join your partner's code.")

/** Compatibility name for existing callers; all shared-data requests now use Supabase. */
class PipoFirebase private constructor(context: Context) {
    private val client=SupabaseClient(context)
    private val appContext=context.applicationContext
    suspend fun hasSession()=client.hasSession()
    suspend fun authenticate(email: String,password: String,signup: Boolean)=client.authenticate(email,password,signup)
    suspend fun signOut()=client.signOut()
    private fun obj(value: Any?)=value as? JSONObject ?: error("Invalid Supabase response")
    private fun rows(value: Any?): List<JSONObject> {val a=value as? JSONArray ?: error("Invalid Supabase collection");return (0 until a.length()).map {a.getJSONObject(it)}}
    private fun map(o: JSONObject): Map<String,Any?> = o.keys().asSequence().associateWith {k->convert(o.get(k))}
    private fun convert(v: Any?): Any? = when(v) {JSONObject.NULL->null;is JSONObject->map(v);is JSONArray->(0 until v.length()).map {convert(v.get(it))};else->v}
    suspend fun identity(): Pair<String,String> {
        val user=obj(client.request("/auth/v1/user"))
        return user.getString("id") to user.optString("email")
    }
    suspend fun probe(): Map<String,Any?> {identity();return mapOf("version" to 2)}
    suspend fun createSpace(role: String): Map<String,Any?> {
        val sid=client.rpc("create_partner_space_with_role",JSONObject().put("p_role",role)) as? String ?: error("Invalid space response")
        UUID.fromString(sid)
        return mapOf("spaceId" to sid)
    }
    suspend fun issueCode()=map(obj(client.rpc("issue_pair_code")))
    suspend fun joinSpace(code: String,role: String)=map(obj(client.rpc("redeem_pair_code_with_role",JSONObject().put("p_code",code).put("p_role",role))))
    suspend fun leaveSpace(): Map<String,Any?> {client.rpc("leave_partner_space");return emptyMap()}
    private suspend fun records(sid: String)=rows(client.request("/rest/v1/shared_records?space_id=eq.$sid&deleted_at=is.null&select=*"))
    private suspend fun space(): String {
        val uid=client.uid()
        return rows(client.request("/rest/v1/space_members?user_id=eq.$uid&select=space_id")).firstOrNull()?.getString("space_id") ?: throw NoMembership()
    }
    suspend fun snapshot(): Map<String,Any?> {
        val uid=client.uid();val sid=space()
        val members=rows(client.request("/rest/v1/space_members?space_id=eq.$sid&select=user_id,member_role"))
        val active=rows(client.request("/rest/v1/partner_spaces?id=eq.$sid&closed_at=is.null&select=id"))
        if(active.isEmpty() || members.none {it.getString("user_id")==uid})throw NoMembership()
        val rs=records(sid)
        val date=rows(client.request("/rest/v1/couple_dates?space_id=eq.$sid&select=since_day,version")).singleOrNull()
        fun payload(r: JSONObject): Map<String,Any?> {
            val value=r.getJSONObject("payload")
            val uploadTime=if(r.getString("kind")=="appreciation" && value.optString("kind")=="Locket") mapOf("at" to OffsetDateTime.parse(r.getString("updated_at")).toInstant().toEpochMilli()) else emptyMap()
            return map(value)+uploadTime+mapOf("id" to r.getString("id"),"version" to r.getLong("version"),"assigneeUid" to convert(r.opt("assigned_to")),"sender" to r.getString("created_by"),"uid" to r.getString("created_by"))
        }
        val self=members.single {it.getString("user_id")==uid}
        val partner=members.singleOrNull {it.getString("user_id")!=uid}
        val role=self.getString("member_role")
        check(role in listOf("user_1","user_2") && (partner==null || partner.getString("member_role")!=role)){"Invalid member roles. Shared edits are blocked."}
        return mapOf("togetherDate" to date?.let(::map),"role" to role,"partnerRole" to (partner?.getString("member_role") ?: ""),"uid" to uid,"spaceId" to sid,"generation" to sid,"partnerUid" to (members.firstOrNull {it.getString("user_id")!=uid}?.getString("user_id") ?: ""),"tasks" to rs.filter {it.getString("kind")=="task"}.map(::payload),"lists" to rs.filter {it.getString("kind")=="list"}.map(::payload),"profiles" to rs.filter {it.getString("kind")=="profile"}.map(::payload),"notes" to rs.filter {it.getString("kind")=="appreciation"}.map(::payload),"tags" to rs.filter {it.getString("kind")=="tag"}.map(::payload))
    }
    suspend fun saveTogetherDate(expectedUid:String,expectedSpace:String,day:Long,expectedVersion:Long):Map<String,Any?> {
        require(validTogetherDate(day)) {"Choose a date from 1900 through today."}
        check(client.uid()==expectedUid && space()==expectedSpace) {"Account or partner changed. Reopen settings."}
        return map(obj(client.rpc("save_couple_date",JSONObject().put("p_space",expectedSpace).put("p_day",day).put("p_expected",expectedVersion))))
    }
    /** Chat refresh does not load tasks, profiles, backups, or notification registration. */
    suspend fun chatSnapshot(expectedUid: String,expectedSpace: String): List<Appreciation> {
        check(client.uid()==expectedUid && space()==expectedSpace) {"Account or partner space changed. Reopen chat."}
        return rows(client.request("/rest/v1/shared_records?space_id=eq.$expectedSpace&kind=eq.appreciation&deleted_at=is.null&select=id,created_by,payload,updated_at")).map {r->chatNote(r,expectedUid)}.filter {it.kind!="Locket"}
    }
    private fun chatNote(r: JSONObject,uid: String): Appreciation {
        val p=r.getJSONObject("payload")
        return Appreciation(id=r.getString("id"),kind=p.getString("kind"),message=p.optString("message"),owner=if(r.getString("created_by")==uid) Owner.Me else Owner.You,
            at=p.optLong("at",OffsetDateTime.parse(r.getString("updated_at")).toInstant().toEpochMilli()),taskTitle=p.optString("taskTitle"),picture=p.optString("picture"),replyTo=p.optString("replyTo"))
    }
    suspend fun chatReceipts(expectedUid: String,expectedSpace: String): Set<String> {
        check(client.uid()==expectedUid) {"Account changed."}
        return rows(client.request("/rest/v1/chat_read_receipts?space_id=eq.$expectedSpace&reader_id=neq.$expectedUid&select=message_id")).map {it.getString("message_id")}.toSet()
    }
    suspend fun markChatRead(expectedUid: String,expectedSpace: String,ids: Set<String>) {
        check(client.uid()==expectedUid) {"Account changed."}
        if(ids.isNotEmpty()) client.rpc("mark_chat_read",JSONObject().put("p_space",expectedSpace).put("p_ids",JSONArray(ids.take(200))))
    }
    suspend fun chatReactions(expectedUid:String,expectedSpace:String):List<ChatReaction> {
        check(client.uid()==expectedUid && space()==expectedSpace) {"Account or partner changed. Reopen chat."}
        UUID.fromString(expectedSpace)
        return rows(client.request("/rest/v1/chat_reactions?space_id=eq.$expectedSpace&select=message_id,user_id,emoji"))
            .map {ChatReaction(it.getString("message_id"),it.getString("user_id"),it.getString("emoji"))}
    }
    /** Explicit desired state makes a retry safe even if the previous response was lost. */
    suspend fun setChatReaction(expectedUid:String,expectedSpace:String,messageId:String,emoji:String) {
        UUID.fromString(messageId);UUID.fromString(expectedSpace)
        require(emoji.isEmpty() || emoji in CHAT_EMOJIS) {"Choose an emoji from the picker."}
        check(client.uid()==expectedUid && space()==expectedSpace) {"Account or partner changed. Reopen chat."}
        client.rpc("set_chat_reaction",JSONObject().put("p_space",expectedSpace).put("p_message",messageId).put("p_emoji",emoji))
    }
    suspend fun saveChatNote(note: Appreciation,expectedUid: String,expectedSpace: String): Appreciation {
        UUID.fromString(note.id)
        check(client.uid()==expectedUid && space()==expectedSpace) {"Account or partner changed. Reopen chat."}
        fun path()="/rest/v1/shared_records?id=eq.${note.id}&space_id=eq.$expectedSpace&select=id,created_by,payload,updated_at,deleted_at"
        val old=rows(client.request(path())).singleOrNull()
        if(old!=null) {
            check(old.getString("created_by")==expectedUid && old.isNull("deleted_at")) {"This message is no longer available."}
            return chatNote(old,expectedUid) // uncertain retry keeps the original message, time and photo
        }
        val photo=if(note.picture.isBlank()) "" else preparePhoto(note.picture)
        check(client.uid()==expectedUid && space()==expectedSpace) {"Partner changed while uploading. Reopen chat."}
        client.rpc("mutate_shared_record",JSONObject().put("p_id",note.id).put("p_space",expectedSpace).put("p_kind","appreciation")
            .put("p_parent",JSONObject.NULL).put("p_assigned",JSONObject.NULL)
            .put("p_payload",JSONObject().put("kind",note.kind).put("message",note.message.trim()).put("taskTitle",note.taskTitle).put("picture",photo).put("at",note.at).put("replyTo",note.replyTo))
            .put("p_expected_version",0).put("p_mutation",note.id).put("p_deleted",false))
        val saved=rows(client.request(path())).singleOrNull() ?: error("Message confirmation is pending. Retry with the same message.")
        return chatNote(saved,expectedUid)
    }
    private suspend fun write(id: String,kind: String,payload: Map<String,Any?>,version: Long,parent: String?=null,assigned: String?=null,deleted: Boolean=false): Map<String,Any?> {
        UUID.fromString(id);val sid=space()
        val body=JSONObject().put("p_id",id).put("p_space",sid).put("p_kind",kind).put("p_parent",parent ?: JSONObject.NULL).put("p_assigned",assigned ?: JSONObject.NULL).put("p_payload",JSONObject(payload)).put("p_expected_version",version).put("p_mutation",UUID.randomUUID().toString()).put("p_deleted",deleted)
        return map(obj(client.rpc("mutate_shared_record",body)))
    }
    private fun assigned(owner: Owner,uid: String,partner: String?)=when(owner){Owner.Me->uid;Owner.You->requireNotNull(partner){"Partner must be paired"};Owner.Together->null}
    suspend fun saveList(list: SharedList,uid: String,partner: String?,version: Long): Map<String,Any?> {
        check(uid==client.uid()) {"Account changed. Reopen this list."}
        return write(list.id,"list",mapOf("name" to list.name,"emoji" to list.emoji,"pinned" to list.pinned,"pictures" to list.pictures),version,assigned=assigned(list.owner,uid,partner))
    }
    suspend fun saveTask(task: Task,uid: String,partner: String?,version: Long): Map<String,Any?> {
        check(uid==client.uid()) {"Account changed. Reopen this task."}
        return write(task.id,"task",mapOf("title" to task.title,"listId" to task.listId,"day" to task.day,"minute" to task.minute,"repeat" to task.repeat.name,"status" to task.status.name,"tags" to task.tags,"parentId" to task.parentId,"completedAt" to task.completedAt),version,parent=task.parentId.ifBlank {task.listId}.takeIf {runCatching {UUID.fromString(it)}.isSuccess},assigned=assigned(task.owner,uid,partner))
    }
    private suspend fun delete(id: String,kind: String,version: Long): Map<String,Any?> {
        val r=records(space()).firstOrNull {it.getString("id")==id} ?: return emptyMap()
        return write(id,kind,map(r.getJSONObject("payload")),version,r.optString("parent_id").takeUnless {it=="null" || it.isBlank()},r.optString("assigned_to").takeUnless {it=="null" || it.isBlank()},true)
    }
    /** Tag names are stored once; task payloads refer to stable tag IDs. */
    suspend fun saveTag(tag: Tag, version: Long): Map<String,Any?> {
        require(tag.name.trim().isNotBlank()) { "Tag name is required." }
        return write(tag.id,"tag",mapOf("name" to tag.name.trim()),version)
    }
    suspend fun deleteTag(id: String, version: Long): Map<String,Any?> = delete(id,"tag",version)
    suspend fun deleteTask(id: String,version: Long)=delete(id,"task",version)
    suspend fun deleteList(id: String,version: Long)=delete(id,"list",version)
    suspend fun call(action: String,body: Map<String,Any?> = emptyMap()): Map<String,Any?> = when(action) {
        "validatePoke" -> validatePoke(body["eventId"] as String)
        "regenerate"->issueCode()
        "profile"->{
            val uid=client.uid();val old=records(space()).firstOrNull {it.getString("kind")=="profile" && it.getString("created_by")==uid}
            @Suppress("UNCHECKED_CAST") val value=body["value"] as Map<String,Any?>
            write(old?.getString("id") ?: UUID.randomUUID().toString(),"profile",value,old?.getLong("version") ?: 0)
        }
        "deleteProfile"->{val uid=client.uid();val old=records(space()).firstOrNull {it.getString("kind")=="profile" && it.getString("created_by")==uid};if(old==null) emptyMap() else delete(old.getString("id"),"profile",old.getLong("version"))}
        "saveNote"->{val id=body["id"] as String;val old=records(space()).firstOrNull {it.getString("id")==id}
            @Suppress("UNCHECKED_CAST") val value=body["value"] as Map<String,Any?>
            write(id,"appreciation",value+mapOf("at" to System.currentTimeMillis()),old?.getLong("version") ?: 0)
        }
        "deleteNote"->{val id=body["id"] as String;val old=records(space()).firstOrNull {it.getString("id")==id};delete(id,"appreciation",old?.getLong("version") ?: 0)}
        else->error("This operation is not available on the Supabase connection.")
    }
    suspend fun ensureProfile(p: Preferences) {
        val uid=client.uid()
        val old=records(space()).firstOrNull {it.getString("kind")=="profile" && it.getString("created_by")==uid}
        val previous=old?.getJSONObject("payload")
        val name=p.myName.trim().ifBlank {"Me"}
        val zone=clockZone(p.clockZone).id
        val photo=if(p.myPhoto.isBlank() || p.myPhoto.startsWith("asset:")) "" else p.myPhoto
        if(previous==null || previous.optString("name")!=name || previous.optString("photo")!=photo || previous.optInt("avatar",-1)!=p.avatar || previous.optString("timezone")!=zone) {
            write(old?.getString("id") ?: UUID.randomUUID().toString(),"profile",mapOf("name" to name,"avatar" to p.avatar,"photo" to if(photo.isBlank()) "" else preparePhoto(photo),"timezone" to zone),old?.getLong("version") ?: 0)
        }
    }
    private var pushKey = ""
    private var pushAt = 0L
    private val pushLock = kotlinx.coroutines.sync.Mutex()
    private fun deviceId(): String {
        val store=appContext.getSharedPreferences("pipo_push_installation",0)
        return store.getString("id",null) ?: UUID.randomUUID().toString().also {
            check(store.edit().putString("id",it).commit())
        }
    }
    suspend fun updateDevice(p: Preferences,force: Boolean=false) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        pushLock.lock()
        try {
            val uid=client.uid()
            val zone=java.time.ZoneId.systemDefault().id
            val signature="$uid:${p.pokeOptIn}:$zone:${p.quietEnabled}:${p.quietStart}:${p.quietEnd}"
            if(!force && signature==pushKey && System.currentTimeMillis()-pushAt<3_600_000L) return@withContext
            PartnerPokeService.channel(appContext)
            client.rpc("set_poke_preferences",JSONObject().put("p_opt_in",p.pokeOptIn).put("p_timezone",zone)
                .put("p_quiet_enabled",p.quietEnabled).put("p_quiet_start",p.quietStart).put("p_quiet_end",p.quietEnd))
            val messaging=com.google.firebase.messaging.FirebaseMessaging.getInstance()
            if(p.pokeOptIn) {
                messaging.isAutoInitEnabled=true
                val token=com.google.android.gms.tasks.Tasks.await(messaging.token,15,java.util.concurrent.TimeUnit.SECONDS)
                check(uid==client.uid()) {"Account changed. Retry notification setup."}
                client.rpc("register_push_device",JSONObject().put("p_device",deviceId()).put("p_token",token))
            } else {
                client.rpc("register_push_device",JSONObject().put("p_device",deviceId()).put("p_token",JSONObject.NULL))
                messaging.isAutoInitEnabled=false
                PartnerPokeService.clear(appContext)
            }
            pushKey=signature;pushAt=System.currentTimeMillis()
        } finally {pushLock.unlock()}
    }
    suspend fun revokeDevice() {
        if(client.hasSession()) client.rpc("register_push_device",JSONObject().put("p_device",deviceId()).put("p_token",JSONObject.NULL))
        pushKey="";pushAt=0
        com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled=false
    }
    suspend fun poke(id: String,version: Long,nonce: String): Map<String,Any?> {
        UUID.fromString(id);UUID.fromString(nonce)
        return map(obj(client.request("/functions/v1/partner-poke","POST",JSONObject().put("task_id",id).put("request_id",nonce))))
    }
    suspend fun validatePoke(event: String): Map<String,Any?> {
        UUID.fromString(event)
        check(PipoRepository.get(appContext).state.value.preferences.pokeOptIn) {"Pokes are disabled on this phone"}
        val uid=client.uid()
        val e=rows(client.request("/rest/v1/poke_events?id=eq.$event&select=*")).singleOrNull() ?: error("Poke is no longer available")
        check(e.getString("recipient_id")==uid && e.getString("status") in listOf("sending","provider_accepted","unknown")) {"Poke unavailable"}
        val expires=OffsetDateTime.parse(e.getString("expires_at")).toInstant().toEpochMilli()
        check(expires>System.currentTimeMillis()) {"Poke expired"}
        val tid=e.getString("task_id");UUID.fromString(tid)
        val task=rows(client.request("/rest/v1/shared_records?id=eq.$tid&kind=eq.task&deleted_at=is.null&select=*")).singleOrNull() ?: error("Task unavailable")
        check(task.getString("space_id")==e.getString("space_id") && task.getJSONObject("payload").optString("status")!="Done") {"Task is already complete"}
        val assignee=task.optString("assigned_to").takeUnless {it=="null" || it.isBlank()}
        check(assignee==null || assignee==uid) {"Task assignment changed"}
        check(uid==client.uid()) {"Account changed"}
        return mapOf("taskId" to tid,"expiresAt" to expires,"recipientUid" to uid,"senderUid" to e.getString("sender_id"))
    }
    suspend fun careSnapshot(): JSONObject {
        val uid=client.uid();val sid=space()
        val entries=client.request("/rest/v1/care_shares?space_id=eq.$sid&select=user_id,enabled,payload,version") as? JSONArray ?: error("Care response unavailable")
        check(uid==client.uid()) {"Account changed"}
        return JSONObject().put("uid",uid).put("spaceId",sid).put("entries",entries)
    }
    suspend fun saveCareShare(sid: String,enabled: Boolean,payload: JSONObject,version: Long): JSONObject {
        check(sid==space()) {"Shared space changed. Reopen Care."}
        return obj(client.rpc("save_care_share",JSONObject().put("p_space",sid).put("p_enabled",enabled).put("p_payload",payload).put("p_expected_version",version)))
    }
    suspend fun notifyLocket(id: String): String {
        UUID.fromString(id)
        return obj(client.request("/functions/v1/partner-locket","POST",JSONObject().put("record_id",id))).optString("status","unknown")
    }
    suspend fun validateLocket(id: String): String {
        UUID.fromString(id)
        val uid=client.uid()
        val record=rows(client.request("/rest/v1/shared_records?id=eq.$id&kind=eq.appreciation&deleted_at=is.null&select=created_by,payload,updated_at")).singleOrNull() ?: error("Locket unavailable")
        val payload=record.getJSONObject("payload")
        check(record.getString("created_by")!=uid && payload.optString("kind")=="Locket" && payload.optString("picture").startsWith("cloud:")) {"Locket unavailable"}
        val age=System.currentTimeMillis()-OffsetDateTime.parse(record.getString("updated_at")).toInstant().toEpochMilli()
        check(age in -60_000L..86_400_000L && uid==client.uid()) {"Locket expired or account changed"}
        return uid
    }
    suspend fun notifyPartnerNote(id: String): String {
        UUID.fromString(id)
        return obj(client.request("/functions/v1/partner-note","POST",JSONObject().put("record_id",id))).optString("status","unknown")
    }
    suspend fun validatePartnerNote(id: String): JSONObject {
        UUID.fromString(id)
        val uid=client.uid();val sid=space()
        val record=rows(client.request("/rest/v1/shared_records?id=eq.$id&space_id=eq.$sid&kind=eq.appreciation&deleted_at=is.null&select=created_by,payload,updated_at")).singleOrNull() ?: error("Note unavailable")
        val payload=record.getJSONObject("payload")
        check(record.getString("created_by")!=uid && payload.optString("kind") in listOf("Message","Thank you","You're the best","Purr-fect","CareSupport","Heart")) {"Note unavailable"}
        val at=OffsetDateTime.parse(record.getString("updated_at")).toInstant().toEpochMilli()
        check(System.currentTimeMillis()-at in -60_000L..86_400_000L && uid==client.uid()) {"Note expired or account changed"}
        return JSONObject().put("uid",uid).put("space",sid).put("sender",record.getString("created_by")).put("kind",payload.optString("kind")).put("message",payload.optString("message").take(500)).put("at",at)
    }
    suspend fun locketSender(id: String): String {
        UUID.fromString(id)
        return rows(client.request("/rest/v1/shared_records?id=eq.$id&kind=eq.appreciation&deleted_at=is.null&select=created_by")).single().getString("created_by")
    }
    suspend fun notificationPerson(sender: String): Pair<String,String> {
        UUID.fromString(sender)
        val uid=client.uid();val sid=space()
        val members=rows(client.request("/rest/v1/space_members?space_id=eq.$sid&select=user_id"))
        check(sender!=uid && members.any {it.getString("user_id")==sender} && members.any {it.getString("user_id")==uid}) {"Partner changed"}
        val profile=rows(client.request("/rest/v1/shared_records?space_id=eq.$sid&kind=eq.profile&created_by=eq.$sender&deleted_at=is.null&select=payload")).firstOrNull()?.getJSONObject("payload")
        check(uid==client.uid()) {"Account changed"}
        return (profile?.optString("name")?.take(80)?.takeIf {it.isNotBlank()} ?: "Your partner") to (profile?.optString("photo") ?: "")
    }
    private suspend fun scopedKey(photo: String): String {
        require(photo.startsWith("cloud:")) {"Invalid shared picture."}
        val key=photo.removePrefix("cloud:")
        require(Regex("[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}\\.jpg").matches(key)) {"Invalid shared picture ID."}
        require(key.substringBefore('/')==space()) {"This picture belongs to another shared space."}
        return key
    }
    suspend fun preparePhoto(photo: String): String {
        if(photo.startsWith("cloud:")) {scopedKey(photo);return photo}
        // Gallery/camera copy files into this account's private app directory.
        val owner=AccountScope.current(appContext)
        val dir=java.io.File(appContext.noBackupFilesDir,"pictures_$owner").canonicalFile
        val image=java.io.File(photo).canonicalFile
        require(image.parentFile==dir && image.isFile) {"Choose a picture from the app's gallery or camera picker."}
        require(image.length() in 1..5_242_880) {"Choose a picture smaller than 5 MB."}
        val key=space()+"/"+UUID.randomUUID()+".jpg"
        client.photoObject(key,image.readBytes())
        return "cloud:$key"
    }
    suspend fun readPhoto(photo: String): ByteArray=client.photoObject(scopedKey(photo))
    companion object {@Volatile private var instance: PipoFirebase?=null;fun get(context: Context)=instance ?: synchronized(this){instance ?: PipoFirebase(context.applicationContext).also {instance=it}}}
}

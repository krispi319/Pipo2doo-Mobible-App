package ai.mwm.client.cloud

import ai.mwm.client.data.*
import android.content.Context
import org.json.*
import java.time.OffsetDateTime
import java.util.UUID

/** Firebase is retained ONLY as a disabled FCM transport, never for pairing or shared data. */
object CloudRelease { const val ENABLED = false }
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
        fun payload(r: JSONObject)=map(r.getJSONObject("payload")) + mapOf("id" to r.getString("id"),"version" to r.getLong("version"),"assigneeUid" to convert(r.opt("assigned_to")),"sender" to r.getString("created_by"),"uid" to r.getString("created_by"))
        val self=members.single {it.getString("user_id")==uid}
        val partner=members.singleOrNull {it.getString("user_id")!=uid}
        val role=self.getString("member_role")
        check(role in listOf("user_1","user_2") && (partner==null || partner.getString("member_role")!=role)){"Invalid member roles. Shared edits are blocked."}
        return mapOf("role" to role,"partnerRole" to (partner?.getString("member_role") ?: ""),"uid" to uid,"spaceId" to sid,"generation" to sid,"partnerUid" to (members.firstOrNull {it.getString("user_id")!=uid}?.getString("user_id") ?: ""),"tasks" to rs.filter {it.getString("kind")=="task"}.map(::payload),"lists" to rs.filter {it.getString("kind")=="list"}.map(::payload),"profiles" to rs.filter {it.getString("kind")=="profile"}.map(::payload),"notes" to rs.filter {it.getString("kind")=="appreciation"}.map(::payload),"tags" to rs.filter {it.getString("kind")=="tag"}.map(::payload))
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
        if(records(space()).none {it.getString("kind")=="profile" && it.getString("created_by")==uid}) {
            write(UUID.randomUUID().toString(),"profile",mapOf("name" to p.myName,"avatar" to p.avatar,"photo" to ""),0)
        }
    }
    suspend fun updateDevice(p: Preferences,force: Boolean=false) { /* FCM delivery is deliberately separate and unverified. */ }
    suspend fun revokeDevice() {}
    suspend fun poke(id: String,version: Long,nonce: String): Map<String,Any?> = error("Poke delivery is not available yet. Pairing does not enable Android push.")
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

package ai.mwm.client.cloud
import ai.mwm.client.data.*
import org.json.*
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
class ChatClient {
 var user="alice";var sid="space";var photoUploads=0;var writes=0
 val saved=mutableMapOf<String,JSONObject>()
 val receipts=mutableSetOf<String>()
 suspend fun uid()=user
 suspend fun request(path:String):Any {
  if(path.contains("chat_read_receipts"))return JSONArray(receipts.map {JSONObject().put("message_id",it)})
  val rows=if(path.contains("?id=eq."))listOfNotNull(saved[path.substringAfter("?id=eq.").substringBefore('&')]) else saved.values.toList()
  return JSONArray(rows)
 }
 suspend fun rpc(name:String,body:JSONObject):Any {
  if(name=="mark_chat_read")return 1
  writes++
  val id=body.getString("p_id")
  check(!saved.containsKey(id))
  saved[id]=JSONObject().put("id",id).put("created_by",user).put("payload",body.getJSONObject("p_payload")).put("updated_at","2026-10-17T12:00:00Z").put("deleted_at",JSONObject.NULL)
  return JSONObject().put("version",1)
 }
}
class ChatApi(val client:ChatClient) {
 private fun rows(v:Any?):List<JSONObject> {val a=v as JSONArray;return (0 until a.length()).map {a.getJSONObject(it)}}
 private suspend fun space()=client.sid
 private suspend fun preparePhoto(photo:String):String {client.photoUploads++;return "cloud:space/photo.jpg"}
    /** Chat refresh does not load tasks, profiles, backups, or notification registration. */
    suspend fun chatSnapshot(expectedUid: String,expectedSpace: String): List<Appreciation> {
        check(client.uid()==expectedUid && space()==expectedSpace) {"Account or partner space changed. Reopen chat."}
        return rows(client.request("/rest/v1/shared_records?space_id=eq.$expectedSpace&kind=eq.appreciation&deleted_at=is.null&select=id,created_by,payload,updated_at")).map {r->chatNote(r,expectedUid)}.filter {it.kind!="Locket"}
    }
    private fun chatNote(r: JSONObject,uid: String): Appreciation {
        val p=r.getJSONObject("payload")
        return Appreciation(id=r.getString("id"),kind=p.getString("kind"),message=p.optString("message"),owner=if(r.getString("created_by")==uid) Owner.Me else Owner.You,
            at=p.optLong("at",OffsetDateTime.parse(r.getString("updated_at")).toInstant().toEpochMilli()),taskTitle=p.optString("taskTitle"),picture=p.optString("picture"))
    }
    suspend fun chatReceipts(expectedUid: String,expectedSpace: String): Set<String> {
        check(client.uid()==expectedUid) {"Account changed."}
        return rows(client.request("/rest/v1/chat_read_receipts?space_id=eq.$expectedSpace&reader_id=neq.$expectedUid&select=message_id")).map {it.getString("message_id")}.toSet()
    }
    suspend fun markChatRead(expectedUid: String,expectedSpace: String,ids: Set<String>) {
        check(client.uid()==expectedUid) {"Account changed."}
        if(ids.isNotEmpty()) client.rpc("mark_chat_read",JSONObject().put("p_space",expectedSpace).put("p_ids",JSONArray(ids.take(200))))
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
            .put("p_payload",JSONObject().put("kind",note.kind).put("message",note.message.trim()).put("taskTitle",note.taskTitle).put("picture",photo).put("at",note.at))
            .put("p_expected_version",0).put("p_mutation",note.id).put("p_deleted",false))
        val saved=rows(client.request(path())).singleOrNull() ?: error("Message confirmation is pending. Retry with the same message.")
        return chatNote(saved,expectedUid)
    }

}
fun main()= runBlocking {
 val client=ChatClient();val api=ChatApi(client);var checks=0
 fun verify(ok:Boolean,name:String){check(ok){name};checks++;println("PASS $name")}
 val note=Appreciation(id=UUID.randomUUID().toString(),kind="Message",message=" hello ",picture="local.jpg",at=55)
 val sent=api.saveChatNote(note,"alice","space")
 verify(sent.message=="hello" && sent.picture.startsWith("cloud:"),"send confirms normalized saved photo message")
 verify(client.writes==1 && client.photoUploads==1,"first send uploads and writes once")
 val retried=api.saveChatNote(note.copy(at=99),"alice","space")
 verify(retried.at==55L && client.writes==1 && client.photoUploads==1,"uncertain retry preserves original message and avoids duplicate image upload")
 verify(api.chatSnapshot("alice","space").single().owner==Owner.Me,"own photo message returned to sender")
 client.user="bob"
 verify(api.chatSnapshot("bob","space").single().owner==Owner.You,"partner message returned to receiver")
 try {api.saveChatNote(note,"alice","space");error("identity guard missing")}catch(e:IllegalStateException){verify(e.message!!.contains("changed"),"stale account send rejected")}
 client.sid="new-space"
 try {api.chatSnapshot("bob","space");error("space guard missing")}catch(e:IllegalStateException){verify(e.message!!.contains("changed"),"stale partner space rejected")}
 println("$checks actual chat API checks passed with HTTP/storage doubles")
}

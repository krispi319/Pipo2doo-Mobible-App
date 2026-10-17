import ai.mwm.client.data.*
import org.json.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
class FakeClient {
 var user="alice";var sid=UUID.randomUUID().toString();var calls=0;var path=""
 var last=JSONObject()
 suspend fun uid()=user
 suspend fun request(p:String):Any {path=p;return JSONArray().put(JSONObject().put("message_id","m").put("user_id","bob").put("emoji","❤️"))}
 suspend fun rpc(name:String,body:JSONObject) {check(name=="set_chat_reaction");calls++;last=body}
}
class Api(val client:FakeClient) {
 private suspend fun space()=client.sid
 private fun rows(value:Any?):List<JSONObject> {val a=value as JSONArray;return (0 until a.length()).map {a.getJSONObject(it)}}
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
 }
fun main()=runBlocking {
 val c=FakeClient();val api=Api(c);val id=UUID.randomUUID().toString();var n=0
 fun test(ok:Boolean) {check(ok);n++}
 api.setChatReaction("alice",c.sid,id,"❤️")
 test(c.last.getString("p_message")==id && c.last.getString("p_space")==c.sid && c.last.getString("p_emoji")=="❤️")
 api.setChatReaction("alice",c.sid,id,"")
 test(c.last.getString("p_emoji")=="")
 val before=c.calls
 test(runCatching {api.setChatReaction("bob",c.sid,id,"❤️")}.isFailure)
 test(runCatching {api.setChatReaction("alice",UUID.randomUUID().toString(),id,"❤️")}.isFailure)
 test(runCatching {api.setChatReaction("alice",c.sid,"bad","❤️")}.isFailure)
 test(runCatching {api.setChatReaction("alice",c.sid,id,"bad")}.isFailure)
 test(c.calls==before)
 test(api.chatReactions("alice",c.sid)==listOf(ChatReaction("m","bob","❤️")))
 test(c.path=="/rest/v1/chat_reactions?space_id=eq.${c.sid}&select=message_id,user_id,emoji")
 test(runCatching {api.chatReactions("bob",c.sid)}.isFailure)
 test(runCatching {api.chatReactions("alice",UUID.randomUUID().toString())}.isFailure)
 println("$n actual reaction API checks passed with transport doubles")
}

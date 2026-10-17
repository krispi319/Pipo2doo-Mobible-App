package ai.mwm.client.cloud
import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
class SupabaseClient(val c:Context) {
 companion object {
 val rows=mutableMapOf<String,JSONObject>()
 var beforeGet:(()->Unit)?=null
 var afterWrite:(()->Unit)?=null
 var offline=false
 }
 suspend fun uid()=c.owner
 suspend fun request(path:String):Any {
  if(offline)error("Offline")
  beforeGet?.also {beforeGet=null;it()}
  val section=path.substringAfter("section=eq.").substringBefore('&')
  return JSONArray().also {a->rows[c.owner+section]?.let {a.put(JSONObject(it.toString()))}}
 }
 suspend fun rpc(name:String,body:JSONObject):Any {
  val key=c.owner+body.getString("p_section");val old=rows[key]?.getLong("version") ?: 0L
  check(body.getLong("p_expected")==old){"Conflict"}
  rows[key]=JSONObject().put("version",old+1).put("payload",JSONObject(body.getJSONObject("p_payload").toString()))
  afterWrite?.also {afterWrite=null;it()}
  return JSONObject().put("version",old+1)
 }
}

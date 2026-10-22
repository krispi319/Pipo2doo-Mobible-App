import ai.mwm.client.data.*
import org.json.*
import kotlinx.coroutines.runBlocking
class Client {var user="a";var space="s";var calls=0;var body=JSONObject()
 suspend fun uid()=user
 suspend fun rpc(n:String,b:JSONObject):Any {check(n=="save_couple_date");calls++;body=b;return JSONObject().put("since_day",b.getLong("p_day")).put("version",b.getLong("p_expected")+1)}
}
class Api(val client:Client) {
 private fun obj(v:Any?)=v as JSONObject
 private fun map(o:JSONObject):Map<String,Any?> = o.keys().asSequence().associateWith {o.get(it)}
 private suspend fun space()=client.space
    suspend fun saveTogetherDate(expectedUid:String,expectedSpace:String,day:Long,expectedVersion:Long):Map<String,Any?> {
        require(validTogetherDate(day)) {"Choose a date from 1900 through today."}
        check(client.uid()==expectedUid && space()==expectedSpace) {"Account or partner changed. Reopen settings."}
        return map(obj(client.rpc("save_couple_date",JSONObject().put("p_space",expectedSpace).put("p_day",day).put("p_expected",expectedVersion))))
    }

}
fun main()=runBlocking {
 var n=0;fun test(ok:Boolean) {check(ok);n++}
 val c=Client();val api=Api(c)
 val result=api.saveTogetherDate("a","s",10000,7)
 test(result["since_day"]==10000L && result["version"]==8L)
 test(c.body.getLong("p_expected")==7L && c.body.getString("p_space")=="s")
 test(runCatching {api.saveTogetherDate("b","s",10000,7)}.isFailure)
 test(runCatching {api.saveTogetherDate("a","other",10000,7)}.isFailure)
 test(runCatching {api.saveTogetherDate("a","s",999999,7)}.isFailure)
 test(runCatching {api.saveTogetherDate("a","s",-999999,7)}.isFailure)
 test(c.calls==1)
 println("$n actual shared-date API checks with transport doubles passed")
}

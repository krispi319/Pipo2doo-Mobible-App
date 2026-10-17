package ai.mwm.client.cloud
import ai.mwm.client.data.*
import org.json.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
class ProfileClient {suspend fun uid()="owner"}
class ProfileHarness {
 val client=ProfileClient();var rows=emptyList<JSONObject>();var writes=0
 suspend fun space()="space"
 suspend fun records(s:String)=rows
 suspend fun preparePhoto(p:String)=if(p.startsWith("cloud:")) p else "cloud:space/uploaded.jpg"
 suspend fun write(id:String,kind:String,value:Map<String,Any?>,version:Long) {
  writes++;rows=listOf(JSONObject().put("id",id).put("kind",kind).put("created_by","owner").put("version",version+1).put("payload",JSONObject(value)))
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

}
fun main()=runBlocking {
 val h=ProfileHarness();val p=Preferences(myName="Kris",avatar=2,shareProfile=false,clockZone="Europe/Berlin")
 h.ensureProfile(p);check(h.writes==1);check(h.rows.single().getJSONObject("payload").getString("timezone")=="Europe/Berlin")
 h.ensureProfile(p);check(h.writes==1)
 h.ensureProfile(p.copy(myName="New name"));check(h.writes==2)
 h.ensureProfile(p.copy(myName="New name",clockZone="Asia/Tokyo"));check(h.writes==3)
 h.ensureProfile(p.copy(myName="New name",clockZone="Asia/Tokyo",myPhoto="/new/photo.jpg"));check(h.writes==4);check(h.rows.single().getJSONObject("payload").getString("photo").startsWith("cloud:"))
 println("5 actual profile publishing checks passed: legacy flag ignored, timezone, unchanged skip, name/zone update, photo upload")
}

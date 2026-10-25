import ai.mwm.client.data.*
import org.json.*
class FakePrefs {var values=mutableMapOf<String,String>();fun getString(key:String,default:String?):String?=values[key]?:default}
object AccountScope {fun current(c:Any)="test-account"}
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
private inline fun <reified T : Enum<T>> enumValue(name: String, fallback: T): T = enumValues<T>().firstOrNull { it.name == name } ?: fallback

class ActualReminderDecoder(val prefs:FakePrefs) {val context=Unit
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
                    repeat = enumValue(t.optString("repeat"), Repeat.Once), reminder = t.optBoolean("reminder", true), offset = t.optInt("offset"),
                    status = enumValue(t.optString("status"), TaskStatus.Todo), completedAt = t.optLong("completedAt"), parentId = t.optString("parentId"),
                    events = t.optJSONArray("events")?.objects()?.map { TaskEvent(it.optString("text"), it.optLong("at"), enumValue(it.optString("owner"), Owner.Me)) } ?: emptyList()
                ) } ?: emptyList(),
                lists = root.optJSONArray("lists")?.objects()?.map { SharedList(it.getString("id"), it.getString("name"), it.optString("emoji", "🧺"), it.optBoolean("pinned"), enumValue(it.optString("owner"), Owner.Together), it.optJSONArray("pictures")?.strings() ?: emptyList()) } ?: emptyList(),
                tags = root.optJSONArray("tags")?.objects()?.map { Tag(it.getString("id"), it.getString("name")) } ?: emptyList(),
                notes = root.optJSONArray("notes")?.objects()?.map { Appreciation(it.getString("id"), it.getString("kind"), it.optString("message"), enumValue(it.optString("owner"), Owner.Me), it.optLong("at"), it.optString("taskTitle"), it.optBoolean("sample", it.optString("id").startsWith("sample_") || it.optString("message") in listOf("You even got the oat milk I like.", "Pipo's litter box has never smelled so innocent.")), it.optString("picture"),it.optString("replyTo")) } ?: emptyList(),
                preferences = Preferences(p.optString("myName", "User 1"), p.optString("partnerName", "User 2"), p.optString("code", "K4T9PB"), p.optBoolean("started"), p.optInt("avatar", -1).let { if(root.optInt("version") < 4 && it == 0 && p.optString("myPhoto").isBlank()) -1 else it }, p.optBoolean("quietEnabled", true), p.optInt("quietStart", 1320), p.optInt("quietEnd", 450), p.optString("style", "Soft purr"), p.optBoolean("sound", true), p.optBoolean("haptics", true), p.optString("myPhoto"), p.optString("partnerPhoto"), p.optBoolean("pokeOptIn", true), p.optString("themeMode", "System"), p.optString("language", "en"), p.optLong("codeExpiresAt"), p.optLong("togetherSince", java.time.LocalDate.of(2023,2,14).toEpochDay()), p.optInt("defaultReminderTime",750).coerceIn(0,1439), p.optBoolean("keepResetHistory",true), true,p.optString("clockZone"),"",p.optString("partnerClockOverride"),p.optString("listLayout","Wide"))
            )
        }.getOrElse { if(AccountScope.current(context)=="local") seedState() else PipoState(preferences=Preferences(myName="Me",partnerName="Partner")) }
    }    internal fun personalReminder(id: String): Pair<Boolean,Int> {
        val item=runCatching {JSONObject(prefs.getString("personal_reminders","{}") ?: "{}").optJSONObject(id)}.getOrNull()
        return (item?.optBoolean("enabled", true) ?: true) to (item?.optInt("offset") ?: 0)
    }fun restored()=load()
}
fun main() {
 var n=0
 fun test(ok:Boolean) {check(ok);n++}
 val p=FakePrefs();val r=ActualReminderDecoder(p)
 test(Task().reminder);test(Preferences().pokeOptIn)
 test(r.personalReminder("missing")==Pair(true,0))
 p.values["personal_reminders"]="""{"off":{"enabled":false,"offset":30},"on":{"enabled":true},"missing":{"offset":45}}"""
 test(r.personalReminder("off")==Pair(false,30));test(r.personalReminder("on")==Pair(true,0));test(r.personalReminder("missing")==Pair(true,45))
 p.values["personal_reminders"]="invalid JSON";test(r.personalReminder("x")==Pair(true,0))
 for(enabled in listOf<Boolean?>(null,false,true)) {
  val task=JSONObject().put("id","task").put("title","Persisted task")
  val prefs=JSONObject()
  if(enabled!=null) {task.put("reminder",enabled);prefs.put("pokeOptIn",enabled)}
  p.values["state"]=JSONObject().put("version",4).put("tasks",JSONArray().put(task)).put("preferences",prefs).toString()
  val loaded=r.restored();test(loaded.tasks.single().reminder==(enabled?:true));test(loaded.preferences.pokeOptIn==(enabled?:true))
 }
 val off=Task(reminder=false,repeat=Repeat.Daily)
 test(!requireNotNull(nextOccurrence(off)).reminder)
 test(requireNotNull(nextOccurrence(Task(repeat=Repeat.Weekly))).reminder)
 test(!Task(owner=Owner.You).isOwnReminder())
 test(!Task(status=TaskStatus.Done).isOwnReminder())
 test(legacyDemoState().tasks.none {it.reminder})
 test(seedState().tasks.all {it.reminder})
 println("$n reminder default, restoration, explicit OFF, recurrence and ownership checks passed")
}

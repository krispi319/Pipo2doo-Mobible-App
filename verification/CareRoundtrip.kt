package ai.mwm.client.data
import org.json.*
import java.time.LocalDate
data class DayLog(val day: Long, val start: Boolean = false, val end: Boolean = false, val flow: String = "None", val cramps: String = "None", val mood: Int? = null, val cravings: Set<String> = emptySet(), val symptoms: Set<String> = emptySet(), val note: String = "")
data class CareState(val logs: List<DayLog> = emptyList(), val cycle: Int = 28, val duration: Int = 5, val checkIn: Boolean = false, val headsUp: Boolean = false, val checkTime: Int = 1200, val headsTime: Int = 540, val consent: Boolean = false, val categories: Set<String> = setOf("Mood", "Cravings", "Symptoms"), val history: Boolean = false, val includeNotes: Boolean = false, val endReminder: Boolean = false, val endTime: Int = 540, val shareSpace: String = "", val pendingShare: Boolean = false) {
    val starts get() = logs.filter { it.start }.map { it.day }.sorted()
    val intervals get() = starts.zipWithNext().filter { (a,b) -> logs.any { it.end && it.day in a until b } }.map { (a,b) -> (b-a).toInt() }.filter { it in 10..120 }.takeLast(6)
    val estimateCycle: Int get() { val values = intervals.sorted(); return if(values.size < 3) cycle else if(values.size % 2 == 1) values[values.size/2] else (values[values.size/2-1]+values[values.size/2])/2 }
    fun nextStart(today: Long = LocalDate.now().toEpochDay()): Long? { var next = starts.lastOrNull()?.plus(estimateCycle) ?: return null; while(next < today) next += estimateCycle; return next }
    fun predicted(day: Long): Boolean { val last = starts.lastOrNull() ?: return false; val delta = day-last; val occurrence=last+(delta/estimateCycle)*estimateCycle; val ended=logs.any { it.end && it.day in occurrence..day }; return delta >= 0 && !ended && delta % estimateCycle < duration && logs.none { it.day == day && (it.flow != "None" || it.start) } }
}

object CareCodec {
    fun encode(s: CareState)=JSONObject().put("cycle",s.cycle).put("duration",s.duration).put("logs",JSONArray(s.logs.map {l->JSONObject().put("day",l.day).put("start",l.start).put("end",l.end).put("flow",l.flow).put("cramps",l.cramps).put("mood",l.mood ?: -1).put("cravings",JSONArray(l.cravings.toList())).put("symptoms",JSONArray(l.symptoms.toList())).put("note",l.note)}))
    fun decode(p: JSONObject): CareState {
        fun strings(o: JSONObject,k: String): Set<String> {val a=o.optJSONArray(k) ?: JSONArray();return (0 until a.length()).map {a.getString(it)}.toSet()}
        val logs=p.optJSONArray("logs") ?: JSONArray()
        return CareState(cycle=p.optInt("cycle",28).coerceIn(10,120),duration=p.optInt("duration",5).coerceIn(1,14),logs=(0 until logs.length()).map {i->val l=logs.getJSONObject(i);DayLog(l.getLong("day"),l.optBoolean("start"),l.optBoolean("end"),l.optString("flow","None"),l.optString("cramps","None"),l.optInt("mood",-1).takeIf {it in 0..4},strings(l,"cravings"),strings(l,"symptoms"),l.optString("note"))})
    }
}

class CareBackupRoundtrip { var last=CareState();fun save(s:CareState){last=s}
    private fun exportState(state: CareState): JSONObject = JSONObject().put("logs",JSONArray(state.logs.map { l -> JSONObject().put("day",l.day).put("start",l.start).put("end",l.end).put("flow",l.flow).put("cramps",l.cramps).put("mood",l.mood ?: -1).put("cravings",JSONArray(l.cravings.toList())).put("symptoms",JSONArray(l.symptoms.toList())).put("note",l.note) })).put("cycle",state.cycle).put("duration",state.duration).put("checkIn",state.checkIn).put("headsUp",state.headsUp).put("checkTime",state.checkTime).put("headsTime",state.headsTime).put("consent",state.consent).put("categories",JSONArray(state.categories.toList())).put("history",state.history).put("includeNotes",state.includeNotes).put("endReminder",state.endReminder).put("endTime",state.endTime).put("shareSpace",state.shareSpace).put("pendingShare",state.pendingShare)
    @Synchronized fun restoreBackup(root: JSONObject) {
        val cloud=CareCodec.decode(root)
        save(cloud.copy(checkIn=root.optBoolean("checkIn"),headsUp=root.optBoolean("headsUp"),
            checkTime=root.optInt("checkTime",1200),headsTime=root.optInt("headsTime",540),
            consent=root.optBoolean("consent"),history=root.optBoolean("history"),includeNotes=root.optBoolean("includeNotes"),
            categories=root.optJSONArray("categories")?.let { a -> (0 until a.length()).map {a.getString(it)}.toSet() } ?: setOf("Mood","Cravings","Symptoms"),
            endReminder=root.optBoolean("endReminder"),endTime=root.optInt("endTime",540),
            shareSpace=root.optString("shareSpace"),pendingShare=root.optBoolean("pendingShare")))
    }

 fun roundtrip(s:CareState):CareState {restoreBackup(exportState(s));return last}
}
fun main() {
val s=CareState(logs=listOf(DayLog(20000,true,false,"Heavy","Mild",3,setOf("Sweet"),setOf("Tired"),"Keep my note")),cycle=31,duration=6,checkIn=true,headsUp=true,checkTime=888,headsTime=444,consent=true,categories=setOf("Mood"),history=true,includeNotes=true,endReminder=true,endTime=999,shareSpace="couple-id",pendingShare=true)
check(CareBackupRoundtrip().roundtrip(s)==s)
check(CareBackupRoundtrip().roundtrip(CareState())==CareState())
check(CareBackupRoundtrip().roundtrip(s.copy(logs=emptyList(),consent=false)).logs.isEmpty())
println("3 actual care serializer/restore round-trip checks passed")
}

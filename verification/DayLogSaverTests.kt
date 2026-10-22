import ai.mwm.client.data.*
import androidx.compose.runtime.saveable.*
import org.json.JSONArray
val DayLogDraftSaver=listSaver<DayLog,Any>(
    save={listOf(it.day,it.start,it.end,it.flow,it.cramps,it.mood ?: -1,JSONArray(it.cravings.toList()).toString(),JSONArray(it.symptoms.toList()).toString(),it.note)},
    restore={v->fun values(i:Int):Set<String> {val a=JSONArray(v[i] as String);return (0 until a.length()).map {a.getString(it)}.toSet()};DayLog(v[0] as Long,v[1] as Boolean,v[2] as Boolean,v[3] as String,v[4] as String,(v[5] as Int).takeIf {it>=0},values(6),values(7),v[8] as String)}
)

fun main() {
 val scope=object:SaverScope {override fun canBeSaved(value:Any)=true}
 val full=DayLog(20000,true,true,"Heavy","Moderate",4,setOf("Comfort food","Sweet"),setOf("Poor sleep","Acne"),"A note, with punctuation\nand a heart ♡")
 val encoded=with(DayLogDraftSaver) {scope.save(full)}!!
 check(DayLogDraftSaver.restore(encoded)==full)
 val empty=DayLog(20001)
 check(DayLogDraftSaver.restore(with(DayLogDraftSaver) {scope.save(empty)}!!)==empty)
 println("2 actual day-log draft saver round-trips passed")
}

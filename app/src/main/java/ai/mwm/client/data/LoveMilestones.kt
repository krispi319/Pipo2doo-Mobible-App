package ai.mwm.client.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.*

data class LoveCelebrationSettings(
    val hundreds:Boolean=true, val anniversaries:Boolean=true, val customDays:List<Int> = emptyList(),
    val reminders:Boolean=false, val minute:Int=540, val advanceDays:Int=1,
    val effect:String="Cats", val sound:Boolean=false, val haptics:Boolean=true, val note:String=""
) {
    fun validated():LoveCelebrationSettings {
        require(minute in 0..1439 && advanceDays in listOf(0,1,3,7)) {"Choose a valid reminder time and advance notice."}
        require(effect in listOf("Cats","Hearts","Glow")) {"Choose a celebration effect."}
        require(customDays.size<=20 && customDays.all {it in 1..365250}) {"Use up to 20 milestones between 1 and 365250 days."}
        require(note.length<=80) {"Keep your celebration note within 80 characters."}
        return copy(customDays=customDays.distinct().sorted(),note=note.trim())
    }
    fun toJson()=JSONObject().put("hundreds",hundreds).put("anniversaries",anniversaries).put("customDays",JSONArray(customDays))
        .put("reminders",reminders).put("minute",minute).put("advanceDays",advanceDays).put("effect",effect)
        .put("sound",sound).put("haptics",haptics).put("note",note)
    companion object {
        fun fromJson(json:JSONObject?):LoveCelebrationSettings {
            if(json==null)return LoveCelebrationSettings()
            val days=json.optJSONArray("customDays") ?: JSONArray()
            return LoveCelebrationSettings(json.optBoolean("hundreds",true),json.optBoolean("anniversaries",true),
                (0 until days.length()).map {days.optInt(it)}.filter {it in 1..365250}.distinct().sorted().take(20),
                json.optBoolean("reminders",false),json.optInt("minute",540).coerceIn(0,1439),
                json.optInt("advanceDays",1).takeIf {it in listOf(0,1,3,7)} ?: 1,
                json.optString("effect","Cats").takeIf {it in listOf("Cats","Hearts","Glow")} ?: "Cats",
                json.optBoolean("sound",false),json.optBoolean("haptics",true),json.optString("note").take(80))
        }
    }
}

data class LoveMilestone(val day:Long,val daysTogether:Long,val years:Int?=null) {
    val title get()=if(years!=null)"Our ${years}-year anniversary" else "$daysTogether days together"
    val celebrationTitle get()=if(years!=null)"$years ${if(years==1)"year" else "years"} of us" else "$daysTogether days of us"
}

/** The original counter is day-zero. Calendar anniversaries clamp leap-day to Feb 28. */
fun loveMilestones(p:Preferences,fromDay:Long,limit:Int=6):List<LoveMilestone> {
    if(limit<=0)return emptyList()
    val start=LocalDate.ofEpochDay(p.togetherSince)
    val from=LocalDate.ofEpochDay(fromDay)
    val s=p.loveCelebrations
    val results=sortedMapOf<Long,LoveMilestone>()
    fun add(day:Long,years:Int?=null) {if(day>=fromDay && day>p.togetherSince) {
        val previous=results[day];results[day]=LoveMilestone(day,day-p.togetherSince,years ?: previous?.years)
    }}
    if(s.hundreds) {
        val passed=(fromDay-p.togetherSince).coerceAtLeast(1)
        val first=((passed+99)/100)*100
        (0..limit).forEach {add(p.togetherSince+first+it*100)}
    }
    if(s.anniversaries) {
        val first=(from.year-start.year).coerceAtLeast(1)
        (first..first+limit).forEach {year->add(start.plusYears(year.toLong()).toEpochDay(),year)}
    }
    s.customDays.forEach {add(p.togetherSince+it)}
    return results.values.take(limit)
}

fun loveIsQuiet(at:Long,p:Preferences):Boolean {
    if(!p.quietEnabled || p.quietStart==p.quietEnd)return false
    val t=Instant.ofEpochMilli(at).atZone(clockZone(p.clockZone)).toLocalTime()
    val minute=t.hour*60+t.minute
    return if(p.quietStart<p.quietEnd)minute in p.quietStart until p.quietEnd else minute>=p.quietStart || minute<p.quietEnd
}
fun loveAfterQuiet(at:Long,p:Preferences):Long {
    if(!loveIsQuiet(at,p))return at
    val time=Instant.ofEpochMilli(at).atZone(clockZone(p.clockZone))
    var end=time.toLocalDate().atTime(p.quietEnd/60,p.quietEnd%60).atZone(time.zone)
    if(end.toInstant().toEpochMilli()<=at)end=end.plusDays(1)
    return end.toInstant().toEpochMilli()
}
data class LoveReminderEvent(val milestone:LoveMilestone,val at:Long,val advance:Boolean) {
    fun key(p:Preferences)="${p.togetherSince}:${milestone.day}:$advance"
}
fun loveReminderEvents(p:Preferences,now:Long):List<LoveReminderEvent> {
    if(!p.started || !p.loveCelebrations.reminders)return emptyList()
    val zone=clockZone(p.clockZone)
    val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val settings=p.loveCelebrations
    return loveMilestones(p,today.minusDays(1).toEpochDay(),12).flatMap {m->
        (listOf(0)+listOf(settings.advanceDays).filter {it>0}).map {offset->
            val at=LocalDate.ofEpochDay(m.day).minusDays(offset.toLong()).atTime(settings.minute/60,settings.minute%60).atZone(zone).toInstant().toEpochMilli()
            LoveReminderEvent(m,loveAfterQuiet(at,p),offset>0)
        }
    }.filter {Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate()>=today}.sortedBy {it.at}
}

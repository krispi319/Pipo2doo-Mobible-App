package ai.mwm.client.data

import java.time.Instant

/** Quick add means today in the app's selected clock, not necessarily the phone's clock. */
fun quickTaskForSave(id:String,title:String,owner:Owner,listId:String,clockSelection:String,now:Long=System.currentTimeMillis()):Task {
    require(title.trim().isNotEmpty()) {"Task title is required."}
    require(listId.isNotBlank()) {"Choose a list before saving."}
    return Task(id=id,title=title.trim(),owner=owner,listId=listId,
        day=Instant.ofEpochMilli(now).atZone(clockZone(clockSelection)).toLocalDate().toEpochDay(),
        minute=null,reminder=true)
}

/** Describe existing repeat semantics without changing when the next task is generated. */
fun taskScheduleHint(task:Task,preferences:Preferences):String {
    val repeat=when(task.repeat) {
        Repeat.Once->"One task on this date"
        Repeat.Daily->"Daily · Each date has its own checkbox"
        Repeat.Weekly->"Weekly · Completing it creates the next weekly task"
    }
    return if(task.minute==null) {
        "Anytime · Date only · $repeat"
    } else "Time in ${clockZone(preferences.clockZone).id} · $repeat"
}

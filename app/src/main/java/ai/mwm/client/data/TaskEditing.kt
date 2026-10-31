package ai.mwm.client.data

import java.time.Instant

/** Retry only fields the user changed; preserve newer partner changes to the rest of the task. */
fun mergeTaskDetailEdit(current:Task,base:Task,edited:Task):Task {
    require(current.id==base.id && base.id==edited.id) {"Task changed. Reopen its details."}
    return current.copy(
        title=if(base.title!=edited.title)edited.title else current.title,
        owner=if(base.owner!=edited.owner)edited.owner else current.owner,
        day=if(base.day!=edited.day)edited.day else current.day,
        minute=if(base.minute!=edited.minute)edited.minute else current.minute,
        listId=if(base.listId!=edited.listId)edited.listId else current.listId,
        note=if(base.note!=edited.note)edited.note else current.note,
        tags=if(base.tags!=edited.tags)edited.tags else current.tags,
        repeat=if(base.repeat!=edited.repeat)edited.repeat else current.repeat,
        reminder=if(base.reminder!=edited.reminder)edited.reminder else current.reminder,
        offset=if(base.offset!=edited.offset)edited.offset else current.offset
    )
}

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

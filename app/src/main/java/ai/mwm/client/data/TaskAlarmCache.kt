package ai.mwm.client.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Minimal alarm-only cache. It never restores partner records into the UI or a demo workspace. */
object TaskAlarmCache {
    private fun store(context: Context) = context.getSharedPreferences("task_alarm_cache_${AccountScope.current(context)}", Context.MODE_PRIVATE)
    fun save(context: Context, state: PipoState) {
        val workspace = context.getSharedPreferences(AccountScope.preferencesName(context),0).getString("last_shared_space", "") ?: ""
        val p = state.preferences
        val rows = JSONArray(state.tasks.filter {it.isOwnReminder()}.map { t ->
            JSONObject().put("id",t.id).put("title",t.title).put("day",t.day).put("minute",t.minute ?: -1)
                .put("scheduledAt",t.scheduledAt).put("scheduleZone",t.scheduleZone).put("offset",t.offset)
                .put("owner",t.owner.name).put("repeat",t.repeat.name)
        })
        store(context).edit().putString("state",JSONObject().put("workspace",workspace).put("tasks",rows)
            .put("clockZone",p.clockZone).put("defaultMinute",p.defaultReminderTime).put("started",p.started)
            .put("quietEnabled",p.quietEnabled).put("quietStart",p.quietStart).put("quietEnd",p.quietEnd)
            .put("myName",p.myName).put("partnerName",p.partnerName).put("style",p.style).toString()).commit()
    }
    fun clear(context: Context) {store(context).edit().clear().commit()}
    fun forAlarm(context: Context, current: PipoState, remote: Boolean): PipoState {
        if(!remote || current.tasks.isNotEmpty()) return current
        return runCatching {
            val root = JSONObject(store(context).getString("state",null) ?: return current)
            val workspace = context.getSharedPreferences(AccountScope.preferencesName(context),0).getString("last_shared_space", "")
            if(workspace.isNullOrBlank() || workspace != root.optString("workspace")) return current
            val rows = root.getJSONArray("tasks")
            val p = current.preferences.copy(started=root.optBoolean("started"),clockZone=root.optString("clockZone"),
                defaultReminderTime=root.optInt("defaultMinute",750),quietEnabled=root.optBoolean("quietEnabled",true),
                quietStart=root.optInt("quietStart",1320),quietEnd=root.optInt("quietEnd",450),myName=root.optString("myName"),
                partnerName=root.optString("partnerName"),style=root.optString("style","Soft purr"))
            current.copy(preferences=p,tasks=(0 until rows.length()).map { i -> val t=rows.getJSONObject(i)
                Task(id=t.getString("id"),title=t.getString("title"),day=t.getLong("day"),minute=t.optInt("minute",-1).takeIf {it>=0},
                    scheduledAt=t.optLong("scheduledAt"),scheduleZone=t.optString("scheduleZone"),offset=t.optInt("offset"),
                    owner=Owner.valueOf(t.getString("owner")),repeat=Repeat.valueOf(t.getString("repeat")))
            }).inTaskZone()
        }.getOrDefault(current)
    }
}

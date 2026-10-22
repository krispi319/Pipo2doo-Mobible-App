package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import org.json.JSONArray

private val DayLogDraftSaver=listSaver<DayLog,Any>(
    save={listOf(it.day,it.start,it.end,it.flow,it.cramps,it.mood ?: -1,JSONArray(it.cravings.toList()).toString(),JSONArray(it.symptoms.toList()).toString(),it.note)},
    restore={v->fun values(i:Int):Set<String> {val a=JSONArray(v[i] as String);return (0 until a.length()).map {a.getString(it)}.toSet()};DayLog(v[0] as Long,v[1] as Boolean,v[2] as Boolean,v[3] as String,v[4] as String,(v[5] as Int).takeIf {it>=0},values(6),values(7),v[8] as String)}
)
@Composable internal fun InlineDayLog(initial:DayLog,ownerKey:String,error:String?,onSave:(DayLog)->Unit,onDelete:()->Unit,exists:Boolean=true) {
    var draft by rememberSaveable(ownerKey,initial.day,stateSaver=DayLogDraftSaver) {mutableStateOf(initial)}
    var baseline by remember(ownerKey,initial.day) {mutableStateOf(initial)}
    var submitted by remember(ownerKey,initial.day) {mutableStateOf<DayLog?>(null)}
    var saved by remember(ownerKey,initial.day) {mutableStateOf(false)}
    LaunchedEffect(initial,error,submitted,exists) {
        if(exists && initial==submitted) {val untouched=draft==submitted;baseline=initial;if(untouched)draft=initial;submitted=null;saved=untouched}
        else if(draft==baseline) {draft=initial;baseline=initial}
        if(error!=null) submitted=null
    }
    val conflict=initial!=baseline && draft!=baseline && submitted==null
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Paper {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Outlined.EditNote,null,Modifier.size(22.dp));Spacer(Modifier.width(7.dp));Column(Modifier.weight(1f)) {PText("Day log",style=MaterialTheme.typography.titleMedium);PText(dayText(initial.day),style=MaterialTheme.typography.labelSmall,muted=true)};if(saved && draft==initial) Icon(Icons.Outlined.CheckCircle,"Saved",Modifier.size(20.dp))}}
        DayLogFields(draft) {draft=it;saved=false}
        if(conflict) Paper {PText("This day changed on another device. Reload before saving.",style=MaterialTheme.typography.bodySmall);OutlinedButton({draft=initial;baseline=initial;saved=false}) {PText("Reload saved day")}}
        Button({submitted=draft;onSave(draft)},Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!conflict && submitted==null) {Icon(Icons.Outlined.Check,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));PText(if(submitted!=null)"Saving…" else "Save day log")}
        OutlinedButton(onDelete,Modifier.fillMaxWidth().heightIn(min=44.dp),enabled=submitted==null) {Icon(Icons.Outlined.DeleteOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));PText("Delete day log")}
        PText("Changes stay as a draft until you tap Save day log.",style=MaterialTheme.typography.labelSmall,muted=true)
    }
}

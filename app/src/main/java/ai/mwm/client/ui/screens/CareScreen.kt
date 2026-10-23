package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val moods=listOf("Very low","Low","Okay","Good","Great")
@Composable internal fun MoodFace(value: Int,modifier: Modifier=Modifier) { val ink=AppTheme.colors.textPrimary;Canvas(modifier) { val w=size.width;val h=size.height;drawCircle(ink,w*.43f,style=Stroke(1.5.dp.toPx()));drawCircle(ink,1.2.dp.toPx(),Offset(w*.34f,h*.4f));drawCircle(ink,1.2.dp.toPx(),Offset(w*.66f,h*.4f));val p=Path().apply {moveTo(w*.3f,h*.65f);quadraticTo(w*.5f,h*(if(value<2) .48f else if(value==2) .65f else .84f),w*.7f,h*.65f)};drawPath(p,ink,style=Stroke(1.5.dp.toPx())) } }

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun CareScreen(state: CareState?, name: String,partner: String,error: String?,allowed: Boolean,onBack: ()->Unit,onSave: (DayLog)->Unit,onDelete: (Long)->Unit,onUpdate: ((CareState)->CareState)->Unit,onClear: ()->Unit,onEnable: ()->Unit,
    shared: CareConnection=CareConnection(),onSharing: (Boolean)->Unit={},onRefresh: ()->Unit={},support: List<Appreciation> = emptyList(),onSupport: (String,(String?)->Unit)->Unit={_,done->done("Connect your partner to send a note.")}) {
    ApprovedDesign {
    val context=LocalContext.current
    val c=AppTheme.colors
    var partnerView by remember {mutableStateOf(false)}
    var month by remember {mutableStateOf(YearMonth.now())}
    var selected by remember {mutableStateOf(LocalDate.now().toEpochDay())}
    var cycleSheet by remember {mutableStateOf(false)}
    var clear by remember {mutableStateOf(false)}
    var delete by remember {mutableStateOf(false)}
    var note by remember {mutableStateOf("")}
    var supportSending by remember {mutableStateOf(false)}
    var supportError by remember {mutableStateOf<String?>(null)}
    val connection=ai.mwm.client.cloud.LocalPartnerConnection.current
    val visible=if(partnerView) {if(connection.paired && connection.online && shared.online && shared.spaceId==connection.spaceId) shared.partner else null} else state
    LazyColumn(contentPadding=PaddingValues(20.dp,16.dp,20.dp,100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Row(verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {PText("Period & Care",style=MaterialTheme.typography.headlineLarge);PText("A little understanding, together",style=MaterialTheme.typography.bodySmall,muted=true)};IconButton(onRefresh) {Icon(Icons.Outlined.Refresh,"Refresh care records")}}}
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(!partnerView,{partnerView=false},label={PText(name,preserveContent=true)});FilterChip(partnerView,{partnerView=true},label={PText(partner,preserveContent=true)})}}
        if(error!=null) item {Paper {PText(error,style=MaterialTheme.typography.bodySmall);TextButton(onRefresh) {PText("Refresh")}}}
        if(partnerView) item {PText(if(visible==null) "$partner hasn't shared care records yet." else "$partner's records · You can follow along and send support",style=MaterialTheme.typography.bodySmall,muted=true,preserveContent=true)}
        if(visible==null) item {Paper {Icon(if(partnerView) Icons.Outlined.FavoriteBorder else Icons.Outlined.LocalFlorist,null,tint=c.accent);PText(if(partnerView) "Give them a little space" else "Opening your records…",style=MaterialTheme.typography.titleMedium);PText(if(partnerView) "Their calendar appears here when they choose to share. Your own records stay separate." else "Your saved logs will appear here.",style=MaterialTheme.typography.bodySmall,muted=true)}}
        if(visible!=null) {
            val active=visible.starts.lastOrNull()?.takeIf {start->visible.logs.none {it.end && it.day>=start}}
            val log=visible.logs.find {it.day==selected}
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.Bottom) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        PText(if(partnerView) "$partner’s cycle" else "$name’s cycle",style=MaterialTheme.typography.labelSmall,muted=true,preserveContent=true)
                        PText(if(active==null) if(partnerView) "Their cycle" else "Your cycle" else "Day ${LocalDate.now().toEpochDay()-active+1}",style=MaterialTheme.typography.displaySmall)
                        PText(active?.let {"Started ${dayText(it)}"} ?: "A little care, every day",style=MaterialTheme.typography.bodySmall,muted=true)
                    }
                    ApprovedCat(Modifier.size(92.dp,84.dp),"care")
                }
            }
            item {Paper(color=c.accent.copy(alpha=.1f).compositeOver(c.surface)) {
                PText("A little heads-up",style=MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {PText("Estimated end",style=MaterialTheme.typography.labelSmall,muted=true);PText(visible.starts.lastOrNull()?.let {dayText(it+visible.duration-1)} ?: "—",style=MaterialTheme.typography.titleMedium)}
                    Column(Modifier.weight(1f)) {PText("Next estimated start",style=MaterialTheme.typography.labelSmall,muted=true);PText(visible.nextStart()?.let {dayText(it)} ?: "Log a start first",style=MaterialTheme.typography.titleMedium)}
                }
                PText("Cycle · ${visible.estimateCycle} days    Period · ${visible.duration} days",style=MaterialTheme.typography.bodySmall,muted=true)
                PText("Estimates may vary from your actual dates.",style=MaterialTheme.typography.bodySmall,muted=true)
                if(!partnerView) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({cycleSheet=true},contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp)) {Icon(Icons.Outlined.Tune,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));PText("Cycle settings")}
                }
            }}
            item {Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                CycleCalendar(visible,month,selected,{month=it},{selected=it})
                if(!partnerView) {
                    val today=LocalDate.now().toEpochDay()
                    val current=state?.logs?.find {it.day==today} ?: DayLog(today)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({onSave(current.copy(start=true,end=false))},Modifier.weight(1f).heightIn(min=48.dp),enabled=active==null,contentPadding=PaddingValues(12.dp,10.dp)) {Icon(Icons.Outlined.WaterDrop,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));PText("Start today")}
                        OutlinedButton({onSave(current.copy(end=true))},Modifier.weight(1f).heightIn(min=48.dp),enabled=active!=null,contentPadding=PaddingValues(12.dp,10.dp)) {Icon(Icons.Outlined.CheckCircleOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));PText("End today")}
                    }
                }
            }}
            if(partnerView) item {Paper {PText(dayText(selected),style=MaterialTheme.typography.titleSmall);PText(log?.let {listOfNotNull(if(it.start)"Period start" else null,if(it.end)"Period end" else null,it.flow.takeIf {v->v!="None"}?.let {v->"$v flow"},it.cramps.takeIf {v->v!="None"}?.let {v->"$v cramps"},it.mood?.let {m->moods.getOrNull(m)},it.cravings.joinToString().takeIf {v->v.isNotBlank()},it.symptoms.joinToString().takeIf {v->v.isNotBlank()}).joinToString(" · ").ifBlank {"Day note"}} ?: "No day log",style=MaterialTheme.typography.bodySmall,muted=true);log?.note?.takeIf {it.isNotBlank()}?.let {PText(it,preserveContent=true)}}}
            if(!partnerView && state!=null) {
                item(key="inline_${connection.uid}_${connection.spaceId}_$selected") {InlineDayLog(log ?: DayLog(selected),connection.uid+":"+connection.spaceId,error,onSave,{delete=true},exists=log!=null)}
                item {Paper {
                    PText("Your reminders",style=MaterialTheme.typography.titleSmall)
                    listOf("Daily check-in","Before expected start","Expected end").forEachIndexed {i,label->
                        val enabled=when(i){0->state.checkIn;1->state.headsUp;else->state.endReminder};val minute=when(i){0->state.checkTime;1->state.headsTime;else->state.endTime}
                        Row(verticalAlignment=Alignment.CenterVertically) {PText(label,Modifier.weight(1f),MaterialTheme.typography.bodyMedium);OutlinedButton({pickTime(context,minute) {value->onUpdate {when(i){0->it.copy(checkTime=value);1->it.copy(headsTime=value);else->it.copy(endTime=value)}}}}) {PText(timeText(minute))};Switch(enabled,{value->onUpdate {when(i){0->it.copy(checkIn=value);1->it.copy(headsUp=value);else->it.copy(endReminder=value)}};if(value && !allowed) onEnable()})}
                    }
                    PText("Reminder settings are per phone. They don't change your partner's reminders.",style=MaterialTheme.typography.bodySmall,muted=true)
                }}
                item {OutlinedButton({clear=true},Modifier.fillMaxWidth().heightIn(min=48.dp)) {Icon(Icons.Outlined.DeleteOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));PText("Clear my day logs")}}
            }
        }
        item {Paper {
            Row(verticalAlignment=Alignment.CenterVertically) {ApprovedCat(Modifier.size(60.dp,48.dp),"care");Spacer(Modifier.width(10.dp));PText("A little support",style=MaterialTheme.typography.titleMedium)}
            PText("Here for each other",style=MaterialTheme.typography.labelSmall,muted=true)
            PText("Notes you send each other, separate from day logs.",style=MaterialTheme.typography.bodySmall,muted=true)
            support.filter {it.kind=="CareSupport"}.sortedByDescending {it.at}.take(10).reversed().forEach {entry->
                Surface(color=if(entry.owner==Owner.Me) c.secondary.copy(alpha=.18f) else c.accent.copy(alpha=.12f),shape=RoundedCornerShape(14.dp)) {Column(Modifier.fillMaxWidth().padding(10.dp)) {PText(if(entry.owner==Owner.Me) name else partner,style=MaterialTheme.typography.labelSmall,muted=true,preserveContent=true);PText(entry.message,preserveContent=true)}}
            }
            OutlinedTextField(note,{note=it.take(500)},Modifier.fillMaxWidth(),label={PText("A note for $partner")},placeholder={PText("I'm here for you ♡")},maxLines=4)
            supportError?.let {PText(it,style=MaterialTheme.typography.bodySmall)}
            Button({supportSending=true;supportError=null;onSupport(note.trim()) {failure->supportSending=false;if(failure==null) note="" else supportError=failure}},enabled=note.isNotBlank() && connection.paired && connection.online && !connection.busy && !supportSending,modifier=Modifier.fillMaxWidth()) {PText(if(supportSending) "Sending…" else "Send support")}
        }}
        if(!partnerView && state!=null) item {
            Paper {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {PText("Let $partner follow along",style=MaterialTheme.typography.titleSmall,preserveContent=true);PText("Includes period dates, mood, symptoms and your day notes. Only you can edit them.",style=MaterialTheme.typography.bodySmall,muted=true)}
                    Switch(shared.enabled,onSharing,enabled=shared.online && !shared.busy && connection.paired)
                }
                PText(shared.status,style=MaterialTheme.typography.bodySmall,muted=true)
                if(state.pendingShare) OutlinedButton({onSharing(true)},enabled=shared.online && !shared.busy) {PText("Publish this phone's saved changes")}
                if(shared.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
    if(delete) AlertDialog(onDismissRequest={delete=false},title={PText("Delete my day log?")},text={PText("This also removes it from your shared calendar when sharing is on.")},confirmButton={TextButton({onDelete(selected);delete=false}) {PText("Delete")}},dismissButton={TextButton({delete=false}) {PText("Cancel")}})
    if(clear) AlertDialog(onDismissRequest={clear=false},title={PText("Clear my day logs?")},text={PText("Your partner's records and support notes stay. If sharing is on, your shared copy will be cleared too.")},confirmButton={TextButton({onClear();clear=false}) {PText("Clear my logs")}},dismissButton={TextButton({clear=false}) {PText("Cancel")}})
    if(cycleSheet && state!=null) {
        var cycle by remember {mutableStateOf(state.cycle.toString())};var duration by remember {mutableStateOf(state.duration.toString())}
        AlertDialog(onDismissRequest={cycleSheet=false},title={PText("Cycle settings")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {OutlinedTextField(cycle,{cycle=it.filter(Char::isDigit).take(3)},label={PText("Cycle length · 10–120 days")});OutlinedTextField(duration,{duration=it.filter(Char::isDigit).take(2)},label={PText("Period length · 1–14 days")})}},confirmButton={TextButton({onUpdate {it.copy(cycle=cycle.toInt(),duration=duration.toInt())};cycleSheet=false},enabled=cycle.toIntOrNull() in 10..120 && duration.toIntOrNull() in 1..14) {PText("Save")}},dismissButton={TextButton({cycleSheet=false}) {PText("Cancel")}})
    }
}
}

@Composable private fun DayLogSheet(initial: DayLog,onClose: ()->Unit,onSave: (DayLog)->Unit,onDelete: ()->Unit) {
    VisualDayLogSheet(initial,onClose,onSave,onDelete)
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun LogChoices(title: String,choices: List<String>,selected: Set<String>,onChange: (Set<String>)->Unit) {Paper {PText(title,style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {choices.forEach {value->Pill(value,value in selected,AppTheme.colors.secondary) {onChange(if(value in selected) selected-value else selected+value)}}}} }

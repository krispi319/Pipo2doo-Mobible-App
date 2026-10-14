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
@Composable private fun MoodFace(value: Int,modifier: Modifier=Modifier) { val ink=AppTheme.colors.textPrimary;Canvas(modifier) { val w=size.width;val h=size.height;drawCircle(ink,w*.43f,style=Stroke(1.5.dp.toPx()));drawCircle(ink,1.2.dp.toPx(),Offset(w*.34f,h*.4f));drawCircle(ink,1.2.dp.toPx(),Offset(w*.66f,h*.4f));val p=Path().apply {moveTo(w*.3f,h*.65f);quadraticTo(w*.5f,h*(if(value<2) .48f else if(value==2) .65f else .84f),w*.7f,h*.65f)};drawPath(p,ink,style=Stroke(1.5.dp.toPx())) } }

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun CareScreen(state: CareState?, name: String,partner: String,error: String?,allowed: Boolean,onBack: ()->Unit,onSave: (DayLog)->Unit,onDelete: (Long)->Unit,onUpdate: ((CareState)->CareState)->Unit,onClear: ()->Unit,onEnable: ()->Unit) {
    val context=LocalContext.current
    val c=AppTheme.colors
    var month by remember {mutableStateOf(YearMonth.now())}
    var selected by remember {mutableStateOf(LocalDate.now().toEpochDay())}
    var draft by remember {mutableStateOf<DayLog?>(null)}
    var cycleSheet by remember {mutableStateOf(false)}
    var sharing by remember {mutableStateOf(false)}
    var clear by remember {mutableStateOf(false)}
    var delete by remember {mutableStateOf(false)}
    LazyColumn(contentPadding=PaddingValues(16.dp,8.dp,16.dp,24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {TextButton(onBack) {Icon(Icons.Outlined.ChevronLeft,null,Modifier.size(22.dp));PText("Today")};PText("Period & Care",Modifier.weight(1f),MaterialTheme.typography.titleMedium,align=androidx.compose.ui.text.style.TextAlign.Center);Badge("Private",c.surfaceSunken)}}
        item {val active=state?.starts?.lastOrNull()?.takeIf {start->state.logs.none {it.end&&it.day>=start}};Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.Bottom) {Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {PText("Only you · $name",style=MaterialTheme.typography.labelSmall,muted=true);PText(if(active==null) "Your cycle" else "Day ${LocalDate.now().toEpochDay()-active+1}",style=MaterialTheme.typography.displayLarge);PText(active?.let {"Started ${dayText(it)} · Logged by you"} ?: "Private tracking on this phone",style=MaterialTheme.typography.bodySmall,muted=true)};ApprovedCat(Modifier.size(92.dp,84.dp),"care")}}
        if(error!=null) item {Paper {PText(error);PText("Close and reopen this page to try again.",muted=true)} }
        if(state==null) item {Paper {PText(if(error==null) "Opening your private vault…" else "Private records unavailable") } }
        if(state!=null) {
            val next=state.nextStart()
            val log=state.logs.find {it.day==selected}
            item {PeriodControls(state,onUpdate)}
            item {Sticky(color=c.accent,rotation=-1f) {
                PText("Estimate · Not a diagnosis",style=MaterialTheme.typography.labelSmall,muted=true)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {Column(Modifier.weight(1f)) {PText("Estimated end",style=MaterialTheme.typography.bodySmall,muted=true);PText(state.starts.lastOrNull()?.let {dayText(it+state.duration-1)} ?: "—",style=MaterialTheme.typography.titleLarge)};Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {PText("Next estimated start",style=MaterialTheme.typography.bodySmall,muted=true);PText(next?.let {dayText(it)} ?: "—",style=MaterialTheme.typography.titleLarge,align=androidx.compose.ui.text.style.TextAlign.End)}}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {OutlinedButton({cycleSheet=true},Modifier.weight(1f),contentPadding=PaddingValues(8.dp)) {PText("Cycle ${state.estimateCycle} days",style=MaterialTheme.typography.bodySmall)};OutlinedButton({cycleSheet=true},Modifier.weight(1f),contentPadding=PaddingValues(8.dp)) {PText("Duration ${state.duration} days",style=MaterialTheme.typography.bodySmall)}}
                PText(if(state.intervals.size<3) "Limited history: ${state.intervals.size} completed intervals. Using your ${state.cycle}-day setting until 3 are logged." else "Using the median of ${state.intervals.size} recent completed intervals. Cycle lengths vary.",style=MaterialTheme.typography.bodySmall,muted=true)
                PText("A guide only — not medical or fertility advice.",style=MaterialTheme.typography.bodySmall,muted=true)
            } }
            item {Paper {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {IconButton({month=month.minusMonths(1)}) {Icon(Icons.Outlined.ChevronLeft,"Previous month")};PText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.getDefault())),Modifier.weight(1f),MaterialTheme.typography.titleLarge,align=androidx.compose.ui.text.style.TextAlign.Center);IconButton({month=month.plusMonths(1)}) {Icon(Icons.Outlined.ChevronRight,"Next month")}}
                Row {java.time.DayOfWeek.entries.forEach {day->PText(day.getDisplayName(java.time.format.TextStyle.NARROW,Locale.getDefault()),Modifier.weight(1f),MaterialTheme.typography.labelSmall,muted=true,align=androidx.compose.ui.text.style.TextAlign.Center)} }
                val first=month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value-1).toLong())
                val weeks=(month.atDay(1).dayOfWeek.value-1+month.lengthOfMonth()+6)/7
                repeat(weeks) { week ->Row(Modifier.fillMaxWidth()) {repeat(7) {i->val date=first.plusDays((week*7+i).toLong());val day=date.toEpochDay();val entry=state.logs.find {it.day==day};val logged=entry?.let {it.start||it.flow!="None"}==true;val estimated=state.predicted(day);Surface(onClick={selected=day},modifier=Modifier.weight(1f).heightIn(min=48.dp).padding(1.dp).semantics {contentDescription="${dayText(day)}${if(logged) ", Period logged" else if(estimated) ", Estimated period" else ""}${entry?.mood?.let {", ${moods[it]} mood"} ?: ""}";this.selected=selected==day},shape=RoundedCornerShape(16.dp),color=if(logged) c.accent else if(estimated) c.accent.copy(alpha=.12f).compositeOver(c.surface) else c.surface,border=if(selected==day) BorderStroke(2.dp,c.textPrimary) else if(estimated) BorderStroke(1.dp,c.accent) else null) {Column(Modifier.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {PText(date.dayOfMonth.toString(),style=MaterialTheme.typography.bodySmall,muted=YearMonth.from(date)!=month);if(entry?.mood!=null) MoodFace(entry.mood,Modifier.size(13.dp)) else Box(Modifier.size(13.dp))}} } } }
                Surface(color=c.surfaceSunken,shape=CircleShape) {Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.SpaceEvenly) {PText("● Logged",style=MaterialTheme.typography.labelSmall);PText("○ Estimated",style=MaterialTheme.typography.labelSmall);PText("☺ Mood",style=MaterialTheme.typography.labelSmall)}}
                Paper(color=c.surfaceSunken) {PText(dayText(selected),style=MaterialTheme.typography.titleMedium);PText(log?.let {listOfNotNull(if(it.start) "Period start" else null,if(it.end) "Period end" else null,if(it.flow!="None") "${it.flow} flow" else null,it.mood?.let {m->moods[m]},it.cravings.joinToString().takeIf(String::isNotEmpty),it.symptoms.joinToString().takeIf(String::isNotEmpty)).joinToString(" · ").ifBlank {"Private note saved"}} ?: "No log yet. Add how you're feeling.",style=MaterialTheme.typography.bodySmall,muted=true);OutlinedButton({draft=log ?: DayLog(selected)},Modifier.fillMaxWidth()) {PText(if(log==null) "Add day log" else "Edit day log")} }
            } }
            item {Paper {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {PText("How's today?",Modifier.weight(1f),MaterialTheme.typography.titleLarge);Badge("Private",c.secondary)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {moods.forEachIndexed { index,label ->Surface(onClick={selected=LocalDate.now().toEpochDay();draft=state.logs.find {it.day==selected}?.copy(mood=index) ?: DayLog(selected,mood=index)},modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp),color=c.surfaceSunken) {Column(Modifier.padding(4.dp,10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {MoodFace(index,Modifier.size(30.dp));PText(label,style=MaterialTheme.typography.labelSmall,align=androidx.compose.ui.text.style.TextAlign.Center)} } } }
                PText("Flow",style=MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("Spotting","Light","Medium","Heavy").forEachIndexed {index,label ->Surface(onClick={selected=LocalDate.now().toEpochDay();draft=(state.logs.find {it.day==selected} ?: DayLog(selected)).copy(flow=label)},modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp),color=c.surfaceSunken) {Column(Modifier.padding(4.dp,10.dp),horizontalAlignment=Alignment.CenterHorizontally) {Icon(Icons.Outlined.WaterDrop,null,Modifier.size((22+index*4).dp),tint=c.accent);PText(label,style=MaterialTheme.typography.labelSmall)}}}}
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("Cravings","Symptoms","Private note","More").forEach {label->Pill("+ $label") {selected=LocalDate.now().toEpochDay();draft=state.logs.find {it.day==selected} ?: DayLog(selected)} } }
            } }
            item {Paper {
                PText("Private health logs",style=MaterialTheme.typography.titleLarge);Badge("Only you",c.secondary)
                PText("Your logs stay encrypted on this phone. They are not sent to your partner or included in shared tasks.",style=MaterialTheme.typography.bodySmall,muted=true)
            } }
            item {PrivatePublicControl(partner)}
            item {Paper {
                PText("Gentle reminders",style=MaterialTheme.typography.titleLarge);Badge(if(allowed) "On-device" else "Android notifications off",c.secondary)
                listOf(true,false).forEach {daily->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {Pill(timeText(if(daily) state.checkTime else state.headsTime)) {pickTime(context,if(daily) state.checkTime else state.headsTime) {minute->onUpdate {if(daily) it.copy(checkTime=minute) else it.copy(headsTime=minute)}}};Column(Modifier.weight(1f)) {PText(if(daily) "Evening check-in" else "Expected start",style=MaterialTheme.typography.titleMedium);PText(if(daily) "Every day" else "2 days before estimated start",style=MaterialTheme.typography.bodySmall,muted=true)};Switch(if(daily) state.checkIn else state.headsUp,{value->onUpdate {if(daily) it.copy(checkIn=value) else it.copy(headsUp=value)};if(value&&!allowed) onEnable()})} }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {Pill(timeText(state.endTime)) {pickTime(context,state.endTime) {minute->onUpdate {it.copy(endTime=minute)}}};Column(Modifier.weight(1f)) {PText("Expected end",style=MaterialTheme.typography.titleMedium);PText("On estimated end · Never auto-ends your period",style=MaterialTheme.typography.bodySmall,muted=true)};Switch(state.endReminder,{value->onUpdate {it.copy(endReminder=value)};if(value&&!allowed) onEnable()})}
                Row(verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {PText("Partner care",style=MaterialTheme.typography.titleMedium);PText("Needs both partners to opt in · Unavailable",style=MaterialTheme.typography.bodySmall,muted=true)};Switch(false,{},enabled=false)}
                Paper(color=c.surfaceSunken) {PText("Lock screen: Pipo2doo · A little reminder. Android may delay alerts to save battery. Tap a time to change it.",style=MaterialTheme.typography.bodySmall,muted=true);if(state.headsUp&&next==null) PText("Log a period start to schedule a heads-up.",style=MaterialTheme.typography.bodySmall)}
            } }
            item {Paper {PText("Locked to this phone",style=MaterialTheme.typography.titleMedium);PText("${state.logs.size} entries · Encrypted · Not shared, backed up or included in analytics.",style=MaterialTheme.typography.bodySmall,muted=true);OutlinedButton({clear=true},Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=c.error)) {PText("Clear all records")} } }
        }
    }
    draft?.let {initial ->DayLogSheet(initial,{draft=null},{onSave(it);draft=null},{delete=true})}
    if(delete) AlertDialog(onDismissRequest={delete=false},title={PText("Delete this day's log?")},text={PText("The private log will be removed permanently.")},confirmButton={OutlinedButton({draft?.let {onDelete(it.day)};draft=null;delete=false}) {PText("Delete log")}},dismissButton={OutlinedButton({delete=false}) {PText("Keep log")}})
    if(clear) AlertDialog(onDismissRequest={clear=false},title={PText("Clear all private records?")},text={PText("This permanently deletes every day log and resets reminders and sharing consent. Your tasks stay. This cannot be undone.")},confirmButton={OutlinedButton({onClear();clear=false}) {PText("Clear all records")}},dismissButton={OutlinedButton({clear=false}) {PText("Cancel")}})
    if(cycleSheet&&state!=null) {var cycle by remember {mutableStateOf(state.cycle.toString())};var duration by remember {mutableStateOf(state.duration.toString())};ModalBottomSheet(onDismissRequest={cycleSheet=false}) {Column(Modifier.padding(20.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {PText("Cycle settings",style=MaterialTheme.typography.titleLarge);OutlinedTextField(cycle,{cycle=it.filter(Char::isDigit).take(3)},label={PText("Cycle length · 10–120 days")});OutlinedTextField(duration,{duration=it.filter(Char::isDigit).take(2)},label={PText("Period duration · 1–14 days")});Button({onUpdate {it.copy(cycle=cycle.toInt(),duration=duration.toInt())};cycleSheet=false},enabled=cycle.toIntOrNull() in 10..120 && duration.toIntOrNull() in 1..14,modifier=Modifier.fillMaxWidth()) {PText("Save settings")} } } }
    if(sharing&&state!=null) SharingSheet(state,partner,{sharing=false}) {categories,history,notes->onUpdate {it.copy(consent=true,categories=categories,history=history,includeNotes=notes)};sharing=false}
}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable private fun DayLogSheet(initial: DayLog,onClose: ()->Unit,onSave: (DayLog)->Unit,onDelete: ()->Unit) {
    var log by remember(initial.day) {mutableStateOf(initial)}
    ModalBottomSheet(onDismissRequest=onClose,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=AppTheme.colors.surface) {Column(Modifier.fillMaxHeight(.92f).imePadding()) {Row(Modifier.padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {PText("Day log",Modifier.weight(1f),MaterialTheme.typography.titleLarge);OutlinedButton(onClose) {PText("Cancel")}};LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Badge(dayText(log.day));Paper {Row(Modifier.fillMaxWidth().clickable {log=log.copy(start=!log.start)},verticalAlignment=Alignment.CenterVertically) {PText("Period start",Modifier.weight(1f));Switch(log.start,{log=log.copy(start=it)})};Row(Modifier.fillMaxWidth().clickable {log=log.copy(end=!log.end)},verticalAlignment=Alignment.CenterVertically) {PText("Period end",Modifier.weight(1f));Switch(log.end,{log=log.copy(end=it)})};PText("Start and end mark this date. Flow records actual period days.",style=MaterialTheme.typography.bodySmall,muted=true)} }
        item {Paper {PText("Flow",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("None","Spotting","Light","Medium","Heavy").forEach {value->Pill(value,log.flow==value) {log=log.copy(flow=value)}}};PText("Cramps",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("None","Mild","Moderate","Severe").forEach {value->Pill(value,log.cramps==value) {log=log.copy(cramps=value)}}}} }
        item {Paper {PText("Mood",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {moods.forEachIndexed {i,value->Pill(value,log.mood==i) {log=log.copy(mood=if(log.mood==i) null else i)}}}} }
        item {LogChoices("Cravings",listOf("Chocolate","Sweet","Salty","Spicy","Fruit","Comfort food","No appetite"),log.cravings) {log=log.copy(cravings=it)} }
        item {LogChoices("Symptoms",listOf("Back ache","Headache","Bloating","Fatigue","Tender breasts","Acne","Nausea","Poor sleep"),log.symptoms) {log=log.copy(symptoms=it)} }
        item {Paper {OutlinedTextField(log.note,{log=log.copy(note=it.take(1000))},Modifier.fillMaxWidth(),label={PText("Private note")},placeholder={PText("Energy, symptoms, care that helps…")});OutlinedButton(onDelete) {PText("Delete day log")} } }
    };Button({onSave(log)},Modifier.fillMaxWidth().padding(20.dp,12.dp)) {PText("Save day log")} } }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun LogChoices(title: String,choices: List<String>,selected: Set<String>,onChange: (Set<String>)->Unit) {Paper {PText(title,style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {choices.forEach {value->Pill(value,value in selected,AppTheme.colors.secondary) {onChange(if(value in selected) selected-value else selected+value)}}}} }
@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable private fun SharingSheet(state: CareState,partner: String,onClose: ()->Unit,onConfirm: (Set<String>,Boolean,Boolean)->Unit) {
    var categories by remember {mutableStateOf(state.categories)};var history by remember {mutableStateOf(state.history)};var notes by remember {mutableStateOf(state.includeNotes)};var informed by remember {mutableStateOf(false)}
    ModalBottomSheet(onDismissRequest=onClose,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {PText("Automatic read-only sharing",style=MaterialTheme.typography.titleLarge);Badge("Setup required",AppTheme.colors.warning);Paper {PText("Only your authenticated, paired partner may view selected saved entries. $partner cannot edit them. Consent is optional, continuous and revocable.");PText("Nothing leaves this phone now. This preference will not activate sharing automatically when a backend is added: pairing and consent must be verified again.",style=MaterialTheme.typography.bodySmall,muted=true)};LogChoices("Categories",listOf("Period dates","Flow & cramps","Mood","Cravings","Symptoms"),categories) {categories=it};Paper {Row(verticalAlignment=Alignment.CenterVertically) {PText("Include existing history",Modifier.weight(1f));Switch(history,{history=it})};Row(verticalAlignment=Alignment.CenterVertically) {PText("Include private notes separately",Modifier.weight(1f));Switch(notes,{notes=it})};PText("Otherwise only new saved entries would be included. Notes stay private unless selected separately.",style=MaterialTheme.typography.bodySmall,muted=true)};Paper {PText("Revoke consent or leave the space to stop future access. Previously viewed information cannot be erased from someone's memory or screenshots.",style=MaterialTheme.typography.bodySmall);Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(informed,{informed=it});PText("I understand and choose this sharing scope",Modifier.weight(1f))}};Button({onConfirm(categories,history,notes)},enabled=informed&&categories.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {PText("Save consent preference")};OutlinedButton(onClose) {PText("Keep private")}} }
}

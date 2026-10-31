package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.reminders.TaskReminders
import ai.mwm.client.ui.theme.AppTheme
import android.content.*
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable private fun LegacyWelcomeScreen(state: PipoState, onStart: () -> Unit, onRegenerate: () -> Unit, onSetCode: (String) -> Unit) {
    val context = LocalContext.current
    var join by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.padding(20.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) { Duo(22.dp); Spacer(Modifier.width(10.dp)); PText("Pipo2doo", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.width(10.dp)); PText("Little tasks, for two", style = MaterialTheme.typography.labelSmall, muted = true) }
        PText("Your cushion is ready.\nNow for your person.", Modifier.padding(horizontal = 20.dp), MaterialTheme.typography.headlineLarge)
        CatDrawing(Modifier.fillMaxWidth().height(208.dp), cushion = true)
        Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp), horizontalArrangement = Arrangement.SpaceBetween) { PText("Me · Here", style = MaterialTheme.typography.labelSmall); PText("You · Saved for you", style = MaterialTheme.typography.labelSmall) }
        PText("Your little shared space", Modifier.fillMaxWidth().padding(16.dp), MaterialTheme.typography.labelLarge, true, TextAlign.Center)
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { state.preferences.code.forEachIndexed { index, char -> Surface(Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(8.dp), color = AppTheme.colors.surface, border = BorderStroke(1.5.dp, if(index < 2) AppTheme.colors.primary else if(index < 4) AppTheme.colors.secondary else AppTheme.colors.accent)) { Box(contentAlignment = Alignment.Center) { PText(char.toString(), style = MaterialTheme.typography.headlineMedium) } } } }
        OutlinedButton(onRegenerate, Modifier.align(Alignment.CenterHorizontally)) { PText("Make a new code", style = MaterialTheme.typography.bodySmall, muted = true) }
        Button({
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Space code", state.preferences.code))
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Our Pipo2doo space code: ${state.preferences.code}. This version stores tasks locally on one device."), "Share code"))
        }, Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp)) { PText("Copy & share code") }
        PText("Tasks and partner profiles work on this device. Cross-phone pairing and sync are not connected in this local version.", Modifier.padding(20.dp, 12.dp), MaterialTheme.typography.bodySmall, true, TextAlign.Center)
        Paper(Modifier.padding(horizontal = 20.dp), color = AppTheme.colors.surfaceSunken) {
            OutlinedButton({ join = !join }) { PText("Have their code? Set it here") }
            if(join) { OutlinedTextField(code, { code = it.uppercase(Locale.getDefault()).filter(Char::isLetterOrDigit).take(6) }, label = { PText("Six-character space code") }, singleLine = true); PText("This sets the label for a local space; it does not connect another phone.", style = MaterialTheme.typography.bodySmall, muted = true); Button({ onSetCode(code); join = false }, enabled = code.length == 6) { PText("Set space code") } }
        }
        Button(onStart, Modifier.fillMaxWidth().padding(20.dp).height(52.dp)) { PText("Start on this device") }
    }
}

@Composable fun TaskDetailScreen(task: Task, state: PipoState, onBack: () -> Unit, onEdit: () -> Unit, onToggle: () -> Unit, onSave: (Task) -> Unit, onDelete: ((String?) -> Unit) -> Unit, onThanks: (String) -> Unit) {
    val canEdit=ai.mwm.client.cloud.canEditTask(task,state,ai.mwm.client.cloud.LocalPartnerConnection.current)
    if(!canEdit) {
        LazyColumn(contentPadding=PaddingValues(bottom=24.dp)) {
            item {BackBar("Today",onBack)}
            item {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Badge("Read-only · ${ownerName(task.owner,state.preferences)}",ownerColor(task.owner))
                PText(task.title,style=MaterialTheme.typography.headlineLarge,align=if(task.owner==Owner.You) TextAlign.End else TextAlign.Start)
                PText("Both partners can change shared tasks. Editing is temporarily unavailable; wait for syncing to finish or reconnect and refresh.",style=MaterialTheme.typography.bodySmall,muted=true)
            }}
            item {Sticky(Modifier.padding(start=52.dp,end=12.dp,top=26.dp,bottom=8.dp)) {PText("Little note",style=MaterialTheme.typography.labelSmall,muted=true);PText(task.note.ifBlank {"No note"});ApprovedCat(Modifier.size(64.dp,38.dp),"note")}}
            item {Paper(Modifier.padding(20.dp)) {PText("When & nudges",style=MaterialTheme.typography.titleMedium);PText("${dayText(task.day)} · ${timeText(task.minute)} · ${task.repeat.label}");PText("Lives in ${state.lists.find {it.id==task.listId}?.name ?: "a list"}",muted=true);PText("Your partner's personal reminders are private.",style=MaterialTheme.typography.bodySmall,muted=true)}}
            if(!task.done) item { Box(Modifier.padding(horizontal=20.dp)) { PokeButton(state.preferences.partnerName,task) } }
            if(task.done) item {Button({onThanks(task.title)},Modifier.fillMaxWidth().padding(20.dp)) {PText("Send ${state.preferences.partnerName} a thank-you")}}
        }
        return
    }
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var move by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleting by remember(task.id) {mutableStateOf(false)}
    var deleteError by remember(task.id) {mutableStateOf<String?>(null)}
    var scheduleExpanded by rememberSaveable { mutableStateOf(false) }
    var noteEditor by rememberSaveable(task.id) { mutableStateOf(false) }
    var noteDraft by rememberSaveable(task.id) { mutableStateOf("") }
    var confirmNoteDelete by rememberSaveable(task.id) { mutableStateOf(false) }
    // Keep the header's approved initial alignment when assignment changes.
    val align = remember(task.id) { if(task.owner == Owner.You) TextAlign.End else TextAlign.Start }
    val editNote: () -> Unit = { noteDraft = task.note; noteEditor = true }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackBar("Today", onBack) { Box { IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "More task options") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ PText(if(task.done) "Reopen task" else "Mark complete") }, { menu = false; onToggle() }); DropdownMenuItem({ PText("Edit task") }, { menu = false; onEdit() }); DropdownMenuItem({ PText("Delete task") }, { menu = false; confirmDelete = true }) } } } }
        item {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PText(if(task.done) "Ticked off · ${Instant.ofEpochMilli(task.completedAt).atZone(clockZone(state.preferences.clockZone)).format(DateTimeFormatter.ofPattern("EEE h:mm a", Locale.getDefault()))}" else "Shared task", Modifier.fillMaxWidth(), MaterialTheme.typography.labelSmall, true, align)
                PText("Title · Tap to edit", Modifier.fillMaxWidth(), MaterialTheme.typography.labelSmall, true, align)
                Surface(onClick=onEdit,shape=RoundedCornerShape(16.dp),color=AppTheme.colors.surface,border=BorderStroke(1.dp,AppTheme.colors.separator)) {Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {PText(task.title,Modifier.weight(1f),MaterialTheme.typography.headlineLarge,align=align,decoration=if(task.done)TextDecoration.LineThrough else null);Icon(Icons.Outlined.Edit,"Edit task title",Modifier.size(20.dp))}}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=if(align==TextAlign.End) Arrangement.End else Arrangement.Start,verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {PText("Whose is it?",style=MaterialTheme.typography.labelSmall,muted=true);Text(ownerName(task.owner,state.preferences),style=MaterialTheme.typography.labelLarge,color=AppTheme.colors.textPrimary,maxLines=1,overflow=TextOverflow.Ellipsis)};Spacer(Modifier.width(12.dp));Surface(shape=CircleShape,color=AppTheme.colors.surface,border=BorderStroke(1.dp,AppTheme.colors.separator)) {Row(Modifier.padding(5.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {listOf(Owner.Me,Owner.Together,Owner.You).forEach { owner->Surface(onClick={onSave(task.copy(owner=owner))},modifier=Modifier.size(40.dp),shape=CircleShape,color=ownerColor(owner).copy(alpha=if(task.owner==owner) 1f else .22f).compositeOver(AppTheme.colors.surface)) {Box(contentAlignment=Alignment.Center) {PText(if(owner==Owner.Together) "Us" else ownerName(owner,state.preferences),style=MaterialTheme.typography.bodySmall)}}} } } }
            }
        }
        item { Sticky(Modifier.padding(start=52.dp,end=12.dp,top=26.dp,bottom=8.dp),rotation=-2.2f) { PText("Little note", style = MaterialTheme.typography.labelSmall, muted = true); PText(task.note.ifBlank { "Tap to add a little note" }, Modifier.fillMaxWidth().clickable(onClick = editNote), style = MaterialTheme.typography.bodyLarge.copy(fontSize=17.sp,lineHeight=26.sp,fontStyle=androidx.compose.ui.text.font.FontStyle.Italic)); Row(verticalAlignment=Alignment.Bottom) {Column(Modifier.weight(1f)) {OutlinedButton(editNote) { Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); PText("Edit note") }; if(task.note.isNotBlank()) OutlinedButton({ confirmNoteDelete = true }) { Icon(Icons.Outlined.DeleteOutline, null,Modifier.size(16.dp)); PText("Delete note",style=MaterialTheme.typography.bodySmall) }}; ApprovedCat(Modifier.size(64.dp,38.dp),"note") } } }
        item { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PText("When & nudges", style = MaterialTheme.typography.titleMedium)
            Surface(shape=RoundedCornerShape(14.dp),color=AppTheme.colors.surface,border=BorderStroke(1.dp,AppTheme.colors.separator),shadowElevation=1.dp) {
                Column {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Column(Modifier.weight(1f).clickable {pickDay(context,task.day) {onSave(task.copy(day=it))}}.padding(16.dp,14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {PText("Due",style=MaterialTheme.typography.labelSmall,muted=true);PText(dayText(task.day),style=MaterialTheme.typography.titleMedium);OutlinedButton({pickTime(context,task.minute) {onSave(task.copy(minute=it))}},contentPadding=PaddingValues(0.dp),modifier=Modifier.height(24.dp)) {PText("By ${timeText(task.minute)}",style=MaterialTheme.typography.bodySmall,muted=true)}}
                        Column(Modifier.weight(1f).padding(16.dp,14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {Row(verticalAlignment=Alignment.CenterVertically) {PText("Gentle nudge",Modifier.weight(1f),MaterialTheme.typography.labelSmall,true);Switch(task.reminder,{onSave(task.copy(reminder=it))})};PText(if(task.offset==60) "1 hr before" else if(task.offset==0) "At due time" else if(task.offset<0) "Morning of" else "${task.offset} min before",style=MaterialTheme.typography.titleMedium);PText("Soft ping, no alarm",style=MaterialTheme.typography.bodySmall,muted=true)}
                    }
                    DashedRule()
                    Row(Modifier.padding(16.dp,12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Box(Modifier.size(12.dp).rotate(12f).background(AppTheme.colors.secondary,RoundedCornerShape(4.dp)));Column(Modifier.weight(1f)) {PText("Lives in",style=MaterialTheme.typography.labelSmall,muted=true);PText(state.lists.find {it.id==task.listId}?.name ?: "A list",style=MaterialTheme.typography.labelLarge)};OutlinedButton({move=true},modifier=Modifier.height(40.dp)) {PText("Move")}}
                }
            }
            OutlinedButton({scheduleExpanded=!scheduleExpanded}) {PText(if(scheduleExpanded) "Hide repeat & tags" else "${task.repeat.label} · Repeat & tags",style=MaterialTheme.typography.bodySmall)}
            if(scheduleExpanded) Paper {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f).clickable { pickDay(context, task.day) { onSave(task.copy(day = it)) } }) { PText("Due · Tap to change", style = MaterialTheme.typography.labelSmall, muted = true); PText(dayText(task.day), style = MaterialTheme.typography.titleMedium) }; IconButton(onEdit) { Icon(Icons.Outlined.EditCalendar, "Edit date and time") } }
                OutlinedButton({ pickTime(context, task.minute) { onSave(task.copy(minute = it)) } }) { PText("Time · ${timeText(task.minute)}") }
                PText("Repeat · ${task.repeat.label}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Repeat.entries.forEach { repeat -> Pill(repeat.label, task.repeat == repeat, AppTheme.colors.secondary) { onSave(task.copy(repeat = repeat)) } } }
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { PText("Ongoing reminder", style = MaterialTheme.typography.labelLarge); PText(if(task.done) "Cleared on completion" else if(task.reminder) "${if(task.offset == -1) "Morning of" else if(task.offset == 0) "At due time" else "${task.offset} min before"}" else "Off", style = MaterialTheme.typography.bodySmall, muted = true) }; Switch(task.reminder, { onSave(task.copy(reminder = it)) }) }
                OutlinedButton(onEdit) { PText("Edit reminder timing & tags", style = MaterialTheme.typography.labelLarge) }
                PText("Android controls dismissal and delivery. Permissions can be changed in Us & settings.", style = MaterialTheme.typography.bodySmall, muted = true)
                Row(verticalAlignment = Alignment.CenterVertically) { PText("Lives in ${state.lists.find { it.id == task.listId }?.name ?: "a list"}", Modifier.weight(1f)); OutlinedButton({ move = true }) { PText("Move") } }
                val tags = task.tags.mapNotNull { id -> state.tags.find { it.id == id }?.name }.joinToString(" · ")
                if(task.tags.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(task.tags.mapNotNull { id -> state.tags.find { it.id == id } }, key = { it.id }) { tag -> InputChip(selected = true, onClick = { onSave(task.copy(tags = task.tags - tag.id)) }, label = { PText(tag.name) }, trailingIcon = { Icon(Icons.Outlined.Close, "Remove ${tag.name} from task", Modifier.size(16.dp)) }, shape = CircleShape) } }
                PText("Tags · ${tags.ifBlank { "None yet" }}", style = MaterialTheme.typography.bodySmall, muted = true)
            }
        } }
        item { Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { PText("Little history", style = MaterialTheme.typography.titleMedium); Seam(Modifier.fillMaxWidth()) {Column(Modifier.fillMaxWidth()) {task.events.sortedByDescending { it.at }.take(10).forEach { event -> Row(Modifier.fillMaxWidth().padding(vertical=7.dp), horizontalArrangement = if(event.owner == Owner.You) Arrangement.End else Arrangement.Start) { Column(Modifier.fillMaxWidth(.45f),horizontalAlignment=if(event.owner==Owner.You) Alignment.Start else Alignment.End) { PText(event.text, style = MaterialTheme.typography.bodySmall.copy(fontWeight=FontWeight.SemiBold),align=if(event.owner==Owner.You) TextAlign.Start else TextAlign.End); PText(Instant.ofEpochMilli(event.at).atZone(clockZone(state.preferences.clockZone)).format(DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a", Locale.getDefault())), style = MaterialTheme.typography.bodySmall, muted = true,align=if(event.owner==Owner.You) TextAlign.Start else TextAlign.End) } } }} } } }
        item { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { if(!task.done) Button(onToggle, Modifier.fillMaxWidth().height(52.dp)) { PText("Mark complete") }; if(!task.done && task.owner != Owner.Me) PokeButton(state.preferences.partnerName, task); if(task.done) Button({ onThanks(task.title) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.accent)) { Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(22.dp));Spacer(Modifier.width(8.dp));PText("Send ${state.preferences.partnerName} a thank-you") }; OutlinedButton({ confirmDelete = true }, Modifier.align(Alignment.End),border=BorderStroke(1.5.dp,AppTheme.colors.error),colors=ButtonDefaults.outlinedButtonColors(contentColor=AppTheme.colors.error)) { Text("Delete task") } } }
    }
    if(move) AlertDialog(onDismissRequest = { move = false }, title = { PText("Move to a list") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PText("Choose a cozy spot for this task.", muted = true)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.lists, key = { it.id }) { list ->
                    Surface(onClick = { onSave(task.copy(listId = list.id)); move = false }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(AppTheme.radius.md.dp), color = if(list.id == task.listId) AppTheme.colors.secondary.copy(alpha = .2f).compositeOver(AppTheme.colors.surface) else AppTheme.colors.surface, border = BorderStroke(1.dp, AppTheme.colors.separator)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PText(list.emoji, style = MaterialTheme.typography.titleLarge)
                            Column(Modifier.weight(1f)) { PText(list.name, style = MaterialTheme.typography.titleMedium); PText(if(list.id == task.listId) "Current list" else "Move here", style = MaterialTheme.typography.bodySmall, muted = true) }
                            Icon(if(list.id == task.listId) Icons.Outlined.CheckCircle else Icons.Outlined.ChevronRight, null, tint = AppTheme.colors.secondary)
                        }
                    }
                }
            }
        }
    }, confirmButton = { OutlinedButton({ move = false }) { PText("Cancel") } })
    if(noteEditor) AlertDialog(onDismissRequest = { noteEditor = false }, title = { PText("Edit little note") }, text = { OutlinedTextField(noteDraft, { noteDraft = it }, modifier = Modifier.fillMaxWidth(), label = { PText("Little note") }, minLines = 3) }, confirmButton = { OutlinedButton({ if(noteDraft.isBlank() && task.note.isNotBlank()) confirmNoteDelete = true else { onSave(task.copy(note = noteDraft)); noteEditor = false } }) { PText("Save note") } }, dismissButton = { OutlinedButton({ noteEditor = false }) { PText("Cancel") } })
    if(confirmNoteDelete) AlertDialog(onDismissRequest = { confirmNoteDelete = false }, title = { PText("Delete this note?") }, text = { PText("The task will stay. Only its little note will be removed.") }, confirmButton = { OutlinedButton({ onSave(task.copy(note = "")); confirmNoteDelete = false; noteEditor = false }) { PText("Delete note") } }, dismissButton = { OutlinedButton({ confirmNoteDelete = false }) { PText("Keep note") } })
    if(confirmDelete) AlertDialog(onDismissRequest = { if(!deleting)confirmDelete = false }, title = { PText("Delete this task?") }, text = {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            PText("Delete “${task.title}” from the shared space for both partners? Its reminder will be cleared. Other existing occurrences will stay.")
            deleteError?.let {PText(it,style=MaterialTheme.typography.bodySmall)}
        }
    }, confirmButton = { OutlinedButton({
        deleting=true;deleteError=null
        onDelete {failure->deleting=false;if(failure==null)confirmDelete=false else deleteError=failure}
    },enabled=!deleting) { PText(if(deleting) "Deleting…" else "Delete for both") } }, dismissButton = { OutlinedButton({ confirmDelete = false },enabled=!deleting) { PText("Cancel") } })
}

@Composable fun ProgressScreen(state: PipoState, onTask: (String) -> Unit, onThanks: () -> Unit) {
    var monthView by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now(clockZone(state.preferences.clockZone))
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val done = state.tasks.filter { it.done }.sortedByDescending { it.completedAt }
    fun completionDay(task: Task) = Instant.ofEpochMilli(task.completedAt).atZone(clockZone(state.preferences.clockZone)).toLocalDate()
    val weekly = done.filter { !completionDay(it).isBefore(monday) && !completionDay(it).isAfter(today) }
    val monthTasks = done.filter { completionDay(it).month == today.month && completionDay(it).year == today.year }
    val month = monthTasks.size
    val rangeTasks = if(monthView) monthTasks else weekly
    val chartStart = if(monthView) today.withDayOfMonth(1) else monday
    val chartBuckets = if(monthView) 5 else 7
    val days = done.map(::completionDay).toSet()
    var streak = 0
    var cursor = if(today in days) today else today.minusDays(1)
    while(cursor in days) { streak++; cursor = cursor.minusDays(1) }
    val mood = (weekly.size * 10).coerceAtMost(100)
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(20.dp,8.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(12.dp)) { Column(Modifier.weight(1f)) { PText("Our progress",style=MaterialTheme.typography.headlineLarge);PText("${state.preferences.myName} & ${state.preferences.partnerName}",style=MaterialTheme.typography.labelLarge,muted=true) };Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { Pill("Week", !monthView, AppTheme.colors.secondary) { monthView = false }; Pill("Month", monthView, AppTheme.colors.secondary) { monthView = true } } } }
        item { Surface(color=AppTheme.colors.surfaceSunken) { Column {DashedRule();Row(Modifier.fillMaxWidth().height(162.dp),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f).padding(start=20.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {PText("This week's cat",style=MaterialTheme.typography.labelSmall,muted=true);PText(if(mood>=60) "Fully stretched out" else "Getting cozy",style=MaterialTheme.typography.titleLarge);PText("${weekly.size} little tasks done",style=MaterialTheme.typography.bodySmall,muted=true);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {repeat(3) {i->Box(Modifier.width(if(i==2) 30.dp else 18.dp).height(6.dp).background(if(i<=mood/34) AppTheme.colors.secondary else AppTheme.colors.separator,CircleShape))}}};Box(Modifier.width(174.dp).fillMaxHeight()) {ApprovedCat(Modifier.align(Alignment.Center).size(174.dp,92.dp),"stretch");Surface(Modifier.align(Alignment.BottomEnd).padding(end=16.dp,bottom=26.dp).size(38.dp),color=AppTheme.colors.accent,shape=CircleShape) {Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {PText(streak.toString(),style=MaterialTheme.typography.labelLarge);PText("Days",style=MaterialTheme.typography.labelSmall)}}}};DashedRule()} } }
        item { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Paper {
                PText("Done this ${if(monthView) "month" else "week"}", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf(Owner.Me,Owner.Together,Owner.You).forEach { owner -> Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) { Box(Modifier.size(10.dp).background(ownerColor(owner),CircleShape));PText(ownerName(owner,state.preferences),style=MaterialTheme.typography.bodySmall,muted=true) } } }
                Row(Modifier.fillMaxWidth().height(164.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    (0 until chartBuckets).forEach { index -> val day = chartStart.plusDays(index.toLong() * if(monthView) 7L else 1L); val tasks = rangeTasks.filter { val d = completionDay(it); if(monthView) !d.isBefore(day) && d.isBefore(day.plusDays(7)) else d == day }; val max = (0 until chartBuckets).maxOf { bucket -> rangeTasks.count { val d = completionDay(it); val start = chartStart.plusDays(bucket.toLong() * if(monthView) 7L else 1L); if(monthView) !d.isBefore(start) && d.isBefore(start.plusDays(7)) else d == start } }.coerceAtLeast(1)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { PText(tasks.size.toString(), style = MaterialTheme.typography.bodySmall); Column(Modifier.height(112.dp).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) { Owner.entries.forEach { owner -> val count = tasks.count { it.owner == owner }; if(count > 0) Box(Modifier.fillMaxWidth().height((count*104f/max).dp).padding(bottom = 2.dp).background(ownerColor(owner), RoundedCornerShape(6.dp))) } }; PText(if(monthView) "W${index + 1}" else day.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())), style = MaterialTheme.typography.labelSmall, muted = true) }
                    }
                }
                DashedRule()
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { listOf(Owner.Me,Owner.Together,Owner.You).forEach { owner -> Column(horizontalAlignment=if(owner==Owner.You) Alignment.End else if(owner==Owner.Together) Alignment.CenterHorizontally else Alignment.Start) { Text(rangeTasks.count { it.owner==owner }.toString(),color=ownerColor(owner),style=MaterialTheme.typography.titleLarge);PText(ownerName(owner,state.preferences),style=MaterialTheme.typography.bodySmall,muted=true) } } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Paper(Modifier.weight(1f), AppTheme.colors.primary.copy(alpha = .22f).compositeOver(AppTheme.colors.surface)) { PText("This week", style = MaterialTheme.typography.labelSmall); PText(weekly.size.toString(), style = MaterialTheme.typography.displayLarge.copy(fontSize=44.sp,fontWeight=FontWeight.Light)) }; Paper(Modifier.weight(1f), AppTheme.colors.accent.copy(alpha = .18f).compositeOver(AppTheme.colors.surface)) { PText("This month", style = MaterialTheme.typography.labelSmall); PText(month.toString(),Modifier.fillMaxWidth(), MaterialTheme.typography.displayLarge.copy(fontSize=44.sp,fontWeight=FontWeight.Light),align=TextAlign.End) } }
            Paper(color=AppTheme.colors.accent.copy(alpha=.22f).compositeOver(AppTheme.colors.surface)) {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {PawStamp(Modifier.size(64.dp));Column(Modifier.weight(1f)) {PText("${state.preferences.partnerName} completed ${weekly.count {it.owner==Owner.You}} tasks this week",style=MaterialTheme.typography.titleMedium);PText("A little kindness goes a long way",style=MaterialTheme.typography.bodySmall,muted=true)}};Button(onThanks,Modifier.fillMaxWidth().heightIn(min=48.dp),colors=ButtonDefaults.buttonColors(containerColor=AppTheme.colors.accent)) {PText("Send ${state.preferences.partnerName} a thank-you")} }
            PText("Recently done",style=MaterialTheme.typography.titleMedium)
        } }
        item {Surface(Modifier.padding(horizontal=20.dp).fillMaxWidth(),shape=RoundedCornerShape(14.dp),color=AppTheme.colors.surface,border=BorderStroke(1.dp,AppTheme.colors.separator)) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {done.take(12).forEach {task->val partner=task.owner==Owner.You;val color=ownerColor(task.owner);Surface(onClick={onTask(task.id)},shape=RoundedCornerShape(14.dp),color=if(task.owner==Owner.Together) color.copy(alpha=.18f).compositeOver(AppTheme.colors.surface) else AppTheme.colors.surfaceSunken) {Row(Modifier.padding(12.dp,10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {if(!partner) Surface(Modifier.size(30.dp),shape=CircleShape,color=color) {Box(contentAlignment=Alignment.Center) {PText(ownerName(task.owner,state.preferences).take(1),style=MaterialTheme.typography.labelSmall)}};Column(Modifier.weight(1f),horizontalAlignment=if(partner) Alignment.End else Alignment.Start) {PText(task.title,style=MaterialTheme.typography.bodyMedium,align=if(partner) TextAlign.End else TextAlign.Start,decoration=TextDecoration.LineThrough);PText("Done by ${ownerName(task.owner,state.preferences)}",style=MaterialTheme.typography.bodySmall,muted=true)};PText(Instant.ofEpochMilli(task.completedAt).atZone(clockZone(state.preferences.clockZone)).format(DateTimeFormatter.ofPattern("h:mm a",Locale.getDefault())),style=MaterialTheme.typography.bodySmall,muted=true);if(partner) Surface(Modifier.size(30.dp),shape=CircleShape,color=color) {Box(contentAlignment=Alignment.Center) {PText(state.preferences.partnerName.take(1),style=MaterialTheme.typography.labelSmall)}}}}};if(done.isEmpty()) PText("Your first tick starts the story.",muted=true)}}}
    }
}

@Composable fun AppreciationScreen(state: PipoState, taskTitle: String, onBack: () -> Unit, onDeleteNote: (String) -> Unit = {}, onSend: (String, String, String, String, String, String, (String?)->Unit) -> Unit) {
    CuteChatScreen(state,taskTitle,onBack,onDeleteNote,onSend)
}

fun celebrate(context: android.content.Context, preferences: Preferences) {
    if(preferences.haptics) runCatching { context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)) }
    if(preferences.sound) runCatching { val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 20); tone.startTone(ToneGenerator.TONE_PROP_ACK, 100); Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 250) }
}

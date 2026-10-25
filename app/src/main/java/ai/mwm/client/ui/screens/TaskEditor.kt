package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.reminders.TaskReminders
import ai.mwm.client.ui.theme.AppTheme
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.PathEffect
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private val DraftSaver = listSaver<Task, Any>(save = { listOf(it.id, it.title, it.owner.name, it.day, it.minute ?: -1, it.listId, it.note, org.json.JSONArray(it.tags).toString(), it.repeat.name, it.reminder, it.offset, it.status.name, it.completedAt, it.parentId, it.assigneeUid, it.scheduledAt, it.scheduleZone, it.displayZone, it.seriesId) }, restore = { Task(id = it[0] as String, title = it[1] as String, owner = Owner.valueOf(it[2] as String), day = it[3] as Long, minute = (it[4] as Int).takeIf { m -> m >= 0 }, listId = it[5] as String, note = it[6] as String, tags = runCatching { org.json.JSONArray(it[7] as String).let { a -> (0 until a.length()).map { index -> a.getString(index) } } }.getOrElse { _ -> (it[7] as String).split(",").filter(String::isNotEmpty) }, repeat = Repeat.valueOf(it[8] as String), reminder = it[9] as Boolean, offset = it[10] as Int, status = TaskStatus.valueOf(it[11] as String), completedAt = it[12] as Long, parentId = it[13] as String, assigneeUid = it.getOrNull(14) as? String ?: "", scheduledAt = it.getOrNull(15) as? Long ?: 0L, scheduleZone = it.getOrNull(16) as? String ?: "", displayZone = it.getOrNull(17) as? String ?: "", seriesId = it.getOrNull(18) as? String ?: "") })

fun pickDay(context: android.content.Context, day: Long, onSelect: (Long) -> Unit) {
    val date = LocalDate.ofEpochDay(day)
    DatePickerDialog(context, { _, year, month, number -> onSelect(LocalDate.of(year, month + 1, number).toEpochDay()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
}
fun pickTime(context: android.content.Context, minute: Int?, onSelect: (Int) -> Unit) {
    val initial = minute ?: 540
    TimePickerDialog(context, { _, hour, minutes -> onSelect(hour * 60 + minutes) }, initial / 60, initial % 60, DateFormat.is24HourFormat(context)).show()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun TaskEditor(task: Task?, state: PipoState, defaultList: String?, onClose: () -> Unit, onSave: (Task, (String?) -> Unit) -> Unit, onSaveTag: (Tag, (String?) -> Unit) -> Unit, onManageTags: () -> Unit, onNewList: () -> Unit, requestNotifications: () -> Unit, initialStatus: TaskStatus = TaskStatus.Todo) {
    val context = LocalContext.current
    val c = AppTheme.colors
    val today = LocalDate.now(clockZone(state.preferences.clockZone)).toEpochDay()
    var draft by rememberSaveable(task?.id, stateSaver = DraftSaver) { mutableStateOf(task ?: Task(listId = defaultList ?: state.lists.firstOrNull()?.id ?: "home", status = initialStatus, day = today, minute = null, reminder = true, offset = 0)) }
    var confirmClearNote by rememberSaveable { mutableStateOf(false) }
    var tagPicker by rememberSaveable { mutableStateOf(false) }
    var newTagName by rememberSaveable { mutableStateOf("") }
    var creatingTag by remember { mutableStateOf(false) }
    var tagError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val presetOffsets = listOf(0, 10, 30, 60, 120, 1440, 2880)
    var customReminder by rememberSaveable { mutableStateOf(draft.offset !in presetOffsets) }
    var customAmount by rememberSaveable { mutableIntStateOf(draft.offset.coerceAtLeast(1)) }
    var customUnit by rememberSaveable { mutableIntStateOf(0) }
    var customAt by rememberSaveable { mutableIntStateOf(draft.minute ?: state.preferences.defaultReminderTime) }
    fun updateCustom(amount: Int = customAmount, unit: Int = customUnit, at: Int = customAt) {
        customAmount = amount.coerceIn(1, if(unit == 2) 29 else if(unit == 1) 720 else 43200); customUnit = unit; customAt = at; customReminder = true
        val offset = when(unit) { 0 -> customAmount; 1 -> customAmount * 60; else -> customAmount * 1440 + (draft.minute ?: state.preferences.defaultReminderTime) - at }
        draft = draft.copy(offset = offset.coerceIn(0, 43200))
    }
    fun updateTime(minute: Int?) {
        draft = draft.copy(minute = minute).withSavedSchedule(null, clockZone(state.preferences.clockZone))
        if(customReminder) updateCustom()
    }
    fun openDueTime() {
        val initial = draft.minute ?: 540
        TimePickerDialog(context, { _, hour, minutes -> updateTime(hour * 60 + minutes) }, initial / 60, initial % 60, DateFormat.is24HourFormat(context)).apply {
            setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "Anytime") { _, _ -> updateTime(null) }
        }.show()
    }
    LaunchedEffect(state.lists) {
        if(state.lists.isNotEmpty() && state.lists.none { it.id == draft.listId }) draft = draft.copy(listId = state.lists.first().id)
    }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = {if(!saving) onClose()}, sheetState = sheet, sheetGesturesEnabled = false, containerColor = c.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = null) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.98f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, enabled = !saving, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "Close without saving", Modifier.size(22.dp), tint = c.textPrimary) }
                Spacer(Modifier.weight(1f)); Duo(22.dp); Spacer(Modifier.width(8.dp)); PText(if(task == null) "New task" else "Edit task", style = MaterialTheme.typography.titleMedium)
            }
            LazyColumn(Modifier.weight(1f), state=rememberLazyListState(), overscrollEffect=null, contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    TaskCaption("What needs doing?")
                    BasicTextField(draft.title, { draft = draft.copy(title = it.take(80)) }, Modifier.fillMaxWidth().drawBehind { drawLine(c.primary, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) }.padding(2.dp, 4.dp, 2.dp, 8.dp), textStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-.3).sp, fontWeight = FontWeight.Bold, color = c.textPrimary), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), maxLines = 4, decorationBox = { inner ->
                        if(draft.title.isBlank()) PText("E.g. Water the basil", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp), muted = true)
                        inner()
                    })
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        PText("Required", style = MaterialTheme.typography.bodySmall, muted = true)
                        PText("${draft.title.length} / 80", style = MaterialTheme.typography.bodySmall, muted = true)
                    }
                }
                item {
                    TaskSectionLabel("Who's on it") { PText("Tap a circle", style = MaterialTheme.typography.bodySmall, muted = true) }
                    TaskOwnerCircles(draft.owner, state.preferences) { draft = draft.copy(owner = it) }
                    PText(when(draft.owner) { Owner.Together -> "Together · Shows on both of your todays"; Owner.Me -> "${state.preferences.myName} · Shows on your today"; Owner.You -> "${state.preferences.partnerName} · Assigned to your partner" }, Modifier.fillMaxWidth().padding(top = 6.dp), MaterialTheme.typography.bodySmall, muted = true, align = TextAlign.Center)
                }
                item {
                    TaskSectionLabel("List") { TaskQuietAction("New list", onNewList) }
                    if(state.lists.isEmpty()) PText("Create your first list to save this task.", muted = true)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.lists, key = { it.id }) { list -> TaskChip("${list.emoji} ${list.name}", draft.listId == list.id) { draft = draft.copy(listId = list.id) } }
                    }
                }
                item {
                    TaskSectionLabel("When") { PText(LocalDate.ofEpochDay(draft.day).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)), style = MaterialTheme.typography.bodySmall, muted = true) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TaskChip("Today", draft.day == today) { draft = draft.copy(day = today) }
                        TaskChip("Tomorrow", draft.day == today + 1) { draft = draft.copy(day = today + 1) }
                        TaskChip("Pick date", draft.day != today && draft.day != today + 1, Icons.Outlined.CalendarToday) { pickDay(context, draft.day) { draft = draft.copy(day = it) } }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TaskTime(draft.minute) { openDueTime() }
                        TaskSegments(Repeat.entries.map { it.label }, draft.repeat.ordinal, Modifier.weight(1f)) { draft = draft.copy(repeat = Repeat.entries[it]) }
                    }
                }
                item {
                    TaskSectionLabel("Tags") { TaskQuietAction("Manage tags", onManageTags) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        draft.tags.mapNotNull { id -> state.tags.find { it.id == id } }.forEach { tag ->
                            Surface(shape = CircleShape, color = c.secondary.copy(alpha = .18f).compositeOver(c.surface), border = BorderStroke(1.5.dp, c.separator)) {
                                Row(Modifier.heightIn(min = 36.dp).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    PText(tag.name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium))
                                    IconButton({ draft = draft.copy(tags = draft.tags - tag.id) }, Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, "Remove tag ${tag.name}", Modifier.size(12.dp), tint = c.textSecondary) }
                                }
                            }
                        }
                        TaskChip("Add tag", icon = Icons.Outlined.Add) { tagPicker = true }
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = c.surfaceSunken) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    PText(if(draft.reminder) "Gentle nudge" else "No nudge", style = MaterialTheme.typography.titleMedium)
                                    PText("On for new tasks · You can turn it off", style = MaterialTheme.typography.bodySmall, muted = true)
                                }
                                // CSS-sized switch; Material's default switch is wider and taller.
                                Box(Modifier.size(50.dp, 30.dp).background(if(draft.reminder) c.secondary else c.surface, CircleShape).border(1.5.dp,c.separator,CircleShape).semantics { contentDescription = "Gentle reminder" }.toggleable(value = draft.reminder, role = Role.Switch) { enabled -> draft = draft.copy(reminder = enabled); if(enabled) requestNotifications() }.padding(4.dp), contentAlignment = if(draft.reminder) Alignment.CenterEnd else Alignment.CenterStart) {
                                    Box(Modifier.size(22.dp).background(c.surfaceElevated, CircleShape))
                                }
                            }
                            if(draft.reminder) {
                                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { TaskCaption("Remind me"); PText("Due ${timeText(draft.minute)}", style = MaterialTheme.typography.bodySmall, muted = true) }
                                val titles = listOf("On time", "10 min", "30 min", "1 hr", "2 hr", "1 day", "2 days", "Custom")
                                titles.chunked(4).forEachIndexed { row, labels ->
                                    Row(Modifier.fillMaxWidth().padding(bottom = if(row == 0) 8.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        labels.forEachIndexed { col, label ->
                                            val index = row * 4 + col
                                            TaskReminderPreset(label, when(index) { 0 -> timeText(draft.minute ?: state.preferences.defaultReminderTime); 5, 6 -> "Same time"; 7 -> ""; else -> "Before" }, if(index == 7) customReminder else !customReminder && draft.offset == presetOffsets[index], Modifier.weight(1f)) {
                                                if(index == 7) updateCustom() else { customReminder = false; draft = draft.copy(offset = presetOffsets[index]) }
                                            }
                                        }
                                    }
                                }
                                if(customReminder) {
                                    Surface(Modifier.padding(top = 10.dp), shape = RoundedCornerShape(14.dp), color = c.surface, border = BorderStroke(1.5.dp, c.primary.copy(alpha = .55f))) {
                                        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                                PText("How early", Modifier.width(82.dp), MaterialTheme.typography.labelLarge, muted = true)
                                                Surface(onClick = { updateCustom((customAmount - 1).coerceAtLeast(1)) }, shape = CircleShape, color = c.surfaceSunken, enabled = customAmount > 1) { Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Remove, "Decrease", Modifier.size(16.dp)) } }
                                                PText("$customAmount", Modifier.width(40.dp), MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp), align = TextAlign.Center)
                                                Surface(onClick = { updateCustom((customAmount + 1).coerceAtMost(if(customUnit == 2) 29 else if(customUnit == 1) 720 else 43200)) }, shape = CircleShape, color = c.surfaceSunken) { Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, "Increase", Modifier.size(16.dp)) } }
                                                PText("Before", Modifier.padding(start = 8.dp), MaterialTheme.typography.bodySmall, muted = true)
                                            }
                                            HorizontalDivider(color = c.separator)
                                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                                PText("Unit", Modifier.width(82.dp), MaterialTheme.typography.labelLarge, muted = true)
                                                TaskSegments(listOf("Min", "Hours", "Days"), customUnit, Modifier.weight(1f)) { updateCustom(customAmount.coerceAtMost(if(it == 2) 29 else if(it == 1) 720 else 43200), it) }
                                            }
                                            if(customUnit == 2) {
                                                HorizontalDivider(color = c.separator)
                                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    PText("At", Modifier.width(82.dp), MaterialTheme.typography.labelLarge, muted = true)
                                                    TaskTime(customAt) { pickTime(context, customAt) { updateCustom(at = it) } }
                                                    PText("On ${LocalDate.ofEpochDay(draft.day - customAmount).format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))}", Modifier.padding(start = 10.dp), MaterialTheme.typography.bodySmall, muted = true)
                                                }
                                            }
                                        }
                                    }
                                }
                                val fire = java.time.Instant.ofEpochMilli(draft.withSavedSchedule(task, clockZone(state.preferences.clockZone)).reminderAt(state.preferences.defaultReminderTime, clockZone(state.preferences.clockZone))).atZone(clockZone(state.preferences.clockZone))
                                Row(Modifier.fillMaxWidth().padding(top = 12.dp).drawBehind { drawLine(c.separator, Offset.Zero, Offset(size.width, 0f), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))) }.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(18.dp), tint = c.textPrimary)
                                    PText("Nudge at ${fire.format(DateTimeFormatter.ofPattern("h:mm a, EEE d MMM", Locale.ENGLISH))} · ${fire.zone.id}", style = MaterialTheme.typography.bodyMedium)
                                }
                                val allowed = TaskReminders.allowed(context)
                                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(8.dp).background(if(allowed) c.success else c.warning, CircleShape))
                                    PText(if(allowed) "Android notifications allowed · Shared task reminders on both phones" else "Android notifications off", style = MaterialTheme.typography.bodySmall, muted = true)
                                }
                                if(!allowed) TaskQuietAction("Enable notifications", requestNotifications)
                                if(state.preferences.quietEnabled) PText("Task reminders arrive silently during your quiet hours.", style = MaterialTheme.typography.bodySmall, muted = true)
                            }
                        }
                    }
                }
                item {
                    TaskSectionLabel("Private note") { PText("Optional · Only you see it", style = MaterialTheme.typography.bodySmall, muted = true) }
                    TaskNotePaper(Modifier.padding(start = 2.dp, end = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { PText("Just for ${state.preferences.myName}", Modifier.weight(1f), MaterialTheme.typography.bodySmall, muted = true); TaskNoteCat() }
                        BasicTextField(draft.note, { draft = draft.copy(note = it.take(240)) }, Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.textPrimary), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), decorationBox = { inner -> if(draft.note.isEmpty()) PText("Anything to remember…", muted = true); inner() })
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { PText("${draft.note.length} / 240", Modifier.weight(1f), MaterialTheme.typography.bodySmall, muted = true); TextButton({ confirmClearNote = true }, enabled = draft.note.isNotEmpty()) { Text("Clear note", color = c.error, style = MaterialTheme.typography.labelLarge) } }
                    }
                }
            }
            if(saveError!=null) PText(saveError ?: "",Modifier.padding(horizontal=20.dp),MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().padding(20.dp, 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { TaskQuietAction("Cancel", {if(!saving) onClose()}) }
                Button({
                    if(!saving) {
                        saving=true
                        saveError=null
                        val submitted=draft.copy(tags=draft.tags.filter { id -> state.tags.any { it.id == id } })
                        onSave(submitted) {failure ->
                            saving=false
                            if(failure==null) {if(submitted.reminder) requestNotifications();onClose()}
                            else saveError=failure
                        }
                    }
                }, enabled = !saving && !creatingTag && draft.title.isNotBlank() && state.lists.any { it.id == draft.listId }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = CircleShape) {
                    if(saving) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
                    else PText("Save task")
                }
            }
        }
    }
    if(tagPicker) AlertDialog(onDismissRequest = { tagPicker = false }, title = { PText("Add tag") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if(state.tags.isEmpty()) PText("No tags yet. Create one below.", muted = true)
            state.tags.forEach { tag -> TaskChip(tag.name, tag.id in draft.tags) { draft = draft.copy(tags = if(tag.id in draft.tags) draft.tags - tag.id else draft.tags + tag.id) } }
            HorizontalDivider()
            OutlinedTextField(newTagName, {newTagName=it.take(30)},label={PText("New tag")},singleLine=true)
            val duplicate=state.tags.any {it.name.equals(newTagName.trim(),true)}
            Button({
                val tag=Tag(name=newTagName.trim())
                creatingTag=true
                tagError=null
                onSaveTag(tag) {failure ->
                    creatingTag=false
                    if(failure==null) {
                        draft=draft.copy(tags=draft.tags+tag.id)
                        newTagName=""
                        tagPicker=false
                    } else tagError=failure
                }
            },enabled=!creatingTag && newTagName.isNotBlank() && !duplicate) {PText(if(creatingTag) "Saving…" else "Create and select")}
            if(duplicate) PText("That tag already exists.",muted=true)
            if(tagError!=null) PText(tagError ?: "",style=MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton({ tagPicker = false }) { PText("Done") } }, dismissButton = { TextButton({ tagPicker = false; onManageTags() }) { PText("Manage tags") } })
    if(confirmClearNote) AlertDialog(onDismissRequest = { confirmClearNote = false }, title = { PText("Clear this note?") }, text = { PText("Your task will stay. The note will be removed when you save.") }, confirmButton = { OutlinedButton({ draft = draft.copy(note = ""); confirmClearNote = false }) { PText("Clear note") } }, dismissButton = { OutlinedButton({ confirmClearNote = false }) { PText("Keep note") } })
}

@Composable fun TagManager(state: PipoState, onSave: (Tag, (String?) -> Unit) -> Unit, onDelete: (String, (String?) -> Unit) -> Unit, onClose: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val duplicate = state.tags.any { it.id != editingId && it.name.equals(name.trim(), true) }
    AlertDialog(onDismissRequest = { if(!busy) onClose() }, title = { PText("Set tags", style = MaterialTheme.typography.titleLarge) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PText("Create, rename or remove tags. Removing a tag also removes it from your tasks.", style = MaterialTheme.typography.bodySmall, muted = true)
            LazyColumn(Modifier.heightIn(max = 240.dp)) { items(state.tags, key = { it.id }) { tag -> Row(verticalAlignment = Alignment.CenterVertically) { OutlinedButton({ editingId = tag.id; name = tag.name }, Modifier.weight(1f), enabled = !busy) { PText(tag.name) }; IconButton({ deleteId = tag.id }, enabled = !busy) { Icon(Icons.Outlined.DeleteOutline, "Delete ${tag.name}") } } } }
            OutlinedTextField(name, { name = it.take(30) }, enabled = !busy, label = { PText(if(editingId == null) "New tag" else "Rename tag") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), singleLine = true, isError = duplicate, supportingText = { if(duplicate) PText("That tag already exists.") })
            Row {
                Button({
                    busy = true; error = null
                    onSave(Tag(editingId ?: newId(), name.trim())) { failure ->
                        busy = false
                        if(failure == null) { name = ""; editingId = null } else error = failure
                    }
                }, enabled = !busy && name.isNotBlank() && !duplicate) { PText(if(busy) "Saving…" else if(editingId == null) "Add tag" else "Rename") }
                if(editingId != null) OutlinedButton({ editingId = null; name = "" }, enabled = !busy) { PText("Cancel edit") }
            }
            error?.let { PText(it, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { OutlinedButton(onClose, enabled = !busy) { PText("Done") } })
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { PText("Remove tag?") }, text = { PText("Your tasks will stay, without this tag.") }, confirmButton = { OutlinedButton({ busy = true; error = null; deleteId = null
        onDelete(id) { failure ->
            busy = false
            if(failure == null) { if(editingId == id) { editingId = null; name = "" } } else error = failure
        } }) { PText("Remove") } }, dismissButton = { OutlinedButton({ deleteId = null }) { PText("Cancel") } }) }
}


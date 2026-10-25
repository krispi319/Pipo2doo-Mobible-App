package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TodayScreen(state: PipoState, onOpen: (String) -> Unit, onToggle: (Task) -> Unit, onPreferences: ((Preferences) -> Preferences) -> Unit = {}, onReminders: () -> Unit, onCare: () -> Unit = {}, locketRequest: String? = null, onLocketOpened: () -> Unit = {}) {
    var filter by rememberSaveable { mutableStateOf("All") }
    val today = currentTaskDay(state.preferences.clockZone)
    val tasks = state.tasks.filter { (!it.done && it.day <= today) || (it.done && it.completedAt > 0 && Instant.ofEpochMilli(it.completedAt).atZone(clockZone(state.preferences.clockZone)).toLocalDate().toEpochDay() == today) }
    var clockTarget by rememberSaveable {mutableStateOf("")}
    val done = tasks.count { it.done }
    val colors = AppTheme.colors
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 144.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(20.dp,12.dp), verticalAlignment = Alignment.Top) { Column(Modifier.weight(1f)) { PText("Good morning, ${state.preferences.myName}", style = MaterialTheme.typography.labelLarge, muted = true); PText("Today", style = MaterialTheme.typography.headlineLarge) }; Surface(onClick = onReminders, modifier = Modifier.size(48.dp), shape = CircleShape, color = colors.surface, border = BorderStroke(1.dp, colors.separator), shadowElevation = 1.dp) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.NotificationsNone, "Notifications",Modifier.size(22.dp)); if(state.tasks.any { it.isOwnReminder() }) Box(Modifier.align(Alignment.TopEnd).padding(top=11.dp,end=12.dp).size(9.dp).background(colors.accent,CircleShape)) } } } }
        item {
            TodayRings(state.preferences,tasks,filter,onClock={clockTarget=if(it==Owner.Me) "mine" else "partner"}) {filter=it}
        }
        item {LocalLocket(state.notes,locketRequest,onLocketOpened,state.preferences)}
        item {
            FlowRow(Modifier.padding(start=20.dp,end=20.dp,bottom=24.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { (listOf("All") + Owner.entries.map { it.label }).forEach { label -> Pill("${if(label=="All") "All" else ownerName(Owner.valueOf(label),state.preferences)} · ${tasks.count { label == "All" || it.owner.label == label }}", filter == label, Owner.entries.find { it.label == label }?.let { ownerColor(it) } ?: colors.primary) { filter = label } } }
        }
        val shown = tasks.filter { filter == "All" || it.owner.label == filter }
        item { Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,bottom=12.dp),horizontalArrangement=Arrangement.SpaceBetween) { PText("Still to do",style=MaterialTheme.typography.titleMedium); PText("${shown.count { !it.done }} left",style=MaterialTheme.typography.bodySmall,muted=true) } }
        items(shown.filterNot { it.done }.sortedWith(compareBy<Task> { it.day }.thenBy { it.minute ?: 1440 }), key = { it.id }) { task -> TaskRow(task, state, { onOpen(task.id) }, { onToggle(task) }) }
        if(shown.none { !it.done }) item { PText("A clear windowsill. Add a little task when you're ready.", Modifier.padding(24.dp), muted = true) }
        item { Row(Modifier.fillMaxWidth().padding(20.dp,12.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(12.dp)) { DashedRule(Modifier.weight(1f).padding(bottom=10.dp));ApprovedCat(Modifier.size(58.dp,44.dp)) } }
        if(shown.any { it.done }) item { Row(Modifier.fillMaxWidth().padding(20.dp,12.dp),horizontalArrangement=Arrangement.SpaceBetween) { PText("Done today",style=MaterialTheme.typography.titleMedium);PText("${shown.count { it.done }} finished",style=MaterialTheme.typography.bodySmall,muted=true) } }
        items(shown.filter { it.done }.sortedByDescending { it.completedAt }, key = { it.id }) { task -> TaskRow(task, state, { onOpen(task.id) }, { onToggle(task) }) }
    }
    if(clockTarget.isNotBlank()) TimezoneChoiceDialog(clockTarget=="mine",{clockTarget=""}) {zone->
        val mine=clockTarget=="mine"
        onPreferences {if(mine)it.copy(clockZone=zone) else it.copy(partnerClockOverride=zone)}
        clockTarget=""
    }
}


@Composable fun UpcomingScreen(state:PipoState,onOpen:(String)->Unit,onToggle:(Task)->Unit,onEdit:(String)->Unit,onCalendar:()->Unit={}) {
    UpcomingNotes(state,onOpen,onToggle,onEdit,onCalendar)
}

@Composable fun ListsScreen(state: PipoState, onOpen: (String) -> Unit, onNew: () -> Unit, onTask: (String) -> Unit, onListSave: (SharedList) -> Unit = {}, onToggle: (Task) -> Unit = {}, onLayoutChange: (String)->Unit = {}) {
    var wide by rememberSaveable {mutableStateOf(false)}
    var query by rememberSaveable { mutableStateOf("") }
    var ownerFilter by rememberSaveable { mutableStateOf("All") }
    var filterMenu by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    val lists = state.lists.filter { list -> (ownerFilter == "All" || list.owner.name == ownerFilter) && (list.name.contains(query, true) || state.tasks.any { it.listId == list.id && it.title.contains(query, true) }) }
    LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 140.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(2) }) { Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.Bottom) { Column(Modifier.weight(1f)) { PText("${state.preferences.myName} & ${state.preferences.partnerName} · ${state.lists.size} lists",style=MaterialTheme.typography.labelSmall,muted=true);PText("Shared lists",style=MaterialTheme.typography.headlineLarge) };FilledIconButton(onNew,Modifier.size(48.dp)) { Icon(Icons.Outlined.Add,"New list") } } }
        item(span = { GridItemSpan(2) }) { Surface(color=AppTheme.colors.surfaceSunken,shape=CircleShape,border=BorderStroke(1.5.dp,AppTheme.colors.separator)) {Row(Modifier.padding(start=18.dp,end=8.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Icon(Icons.Outlined.Search,null,Modifier.size(18.dp));BasicTextField(query,{query=it},Modifier.weight(1f).padding(vertical=10.dp),singleLine=true,textStyle=MaterialTheme.typography.bodyLarge.copy(color=AppTheme.colors.textPrimary),decorationBox={inner->if(query.isBlank()) PText("Find a list or a task…",muted=true);inner()});Surface(onClick={filterMenu=true},shape=CircleShape,color=AppTheme.colors.surface,border=BorderStroke(1.dp,AppTheme.colors.separator)) {PText(if(ownerFilter=="All") "All of us" else ownerName(Owner.valueOf(ownerFilter),state.preferences),Modifier.padding(12.dp,8.dp),MaterialTheme.typography.bodySmall)}} } }
        item(span={GridItemSpan(2)}) {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(wide,{wide=true},label={PText("Wide")},leadingIcon={Icon(Icons.Outlined.ViewAgenda,null,Modifier.size(17.dp))});FilterChip(!wide,{wide=false},label={PText("Grid")},leadingIcon={Icon(Icons.Outlined.GridView,null,Modifier.size(17.dp))})}}
        if(query.isNotBlank()) {
            items(state.tasks.filter { it.title.contains(query, true) && (ownerFilter=="All" || it.owner.name==ownerFilter) }, key = { "search_${it.id}" }, span = { GridItemSpan(2) }) { task -> TaskRow(task, state, { onTask(task.id) }, { onToggle(task) }, aligned = false) }
        }
        if(lists.any { it.pinned }) item(span = { GridItemSpan(2) }) { Column { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { PText("Pinned on the fridge", Modifier.weight(1f), MaterialTheme.typography.titleMedium); OutlinedButton({ manage = true },contentPadding=PaddingValues(horizontal=12.dp),modifier=Modifier.height(40.dp)) { Icon(Icons.Outlined.Tune,null,Modifier.size(16.dp));PText("Manage",style=MaterialTheme.typography.bodySmall) } };PText("Tap a pin to unpin, or choose lists in fridge settings.",style=MaterialTheme.typography.bodySmall,muted=true) } }
        items(lists.filter { it.pinned }.chunked(if(wide) 1 else 2), key = { "pinned_${it.first().id}" }, span = { GridItemSpan(2) }) { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                pair.forEach { list -> FridgeListCard(list,state.tasks.filter { it.listId==list.id },state,true,{onOpen(list.id)},{onListSave(list.copy(pinned=false))},Modifier.weight(1f).fillMaxHeight()) }
                if(pair.size==1 && !wide) Spacer(Modifier.weight(1f))
            }
        }
        item(span = { GridItemSpan(2) }) { PText("All lists", Modifier.padding(top = 8.dp), MaterialTheme.typography.titleMedium) }
        items(lists.filterNot { it.pinned }.chunked(if(wide) 1 else 2), key = { "list_${it.first().id}" }, span = { GridItemSpan(2) }) { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                pair.forEach { list -> FridgeListCard(list,state.tasks.filter { it.listId==list.id },state,false,{onOpen(list.id)},{onListSave(list.copy(pinned=true))},Modifier.weight(1f).fillMaxHeight()) }
                if(pair.size==1 && !wide) Spacer(Modifier.weight(1f))
            }
        }
        item(span={GridItemSpan(if(wide) 2 else 1)}) { Surface(onClick=onNew,modifier=Modifier.fillMaxWidth().heightIn(min=if(wide) 130.dp else 270.dp),shape=RoundedCornerShape(20.dp),color=AppTheme.colors.primary.copy(alpha=.06f),border=BorderStroke(1.5.dp,AppTheme.colors.primary)) {Column(Modifier.padding(14.dp,12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp,Alignment.CenterVertically)) {OutlinedIconButton(onNew,Modifier.size(56.dp)) {Icon(Icons.Outlined.Add,"New list")};PText("New list",style=MaterialTheme.typography.titleLarge);PText("Name it, pick an emoji or a cat",style=MaterialTheme.typography.bodySmall,muted=true,align=TextAlign.Center)}} }
        item(span = { GridItemSpan(2) }) { PText("${state.tasks.count { !it.done }} open tasks · ${state.lists.size} shared lists", Modifier.fillMaxWidth().padding(12.dp), MaterialTheme.typography.bodySmall, true, TextAlign.Center) }
    }
    if(filterMenu) AlertDialog(onDismissRequest={filterMenu=false},title={PText("Whose lists?")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {(listOf("All")+Owner.entries.map {it.name}).forEach {value->Pill(if(value=="All") "All of us" else ownerName(Owner.valueOf(value),state.preferences),ownerFilter==value) {ownerFilter=value;filterMenu=false}}}},confirmButton={TextButton({filterMenu=false}) {PText("Close")}})
    if(manage) AlertDialog(onDismissRequest = { manage = false }, title = { PText("Fridge settings") }, text = { LazyColumn { items(state.lists, key = { it.id }) { list -> Row(verticalAlignment = Alignment.CenterVertically) { PText(list.name, Modifier.weight(1f)); Switch(list.pinned, { onListSave(list.copy(pinned = it)) },enabled=ai.mwm.client.cloud.canEditList(list,ai.mwm.client.cloud.LocalPartnerConnection.current)) } } } }, confirmButton = { OutlinedButton({ manage = false }) { PText("Done") } })
}

@Composable private fun FridgeListCard(list: SharedList, tasks: List<Task>, state: PipoState, pinned: Boolean, onOpen: () -> Unit, onPin: () -> Unit, modifier: Modifier = Modifier) {
    val owner = list.owner
    val color = ownerColor(owner)
    val done = tasks.count { it.done }
    val c = AppTheme.colors
    Box(modifier.padding(top=if(pinned) 16.dp else 0.dp)) {
        Surface(Modifier.fillMaxSize().clickable(onClick=onOpen),shape=RoundedCornerShape(20.dp),color=if(pinned) color.copy(alpha=if(owner==Owner.Together) .34f else .3f).compositeOver(c.surface) else if(owner==Owner.Together) color.copy(alpha=.1f).compositeOver(c.surface) else c.surface,border=if(pinned) null else BorderStroke(1.dp,c.separator),shadowElevation=if(pinned) 4.dp else 1.dp) {
            Column(Modifier.padding(12.dp,if(pinned) 26.dp else 14.dp,12.dp,14.dp),verticalArrangement=Arrangement.spacedBy(if(pinned) 12.dp else 8.dp),horizontalAlignment=if(owner==Owner.You) Alignment.End else Alignment.Start) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    if(pinned) Badge(ownerName(owner,state.preferences),color)
                    Surface(Modifier.size(40.dp,36.dp),shape=RoundedCornerShape(14.dp),color=c.surface,border=BorderStroke(1.dp,c.textPrimary)) {Box(contentAlignment=Alignment.Center) {PText(list.emoji,style=MaterialTheme.typography.titleLarge)}}
                    if(!pinned) IconButton(onPin,enabled=ai.mwm.client.cloud.canEditList(list,ai.mwm.client.cloud.LocalPartnerConnection.current),modifier=Modifier.size(36.dp).background(c.surfaceElevated,CircleShape).border(1.dp,c.separator,CircleShape)) {Icon(Icons.Outlined.PushPin,"Pin ${list.name}",Modifier.size(18.dp))}
                }
                PText(list.name,Modifier.fillMaxWidth(),MaterialTheme.typography.titleMedium.copy(fontSize=if(pinned) 22.sp else 21.sp,lineHeight=if(pinned) 27.sp else 26.sp,fontWeight=FontWeight.Bold),align=if(owner==Owner.You) TextAlign.End else TextAlign.Start)
                if(!pinned) Badge(ownerName(owner,state.preferences),color)
                val next=tasks.filterNot {it.done}.take(2).joinToString(", ") {it.title}
                if(list.pictures.isNotEmpty()) Box(Modifier.weight(1f).fillMaxWidth().heightIn(min=100.dp).clip(RoundedCornerShape(8.dp))) {
                    LocalPicture(list.pictures.first(),Modifier.matchParentSize())
                    Box(Modifier.align(if(owner==Owner.You) Alignment.BottomEnd else Alignment.BottomStart).padding(6.dp)) { Badge("${list.pictures.size} ${if(list.pictures.size==1) "picture" else "pictures"}",c.surface) }
                }
                else Surface(Modifier.weight(1f).fillMaxWidth().heightIn(min=84.dp),color=color.copy(alpha=.16f).compositeOver(c.surface),shape=RoundedCornerShape(8.dp)) {Column(Modifier.padding(12.dp,10.dp),verticalArrangement=Arrangement.Center) {PText(if(tasks.isNotEmpty() && next.isBlank()) "Last done" else "Next up",style=MaterialTheme.typography.labelSmall,muted=true);PText(next.ifBlank {if(tasks.isEmpty()) "Add your first little task" else tasks.maxByOrNull {it.completedAt}?.title ?: "All tasks complete"},style=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.SemiBold))}}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    PText("${tasks.size-done} open",style=MaterialTheme.typography.labelLarge)
                    Badge("$done/${tasks.size} done",color)
                }
                Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                    drawLine(c.textPrimary.copy(alpha=.25f),Offset(0f,3.dp.toPx()),Offset(size.width,3.dp.toPx()),1.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),4.dp.toPx())))
                    val x = size.width * if(tasks.isEmpty()) 0f else done.toFloat()/tasks.size
                    drawLine(color,Offset(0f,3.dp.toPx()),Offset(x,3.dp.toPx()),4.dp.toPx(),StrokeCap.Round)
                    drawCircle(color,4.dp.toPx(),Offset(x,3.dp.toPx()))
                }
            }
        }
        if(pinned) {
            IconButton(onPin,enabled=ai.mwm.client.cloud.canEditList(list,ai.mwm.client.cloud.LocalPartnerConnection.current),modifier=Modifier.align(Alignment.TopCenter).offset(y=(-2).dp).size(36.dp).background(c.accent,CircleShape).border(1.dp,c.separator,CircleShape)) {Icon(Icons.Outlined.PushPin,"Unpin ${list.name}",Modifier.size(18.dp),tint=c.onPrimary)}
        }
    }
}

@Composable fun ListDetailScreen(list: SharedList, state: PipoState, onBack: () -> Unit, onOpen: (String) -> Unit, onToggle: (Task) -> Unit, onStatus: (String, TaskStatus) -> Unit, onAdd: (String, TaskStatus) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onPin: () -> Unit, onQuickAdd: (Task) -> Unit = {}, onListSave: (SharedList) -> Unit = {}) {
    val editable=ai.mwm.client.cloud.canEditList(list,ai.mwm.client.cloud.LocalPartnerConnection.current)
    val deletable=editable && state.tasks.filter {it.listId==list.id}.all {ai.mwm.client.cloud.canEditTask(it,state,ai.mwm.client.cloud.LocalPartnerConnection.current)}
    var board by rememberSaveable(list.id) { mutableStateOf(true) }
    var quickTitle by rememberSaveable(list.id) { mutableStateOf("") }
    var quickOwner by rememberSaveable(list.id) { mutableStateOf(Owner.Me) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var doneExpanded by rememberSaveable { mutableStateOf(true) }
    var more by remember { mutableStateOf(false) }
    val tasks = state.tasks.filter { it.listId == list.id && (filter == "All" || it.owner.label == filter) }
    Column(Modifier.fillMaxSize()) {
        BackBar("Lists", onBack) { Box { IconButton({ more = true }) { Icon(Icons.Outlined.MoreVert, "More list options") }; DropdownMenu(more, { more = false }) { DropdownMenuItem({ PText("Edit list") }, { more = false; onEdit() },enabled=editable); DropdownMenuItem({ PText(if(list.pinned) "Unpin list" else "Pin to fridge") }, { more = false; onPin() },enabled=editable); DropdownMenuItem({ PText("Delete list") }, { more = false; onDelete() },enabled=deletable) } } }
        val allTasks = state.tasks.filter { it.listId == list.id }
        Column(Modifier.padding(16.dp,4.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { Surface(Modifier.size(44.dp),color=AppTheme.colors.secondary.copy(alpha=.3f).compositeOver(AppTheme.colors.surface),shape=RoundedCornerShape(14.dp)) { Box(contentAlignment=Alignment.Center) { PText(list.emoji,style=MaterialTheme.typography.headlineLarge) } };Column(Modifier.weight(1f)) { PText(list.name,style=MaterialTheme.typography.headlineSmall);PText("Shared by ${state.preferences.myName} & ${state.preferences.partnerName} · ${allTasks.size} tasks",style=MaterialTheme.typography.labelLarge,muted=true) } }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) { Duo(22.dp);LinearProgressIndicator(progress={if(allTasks.isEmpty()) 0f else allTasks.count { it.done }.toFloat()/allTasks.size},modifier=Modifier.weight(1f).height(6.dp),color=AppTheme.colors.secondary,trackColor=AppTheme.colors.surfaceSunken);PText("${allTasks.count { it.done }} of ${allTasks.size} done",style=MaterialTheme.typography.bodySmall,muted=true) }
        }
        ListPictureRail(list,onListSave,editable)
        Column(Modifier.padding(16.dp,6.dp)) {
            Surface(shape=CircleShape,color=AppTheme.colors.surfaceSunken,border=BorderStroke(1.dp,AppTheme.colors.separator)) { Row(Modifier.padding(4.dp)) { listOf(false,true).forEach { mode -> Surface(onClick={board=mode},shape=CircleShape,color=if(board==mode) AppTheme.colors.surfaceElevated else AppTheme.colors.surfaceSunken,shadowElevation=if(board==mode) 1.dp else 0.dp) { Row(Modifier.padding(20.dp,10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) { Icon(if(mode) Icons.Outlined.ViewWeek else Icons.Outlined.Checklist,null,Modifier.size(16.dp));PText(if(mode) "Board" else "List",style=MaterialTheme.typography.labelLarge) } } } } }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("All") + Owner.entries.map { it.label }) { label -> Pill("${if(label == "All") "Everyone" else if(label == "Me") state.preferences.myName else if(label == "You") state.preferences.partnerName else label} · ${allTasks.count { label=="All" || it.owner.label==label }}", filter == label) { filter = label } } }
        if(board) BoardView(tasks, state, Modifier.weight(1f), onOpen, onToggle, onStatus, editable) { status -> onAdd(list.id, status) }
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            items(tasks.filterNot { it.done }, key = { it.id }) { task -> TaskRow(task, state, { onOpen(task.id) }, { onToggle(task) }) }
            item { OutlinedButton({ doneExpanded = !doneExpanded }) { PText("${if(doneExpanded) "Hide" else "Show"} completed · ${tasks.count { it.done }}") } }
            if(doneExpanded) items(tasks.filter { it.done }.sortedByDescending { it.completedAt }, key = { it.id }) { task -> TaskRow(task, state, { onOpen(task.id) }, { onToggle(task) }) }
        }
        Surface(Modifier.padding(12.dp,8.dp).imePadding(),shape=CircleShape,color=AppTheme.colors.surfaceElevated,border=BorderStroke(1.dp,AppTheme.colors.separator),shadowElevation=8.dp) {
            Row(Modifier.fillMaxWidth().padding(6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Pill(ownerName(quickOwner,state.preferences),true,ownerColor(quickOwner).copy(alpha=.22f).compositeOver(AppTheme.colors.surface)) { quickOwner=Owner.entries[(quickOwner.ordinal+1)%Owner.entries.size] }
                BasicTextField(quickTitle,{quickTitle=it},Modifier.weight(1f),singleLine=true,textStyle=MaterialTheme.typography.bodyMedium.copy(color=AppTheme.colors.textPrimary),decorationBox={inner->if(quickTitle.isBlank()) PText("Add to To do…",muted=true);inner()})
                FilledIconButton({onQuickAdd(Task(title=quickTitle,owner=quickOwner,listId=list.id));quickTitle=""},enabled=editable && quickTitle.isNotBlank(),modifier=Modifier.size(44.dp)) { Icon(Icons.Outlined.Add,"Add task") }
            }
        }
    }
}


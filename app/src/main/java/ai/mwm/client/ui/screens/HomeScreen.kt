package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.reminders.TaskReminders
import ai.mwm.client.ui.theme.AppTheme
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable fun HomeScreen(openTaskId: String? = null, onTaskLinkHandled: () -> Unit = {}, openCare: Boolean = false, onCareLinkHandled: () -> Unit = {}, openLocketId: String? = null, onLocketLinkHandled: () -> Unit = {}, openChat: Boolean = false, onChatLinkHandled: () -> Unit = {}) {
    val preview = LocalInspectionMode.current
    val vm: PipoViewModel? = if(preview) null else viewModel()
    val state = vm?.state?.collectAsState()?.value ?: remember { seedState().copy(preferences = Preferences(started = true)) }
    val connection = vm?.connection?.collectAsState()?.value ?: ai.mwm.client.cloud.PartnerConnection()
    var pairingOpen by rememberSaveable { mutableStateOf(false) }
    val careVm: CareViewModel? = if(preview) null else viewModel()
    val care = careVm?.state?.collectAsState()?.value
    val careConnection = careVm?.connection?.collectAsState()?.value ?: CareConnection()
    val careError = careVm?.error?.collectAsState()?.value
    var careOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var locketToOpen by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf("Today") }
    var taskId by rememberSaveable { mutableStateOf<String?>(null) }
    var listId by rememberSaveable { mutableStateOf<String?>(null) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var thanksFor by rememberSaveable { mutableStateOf("") }
    var remindersOpen by rememberSaveable { mutableStateOf(false) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var addListId by rememberSaveable { mutableStateOf<String?>(null) }
    var addStatus by rememberSaveable { mutableStateOf(TaskStatus.Todo.name) }
    var tagsOpen by rememberSaveable { mutableStateOf(false) }
    var listEditorOpen by rememberSaveable { mutableStateOf(false) }
    var editListId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteListConfirm by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionAsked by rememberSaveable { mutableStateOf(context.getSharedPreferences("pipo_permission_ui", 0).getBoolean("asked", false)) }
    var permissionVersion by remember { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val translationCode = LocalAppLanguage.current
    val error = vm?.error?.collectAsState()?.value
    LaunchedEffect(error) { if(error != null) { snack.showSnackbar(localized(error, translationCode)); vm?.clearError() } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionVersion++; vm?.syncReminders(); careVm?.refresh()
        scope.launch { snack.showSnackbar(if(granted) "Notifications enabled. Your reminders are ready." else "Notifications are off. You can enable them in Android settings.") }
    }
    val allowed = remember(permissionVersion) { TaskReminders.allowed(context) }
    val notificationControl: () -> Unit = {
        if(Build.VERSION.SDK_INT >= 33 && !TaskReminders.allowed(context) && !permissionAsked && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionAsked = true
            context.getSharedPreferences("pipo_permission_ui", 0).edit().putBoolean("asked", true).apply()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    val requestIfNeeded: () -> Unit = { if(!TaskReminders.allowed(context)) notificationControl() }
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_RESUME) { permissionVersion++; vm?.syncReminders(); vm?.resumeConnection(); careVm?.resume() } else if(event == Lifecycle.Event.ON_PAUSE) {vm?.pauseConnection();careVm?.pause()} }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer);careVm?.pause() }
    }
    LaunchedEffect(Unit) { vm?.syncReminders(); vm?.resumeConnection();careVm?.resume() }
    LaunchedEffect(openTaskId) { if(openTaskId != null) { careOpen=false; taskId = openTaskId; listId = null; notesOpen = false; remindersOpen = false; onTaskLinkHandled() } }
    LaunchedEffect(openCare) { if(openCare) {tab="Care";careOpen=false;taskId=null;listId=null;notesOpen=false;remindersOpen=false;onCareLinkHandled()} }
    val accountScope by AccountScope.changes.collectAsState()
    LaunchedEffect(accountScope) {
        locketToOpen=null;taskId=null;listId=null;notesOpen=false;careOpen=false;remindersOpen=false
        editorOpen=false;listEditorOpen=false;tagsOpen=false;editId=null;editListId=null
        deleteListConfirm=null;thanksFor="";tab="Today"
        snack.currentSnackbarData?.dismiss()
    }
    var previousSpace by remember(accountScope) {mutableStateOf(connection.spaceId)}
    LaunchedEffect(connection.spaceId) {
        if(previousSpace.isNotBlank() && previousSpace!=connection.spaceId) {
            locketToOpen=null;taskId=null;listId=null;notesOpen=false;careOpen=false;remindersOpen=false
            editorOpen=false;listEditorOpen=false;tagsOpen=false;editId=null;editListId=null
            deleteListConfirm=null;thanksFor="";tab="Today"
            snack.currentSnackbarData?.dismiss()
        }
        previousSpace=connection.spaceId
    }
    LaunchedEffect(openChat,accountScope) {
        if(openChat) {taskId=null;listId=null;careOpen=false;remindersOpen=false;editorOpen=false;pairingOpen=false;thanksFor="";notesOpen=true;onChatLinkHandled()}
    }
    var readNotes by remember(accountScope,connection.spaceId) {mutableStateOf(ChatReadState.read(context,connection.spaceId))}
    val unreadNotes=if(connection.paired) ChatReadState.unread(state.notes,readNotes) else 0
    LaunchedEffect(notesOpen,state.notes,accountScope,connection.spaceId) {
        if(notesOpen && connection.spaceId.isNotBlank()) {
            val ids=ChatReadState.incoming(state.notes).map {it.id}.toSet()
            ChatReadState.mark(context,connection.spaceId,ids)
            readNotes=ChatReadState.read(context,connection.spaceId)
            // Clear only the notifications whose messages are now visible in this inbox.
            val manager=context.getSystemService(android.app.NotificationManager::class.java)
            ids.forEach {manager.cancel("partner_note",it.hashCode())}
        }
    }
    LaunchedEffect(openLocketId) {
        if(openLocketId!=null) {
            tab="Today";taskId=null;listId=null;notesOpen=false;careOpen=false;remindersOpen=false
            editorOpen=false;listEditorOpen=false;tagsOpen=false;pairingOpen=false
            locketToOpen=openLocketId
            onLocketLinkHandled()
        }
    }
    LaunchedEffect(tab,connection.spaceId,connection.online) {if(tab=="Care") careVm?.refresh()}
    val openTask: (String) -> Unit = { taskId = it }
    fun add(list: String? = null, status: TaskStatus = TaskStatus.Todo) { editId = null; addListId = list; addStatus = status.name; editorOpen = true }
    val toggle: (Task) -> Unit = { task ->
        vm?.toggle(task)
        if(!task.done && !connection.connected) celebrate(context, state.preferences)
        scope.launch {
            if(connection.connected){snack.showSnackbar("Updating shared task… Its status changes after server confirmation.");return@launch}
            val result = snack.showSnackbar(localized(if(task.done) "Task reopened" else if(task.repeat == Repeat.Once) "Prrr… one little task done!" else "Task done. Its next ${task.repeat.label.lowercase()} occurrence is scheduled.", translationCode), actionLabel = localized("Undo", translationCode), duration = SnackbarDuration.Short)
            if(result == SnackbarResult.ActionPerformed) vm?.status(task.id, if(task.done) TaskStatus.Done else TaskStatus.Todo)
        }
    }
    val detail = state.tasks.find { it.id == taskId }
    val list = state.lists.find { it.id == listId }
    val primary = taskId == null && listId == null && !notesOpen && !remindersOpen && !careOpen && state.preferences.started && !pairingOpen
    BackHandler(!primary && state.preferences.started && !editorOpen) {
        when { pairingOpen -> pairingOpen = false; careOpen -> careOpen = false; taskId != null -> taskId = null; listId != null -> listId = null; notesOpen -> notesOpen = false; remindersOpen -> remindersOpen = false }
    }
    val userContent = remember(state, connection) { (state.tasks.flatMap { listOf(it.title,it.note,it.id,it.parentId) } + state.lists.flatMap {listOf(it.name,it.id)} + state.tags.flatMap {listOf(it.name,it.id)} + state.notes.flatMap {listOf(it.message,it.taskTitle,it.id)} + listOf(state.preferences.myName,state.preferences.partnerName,state.preferences.code,connection.code,connection.accountEmail,connection.uid)).filter {it.isNotBlank()}.toSet() }
    CompositionLocalProvider(LocalUserContent provides userContent, ai.mwm.client.cloud.LocalPartnerConnection provides connection, ai.mwm.client.cloud.LocalPartnerNote provides {kind,message,task,picture -> vm?.thank(kind,message,task,picture)}, ai.mwm.client.cloud.LocalPartnerPoke provides {task -> vm?.poke(task)}) {
    Scaffold(containerColor = AppTheme.colors.background,
        topBar = {
            if(connection.connected && !connection.online) {
                Row(Modifier.fillMaxWidth().background(AppTheme.colors.surfaceSunken).padding(horizontal=14.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudOff,"Shared space offline",Modifier.size(18.dp),tint=AppTheme.colors.textSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Shared space not synced",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                    TextButton(onClick={vm?.checkConnection()}) { Text("Retry") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) { data ->
        Snackbar(action = { data.visuals.actionLabel?.let { label -> TextButton(onClick = { data.performAction() }) { Text(appDisplayText(label)) } } }, dismissAction = { if(data.visuals.withDismissAction) IconButton(onClick = { data.dismiss() }) { Icon(Icons.Outlined.Close, "Dismiss message") } }) { Text(appDisplayText(data.visuals.message)) }
    } }, bottomBar = {
        if(primary) PreviewNavigation(tab) { tab = it }
    }, floatingActionButton = {
        if(primary) Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            BadgedBox(badge={if(unreadNotes>0) androidx.compose.material3.Badge {Text(if(unreadNotes>99) "99+" else unreadNotes.toString())}}) {
                SmallFloatingActionButton({thanksFor="";notesOpen=true},modifier=Modifier.size(48.dp),containerColor=AppTheme.colors.secondary,shape=CircleShape) {
                    Icon(Icons.Outlined.ChatBubbleOutline,if(unreadNotes>0) "Open chat, $unreadNotes unread messages" else "Open chat")
                }
            }
            if(tab in listOf("Today","Upcoming")) FloatingActionButton({add()},containerColor=AppTheme.colors.primary,shape=CircleShape) {Icon(Icons.Outlined.Add,"Add a task")}
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().mainTabSwipes(primary && !editorOpen && !tagsOpen && !listEditorOpen, tab) { tab = it }.background(Brush.radialGradient(listOf(AppTheme.colors.primary.copy(alpha = .035f), Color.Transparent), center = Offset.Zero, radius = 1100f)).padding(padding).then(if(!primary) Modifier.navigationBarsPadding() else Modifier)) {
            when {
                !state.preferences.started || pairingOpen -> WelcomeScreen(state, { pairingOpen=false; vm?.start() }, { pairingOpen=true; vm?.regenerate() }, { vm?.setCode(it) }, {action,code->pairingOpen=true; vm?.connect(action,code)}, {vm?.checkConnection()}, {vm?.signOut()}, {vm?.chooseRole(it)})
                taskId != null && detail != null -> TaskDetailScreen(detail, state, { taskId = null }, { editId = detail.id; editorOpen = true }, { toggle(detail) }, { task -> if(task.reminder && !detail.reminder) requestIfNeeded(); vm?.save(task) }, { vm?.delete(detail.id); taskId = null }, { title -> taskId = null; thanksFor = title; notesOpen = true })
                taskId != null -> Column(Modifier.padding(24.dp)) { PText(if(connection.connected && !connection.online) "Reconnecting · Waiting for a fresh task snapshot." else "This task is no longer available."); OutlinedButton({ taskId = null }) { PText("Back") } }
                listId != null && list != null -> ListDetailScreen(list, state, { listId = null }, openTask, toggle, { id, status -> vm?.status(id, status) }, { id, status -> add(id, status) }, { editListId = list.id; listEditorOpen = true }, { deleteListConfirm = list.id }, { vm?.saveList(list.copy(pinned = !list.pinned)) }, { vm?.save(it) }, {vm?.saveList(it)})
                notesOpen -> AppreciationScreen(state, thanksFor, { notesOpen = false }, { vm?.deleteNote(it) }, { id,kind,message,task,picture,done ->
                    if(vm!=null) vm.sendChatNote(id,kind,message,task,picture,done) else done("Preview only · Run the app to send a message.")
                })
                remindersOpen -> RemindersScreen(state, allowed, { remindersOpen = false }, notificationControl, openTask, toggle)
                tab == "Care" -> CareScreen(if(careConnection.spaceId==connection.spaceId) care else null,state.preferences.myName,state.preferences.partnerName,careError,allowed,{tab="Today"},{careVm?.save(it)},{careVm?.delete(it)},{careVm?.update(it)},{careVm?.clear()},notificationControl,careConnection,{careVm?.setSharing(it)},{careVm?.refresh()},state.notes,{message,done->if(vm!=null) vm.sendSupport(message,done) else done("Connect your partner to send a note.")})
                tab == "Upcoming" -> UpcomingScreen(state, openTask, toggle, { id -> editId=id; editorOpen=true })
                tab == "Lists" -> ListsScreen(state, { listId = it }, { editListId = null; listEditorOpen = true }, openTask, { vm?.saveList(it) }, toggle)
                tab == "Progress" -> ProgressScreen(state, openTask) { thanksFor = ""; notesOpen = true }
                tab == "Us" -> SettingsScreen(state, allowed, { vm?.preferences(it) }, notificationControl, { if(allowed) { vm?.testReminder(); scope.launch { snack.showSnackbar("Test task created. Complete it to clear its reminder.") } } else { requestIfNeeded(); scope.launch { snack.showSnackbar("Enable notifications, then create your test task.") } } }, { tagsOpen = true }, { vm?.leaveConnection(); careVm?.refresh(); tab = "Today" }, { tab="Care" }, {vm?.resetWorkspace()}, {pairingOpen=true}, {vm?.checkConnection()})
                else -> TodayScreen(state, openTask, toggle, { vm?.preferences(it) }, { remindersOpen = true }, { tab="Care" }, locketToOpen, {locketToOpen=null})
            }
        }
    }
    if(connection.needsAuth) PairingSignInDialog(connection, {vm?.dismissAuth()}, {email,password,signup->vm?.authenticate(email,password,signup)})
    if(editorOpen) {
        val edited = state.tasks.find { it.id == editId }
        TaskEditor(edited, state, addListId, { editorOpen = false }, {task,done -> if(vm!=null) vm.saveWithResult(task,done) else done(null) }, {tag,done -> if(vm!=null) vm.saveTagWithResult(tag,done) else done(null)}, { tagsOpen = true }, { editListId = null; listEditorOpen = true }, requestIfNeeded, TaskStatus.valueOf(addStatus))
    }
    if(tagsOpen) TagManager(state, { tag, done -> if(vm != null) vm.saveTagWithResult(tag, done) else done(null) }, { id, done -> if(vm != null) vm.deleteTagWithResult(id, done) else done(null) }, { tagsOpen = false })
    if(listEditorOpen) ListEditor(state.lists.find { it.id == editListId }, { list, done ->
        if(vm != null) vm.saveListWithResult(list) { failure ->
            if(failure == null && editListId == null && !editorOpen) { tab = "Lists"; listId = list.id }
            done(failure)
        } else done(null)
    }, { listEditorOpen = false }, state.preferences)
    deleteListConfirm?.let { id -> AlertDialog(onDismissRequest = { deleteListConfirm = null }, title = { PText("Delete this list?") }, text = { PText("All tasks in this list will be deleted and their reminders cleared.") }, confirmButton = { OutlinedButton({ vm?.deleteList(id); deleteListConfirm = null; listId = null }) { PText("Delete") } }, dismissButton = { OutlinedButton({ deleteListConfirm = null }) { PText("Cancel") } }) }
    }
}

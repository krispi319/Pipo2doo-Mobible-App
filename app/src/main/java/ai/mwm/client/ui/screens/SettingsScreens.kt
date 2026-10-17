package ai.mwm.client.ui.screens

import ai.mwm.client.R
import ai.mwm.client.data.*
import ai.mwm.client.reminders.TaskReminders
import ai.mwm.client.ui.theme.AppTheme
import android.content.*
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable private fun SettingsGroup(title: String, aside: String = "", content: @Composable ColumnScope.() -> Unit) {
    val c=AppTheme.colors
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=c.surfaceSunken,border=BorderStroke(1.dp,c.separator)) {
        Column(Modifier.padding(6.dp)) {
            Row(Modifier.fillMaxWidth().padding(12.dp,10.dp),verticalAlignment=Alignment.CenterVertically) {
                PText(title,Modifier.weight(1f),MaterialTheme.typography.titleMedium)
                if(aside.isNotEmpty()) Badge(aside)
            }
            DashedRule(Modifier.padding(horizontal=12.dp))
            content()
        }
    }
}
@Composable private fun SettingsStack(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
}
@Composable private fun SettingsRow(icon: ImageVector, title: String, subtitle: String = "", color: Color = AppTheme.colors.secondary, paper: Boolean = false, end: @Composable () -> Unit = {}) {
    val c=AppTheme.colors
    Surface(Modifier.fillMaxWidth(),color=if(paper) c.surface else Color.Transparent,shape=RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp,10.dp).heightIn(min=40.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(color.copy(alpha=.3f).compositeOver(c.surface),CircleShape).border(1.dp,color.copy(alpha=.55f),CircleShape),contentAlignment=Alignment.Center) {Icon(icon,null,Modifier.size(20.dp),tint=c.textPrimary)}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {PText(title,style=MaterialTheme.typography.bodyMedium);if(subtitle.isNotEmpty()) PText(subtitle,style=MaterialTheme.typography.bodySmall,muted=true)}
            end()
        }
    }
}
@Composable private fun SettingsStatus(label: String, good: Boolean) {
    val c=AppTheme.colors
    val color=if(good) c.success else c.warning
    Surface(shape=CircleShape,color=color.copy(alpha=.14f).compositeOver(c.surface)) {
        Row(Modifier.padding(9.dp,4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).background(color,CircleShape));Text(appDisplayText(label),color=color,style=MaterialTheme.typography.labelSmall)
        }
    }
}
private fun clock(minute: Int)=String.format(Locale.ENGLISH,"%02d:%02d",minute/60,minute%60)

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(state: PipoState, notificationsAllowed: Boolean, onPreferences: ((Preferences) -> Preferences) -> Unit, onEnable: () -> Unit, onTestReminder: () -> Unit, onTags: () -> Unit, onLeave: () -> Unit, onCare: () -> Unit = {}, onReset: () -> Unit = {}, onPair: () -> Unit = {}, onCheck: () -> Unit = {}, onBackup: () -> Unit = {}) {
    val context=LocalContext.current
    val connection=ai.mwm.client.cloud.LocalPartnerConnection.current
    val p=state.preferences
    val c=AppTheme.colors
    val pickMe=rememberPhotoPicker { photo -> onPreferences { it.copy(myPhoto=photo) } }
    var myName by rememberSaveable(connection.uid,p.myName) {mutableStateOf(p.myName)}
    var pendingName by rememberSaveable {mutableStateOf<String?>(null)}
    var savedName by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(connection.uid) {pendingName=null;savedName=null;myName=p.myName}
    LaunchedEffect(p.myName,pendingName) {if(pendingName!=null && pendingName==p.myName) {savedName=p.myName;pendingName=null}}
    var reset by rememberSaveable {mutableStateOf(false)}
    var confirmLeave by rememberSaveable {mutableStateOf(false)}
    var languageOpen by rememberSaveable {mutableStateOf(false)}
    var preview by remember {mutableStateOf(false)}
    val date=LocalDate.ofEpochDay(p.togetherSince)
    val days=java.time.temporal.ChronoUnit.DAYS.between(date,LocalDate.now()).coerceAtLeast(0)
    val manager=context.getSystemService(android.app.NotificationManager::class.java)
    // Read system channels when returning from Android settings, not illustrative mockup states.
    var soundRevision by remember {mutableIntStateOf(0)}
    val taskChannel=remember(soundRevision,notificationsAllowed) {manager.getNotificationChannel(ai.mwm.client.reminders.NotificationSounds.channelId(context,TaskReminders.CHANNEL))}
    val taskEnabled=notificationsAllowed && taskChannel!=null && taskChannel.importance!=android.app.NotificationManager.IMPORTANCE_NONE
    val permission=manager.areNotificationsEnabled() && (android.os.Build.VERSION.SDK_INT<33 || androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)
    val setDate: () -> Unit = {
        android.app.DatePickerDialog(context,{_,year,month,day->onPreferences {it.copy(togetherSince=LocalDate.of(year,month+1,day).toEpochDay())}},date.year,date.monthValue-1,date.dayOfMonth).apply {datePicker.maxDate=System.currentTimeMillis()}.show()
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,12.dp,16.dp,104.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item {Surface(shape=RoundedCornerShape(20.dp),color=c.surface,border=BorderStroke(1.dp,c.separator),shadowElevation=1.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp,14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {PText("Us & settings",style=MaterialTheme.typography.headlineLarge);Badge("${p.myName} & ${p.partnerName} · ${if(connection.paired) "Connected" else if(connection.connected) "Waiting" else "Local mode"}",c.secondary)};Duo(22.dp)
            }
        }}
        item {Surface(shape=RoundedCornerShape(28.dp),color=c.surface,border=BorderStroke(1.dp,c.separator),shadowElevation=4.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp,20.dp,16.dp,16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(224.dp).height(144.dp)) {
                    SettingsOrbit(p,days,Modifier.align(Alignment.TopCenter))
                    FilledIconButton(pickMe,Modifier.align(Alignment.BottomStart).size(48.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=c.surfaceElevated,contentColor=c.textPrimary)) {Icon(Icons.Outlined.Edit,"Change my photo",Modifier.size(18.dp))}
                    Box(Modifier.align(Alignment.BottomEnd).size(40.dp).background(c.surfaceElevated,CircleShape).border(1.dp,c.separator,CircleShape),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Lock,"Partner photo is read-only",Modifier.size(16.dp),tint=c.textSecondary)}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    ProfileNameCard("Me${if(connection.role.isBlank()) "" else " · ${ai.mwm.client.cloud.roleLabel(connection.role)}"}",p.myName,"You can edit",c.primary,Modifier.weight(1f),false)
                    Text("+",color=c.secondary,style=MaterialTheme.typography.titleLarge)
                    ProfileNameCard("You${if(connection.partnerRole.isBlank()) "" else " · ${ai.mwm.client.cloud.roleLabel(connection.partnerRole)}"}",p.partnerName,"Read-only",c.accent,Modifier.weight(1f),true)
                }
                Surface(onClick=setDate,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),color=c.secondary.copy(alpha=.14f).compositeOver(c.surfaceSunken),border=BorderStroke(1.dp,c.secondary.copy(alpha=.45f))) {
                    Row(Modifier.padding(10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Surface(shape=RoundedCornerShape(8.dp),color=c.surface) {Column(Modifier.width(46.dp),horizontalAlignment=Alignment.CenterHorizontally) {Box(Modifier.fillMaxWidth().background(c.secondary),contentAlignment=Alignment.Center) {PText(date.format(DateTimeFormatter.ofPattern("MMM",Locale.ENGLISH)).uppercase(),style=MaterialTheme.typography.labelSmall)};PText(date.dayOfMonth.toString(),style=MaterialTheme.typography.titleLarge)}}
                        Column(Modifier.weight(1f)) {PText("Together since · Just for me",style=MaterialTheme.typography.labelSmall,muted=true);PText(date.format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.ENGLISH)),style=MaterialTheme.typography.titleMedium)}
                        Surface(shape=CircleShape,color=c.surface,border=BorderStroke(1.dp,c.separator)) {PText("✎ Set date",Modifier.padding(12.dp,10.dp),MaterialTheme.typography.labelLarge)}
                    }
                }
                if(connection.authenticated) PText("Signed in as ${connection.accountEmail.ifBlank {"your account"}}",style=MaterialTheme.typography.bodySmall,muted=true)
                PText(connection.message,style=MaterialTheme.typography.bodySmall,muted=true)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {OutlinedButton(onPair,enabled=!connection.busy) {PText(if(connection.paired) "Connection details" else "Pair with partner")};OutlinedButton(onCheck,enabled=!connection.busy) {PText("Check setup")}}
            }
        }}
        item {SettingsGroup("Account backup","v1.2.0") {
            SettingsStack {
                val backup by ai.mwm.client.cloud.AccountBackup.status.collectAsState()
                val owner=AccountScope.current(context)
                PText("Restore after reinstall",style=MaterialTheme.typography.titleSmall)
                PText("Sign in with the same account to restore your personal settings, pictures and care records. Account backup is separate from sharing with your partner.",style=MaterialTheme.typography.bodySmall,muted=true)
                PText("Settings: "+(backup["$owner:main"] ?: "Not checked this session"),style=MaterialTheme.typography.bodySmall,muted=true)
                PText("Care: "+(backup["$owner:care"] ?: "Not checked this session"),style=MaterialTheme.typography.bodySmall,muted=true)
                OutlinedButton(onClick=onBackup,enabled=connection.authenticated && !connection.busy) {PText("Back up now")}
                PText("Before uninstalling, wait for BOTH sections to say Saved to your account. Unsaved/offline changes cannot survive uninstalling. Health records are stored privately in Supabase and encrypted locally; this is not end-to-end encryption.",style=MaterialTheme.typography.bodySmall,muted=true)
            }
        }}
        item {SettingsGroup("Gentle reminders","Just for me") {
            SettingsStack {
                PText("Default reminder time",style=MaterialTheme.typography.titleSmall)
                PText("For reminders on tasks with no chosen time. Android delivery is approximate.",style=MaterialTheme.typography.bodySmall,muted=true)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(510,750,1080,1230).forEach {minute->Pill(clock(minute),p.defaultReminderTime==minute) {onPreferences {it.copy(defaultReminderTime=minute)}}}
                    Pill(if(p.defaultReminderTime !in listOf(510,750,1080,1230)) clock(p.defaultReminderTime) else "Other…") {pickTime(context,p.defaultReminderTime) {minute->onPreferences {it.copy(defaultReminderTime=minute)}}}
                }
            }
            DashedRule(Modifier.padding(horizontal=12.dp))
            SettingsStack {
                PText("Reminder style",style=MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth().background(c.surface,CircleShape).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    listOf("Quiet badge" to "Sticky note","Soft nudge" to "Soft purr","Nudge + repeat" to "Paw nudge").forEach {(label,value)->Surface(onClick={onPreferences {it.copy(style=value)}},modifier=Modifier.weight(1f).heightIn(min=48.dp).semantics {selected=p.style==value},shape=CircleShape,color=if(p.style==value) c.primary.copy(alpha=.4f).compositeOver(c.surface) else Color.Transparent) {Box(Modifier.padding(5.dp),contentAlignment=Alignment.Center) {PText(label,style=MaterialTheme.typography.labelSmall,align=TextAlign.Center)}}}
                }
                if(p.style=="Paw nudge") PText("Repeats about every 15 minutes until completed or dismissed; respects quiet hours.",style=MaterialTheme.typography.bodySmall,muted=true)
            }
            DashedRule(Modifier.padding(horizontal=12.dp))
            SettingsStack {
                Row(verticalAlignment=Alignment.CenterVertically) {PText("Quiet hours",Modifier.weight(1f),MaterialTheme.typography.titleSmall);Switch(p.quietEnabled,{value->onPreferences {it.copy(quietEnabled=value)}})}
                val calm=(p.quietEnd-p.quietStart+1440)%1440
                PText(if(p.quietEnabled) "${calm/60} h ${calm%60} min of calm" else "Quiet hours off",style=MaterialTheme.typography.bodySmall,muted=true)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(true,false).forEachIndexed {index,start->
                        if(index==1) Icon(Icons.Outlined.Bedtime,null,Modifier.size(32.dp),tint=c.textSecondary)
                        val minute=if(start) p.quietStart else p.quietEnd
                        Surface(onClick={pickTime(context,minute) {value->onPreferences {if(start) it.copy(quietStart=value) else it.copy(quietEnd=value)}}},modifier=Modifier.weight(1f).semantics {contentDescription="Quiet hours ${if(start) "start" else "end"}, ${clock(minute)}"},shape=RoundedCornerShape(14.dp),color=c.surface,border=BorderStroke(1.dp,c.separator)) {Column(Modifier.padding(14.dp,8.dp),horizontalAlignment=if(start) Alignment.Start else Alignment.End) {PText(if(start) "From" else "Until",style=MaterialTheme.typography.labelSmall,muted=true);PText(clock(minute),style=MaterialTheme.typography.headlineSmall)}}
                    }
                }
            }
        }}
        item {SettingsGroup("Notifications","This phone") {
            SettingsRow(Icons.Outlined.NotificationsNone,"Android permission",if(permission) "Allowed on this phone" else "Off in Android",c.primary,true) {SettingsStatus(if(permission) "On" else "Off",permission)}
            SettingsRow(Icons.Outlined.Alarm,"Task reminders channel",if(taskEnabled) "Controlled by Android settings" else "Enable to receive reminders",c.primary) {SettingsStatus(if(taskEnabled) "Enabled" else "Off",taskEnabled)}
            DashedRule(Modifier.padding(horizontal=12.dp))
            SettingsRow(Icons.Outlined.MailOutline,"Partner notifications","Messages, thank-you, pokes and Locket photos",c.accent) {SettingsStatus(if(connection.paired && p.pokeOptIn && ai.mwm.client.cloud.PartnerPokeService.allowed(context)) "Enabled" else "Unavailable",connection.paired && p.pokeOptIn && ai.mwm.client.cloud.PartnerPokeService.allowed(context))}
            SettingsRow(Icons.Outlined.Image,"Locket photos channel","New pictures appear in your phone's notification bar",c.accent) {SettingsStatus(if(ai.mwm.client.cloud.PartnerPokeService.allowed(context,ai.mwm.client.cloud.PartnerPokeService.LOCKET_CHANNEL)) "Enabled" else "Off",ai.mwm.client.cloud.PartnerPokeService.allowed(context,ai.mwm.client.cloud.PartnerPokeService.LOCKET_CHANNEL))}
            SettingsRow(Icons.Outlined.ChatBubbleOutline,"Messages & thank-you channel","Partner messages and support notes",c.secondary) {SettingsStatus(if(ai.mwm.client.cloud.PartnerPokeService.allowed(context,ai.mwm.client.cloud.PartnerPokeService.MESSAGE_CHANNEL)) "Enabled" else "Off",ai.mwm.client.cloud.PartnerPokeService.allowed(context,ai.mwm.client.cloud.PartnerPokeService.MESSAGE_CHANNEL))}
            SettingsRow(Icons.Outlined.Pets,"Receive partner notifications",if(connection.paired) "Opt-in · Respects quiet hours" else "Preference saved for future paired use",c.accent) {Switch(p.pokeOptIn,{value->onPreferences {it.copy(pokeOptIn=value)};if(value && !permission) onEnable()},Modifier.semantics {contentDescription="Receive partner messages, thank-you, pokes and Locket photos"})}
            DashedRule(Modifier.padding(horizontal=12.dp))
            SettingsStack {NotificationSoundPicker {soundRevision++}}
            SettingsRow(Icons.Outlined.Settings,"Android channels","Sound, pop-up and badges") {Pill("Open settings ↗",onClick=onEnable)}
            SettingsStack {OutlinedButton({context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,ai.mwm.client.reminders.NotificationSounds.channelId(context,TaskReminders.CHANNEL)))}) {PText("Task channel settings",style=MaterialTheme.typography.labelLarge)};OutlinedButton(onTestReminder) {PText("Test reminder",style=MaterialTheme.typography.labelLarge)}}
        }}
        item {SettingsGroup("Celebrations","Shared joy") {
            SettingsRow(Icons.Outlined.MusicNote,"Little purr sound","When a task is done") {Switch(p.sound,{value->onPreferences {it.copy(sound=value)}},Modifier.semantics {contentDescription="Little purr sound"})}
            DashedRule(Modifier.padding(horizontal=12.dp))
            SettingsRow(Icons.Outlined.Vibration,"Haptic tap","Soft buzz on check-off") {Switch(p.haptics,{value->onPreferences {it.copy(haptics=value)}},Modifier.semantics {contentDescription="Haptic tap"})}
            SettingsRow(Icons.Outlined.Celebration,"Two-cat confetti","Try a little celebration",paper=true) {Pill("▶ Preview",true,c.secondary) {celebrate(context,p);preview=true}}
        }}
        item {SettingsGroup("My profile",if(connection.connected && p.shareProfile) "Name shared" else "Private to me") {
            SettingsRow(Icons.Outlined.PersonOutline,"Share name and profile avatar","Your shared photo appears on partner notifications. Initials appear if no photo is shared. Care sharing is managed separately in Care.") {Switch(p.shareProfile,{value->onPreferences {it.copy(shareProfile=value)}},enabled=connection.connected && !connection.busy,modifier=Modifier.semantics {contentDescription="Share display profile with partner"})}
            SettingsStack {
                PText("My name",style=MaterialTheme.typography.labelSmall,muted=true)
                Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    BasicTextField(myName,{myName=it.take(20);savedName=null;pendingName=null},Modifier.weight(1f).heightIn(min=48.dp).drawBehind {drawLine(c.separator,Offset(0f,size.height),Offset(size.width,size.height),1.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),4.dp.toPx())))}.padding(vertical=12.dp).semantics {contentDescription="My name"},singleLine=true,textStyle=MaterialTheme.typography.titleMedium.copy(color=c.textPrimary),cursorBrush=SolidColor(c.primary))
                    Button({val name=myName.trim();pendingName=name;savedName=null;onPreferences {it.copy(myName=name)}},enabled=myName.isNotBlank(),modifier=Modifier.heightIn(min=48.dp),shape=CircleShape,contentPadding=PaddingValues(horizontal=14.dp),colors=ButtonDefaults.buttonColors(containerColor=c.success,contentColor=c.surfaceElevated)) {Icon(Icons.Outlined.Check,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Confirm")}
                }
                savedName?.let {Text("✓ ${if(connection.connected && p.shareProfile) "Saved to shared space" else "Saved privately on this phone"} · \"$it\"",color=c.success,style=MaterialTheme.typography.labelMedium)}
            }
            DashedRule(Modifier.padding(horizontal=12.dp))
            SettingsStack {
                PText("Photo",style=MaterialTheme.typography.titleSmall)
                PText("Pick from your photos or wear a cat · Photos stay on this phone",style=MaterialTheme.typography.bodySmall,muted=true)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    val labels=listOf("Photos","Sleepy","Curious","Loaf")
                    labels.forEachIndexed {index,label->
                        val preset=when(index) {1->0;2->1;else->3}
                        val chosen=if(index==0) p.myPhoto.isNotBlank() else p.myPhoto.isBlank() && p.avatar==preset
                        Surface(onClick=if(index==0) pickMe else { {onPreferences {it.copy(avatar=preset,myPhoto="")}} },modifier=Modifier.weight(1f).semantics {contentDescription=if(index==0) "Choose from photos" else "$label cat preset";selected=chosen},shape=RoundedCornerShape(14.dp),color=c.surface,border=BorderStroke(if(chosen) 2.dp else 1.dp,if(chosen) c.primary else c.separator)) {
                            Column(Modifier.padding(4.dp,8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(44.dp).background((if(index==2) c.accent else if(index==3) c.secondary else c.primary).copy(alpha=.22f),CircleShape),contentAlignment=Alignment.Center) {if(index==0) Icon(Icons.Outlined.AddPhotoAlternate,null,Modifier.size(24.dp),tint=c.textPrimary) else CatDrawing(Modifier.size(36.dp),variant=preset)}
                                PText(if(index==0 && chosen) "In use" else label,style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }}
        item {Surface(onClick=onCare,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=c.surface,border=BorderStroke(1.dp,c.separator),shadowElevation=1.dp) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Icon(Icons.Outlined.LocalFlorist,null,Modifier.size(32.dp),tint=c.secondary);Column(Modifier.weight(1f)) {PText("Period & care",style=MaterialTheme.typography.titleMedium);PText("Your calendar, partner updates & support notes",style=MaterialTheme.typography.bodySmall,muted=true)};SettingsStatus("Care",true);Icon(Icons.Outlined.ArrowForward,"Open Period and care",Modifier.size(20.dp),tint=c.textSecondary)}
                DashedRule()
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {PText("${p.myName} logs",style=MaterialTheme.typography.labelSmall);PText("Start & end dates\nMood & symptoms\nExpected reminders",style=MaterialTheme.typography.bodySmall,muted=true)};Column(Modifier.weight(1f),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {PText("Partner access",style=MaterialTheme.typography.labelSmall);PText(if(connection.paired) "View in Care tab\nOwner-controlled records" else "Connect to share\nOwner-controlled records",style=MaterialTheme.typography.bodySmall,muted=true,align=TextAlign.End)}}
                PText("Pairing alone never grants access to health records.",style=MaterialTheme.typography.bodySmall,muted=true)
            }
        }}
        item {Surface(shape=RoundedCornerShape(20.dp),color=c.surface,border=BorderStroke(1.dp,c.warning.copy(alpha=.38f))) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {Icon(Icons.Outlined.RestartAlt,null,Modifier.size(40.dp),tint=c.textSecondary);Column(Modifier.weight(1f)) {PText("Start fresh",style=MaterialTheme.typography.titleMedium);PText("Clear permitted lists and tasks. Partner-assigned tasks are protected.",style=MaterialTheme.typography.bodySmall,muted=true)}}
                Row(Modifier.fillMaxWidth()) {listOf(state.lists.size to "Lists",state.tasks.size to "Tasks",state.tasks.count {it.isOwnReminder()} to "Reminders").forEach {(count,label)->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {PText(count.toString(),style=MaterialTheme.typography.headlineSmall);PText(label,style=MaterialTheme.typography.labelSmall,muted=true)}}}
                DashedRule()
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {Column(Modifier.weight(1f)) {PText("Cleared",style=MaterialTheme.typography.labelSmall,muted=true);PText("× Lists, tasks & tags\n× Scheduled reminders",style=MaterialTheme.typography.bodySmall,muted=true)};Column(Modifier.weight(1f)) {PText("Kept",style=MaterialTheme.typography.labelSmall,muted=true);PText("✓ Profile & settings\n✓ Your care logs",style=MaterialTheme.typography.bodySmall,muted=true)}}
                DashedRule()
                Row(verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {PText("Keep thank-you history",style=MaterialTheme.typography.bodyMedium);PText("Appreciation notes stay in Our progress",style=MaterialTheme.typography.bodySmall,muted=true)};Switch(p.keepResetHistory,{value->onPreferences {it.copy(keepResetHistory=value)}},Modifier.semantics {contentDescription="Keep thank-you history"})}
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {PText(if(connection.connected) "Reset is blocked if it includes your partner's individual tasks. You'll confirm first." else "Only this phone is reset. You'll confirm first.",Modifier.weight(1f),MaterialTheme.typography.bodySmall,true);OutlinedButton({reset=true},shape=CircleShape,border=BorderStroke(1.5.dp,c.error),colors=ButtonDefaults.outlinedButtonColors(contentColor=c.error)) {Text("Reset all…")}}
            }
        }}
        item {Surface(shape=RoundedCornerShape(20.dp),color=c.surface,border=BorderStroke(1.dp,c.error.copy(alpha=.3f))) {
            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Duo(22.dp);Column(Modifier.weight(1f)) {PText("Leave shared space",style=MaterialTheme.typography.titleMedium);PText(if(connection.connected) "Leave your connected space. Future partner access ends." else "Exit local mode. Your tasks stay; reminders pause.",style=MaterialTheme.typography.bodySmall,muted=true)};OutlinedButton({confirmLeave=true},shape=CircleShape,border=BorderStroke(1.5.dp,c.error),colors=ButtonDefaults.outlinedButtonColors(contentColor=c.error)) {Text("Leave…")}}
        }}
        // Preserve the existing device preferences and tag manager without altering any other screen.
        item {SettingsGroup("Device preferences") {
            SettingsStack {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {PText("Theme",Modifier.weight(1f));ThemeIconSlider(p.themeMode) {mode->onPreferences {it.copy(themeMode=mode)}}};OutlinedButton({languageOpen=true}) {PText("App language · ${appLanguages.firstOrNull {it.code==p.language}?.name ?: "English"}")};OutlinedButton(onTags) {PText("Task tags · ${state.tags.size}")}}
        }}
    }
    if(reset) AlertDialog(onDismissRequest={reset=false},title={PText("Reset workspace content?")},text={PText("All lists, tasks and tags will be permanently removed and task reminders cleared. ${if(p.keepResetHistory) "Thank-you notes stay." else "Thank-you notes will also be removed."} Your profile, settings and encrypted health vault stay. ${if(connection.connected) "Shared tasks and lists are affected for both members." else "Only this phone is affected."} This cannot be undone.")},confirmButton={OutlinedButton({onReset();reset=false},colors=ButtonDefaults.outlinedButtonColors(contentColor=c.error)) {PText("Reset all")}},dismissButton={OutlinedButton({reset=false}) {PText("Cancel")}})
    if(confirmLeave) AlertDialog(onDismissRequest={confirmLeave=false},title={PText("Leave this space?")},text={PText(if(connection.connected) "This closes the shared space for both partners and invalidates all invitation codes. Both accounts lose access to its shared data. Your separate local workspace and care logs stay on this phone." else "You'll return to the welcome screen. Your local lists stay; task reminders pause until you start again. Local health-sharing consent is cleared. No connected partner will be notified.")},confirmButton={OutlinedButton({confirmLeave=false;onLeave()}) {PText("Leave space")}},dismissButton={OutlinedButton({confirmLeave=false}) {PText("Stay")}})
    if(languageOpen) AlertDialog(onDismissRequest={languageOpen=false},title={PText("Choose language")},text={LazyColumn {items(appLanguages,key={it.code}) {language->Row(Modifier.fillMaxWidth().selectable(p.language==language.code,role=Role.RadioButton) {onPreferences {it.copy(language=language.code)};languageOpen=false}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {RadioButton(p.language==language.code,onClick=null);PText(language.name,Modifier.weight(1f))}}}},confirmButton={OutlinedButton({languageOpen=false}) {PText("Close")}})
    if(preview) {
        var started by remember {mutableStateOf(false)}
        val progress by animateFloatAsState(if(started) 1f else 0f,animationSpec=androidx.compose.animation.core.tween(1700),label="Confetti fall")
        LaunchedEffect(Unit) {started=true;delay(2200);preview=false}
        AlertDialog(onDismissRequest={preview=false},title={PText("A little shared joy")},text={Column(horizontalAlignment=Alignment.CenterHorizontally) {Box(Modifier.fillMaxWidth().height(150.dp)) {Canvas(Modifier.fillMaxSize()) {for(i in 0..25) {val x=size.width*((i*37%100)/100f);val y=((i*13%100)/100f+progress)*size.height%size.height;drawCircle(listOf(c.primary,c.accent,c.secondary)[i%3],(3+i%4).dp.toPx(),Offset(x,y))}};CatDrawing(Modifier.align(Alignment.Center).size(190.dp,110.dp),double=true)};PText("Two cats, one little win.",style=MaterialTheme.typography.bodySmall,muted=true)}},confirmButton={OutlinedButton({preview=false}) {PText("Lovely")}})
    }
}

@Composable private fun SettingsOrbit(p: Preferences,days: Long,modifier: Modifier = Modifier) {
    val c=AppTheme.colors
    val lensInk=MaterialTheme.colorScheme.onSecondary
    Box(modifier.size(214.dp,132.dp)) {
        listOf(true,false).forEach {me->
            Box(Modifier.align(if(me) Alignment.CenterStart else Alignment.CenterEnd).size(132.dp).border(4.dp,if(me) c.primary else c.accent,CircleShape).padding(4.dp).clip(CircleShape)) {
                val photo=if(me) p.myPhoto else p.partnerPhoto
                if(photo==if(me) "asset:profile_mina" else "asset:profile_partner") Image(painterResource(if(me) R.drawable.settings_demo_me else R.drawable.settings_demo_partner),"Sample ${if(me) "personal" else "partner"} portrait",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                else ProfilePhoto(photo,me,Modifier.fillMaxSize(),if(me) p.avatar else -1)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val a=Path().apply {addOval(Rect(-4.dp.toPx(),-4.dp.toPx(),136.dp.toPx(),136.dp.toPx()))}
            val b=Path().apply {addOval(Rect(size.width-136.dp.toPx(),-4.dp.toPx(),size.width+4.dp.toPx(),136.dp.toPx()))}
            drawPath(Path.combine(PathOperation.Intersect,a,b),c.secondary)
        }
        Column(Modifier.align(Alignment.Center).width(48.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(days.toString(),color=lensInk,fontSize=19.sp,fontWeight=FontWeight.Bold,maxLines=1)
            Text("┄",color=lensInk,fontSize=10.sp,lineHeight=12.sp)
            Text("days\ntogether",color=lensInk,fontSize=10.sp,lineHeight=12.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center)
        }
    }
}

@Composable private fun ProfileNameCard(role: String,name: String,tag: String,color: Color,modifier: Modifier,end: Boolean) {
    val c=AppTheme.colors
    Surface(modifier,shape=RoundedCornerShape(if(end) 14.dp else 3.dp,if(end) 3.dp else 14.dp,14.dp,14.dp),color=color.copy(alpha=.12f).compositeOver(c.surface),border=BorderStroke(1.dp,color.copy(alpha=.35f))) {
        Column(Modifier.padding(12.dp,8.dp),horizontalAlignment=if(end) Alignment.End else Alignment.Start,verticalArrangement=Arrangement.spacedBy(4.dp)) {PText(role,style=MaterialTheme.typography.labelSmall,muted=true);PText(name,style=MaterialTheme.typography.titleLarge);Badge(tag,color)}
    }
}

@Composable private fun ThemeIconSlider(mode: String, onChange: (String) -> Unit) {
    val modes = listOf("Light", "System", "Night")
    val labels = listOf("Light mode", "Use system", "Night mode")
    val icons = listOf(Icons.Outlined.LightMode, Icons.Outlined.SettingsBrightness, Icons.Outlined.DarkMode)
    val selected = modes.indexOf(mode).coerceAtLeast(0)
    val offset by animateDpAsState((selected * 48).dp, label="Theme slider")
    val language = LocalAppLanguage.current
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    Box(Modifier.size(144.dp,48.dp).clip(CircleShape).background(AppTheme.colors.surface).pointerInput(mode, direction) {
        var distance = 0f
        detectHorizontalDragGestures(onDragStart={distance=0f},onHorizontalDrag={change, amount->change.consume();distance+=amount},onDragEnd={
            if(kotlin.math.abs(distance)>12.dp.toPx()) {val step=if(distance>0) 1 else -1;val logical=if(direction==LayoutDirection.Rtl) -step else step;onChange(modes[(selected+logical).coerceIn(0,2)])}
        })
    }) {
        Box(Modifier.offset(x=offset).padding(4.dp).size(40.dp).background(AppTheme.colors.primary,CircleShape))
        Row {modes.forEachIndexed {index,value->Box(Modifier.size(48.dp).selectable(selected==index,role=Role.RadioButton) {onChange(value)}.semantics {contentDescription=localized(labels[index],language)},contentAlignment=Alignment.Center) {Icon(icons[index],null,Modifier.size(22.dp),tint=if(selected==index) AppTheme.colors.onPrimary else AppTheme.colors.textSecondary)}}}
    }
}

@Composable fun RemindersScreen(state: PipoState, allowed: Boolean, onBack: () -> Unit, onEnable: () -> Unit, onOpen: (String) -> Unit, onToggle: (Task) -> Unit) {
    val context = LocalContext.current
    val reminders = state.tasks.filter { it.isOwnReminder() }
    val active = context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.mapNotNull { it.tag }.toSet()
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
        item { BackBar("Notifications", onBack) }
        item { Header("Task reminders", "${active.size} showing · ${reminders.size} unfinished reminders") }
        item { Paper(Modifier.padding(16.dp), color = AppTheme.colors.surfaceSunken) { PText(if(allowed) "Ongoing reminders are enabled" else "Enable notifications to receive reminders", style = MaterialTheme.typography.titleMedium); PText(TaskReminders.LIMITS, style = MaterialTheme.typography.bodySmall, muted = true); OutlinedButton(onEnable) { PText(if(allowed) "Android notification settings" else "Enable notifications") } } }
        items(reminders.sortedBy { it.reminderAt(state.preferences.defaultReminderTime) }, key = { it.id }) { task -> TaskRow(task, state, { onOpen(task.id) }, { onToggle(task) }, extra = { PText("${dayText(task.day)} · ${if(task.id in active) "Showing on phone" else "Scheduled or dismissed in Android"}", style = MaterialTheme.typography.bodySmall, muted = true) }) }
        if(reminders.isEmpty()) item { PText("No unfinished reminders. Turn on Ongoing reminder when adding or editing a task.", Modifier.padding(24.dp), muted = true) }
    }
}

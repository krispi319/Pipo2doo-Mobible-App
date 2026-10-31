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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
            Column(Modifier.padding(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {content()}
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
@Composable fun SettingsScreen(state: PipoState, notificationsAllowed: Boolean, onPreferences: ((Preferences) -> Preferences) -> Unit, onEnable: () -> Unit, onTestReminder: () -> Unit, onTags: () -> Unit, onLeave: () -> Unit, onCare: () -> Unit = {}, onReset: () -> Unit = {}, onPair: () -> Unit = {}, onCheck: () -> Unit = {}, onBackup: () -> Unit = {},onTogetherDate:(Long,Long,(String?)->Unit)->Unit={day,_,done->onPreferences {it.copy(togetherSince=day)};done(null)},onSaveName:(String,(String?)->Unit)->Unit={_,done->done("Name saving is unavailable. Reopen your account settings.")},onChatVisibility:(Boolean)->Unit={}) {
    ApprovedDesign {
    val context=LocalContext.current
    val connection=ai.mwm.client.cloud.LocalPartnerConnection.current
    val currentConnection by rememberUpdatedState(connection)
    val p=state.preferences
    val c=AppTheme.colors
    val pickMe=rememberPhotoPicker { photo -> onPreferences { it.copy(myPhoto=photo) } }
    var myName by rememberSaveable(connection.uid) {mutableStateOf(p.myName)}
    var nameBaseline by remember(connection.uid) {mutableStateOf(p.myName)}
    var pendingName by remember(connection.uid,connection.spaceId) {mutableStateOf<String?>(null)}
    var savedName by remember(connection.uid,connection.spaceId) {mutableStateOf<String?>(null)}
    var nameError by remember(connection.uid,connection.spaceId) {mutableStateOf<String?>(null)}
    LaunchedEffect(p.myName) {if(myName==nameBaseline && pendingName==null)myName=p.myName;nameBaseline=p.myName}
    var dateSaving by remember(connection.uid,connection.spaceId) {mutableStateOf(false)}
    var dateError by remember(connection.uid,connection.spaceId) {mutableStateOf<String?>(null)}
    var backupDetails by remember {mutableStateOf(false)}
    var reset by rememberSaveable {mutableStateOf(false)}
    var confirmLeave by rememberSaveable {mutableStateOf(false)}
    var languageOpen by rememberSaveable {mutableStateOf(false)}
    var preview by remember {mutableStateOf(false)}
    val date=LocalDate.ofEpochDay(p.togetherSince)
    val today=currentTaskDay(p.clockZone)
    val days=if(connection.connected && !connection.togetherDateKnown)null else (today-p.togetherSince).coerceAtLeast(0)
    val manager=context.getSystemService(android.app.NotificationManager::class.java)
    // Read system channels when returning from Android settings, not illustrative mockup states.
    var soundRevision by remember {mutableIntStateOf(0)}
    val taskChannel=remember(soundRevision,notificationsAllowed) {manager.getNotificationChannel(ai.mwm.client.reminders.NotificationSounds.channelId(context,TaskReminders.CHANNEL))}
    val taskEnabled=notificationsAllowed && taskChannel!=null && taskChannel.importance!=android.app.NotificationManager.IMPORTANCE_NONE
    val permission=manager.areNotificationsEnabled() && (android.os.Build.VERSION.SDK_INT<33 || androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)
    val setDate:()->Unit = {
        if(!dateSaving && !connection.busy && (!connection.connected || connection.online)) {
            val owner=connection.uid;val space=connection.spaceId;val version=connection.togetherDateVersion
            android.app.DatePickerDialog(context,{_,year,month,day->
                dateSaving=true;dateError=null
                onTogetherDate(LocalDate.of(year,month+1,day).toEpochDay(),version,saveDate@{failure->
                    if(owner!=currentConnection.uid || space!=currentConnection.spaceId)return@saveDate
                    dateSaving=false;dateError=failure
                })
            },date.year,date.monthValue-1,date.dayOfMonth).apply {
                datePicker.minDate=LocalDate.of(1900,1,1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                datePicker.maxDate=System.currentTimeMillis()
            }.show()
        }
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,12.dp,16.dp,104.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item {Surface(shape=RoundedCornerShape(20.dp),color=c.surface,border=BorderStroke(1.dp,c.separator),shadowElevation=1.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp,14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {PText("Us & settings",style=MaterialTheme.typography.headlineLarge);Badge("${p.myName} & ${p.partnerName} · ${if(connection.paired) "Connected" else if(connection.connected) "Waiting" else "Local mode"}",c.secondary)};Duo(22.dp)
            }
        }}
        item {Surface(shape=RoundedCornerShape(28.dp),color=c.secondary.copy(alpha=.1f).compositeOver(c.surface),border=BorderStroke(1.dp,c.separator),shadowElevation=1.dp) {
            Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                SettingsOrbit(p,days,onEditPhoto=pickMe)
                if(connection.authenticated) PText("Signed in as ${connection.accountEmail.ifBlank {"your account"}}",style=MaterialTheme.typography.bodySmall,muted=true)
                PText(connection.message,style=MaterialTheme.typography.bodySmall,muted=true)
                Surface(onClick=setDate,enabled=!dateSaving && !connection.busy && (!connection.connected || connection.online),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),color=c.secondary.copy(alpha=.14f).compositeOver(c.surfaceSunken),border=BorderStroke(1.dp,c.separator)) {
                    Row(Modifier.padding(10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Surface(shape=RoundedCornerShape(8.dp),color=c.surface) {Column(Modifier.width(46.dp),horizontalAlignment=Alignment.CenterHorizontally) {Box(Modifier.fillMaxWidth().background(c.secondary),contentAlignment=Alignment.Center) {PText(date.format(DateTimeFormatter.ofPattern("MMM",Locale.ENGLISH)).uppercase(),style=MaterialTheme.typography.labelSmall)};PText(date.dayOfMonth.toString(),style=MaterialTheme.typography.titleLarge)}}
                        Column(Modifier.weight(1f)) {PText(if(connection.connected) "Together since · Shared" else "Together since · This phone",style=MaterialTheme.typography.labelSmall,muted=true);PText(if(days==null) "Choose our date" else date.format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.ENGLISH)),style=MaterialTheme.typography.titleMedium)}
                        Surface(shape=CircleShape,color=c.surface,border=BorderStroke(1.dp,c.separator)) {PText(if(dateSaving)"Saving…" else "✎ Edit",Modifier.padding(12.dp,10.dp),MaterialTheme.typography.labelLarge)}
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically) {OutlinedButton(onPair,Modifier.weight(1f).heightIn(min=48.dp),enabled=!connection.busy,contentPadding=PaddingValues(10.dp)) {PText(if(connection.paired) "Connection details" else "Pair with partner",align=TextAlign.Center)};OutlinedButton(onCheck,Modifier.weight(1f).heightIn(min=48.dp),enabled=!connection.busy,contentPadding=PaddingValues(10.dp)) {PText("Check setup",align=TextAlign.Center)}}
                dateError?.let {PText(it,style=MaterialTheme.typography.bodySmall)}
                if(connection.connected && !connection.online) PText("Reconnect to change the shared date",style=MaterialTheme.typography.labelSmall,muted=true)

            }
        }}
        item {SettingsGroup("My profile",if(connection.connected) "Shared with partner" else "Me") {
            SettingsStack {
                PText("Display name",style=MaterialTheme.typography.labelSmall,muted=true)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(myName,{myName=it.take(20);savedName=null;nameError=null},Modifier.weight(1f).heightIn(min=48.dp).semantics {contentDescription="My name"},enabled=pendingName==null,singleLine=true,textStyle=MaterialTheme.typography.titleMedium.copy(color=c.textPrimary),shape=RoundedCornerShape(14.dp))
                    Button({
                        val name=myName.trim();val owner=connection.uid;val space=connection.spaceId
                        pendingName=name;savedName=null;nameError=null
                        onSaveName(name,nameResult@{failure->
                            if(owner!=currentConnection.uid || space!=currentConnection.spaceId)return@nameResult
                            pendingName=null
                            if(failure==null)savedName=name else nameError=failure
                        })
                    },enabled=myName.isNotBlank() && pendingName==null,modifier=Modifier.heightIn(min=48.dp),shape=CircleShape,contentPadding=PaddingValues(horizontal=14.dp),colors=ButtonDefaults.buttonColors(containerColor=c.primary,contentColor=c.onPrimary)) {Icon(Icons.Outlined.Check,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(if(pendingName!=null)"Saving…" else "Save")}
                }
                nameError?.let {Text(it,color=c.error,style=MaterialTheme.typography.bodySmall)}
                savedName?.let {Text("✓ ${if(connection.connected && p.shareProfile) "Saved to shared space" else "Saved privately on this phone"} · \"$it\"",color=c.success,style=MaterialTheme.typography.labelMedium)}
            }
            DashedRule(Modifier.padding(horizontal=12.dp))
            SettingsStack {
                PText("Photo",style=MaterialTheme.typography.titleSmall)
                PText("Pick a photo or a cat · Your partner sees your profile",style=MaterialTheme.typography.bodySmall,muted=true)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    val labels=listOf("Photos","Sleepy","Curious","Loaf")
                    labels.forEachIndexed {index,label->
                        val preset=when(index) {1->0;2->1;else->3}
                        val chosen=if(index==0) p.myPhoto.isNotBlank() else p.myPhoto.isBlank() && p.avatar==preset
                        Surface(onClick=if(index==0) pickMe else { {onPreferences {it.copy(avatar=preset,myPhoto="")}} },modifier=Modifier.weight(1f).semantics {contentDescription=if(index==0) "Choose from photos" else "$label cat preset";selected=chosen},shape=RoundedCornerShape(14.dp),color=c.surface,border=BorderStroke(if(chosen) 2.dp else 1.dp,if(chosen) c.primary else c.separator)) {
                            Column(Modifier.padding(4.dp,8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(44.dp).background((if(index==2) c.accent else if(index==3) c.secondary else c.primary).copy(alpha=.22f),CircleShape),contentAlignment=Alignment.Center) {if(index==0) Icon(Icons.Outlined.AddPhotoAlternate,null,Modifier.size(24.dp),tint=c.textPrimary) else CatDrawing(Modifier.size(36.dp),variant=preset);if(chosen) Icon(Icons.Outlined.CheckCircle,"Selected avatar",Modifier.align(Alignment.TopEnd).size(15.dp).background(c.surface,CircleShape),tint=c.textPrimary)}
                                PText(if(index==0) "Photo" else label,style=MaterialTheme.typography.labelSmall,align=TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }}
        item {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {PText("Device preferences",Modifier.weight(1f),MaterialTheme.typography.titleMedium);PText("Just for this device",style=MaterialTheme.typography.labelSmall,muted=true)}
                Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),color=c.surfaceSunken,border=BorderStroke(1.dp,c.separator)) {
                    Column(Modifier.padding(14.dp)) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val stacked=maxWidth<330.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.2f
                            Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                PreferenceSymbol(Icons.Outlined.LightMode,c.secondary)
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                    PText("Appearance",style=MaterialTheme.typography.titleSmall)
                                    PText("A little light, a little dark. Your call.",style=MaterialTheme.typography.labelSmall,muted=true)
                                    if(stacked) ThemeIconSlider(p.themeMode) {mode->onPreferences {it.copy(themeMode=mode)}}
                                }
                                if(!stacked) ThemeIconSlider(p.themeMode) {mode->onPreferences {it.copy(themeMode=mode)}}
                            }
                        }
                        DashedRule(Modifier.padding(vertical=12.dp))
                        DevicePreferenceRow(Icons.Outlined.Language,"App language","The words that feel like home.",c.primary,{languageOpen=true}) {
                            Text(appLanguages.firstOrNull {it.code==p.language}?.name ?: "English",modifier=Modifier.weight(1f,false),style=MaterialTheme.typography.labelMedium,color=c.textSecondary,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Icon(Icons.Outlined.ExpandMore,null,Modifier.size(20.dp),tint=c.textSecondary)
                        }
                        DashedRule(Modifier.padding(vertical=12.dp))
                        DevicePreferenceRow(Icons.Outlined.LocalOffer,"Task tags","A little order for your everyday.",c.accent,onTags) {
                            Surface(modifier=Modifier.weight(1f,false),shape=CircleShape,color=c.surface) {PText("${state.tags.size} tags",Modifier.padding(10.dp,6.dp),MaterialTheme.typography.labelMedium,true)}
                            Icon(Icons.Outlined.ChevronRight,null,Modifier.size(20.dp),tint=c.textSecondary)
                        }
                    }
                }
            }
        }
        item {Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {PText("Our timezones",style=MaterialTheme.typography.titleMedium);TimezoneControls(p,onPreferences)}}
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
        item {SettingsGroup("Chat visibility","Online or invisible") {
            SettingsRow(Icons.Outlined.ChatBubbleOutline,"Show online status",when {
                !connection.paired -> "Connect your partner to choose your visibility"
                connection.chatPresence.saving -> "Saving visibility…"
                !connection.chatPresence.known -> "Checking online status…"
                connection.chatPresence.visible -> "Online while this app is active and connected"
                else -> "Invisible · Your partner won't see when you're online"
            },c.secondary) {
                Switch(connection.chatPresence.visible,onChatVisibility,enabled=connection.paired && connection.online && connection.chatPresence.known && !connection.chatPresence.saving,
                    modifier=Modifier.semantics {contentDescription="Show online status"})
            }
            SettingsStack {
                PText("Invisible hides your activity on all your devices. Your partner sees Offline. Messages and read receipts still work normally.",style=MaterialTheme.typography.bodySmall,muted=true)
                if(connection.chatPresence.error.isNotBlank()) {
                    PText(connection.chatPresence.error,style=MaterialTheme.typography.bodySmall)
                    TextButton(onCheck,enabled=!connection.chatPresence.saving) {PText("Retry status")}
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
            SettingsStack {OutlinedButton({context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,ai.mwm.client.reminders.NotificationSounds.channelId(context,TaskReminders.CHANNEL)))},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),contentPadding=PaddingValues(14.dp,12.dp)) {Icon(Icons.Outlined.Settings,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));PText("Task channel settings",style=MaterialTheme.typography.labelLarge)};OutlinedButton(onTestReminder,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),contentPadding=PaddingValues(14.dp,12.dp)) {Icon(Icons.Outlined.NotificationsActive,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));PText("Test reminder",style=MaterialTheme.typography.labelLarge)}}
        }}
        item {SettingsGroup("Celebrations","Shared joy") {
            SettingsRow(Icons.Outlined.MusicNote,"Little purr sound","When a task is done") {Switch(p.sound,{value->onPreferences {it.copy(sound=value)}},Modifier.semantics {contentDescription="Little purr sound"})}
            DashedRule(Modifier.padding(horizontal=12.dp))
            SettingsRow(Icons.Outlined.Vibration,"Haptic tap","Soft buzz on check-off") {Switch(p.haptics,{value->onPreferences {it.copy(haptics=value)}},Modifier.semantics {contentDescription="Haptic tap"})}
            SettingsRow(Icons.Outlined.Celebration,"Two-cat confetti","Try a little celebration",paper=true) {Pill("▶ Preview",true,c.secondary) {celebrate(context,p);preview=true}}
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
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Surface(Modifier.weight(.64f),shape=RoundedCornerShape(20.dp),color=c.surface,border=BorderStroke(1.dp,c.separator),shadowElevation=1.dp) {
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        PText("Account backup",style=MaterialTheme.typography.titleSmall)
                        val backup by ai.mwm.client.cloud.AccountBackup.status.collectAsState()
                        val owner=AccountScope.current(context)
                        val main=backup["$owner:main"].orEmpty();val care=backup["$owner:care"].orEmpty()
                        val ready=connection.authenticated && main.startsWith("Saved to") && care.startsWith("Saved to")
                        val backing=connection.authenticated && (main.startsWith("Checking account") || care.startsWith("Checking account"))
                        val backupLabel=when {
                            !connection.authenticated->"Sign in to back up"
                            backing->"Backing up…"
                            ready->"Backed up"
                            main.startsWith("Not backed up") || care.startsWith("Not backed up")->"Needs attention"
                            main.startsWith("Changes waiting") || care.startsWith("Changes waiting")->"Changes waiting"
                            else->"Not checked yet"
                        }
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Icon(if(ready)Icons.Outlined.CloudDone else Icons.Outlined.CloudUpload,null,Modifier.size(24.dp),tint=if(ready)c.success else c.textSecondary)
                            PText(backupLabel,style=MaterialTheme.typography.bodySmall)
                        }
                        Button(onBackup,Modifier.fillMaxWidth(),enabled=connection.authenticated && !connection.busy && !backing){PText("Back up")}
                        if(!ready && connection.authenticated)TextButton({backupDetails=true}){PText("View status",style=MaterialTheme.typography.labelSmall)}
                    }
                }
                AppUpdateCard(Modifier.weight(.36f).fillMaxHeight())
            }
        }

    }
    if(backupDetails) {
        val backup by ai.mwm.client.cloud.AccountBackup.status.collectAsState()
        val owner=AccountScope.current(context)
        AlertDialog(onDismissRequest={backupDetails=false},title={PText("Backup status")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {PText("Settings: "+(backup["$owner:main"] ?: "Not checked"),style=MaterialTheme.typography.bodySmall);PText("Care: "+(backup["$owner:care"] ?: "Not checked"),style=MaterialTheme.typography.bodySmall);PText("Only uploaded changes restore. Private backups are separate from partner sharing.",style=MaterialTheme.typography.labelSmall,muted=true)}},confirmButton={TextButton({backupDetails=false}) {PText("Close")}})
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
}

@Composable private fun SettingsOrbit(p:Preferences,days:Long?,modifier:Modifier=Modifier,onEditPhoto:()->Unit={}) {
    val c=AppTheme.colors
    val connection=ai.mwm.client.cloud.LocalPartnerConnection.current
    Row(modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(true,false).forEachIndexed {index,me->
            if(index==1) Column(Modifier.weight(.9f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.Favorite,null,Modifier.size(18.dp),tint=c.accent)
                BasicText(days?.toString() ?: "♡",Modifier.fillMaxWidth(),style=MaterialTheme.typography.headlineSmall.copy(color=c.textPrimary,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center),maxLines=1,softWrap=false,autoSize=TextAutoSize.StepBased(minFontSize=10.sp,maxFontSize=24.sp,stepSize=1.sp))
                PText(if(days==null) "Set our date" else "days together",style=MaterialTheme.typography.labelSmall,muted=true,align=TextAlign.Center)
            }
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(72.dp).padding(bottom=4.dp)) {
                    Box(Modifier.fillMaxSize().border(1.5.dp,c.separator,CircleShape).padding(3.dp).clip(CircleShape)) {
                        val photo=if(me)p.myPhoto else p.partnerPhoto
                        if(photo==if(me)"asset:profile_mina" else "asset:profile_partner") Image(painterResource(if(me)R.drawable.settings_demo_me else R.drawable.settings_demo_partner),"Sample portrait",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        else ProfilePhoto(photo,me,Modifier.fillMaxSize(),if(me)p.avatar else -1)
                    }
                    if(me) OutlinedIconButton(onEditPhoto,Modifier.align(Alignment.BottomEnd).offset(x=4.dp,y=4.dp).size(36.dp),colors=IconButtonDefaults.outlinedIconButtonColors(containerColor=c.surface,contentColor=c.textPrimary),border=BorderStroke(1.dp,c.separator)) {Icon(Icons.Outlined.Edit,"Change my photo",Modifier.size(16.dp))}
                    else Box(Modifier.align(Alignment.BottomEnd).offset(x=4.dp,y=4.dp).size(28.dp).background(c.surface,CircleShape).border(1.dp,c.separator,CircleShape),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Lock,"Partner photo is read-only",Modifier.size(13.dp),tint=c.textSecondary)}
                }
                Text(if(me)p.myName else p.partnerName,Modifier.fillMaxWidth(),style=MaterialTheme.typography.titleSmall,color=c.textPrimary,textAlign=TextAlign.Center,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                val role=if(me)connection.role else connection.partnerRole
                if(role.isNotBlank()) PText(ai.mwm.client.cloud.roleLabel(role),style=MaterialTheme.typography.labelSmall,muted=true,align=TextAlign.Center)
            }
        }
    }
}

@Composable private fun PreferenceSymbol(icon:ImageVector,color:Color) {
    val c=AppTheme.colors
    Box(Modifier.size(36.dp).background(color.copy(alpha=.12f).compositeOver(c.surface),CircleShape).border(1.dp,color.copy(alpha=.6f),CircleShape),contentAlignment=Alignment.Center) {Icon(icon,null,Modifier.size(19.dp),tint=c.textPrimary)}
}

@Composable private fun DevicePreferenceRow(icon:ImageVector,title:String,subtitle:String,color:Color,onClick:()->Unit,end:@Composable RowScope.()->Unit) {
    val c=AppTheme.colors
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=Color.Transparent) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical=10.dp)) {
            val stacked=maxWidth<270.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.25f
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                PreferenceSymbol(icon,color)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    PText(title,style=MaterialTheme.typography.titleSmall)
                    PText(subtitle,style=MaterialTheme.typography.labelSmall,muted=true)
                    if(stacked) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp),content=end)
                }
                if(!stacked) Row(Modifier.widthIn(max=100.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp),content=end)
            }
        }
    }
}

@Composable private fun ProfileNameCard(role: String,name: String,tag: String,color: Color,modifier: Modifier,end: Boolean) {
    val c=AppTheme.colors
    Surface(modifier,shape=RoundedCornerShape(18.dp),color=color.copy(alpha=.12f).compositeOver(c.surface),border=BorderStroke(1.dp,color.copy(alpha=.35f))) {
        Column(Modifier.padding(12.dp,8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {PText(role,style=MaterialTheme.typography.labelSmall,muted=true);Text(name,style=MaterialTheme.typography.titleMedium,color=c.textPrimary,maxLines=2,textAlign=TextAlign.Center,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);Badge(tag,color)}
    }
}

@Composable private fun ThemeIconSlider(mode: String, onChange: (String) -> Unit) {
    val modes = listOf("Light", "Night", "System")
    val labels = listOf("Light mode", "Night mode", "Use system")
    val icons = listOf(Icons.Outlined.LightMode, Icons.Outlined.DarkMode, Icons.Outlined.DesktopWindows)
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
        items(reminders.sortedBy { it.reminderAt(state.preferences.defaultReminderTime,clockZone(state.preferences.clockZone)) }, key = { it.id }) { task -> TaskRow(task, state, { onOpen(task.id) }, { onToggle(task) }, extra = { PText("${dayText(task.day)} · ${if(task.id in active) "Showing on phone" else "Scheduled or dismissed in Android"}", style = MaterialTheme.typography.bodySmall, muted = true) }) }
        if(reminders.isEmpty()) item { PText("No unfinished reminders. Turn on Ongoing reminder when adding or editing a task.", Modifier.padding(24.dp), muted = true) }
    }
}

package ai.mwm.client.ui.screens

import ai.mwm.client.R
import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale


import androidx.compose.runtime.saveable.*
import androidx.compose.ui.semantics.*
import ai.mwm.client.reminders.LoveDayReminders
import kotlinx.coroutines.delay

internal val LoveSettingsSaver=Saver<LoveCelebrationSettings,String>(save={it.toJson().toString()},restore={LoveCelebrationSettings.fromJson(org.json.JSONObject(it))})

    @Composable private fun LovePageTitle(title:String,onBack:()->Unit) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onBack) {Icon(Icons.Outlined.ArrowBack,"Back")}
            PText(title,Modifier.weight(1f),MaterialTheme.typography.titleLarge)
            Duo(20.dp)
        }
    }

    @Composable private fun LoveCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
        val c=AppTheme.colors
        Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=c.surface,border=BorderStroke(1.dp,c.separator)) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
        }
    }

@Composable internal fun LoveMilestonesScreen(p:Preferences,onBack:()->Unit,onSettings:()->Unit,onEditPhoto:()->Unit,onCelebrate:(LoveMilestone)->Unit) {
    val c=AppTheme.colors
    val today=currentTaskDay(p.clockZone)
    val milestones=remember(p,today){loveMilestones(p,today)}
    LazyColumn(contentPadding=PaddingValues(16.dp,12.dp,16.dp,24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {LovePageTitle("Our milestones",onBack)}
        item {MilestonesCoupleCard(p,(today-p.togetherSince).coerceAtLeast(0),onEditPhoto)}
        milestones.firstOrNull()?.let {next->item {Surface(onClick={onCelebrate(next)},shape=RoundedCornerShape(20.dp),color=c.secondary.copy(alpha=.22f).compositeOver(c.surface),border=BorderStroke(1.dp,c.separator)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                TaskCaption("Our next little milestone")
                PText(next.title,style=MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    PText(dayText(next.day),style=MaterialTheme.typography.bodySmall,muted=true)
                    PText(if(next.day==today)"Today ♡" else "In ${next.day-today} days",style=MaterialTheme.typography.labelMedium)
                }
            }
        }}}
        if(milestones.size>1)item {LoveCard {
            PText("Coming up",style=MaterialTheme.typography.titleSmall)
            milestones.drop(1).take(3).forEach {m->
                Row(Modifier.fillMaxWidth().clickable {onCelebrate(m)}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Icon(if(m.years!=null)Icons.Outlined.CalendarMonth else Icons.Outlined.FavoriteBorder,null,Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {PText(m.title,style=MaterialTheme.typography.bodyMedium);PText(dayText(m.day),style=MaterialTheme.typography.labelSmall,muted=true)}
                    Icon(Icons.Outlined.ChevronRight,"Preview ${m.title}",Modifier.size(18.dp))
                }
            }
        }}
        if(milestones.isEmpty())item {PText("Choose the milestones you want to celebrate.",muted=true)}
        item {OutlinedButton(onSettings,Modifier.fillMaxWidth().heightIn(min=48.dp)) {Icon(Icons.Outlined.Tune,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));PText("Celebration settings",style=MaterialTheme.typography.labelLarge)}}
        milestones.firstOrNull()?.let {next->item {TextButton({onCelebrate(next)},Modifier.fillMaxWidth()) {Icon(Icons.Outlined.PlayArrow,null,Modifier.size(18.dp));PText("Preview our celebration",style=MaterialTheme.typography.labelLarge)}}}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LoveCelebrationOptions(p:Preferences,onBack:()->Unit,onPreview:(LoveCelebrationSettings)->Unit,onSave:(LoveCelebrationSettings,(String?)->Unit)->Unit,onEnable:()->Unit,allowed:Boolean) {
    val c=AppTheme.colors
    val context=androidx.compose.ui.platform.LocalContext.current
    var draft by rememberSaveable(stateSaver=LoveSettingsSaver){mutableStateOf(p.loveCelebrations)}
    var saving by remember {mutableStateOf(false)}
    var failure by remember {mutableStateOf<String?>(null)}
    var custom by rememberSaveable {mutableStateOf(false)}
    var customText by rememberSaveable {mutableStateOf("")}
    var customError by remember {mutableStateOf<String?>(null)}
    var timing by rememberSaveable {mutableStateOf(false)}
    var section by rememberSaveable {mutableIntStateOf(0)}
    androidx.activity.compose.BackHandler(enabled=saving) { }
    val chips=FilterChipDefaults.filterChipColors(containerColor=c.surface,selectedContainerColor=c.secondary,selectedLabelColor=c.textPrimary,labelColor=c.textPrimary)
    Column(Modifier.fillMaxSize().imePadding().padding(16.dp,12.dp)) {
        LovePageTitle("Love-day celebrations",{if(!saving)onBack()})
        LazyColumn(Modifier.weight(1f).freezeWhileSaving(saving),contentPadding=PaddingValues(top=12.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item {
                val next=loveMilestones(p.copy(loveCelebrations=draft),currentTaskDay(p.clockZone),1).firstOrNull()
                Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=c.secondary.copy(alpha=.18f).compositeOver(c.surface),border=BorderStroke(1.dp,c.separator)) {
                    Column(Modifier.padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.Favorite,null,Modifier.size(24.dp),tint=c.accent)
                        TaskCaption("Our next celebration")
                        PText(next?.title ?: "Choose your milestones",style=MaterialTheme.typography.headlineSmall,align=TextAlign.Center)
                        next?.let {PText(dayText(it.day),style=MaterialTheme.typography.labelSmall,muted=true)}
                        PText(draft.note.ifBlank {"Your little note can go here"},style=MaterialTheme.typography.bodySmall,muted=true,align=TextAlign.Center,preserveContent=true)
                    }
                }
            }
            item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("Celebrate","Reminders","Look & feel").forEachIndexed {index,label->
                    FilterChip(section==index,{section=index},enabled=!saving,colors=chips,modifier=Modifier.weight(1f),label={PText(label,style=MaterialTheme.typography.labelSmall)})
                }
            }}
            if(section==0)item {Column {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    FilterChip(draft.hundreds,{draft=draft.copy(hundreds=!draft.hundreds)},enabled=!saving,colors=chips,label={PText("Every 100 days",style=MaterialTheme.typography.labelSmall)})
                    FilterChip(draft.anniversaries,{draft=draft.copy(anniversaries=!draft.anniversaries)},enabled=!saving,colors=chips,label={PText("Anniversaries",style=MaterialTheme.typography.labelSmall)})
                    FilterChip(draft.customDays.isNotEmpty(),{customText=draft.customDays.joinToString(", ");customError=null;custom=true},enabled=!saving,colors=chips,label={PText("Custom",style=MaterialTheme.typography.labelSmall)})
                }
                if(custom) {
                    TaskCaption("Days, separated by commas")
                    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),color=c.surface,border=BorderStroke(1.dp,if(customError!=null)c.error else c.separator)) {
                        BasicTextField(customText,{customText=it.take(200);customError=null},Modifier.fillMaxWidth().padding(12.dp).heightIn(min=28.dp).semantics {contentDescription="Custom love days"},singleLine=true,textStyle=MaterialTheme.typography.bodyMedium.copy(color=c.textPrimary),decorationBox={inner->if(customText.isBlank())PText("365, 520, 1000",muted=true);inner()})
                    }
                    PText(customError ?: "Up to 20 milestones · Clear to turn off",style=MaterialTheme.typography.labelSmall,muted=customError==null)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                        TextButton({custom=false}){PText("Cancel")}
                        TextButton({
                            val values=customText.trim().split(Regex("[,\\s]+")).filter {it.isNotBlank()}.map {it.toIntOrNull()}
                            if(values.any {it==null || it !in 1..365250} || values.size>20)customError="Use up to 20 whole numbers from 1 to 365250."
                            else {draft=draft.copy(customDays=values.filterNotNull().distinct().sorted());custom=false}
                        }){PText("Done")}
                    }
                }
                if(draft.customDays.isNotEmpty())PText("Custom days: ${draft.customDays.joinToString()}",style=MaterialTheme.typography.labelSmall,muted=true)
                loveMilestones(p.copy(loveCelebrations=draft),currentTaskDay(p.clockZone),1).firstOrNull()?.let {PText("Next: ${it.title} · ${dayText(it.day)}",style=MaterialTheme.typography.labelSmall,muted=true)}
            }}
            if(section==1)item {LoveCard {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TaskCaption("Remind me",Modifier.weight(1f))
                    Switch(draft.reminders,{draft=draft.copy(reminders=it)},enabled=!saving,colors=SwitchDefaults.colors(checkedTrackColor=c.secondary,checkedThumbColor=c.surface,uncheckedTrackColor=c.surfaceSunken,uncheckedThumbColor=c.separator,uncheckedBorderColor=c.separator),modifier=Modifier.semantics {contentDescription="Love-day reminders"})
                }
                TextButton({timing=true},enabled=!saving) {Icon(Icons.Outlined.NotificationsNone,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));PText("On the day · ${timeText(draft.minute)}")}
                PText(if(draft.advanceDays==0)"On the day only · Your local time" else "Also ${draft.advanceDays} ${if(draft.advanceDays==1)"day" else "days"} before · Your local time",style=MaterialTheme.typography.labelSmall,muted=true)
                if(draft.reminders && !allowed)TextButton(onEnable) {PText("Enable notifications")}
            }}
            if(section==2)item {Column {
                TaskCaption("Celebration effect")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("Cats" to "Cat confetti","Hearts" to "Hearts","Glow" to "Soft glow").forEach {(value,label)->FilterChip(draft.effect==value,{draft=draft.copy(effect=value)},enabled=!saving,colors=chips,label={PText(label,style=MaterialTheme.typography.labelSmall)})}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.sound,{draft=draft.copy(sound=!draft.sound)},enabled=!saving,colors=chips,label={PText("Sound",style=MaterialTheme.typography.labelSmall)},leadingIcon={Icon(Icons.Outlined.MusicNote,null,Modifier.size(16.dp))})
                    FilterChip(draft.haptics,{draft=draft.copy(haptics=!draft.haptics)},enabled=!saving,colors=chips,label={PText("Haptic",style=MaterialTheme.typography.labelSmall)},leadingIcon={Icon(Icons.Outlined.Vibration,null,Modifier.size(16.dp))})
                }
                PText("Respects your quiet hours",style=MaterialTheme.typography.labelSmall,muted=true)
            }}
            item {Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                TaskCaption("Your celebration note · Optional")
                OutlinedTextField(draft.note,{draft=draft.copy(note=it.take(80))},Modifier.fillMaxWidth().semantics {contentDescription="Celebration note"},enabled=!saving,textStyle=MaterialTheme.typography.bodyMedium,shape=RoundedCornerShape(14.dp),minLines=2,maxLines=3)
                PText("Appears on your celebration card",style=MaterialTheme.typography.labelSmall,muted=true)
            }}
            item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {TaskCaption("Day counting");PText("Start date = Day 0",style=MaterialTheme.typography.labelSmall,muted=true)}}
        }
        failure?.let {Text(it,color=c.error,style=MaterialTheme.typography.bodySmall)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton({onPreview(draft)},Modifier.weight(1f).heightIn(min=48.dp),enabled=!saving) {Icon(Icons.Outlined.PlayArrow,null,Modifier.size(16.dp));PText("Preview",style=MaterialTheme.typography.labelLarge)}
            Button({saving=true;failure=null;onSave(draft) {error->saving=false;failure=error;if(error==null)onBack()}},Modifier.weight(1f).heightIn(min=48.dp),enabled=!saving) {PText(if(saving)"Saving…" else "Save",style=MaterialTheme.typography.labelLarge)}
        }
    }
    if(timing)AlertDialog(onDismissRequest={timing=false},title={PText("Love-day reminder")},text={Column {
        TextButton({pickTime(context,draft.minute){draft=draft.copy(minute=it)}}) {PText("Time · ${timeText(draft.minute)}")}
        TaskCaption("Advance notice")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf(0,1,3,7).forEach {days->FilterChip(draft.advanceDays==days,{draft=draft.copy(advanceDays=days)},colors=chips,label={PText(if(days==0)"None" else "$days ${if(days==1)"day" else "days"}",style=MaterialTheme.typography.labelSmall)})}}
    }},confirmButton={TextButton({timing=false}){PText("Done")}},containerColor=c.surface)
}

@Composable internal fun LoveCelebrationDay(p:Preferences,m:LoveMilestone,onBack:()->Unit) {
    val c=AppTheme.colors
    val context=androidx.compose.ui.platform.LocalContext.current
    val inspection=LocalInspectionMode.current
    var animation by remember(m.day,p.loveCelebrations.effect) {mutableFloatStateOf(0f)}
    LaunchedEffect(m.day,p.loveCelebrations.effect) {
        if(!inspection && !loveIsQuiet(System.currentTimeMillis(),p))celebrate(context,p.copy(sound=p.loveCelebrations.sound,haptics=p.loveCelebrations.haptics))
        if(!inspection)androidx.compose.animation.core.animate(0f,1f,animationSpec=androidx.compose.animation.core.tween(2400)) {value,_->animation=value}
    }
    Column(Modifier.fillMaxSize().padding(16.dp,12.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        LovePageTitle("Our celebration",onBack)
        Spacer(Modifier.weight(1f))
        LoveCard {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {Icon(Icons.Outlined.Favorite,null,Modifier.size(28.dp),tint=c.accent)}
            PText(m.celebrationTitle,Modifier.fillMaxWidth(),MaterialTheme.typography.headlineMedium,align=TextAlign.Center)
            PText(dayText(m.day),Modifier.fillMaxWidth(),MaterialTheme.typography.labelSmall,muted=true,align=TextAlign.Center)
            SettingsOrbit(p,m.daysTogether)
            if(p.loveCelebrations.note.isNotBlank())PText(p.loveCelebrations.note,Modifier.fillMaxWidth(),MaterialTheme.typography.titleMedium.copy(fontStyle=FontStyle.Italic),align=TextAlign.Center,preserveContent=true)
            Box(Modifier.fillMaxWidth().height(90.dp),contentAlignment=Alignment.Center) {
                when(p.loveCelebrations.effect) {
                    "Cats"->{Canvas(Modifier.fillMaxSize()) {for(i in 0..17)drawCircle(listOf(c.primary,c.accent,c.secondary)[i%3],(2+i%3).dp.toPx(),androidx.compose.ui.geometry.Offset(size.width*((i*37%100)/100f),size.height*((i*19%100)/100f+animation)%size.height))};CatDrawing(Modifier.size(140.dp,80.dp),double=true)}
                    "Hearts"->Row(horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.CenterVertically) {repeat(3){i->Icon(Icons.Outlined.Favorite,null,Modifier.size((24+12*i).dp).offset(y=((-12*animation)*(if(i%2==0)1 else -1)).dp),tint=c.accent)}}
                    else->Box(Modifier.size((70+10*animation).dp).background(Brush.radialGradient(listOf(c.accent.copy(alpha=.4f),Color.Transparent)),CircleShape),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(40.dp),tint=c.accent)}
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onBack,Modifier.fillMaxWidth().heightIn(min=48.dp)) {PText("Lovely ♡")}
    }
}

@Composable internal fun LoveCelebrationAutoHost(p:Preferences,eligible:Boolean,scopeKey:String):Boolean {
    val context=androidx.compose.ui.platform.LocalContext.current
    val today=currentTaskDay(p.clockZone)
    var showing by remember(scopeKey,p.togetherSince){mutableStateOf<LoveMilestone?>(null)}
    LaunchedEffect(p,eligible,today,scopeKey) {
        if(eligible && p.started)loveMilestones(p,today,1).firstOrNull {it.day==today && !LoveDayReminders.wasShown(context,p,it)}?.let {
            LoveDayReminders.markShown(context,p,it);showing=it
        }
    }
    showing?.let {m->androidx.compose.ui.window.Dialog(onDismissRequest={showing=null},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        ApprovedDesign {Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {LoveCelebrationDay(p,m){showing=null}}}
    }}
    return showing!=null
}

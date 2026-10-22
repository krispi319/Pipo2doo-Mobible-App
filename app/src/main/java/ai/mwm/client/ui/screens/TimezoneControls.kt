package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable internal fun TimezoneControls(p:Preferences,onPreferences:((Preferences)->Preferences)->Unit) {
    val c=AppTheme.colors
    var target by rememberSaveable {mutableStateOf("")}
    var tick by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit) {while(true) {tick=System.currentTimeMillis();delay(30_000)}}
    val instant=Instant.ofEpochMilli(tick)
    val mine=instant.atZone(clockZone(p.clockZone))
    val partner=partnerClockZone(p)?.let {instant.atZone(it)}
    Surface(shape=RoundedCornerShape(28.dp),color=c.surfaceSunken,border=BorderStroke(1.dp,c.separator)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            TimezonePersonRow(p,true,mine) {target="mine"}
            // Wall-clock position from midnight to midnight; never changes the selected zone.
            BoxWithConstraints(Modifier.fillMaxWidth().height(42.dp).semantics {contentDescription="Your day: ${mine.format(DateTimeFormatter.ofPattern("HH:mm"))}"}) {
                Box(Modifier.fillMaxWidth().height(12.dp).align(Alignment.Center).background(Brush.horizontalGradient(listOf(c.accent.copy(alpha=.35f),c.warning.copy(alpha=.45f),c.primary.copy(alpha=.35f),c.accent.copy(alpha=.35f))),CircleShape))
                val fraction=mine.toLocalTime().toSecondOfDay()/86400f
                Surface(Modifier.offset(x=(maxWidth-36.dp).coerceAtLeast(0.dp)*fraction).align(Alignment.CenterStart).size(36.dp),shape=CircleShape,color=c.surface,border=BorderStroke(2.dp,c.surface)) {
                    ProfilePhoto(p.myPhoto,true,Modifier.fillMaxSize().padding(2.dp),p.avatar)
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                listOf("12am","6am","12pm","6pm","12am").forEach {PText(it,style=MaterialTheme.typography.labelSmall,muted=true)}
            }
            DashedRule(Modifier.padding(vertical=4.dp))
            TimezonePersonRow(p,false,partner) {target="partner"}
            Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Info,null,Modifier.padding(top=2.dp).size(13.dp),tint=c.textSecondary)
                val gap=partner?.let {
                    val minutes=(it.offset.totalSeconds-mine.offset.totalSeconds)/60
                    val amount=kotlin.math.abs(minutes)
                    if(minutes==0) "Same time" else "${amount/60}h${if(amount%60==0)"" else " ${amount%60}m"} ${if(minutes>0)"ahead" else "behind"}"
                }
                PText(listOfNotNull(gap,if(p.partnerClockOverride.isNotBlank())"Partner display override" else null,"Reminders use this phone's time").joinToString(" · "),Modifier.weight(1f),MaterialTheme.typography.labelSmall,true)
            }
        }
    }
    if(target.isNotBlank()) TimezoneChoiceDialog(target=="mine",{target=""}) {zone->
        val own=target=="mine"
        onPreferences {if(own)it.copy(clockZone=zone) else it.copy(partnerClockOverride=zone)}
        target=""
    }
}

@Composable private fun TimezonePersonRow(p:Preferences,me:Boolean,time:ZonedDateTime?,onClick:()->Unit) {
    val c=AppTheme.colors;val tone=if(me)c.primary else c.secondary
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().semantics {contentDescription=if(me)"Choose your timezone" else "Choose partner timezone on this phone"},color=Color.Transparent,shape=RoundedCornerShape(18.dp)) {
        BoxWithConstraints(Modifier.padding(4.dp,6.dp)) {
            val stacked=maxWidth<290.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.25f
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Surface(Modifier.size(38.dp),shape=CircleShape,color=tone.copy(alpha=.2f).compositeOver(c.surface)) {ProfilePhoto(if(me)p.myPhoto else p.partnerPhoto,me,Modifier.fillMaxSize().padding(3.dp),if(me)p.avatar else -1)}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text((if(me)p.myName else p.partnerName)+(if(me)" · You" else ""),style=MaterialTheme.typography.titleSmall,color=c.textPrimary,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(time?.zone?.id?.substringAfterLast('/')?.replace('_',' ') ?: "Tap to choose timezone",style=MaterialTheme.typography.labelSmall,color=c.textSecondary,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(stacked) TimezoneDigits(time,tone)
                }
                if(!stacked) TimezoneDigits(time,tone)
                Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp),tint=c.textSecondary)
            }
        }
    }
}

@Composable private fun TimezoneDigits(time:ZonedDateTime?,tone:Color) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        Icon(if(time==null)Icons.Outlined.Schedule else if(time.hour in 6..18)Icons.Outlined.WbSunny else Icons.Outlined.NightsStay,null,Modifier.size(17.dp),tint=tone)
        Text(time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--",style=MaterialTheme.typography.headlineSmall.copy(fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold),color=if(time==null)AppTheme.colors.textSecondary else tone.copy(alpha=.6f).compositeOver(AppTheme.colors.textPrimary),maxLines=1,softWrap=false)
    }
}

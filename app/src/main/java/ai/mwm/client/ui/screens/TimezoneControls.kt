package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable internal fun TimezoneControls(p: Preferences,onPreferences: ((Preferences)->Preferences)->Unit) {
    var target by remember {mutableStateOf("")}
    var tick by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit) {while(true){tick=System.currentTimeMillis();delay(30_000)}}
    val mine=clockZone(p.clockZone)
    val partner=partnerClockZone(p)
    val format=DateTimeFormatter.ofPattern("EEE d MMM · HH:mm")
    Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        PText("Our clocks",style=MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {PText(p.myName,preserveContent=true,style=MaterialTheme.typography.labelSmall);PText(Instant.ofEpochMilli(tick).atZone(mine).format(format),style=MaterialTheme.typography.bodyMedium);PText(mine.id,style=MaterialTheme.typography.labelSmall,muted=true)}
            Column(Modifier.weight(1f)) {PText(p.partnerName,preserveContent=true,style=MaterialTheme.typography.labelSmall);PText(partner?.let {Instant.ofEpochMilli(tick).atZone(it).format(format)} ?: "Waiting for partner",style=MaterialTheme.typography.bodyMedium);PText(partner?.id ?: "Choose a timezone below",style=MaterialTheme.typography.labelSmall,muted=true)}
        }
        OutlinedButton({target="mine"}) {PText("My clock · ${if(p.clockZone.isBlank()) "Automatic" else p.clockZone.substringAfterLast('/').replace('_',' ')}")}
        OutlinedButton({target="partner"}) {PText("Partner clock · ${if(p.partnerClockOverride.isBlank()) "Use partner's choice" else p.partnerClockOverride.substringAfterLast('/').replace('_',' ')}")}
        PText("Clock display only · Task reminders use this phone's timezone.",style=MaterialTheme.typography.labelSmall,muted=true)
    }
    if(target.isNotBlank()) TimezoneChoiceDialog(target=="mine",{target=""}) {zone->
        val mineTarget=target=="mine"
        onPreferences {if(mineTarget)it.copy(clockZone=zone) else it.copy(partnerClockOverride=zone)}
        target=""
    }
}

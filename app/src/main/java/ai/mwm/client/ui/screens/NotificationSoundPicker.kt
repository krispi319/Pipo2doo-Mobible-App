package ai.mwm.client.ui.screens

import ai.mwm.client.reminders.NotificationSounds
import ai.mwm.client.reminders.TaskReminders
import ai.mwm.client.reminders.CareReminders
import ai.mwm.client.cloud.PartnerPokeService
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable internal fun NotificationSoundPicker(onChanged: ()->Unit) {
    val context=LocalContext.current
    var tone by remember {mutableStateOf(NotificationSounds.selected(context))}
    var menu by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    DisposableEffect(Unit) {onDispose {NotificationSounds.stopPreview()}}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(if(tone=="silent") Icons.Outlined.VolumeOff else Icons.Outlined.MusicNote,null,Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {PText("Notification sound",style=MaterialTheme.typography.titleSmall);PText("This phone · messages and reminders",style=MaterialTheme.typography.labelSmall,muted=true)}
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton({menu=true},Modifier.fillMaxWidth().heightIn(min=48.dp),shape=CircleShape,contentPadding=PaddingValues(14.dp,12.dp)) {PText(NotificationSounds.label(tone),Modifier.weight(1f));Icon(Icons.Outlined.ExpandMore,null)}
                DropdownMenu(menu,{menu=false}) {NotificationSounds.choices.forEach {(key,label)->DropdownMenuItem(text={PText(label)},leadingIcon={Icon(if(key=="silent") Icons.Outlined.VolumeOff else Icons.Outlined.MusicNote,null,Modifier.size(20.dp))},trailingIcon={if(tone==key) Icon(Icons.Outlined.Check,null)},onClick={
                    menu=false
                    runCatching {NotificationSounds.stopPreview();NotificationSounds.select(context,key);tone=key;TaskReminders.createChannel(context);PartnerPokeService.channel(context);onChanged()}.onSuccess {error=null}.onFailure {error="Sound could not be changed. Try again."}
                })}}
            }
            OutlinedIconButton({NotificationSounds.preview(context)},enabled=tone!="silent",modifier=Modifier.size(48.dp)) {Icon(Icons.Outlined.PlayArrow,"Preview ${NotificationSounds.label(tone)}")}
        }
        error?.let {PText(it,style=MaterialTheme.typography.bodySmall)}
        PText("Android sound settings and Quiet hours still apply.",style=MaterialTheme.typography.labelSmall,muted=true)
    }
}

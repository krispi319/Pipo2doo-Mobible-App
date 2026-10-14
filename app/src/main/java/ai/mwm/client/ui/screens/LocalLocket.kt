package ai.mwm.client.ui.screens

import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable fun LocalLocket(notes: List<ai.mwm.client.data.Appreciation> = emptyList()) {
    val connection=ai.mwm.client.cloud.LocalPartnerConnection.current
    if(connection.connected) {SharedLocket(notes);return}
    val context=LocalContext.current
    val prefs=remember {context.getSharedPreferences("pipo_locket",0)}
    var photo by remember {mutableStateOf(prefs.getString("photo","asset:demo_locket")!!)}
    var caption by remember {mutableStateOf(prefs.getString("caption","Pipo stole my keyboard again")!!)}
    var heart by remember {mutableStateOf(prefs.getBoolean("heart",false))}
    var open by remember {mutableStateOf(false)}
    var draft by remember {mutableStateOf(caption)}
    val picker=rememberPhotoPicker {photo=it;prefs.edit().putString("photo",it).apply()}
    val c=AppTheme.colors
    Surface(Modifier.fillMaxWidth().padding(start=44.dp,end=16.dp,top=8.dp,bottom=18.dp),shape=RoundedCornerShape(20.dp,4.dp,20.dp,20.dp),color=c.accent.copy(alpha=.14f).compositeOver(c.surface)) {
        Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            IconButton({heart=!heart;prefs.edit().putBoolean("heart",heart).apply()}) {Icon(if(heart) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,if(heart) "Remove local heart" else "Save a local heart",tint=c.accent)}
            Surface(onClick={draft=caption;open=true},modifier=Modifier.weight(1f),color=androidx.compose.ui.graphics.Color.Transparent) {Column {PText(if(photo.startsWith("asset:")) "Sample locket · Not received" else "My local locket",style=MaterialTheme.typography.labelSmall,muted=true);PText(caption,style=MaterialTheme.typography.titleSmall,preserveContent=true)}}
            Surface(onClick={draft=caption;open=true},shape=CircleShape,color=c.accent,modifier=Modifier.size(64.dp)) {LocalPicture(photo,Modifier.padding(3.dp).clip(CircleShape))}
        }
    }
    if(open) AlertDialog(onDismissRequest={open=false},title={PText("Local locket")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {LocalPicture(photo,Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)));OutlinedTextField(draft,{draft=it.take(120)},label={PText("Caption")});OutlinedButton(picker) {PText("Change picture")};PText("Photo and hearts stay on this phone. Nothing is delivered to a partner.",style=MaterialTheme.typography.bodySmall,muted=true)}},confirmButton={Button({caption=draft.trim();prefs.edit().putString("caption",caption).apply();open=false},enabled=draft.isNotBlank()) {PText("Save")}},dismissButton={OutlinedButton({open=false}) {PText("Cancel")}})
}

@Composable private fun SharedLocket(notes: List<ai.mwm.client.data.Appreciation>) {
    val connection=ai.mwm.client.cloud.LocalPartnerConnection.current
    val send=ai.mwm.client.cloud.LocalPartnerNote.current
    val locket=notes.filter {it.kind=="Locket" && it.owner==ai.mwm.client.data.Owner.You && it.picture.isNotBlank()}.maxByOrNull {it.at}
    val heart=locket!=null && notes.any {it.kind=="Heart" && it.taskTitle==locket.id && it.owner==ai.mwm.client.data.Owner.Me}
    var open by remember {mutableStateOf(false)}
    var compose by remember {mutableStateOf(false)}
    var draftPhoto by remember {mutableStateOf("")}
    var draftCaption by remember {mutableStateOf("")}
    val picker=rememberPhotoPicker {draftPhoto=it;compose=true}
    val c=AppTheme.colors
    Surface(Modifier.fillMaxWidth().padding(start=44.dp,end=16.dp,top=8.dp,bottom=18.dp),shape=RoundedCornerShape(20.dp,4.dp,20.dp,20.dp),color=c.accent.copy(alpha=.14f).compositeOver(c.surface)) {
        Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            IconButton({locket?.let {send("Heart","Loved your picture.",it.id,"")}},enabled=locket!=null && !heart && connection.online && !connection.busy) {Icon(if(heart) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,if(heart) "Heart shared" else "Send a heart",tint=c.accent)}
            Surface(onClick={open=true},modifier=Modifier.weight(1f),color=androidx.compose.ui.graphics.Color.Transparent) {Column {PText(if(locket==null) "Partner locket" else "From your partner",style=MaterialTheme.typography.labelSmall,muted=true);PText(locket?.message ?: "No picture shared yet",style=MaterialTheme.typography.titleSmall)}}
            Surface(onClick={open=true},shape=CircleShape,color=c.accent,modifier=Modifier.size(64.dp)) {if(locket!=null)LocalPicture(locket.picture,Modifier.padding(3.dp).clip(CircleShape)) else Box(contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Image,"No partner photo")}}
        }
    }
    if(open) AlertDialog(onDismissRequest={open=false},title={PText("Partner locket")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {locket?.let {LocalPicture(it.picture,Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)));PText(it.message,preserveContent=true)} ?: PText("Share a picture with your partner to start your locket.");PText("Only your authenticated shared space can open these photos.",style=MaterialTheme.typography.bodySmall,muted=true)}},confirmButton={OutlinedButton({open=false;picker()},enabled=connection.paired && connection.online) {PText("Share my picture")}},dismissButton={OutlinedButton({open=false}) {PText("Close")}})
    if(compose) AlertDialog(onDismissRequest={compose=false},title={PText("Share a locket")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {LocalPicture(draftPhoto,Modifier.fillMaxWidth().height(200.dp));OutlinedTextField(draftCaption,{draftCaption=it.take(120)},label={PText("Caption")});PText("Uploads only this picture and caption. Health records remain private.",style=MaterialTheme.typography.bodySmall,muted=true)}},confirmButton={Button({send("Locket",draftCaption,"",draftPhoto);compose=false},enabled=draftCaption.isNotBlank() && connection.online && !connection.busy) {PText("Share")}},dismissButton={OutlinedButton({compose=false}) {PText("Cancel")}})
}

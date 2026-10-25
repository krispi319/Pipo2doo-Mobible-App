package ai.mwm.client.ui.screens

import ai.mwm.client.ui.theme.AppTheme
import ai.mwm.client.data.Appreciation
import ai.mwm.client.data.Owner
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable fun LocalLocket(notes: List<ai.mwm.client.data.Appreciation> = emptyList(), locketRequest: String? = null, onLocketOpened: () -> Unit = {}, preferences: ai.mwm.client.data.Preferences = ai.mwm.client.data.Preferences()) {
    val connection=ai.mwm.client.cloud.LocalPartnerConnection.current
    if(connection.connected) {SharedLocket(notes,locketRequest,onLocketOpened,preferences);return}
    val context=LocalContext.current
    // Preserve the existing local picture, caption and heart keys verbatim.
    val prefs=remember {context.getSharedPreferences("pipo_locket",0)}
    var photo by remember {mutableStateOf(prefs.getString("photo","asset:demo_locket")!!)}
    var caption by remember {mutableStateOf(prefs.getString("caption","Pipo stole my keyboard again")!!)}
    var heart by remember {mutableStateOf(prefs.getBoolean("heart",false))}
    var uploadedAt by remember {mutableLongStateOf(prefs.getLong("uploaded_at",0))}
    var locketOpen by remember {mutableStateOf(false)}
    var partnerInfo by remember {mutableStateOf(false)}
    var draft by remember {mutableStateOf(caption)}
    val openLocal:()->Unit={draft=caption;locketOpen=true}
    val picker=rememberPhotoPicker {
        photo=it;uploadedAt=System.currentTimeMillis()
        prefs.edit().putString("photo",it).putLong("uploaded_at",uploadedAt).apply()
        draft=caption;locketOpen=true
    }
    LaunchedEffect(locketRequest) {if(locketRequest!=null) {openLocal();onLocketOpened()}}
    LocketCard(
        preferences=preferences,
        photos=if(photo.isBlank()) emptyList() else listOf(Appreciation(id="local_locket",kind="Locket",message=caption,picture=photo,owner=Owner.Me,at=uploadedAt)),
        canAdd=true,onGallery=openLocal,onAdd=picker,
        onPhoto={owner->if(owner==Owner.Me) openLocal() else partnerInfo=true}
    )
    if(locketOpen) AlertDialog(onDismissRequest={locketOpen=false},title={PText("Our locket")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            LocalPicture(photo,Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)))
            if(photo.startsWith("asset:")) PText("Sample photo",style=MaterialTheme.typography.bodySmall,muted=true)
            OutlinedTextField(draft,{draft=it.take(120)},label={PText("Caption")})
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton({heart=!heart;prefs.edit().putBoolean("heart",heart).apply()}) {
                    Icon(if(heart) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,if(heart) "Remove local heart" else "Save a local heart")
                }
                OutlinedButton(picker) {PText("Change picture")}
            }
            PText("Saved on this phone. Connect a partner to share pictures.",style=MaterialTheme.typography.bodySmall,muted=true)
        }
    },confirmButton={Button({caption=draft.trim();prefs.edit().putString("caption",caption).apply();locketOpen=false},enabled=draft.isNotBlank()) {PText("Save")}},dismissButton={OutlinedButton({locketOpen=false}) {PText("Cancel")}})
    if(partnerInfo) AlertDialog(onDismissRequest={partnerInfo=false},title={PText("Your partner's space")},text={PText("Connect your partner in Us & settings to see their locket pictures here.")},confirmButton={OutlinedButton({partnerInfo=false}) {PText("Got it")}})

}

@Composable private fun SharedLocket(notes: List<Appreciation>, locketRequest: String?, onLocketOpened: () -> Unit, preferences: ai.mwm.client.data.Preferences) {
    val connection=ai.mwm.client.cloud.LocalPartnerConnection.current
    val send=ai.mwm.client.cloud.LocalPartnerNote.current
    val photos=notes.filter {it.kind=="Locket" && it.picture.isNotBlank()}.sortedByDescending {it.at}
    var locketOpen by remember(connection.uid,connection.spaceId) {mutableStateOf(false)}
    var compose by remember(connection.uid,connection.spaceId) {mutableStateOf(false)}
    var draftPhoto by remember(connection.uid,connection.spaceId) {mutableStateOf("")}
    var draftCaption by remember(connection.uid,connection.spaceId) {mutableStateOf("")}
    LaunchedEffect(locketRequest) {if(locketRequest!=null) {locketOpen=true;onLocketOpened()}}
    val picker=rememberPhotoPicker {draftPhoto=it;draftCaption="";compose=true}
    val c=AppTheme.colors
    LocketCard(preferences,photos,connection.paired && connection.online && !connection.busy,
        onGallery={locketOpen=true},onAdd=picker,onPhoto={locketOpen=true})

    if(locketOpen) AlertDialog(onDismissRequest={locketOpen=false},title={PText("Our locket")},text={
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=460.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(photos.isEmpty()) item {PText("Share your first picture to start your locket.")}
            items(photos,key={it.id}) {photo->
                val mine=photo.owner==Owner.Me
                val heart=notes.any {it.kind=="Heart" && it.taskTitle==photo.id && it.owner==(if(mine) Owner.You else Owner.Me)}
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    PText(if(mine) preferences.myName else preferences.partnerName,preserveContent=true,style=MaterialTheme.typography.labelSmall,muted=true)
                    LocalPicture(photo.picture,Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)))
                    PText("Uploaded "+java.time.Instant.ofEpochMilli(photo.at).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")),style=MaterialTheme.typography.labelSmall,muted=true)
                    PText(photo.message,preserveContent=true)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        IconButton({send("Heart","Loved your picture.",photo.id,"")},enabled=!mine && !heart && connection.online && !connection.busy) {Icon(if(heart) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,if(mine) "Partner heart" else "Send a heart",tint=c.accent)}
                        PText(if(mine) {if(heart) "Loved by ${preferences.partnerName}" else "Shared by ${preferences.myName}"} else {if(heart) "Heart sent" else "Send a little love"},style=MaterialTheme.typography.bodySmall,muted=true)
                    }
                }
            }
        }
    },confirmButton={OutlinedButton({locketOpen=false;picker()},enabled=connection.paired && connection.online && !connection.busy) {PText("Share my picture")}},dismissButton={TextButton({locketOpen=false}) {PText("Close")}})
    if(compose) AlertDialog(onDismissRequest={compose=false},title={PText("Share a locket")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {LocalPicture(draftPhoto,Modifier.fillMaxWidth().height(200.dp));OutlinedTextField(draftCaption,{draftCaption=it.take(120)},label={PText("Caption")});PText("Share this picture and caption with your partner.",style=MaterialTheme.typography.bodySmall,muted=true)}},confirmButton={Button({send("Locket",draftCaption.trim(),"",draftPhoto);compose=false},enabled=draftCaption.isNotBlank() && connection.online && !connection.busy) {PText("Share")}},dismissButton={OutlinedButton({compose=false}) {PText("Cancel")}})
}


/** One card geometry for local and paired modes; storage/sharing actions stay separate. */
@Composable private fun LocketCard(
    preferences: ai.mwm.client.data.Preferences,
    photos: List<Appreciation>,
    canAdd: Boolean,
    onGallery: () -> Unit,
    onAdd: () -> Unit,
    onPhoto: (Owner) -> Unit
) {
    val c=AppTheme.colors
    Surface(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),shape=RoundedCornerShape(24.dp),color=c.accent.copy(alpha=.08f).compositeOver(c.surface),border=androidx.compose.foundation.BorderStroke(1.dp,c.separator)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(18.dp),tint=c.textSecondary)
                PText("Our locket",Modifier.weight(1f),MaterialTheme.typography.titleSmall)
                IconButton(onGallery,Modifier.size(40.dp)) {Icon(Icons.Outlined.Collections,"View memories",Modifier.size(20.dp))}
                IconButton(onAdd,enabled=canAdd,modifier=Modifier.size(40.dp)) {Icon(Icons.Outlined.AddPhotoAlternate,"Share a locket picture",Modifier.size(20.dp))}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                listOf(Owner.Me,Owner.You).forEach {owner->
                    val latest=photos.firstOrNull {it.owner==owner}
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Surface(onClick={onPhoto(owner)},modifier=Modifier.fillMaxWidth().height(96.dp),shape=RoundedCornerShape(20.dp),color=c.surface,border=androidx.compose.foundation.BorderStroke(2.dp,c.accent.copy(alpha=.35f))) {
                            if(latest!=null) LocalPicture(latest.picture,Modifier.padding(4.dp).clip(RoundedCornerShape(15.dp)))
                            else Box(contentAlignment=Alignment.Center) {Icon(Icons.Outlined.FavoriteBorder,"No photo yet",tint=c.accent)}
                        }
                        Text(if(owner==Owner.Me) preferences.myName else preferences.partnerName,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,color=c.textPrimary)
                        latest?.takeIf {it.at>0}?.let {PText(java.time.Instant.ofEpochMilli(it.at).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM · HH:mm")),style=MaterialTheme.typography.labelSmall,muted=true)}
                    }
                }
            }
        }
    }
}

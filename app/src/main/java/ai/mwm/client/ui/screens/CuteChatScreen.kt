package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.cloud.LocalPartnerConnection
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun chatIcon(kind: String)=when(kind) {
    "Thank you","Heart"->Icons.Outlined.FavoriteBorder
    "You're the best"->Icons.Outlined.StarOutline
    "Purr-fect"->Icons.Outlined.Pets
    "CareSupport"->Icons.Outlined.LocalFlorist
    else->Icons.Outlined.ChatBubbleOutline
}

@Composable internal fun CuteChatScreen(state: PipoState,taskTitle: String,onBack: ()->Unit,onDeleteNote: (String)->Unit,onSend: (String,String,String,String,String,(String?)->Unit)->Unit) {
    val c=AppTheme.colors
    val connection=LocalPartnerConnection.current
    val p=state.preferences
    var kind by rememberSaveable(taskTitle) {mutableStateOf(if(taskTitle.isBlank()) "Message" else "Thank you")}
    var message by rememberSaveable {mutableStateOf("")}
    var picture by rememberSaveable {mutableStateOf("")}
    var draftId by rememberSaveable {mutableStateOf(newId())}
    var menu by remember {mutableStateOf(false)}
    var sending by remember {mutableStateOf(false)}
    var failure by remember {mutableStateOf<String?>(null)}
    val picker=rememberPhotoPicker {picture=it;draftId=newId()}
    val notes=state.notes.filter {it.kind!="Locket"}.sortedBy {it.at}
    val listState=rememberLazyListState()
    var firstScroll by remember {mutableStateOf(true)}
    LaunchedEffect(notes.lastOrNull()?.id) {
        if(notes.isNotEmpty()) {
            val nearBottom=(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0)>=notes.size-3
            if(firstScroll || nearBottom || notes.last().owner==Owner.Me) listState.scrollToItem(notes.lastIndex)
            firstScroll=false
        }
    }
    val canSend=!sending && !connection.busy && (!connection.connected || connection.paired && connection.online) && (kind!="Message" || message.isNotBlank() || picture.isNotBlank())
    fun send() {
        if(sending || !canSend) return
        sending=true;failure=null
        onSend(draftId,kind,message,taskTitle,picture) {error->
            sending=false
            if(error==null) {message="";picture="";draftId=newId()} else failure=error
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            IconButton(onBack) {Icon(Icons.Outlined.ArrowBack,"Back")}
            ProfilePhoto(p.partnerPhoto,false,Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {Text(p.partnerName,style=MaterialTheme.typography.titleMedium,color=c.textPrimary,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);PText(if(connection.paired) if(connection.online) "Our little chat ♡" else "Reconnecting…" else "Notes on this phone",style=MaterialTheme.typography.labelSmall,muted=true)}
            ApprovedCat(Modifier.size(46.dp,36.dp),"note")
        }
        HorizontalDivider(color=c.separator.copy(alpha=.5f))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=listState,contentPadding=PaddingValues(16.dp,16.dp,16.dp,20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(notes.isEmpty()) item {Column(Modifier.fillMaxWidth().padding(vertical=36.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {ApprovedCat(Modifier.size(116.dp,80.dp),"care");PText("A little hello goes a long way",style=MaterialTheme.typography.titleSmall);PText("Send a message or a tiny thank-you ♡",style=MaterialTheme.typography.bodySmall,muted=true)}}
            items(notes,key={it.id}) {note->
                val mine=note.owner==Owner.Me
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.align(if(mine) Alignment.CenterEnd else Alignment.CenterStart).fillMaxWidth(.87f),horizontalAlignment=if(mine) Alignment.End else Alignment.Start,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Surface(shape=if(mine) RoundedCornerShape(20.dp,20.dp,6.dp,20.dp) else RoundedCornerShape(20.dp,20.dp,20.dp,6.dp),color=(if(mine)c.primary else c.accent).copy(alpha=.18f).compositeOver(c.surface),border=BorderStroke(1.dp,c.separator.copy(alpha=.55f))) {
                            Column(Modifier.fillMaxWidth().padding(14.dp,12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                if(note.kind!="Message") Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {Icon(chatIcon(note.kind),null,Modifier.size(18.dp),tint=c.textSecondary);PText(if(note.kind=="Heart") "A little love" else note.kind,Modifier.weight(1f),MaterialTheme.typography.titleSmall)}
                                if(note.message.isNotBlank()) PText(note.message,preserveContent=true)
                                if(note.picture.isNotBlank()) LocalPicture(note.picture,Modifier.fillMaxWidth().height(172.dp).clip(RoundedCornerShape(14.dp)))
                                chatContext(note,state)?.let {label->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {Icon(if(note.kind=="Heart") Icons.Outlined.Image else Icons.Outlined.AttachFile,null,Modifier.size(14.dp));PText(label,Modifier.weight(1f),MaterialTheme.typography.labelSmall,true,preserveContent=true)}}
                                if(note.sample) TextButton({onDeleteNote(note.id)},contentPadding=PaddingValues(0.dp)) {PText("Delete example",style=MaterialTheme.typography.labelSmall)}
                            }
                        }
                        Row(Modifier.padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            PText(Instant.ofEpochMilli(note.at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm",Locale.getDefault())),style=MaterialTheme.typography.labelSmall,muted=true)
                            if(mine && !note.sample) {Icon(Icons.Outlined.Check,null,Modifier.size(13.dp),tint=c.textSecondary);PText(if(connection.connected) "Shared" else "Saved here",style=MaterialTheme.typography.labelSmall,muted=true)}
                        }
                    }
                }
            }
        }
        Surface(color=c.surfaceElevated,shape=RoundedCornerShape(24.dp,24.dp,0.dp,0.dp),shadowElevation=5.dp) {
            Column(Modifier.fillMaxWidth().padding(14.dp,12.dp,14.dp,14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(taskTitle.isNotBlank() && runCatching {java.util.UUID.fromString(taskTitle)}.isFailure) PText("For $taskTitle",style=MaterialTheme.typography.labelSmall,muted=true,preserveContent=true)
                if(kind!="Message") PText(kind,Modifier.padding(start=4.dp),MaterialTheme.typography.labelSmall,true)
                if(picture.isNotBlank()) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {LocalPicture(picture,Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)));TextButton({picture="";draftId=newId()},enabled=!sending) {PText("Remove photo")}}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box {
                        FilledTonalIconButton({menu=true},Modifier.size(48.dp),enabled=!sending,shape=CircleShape) {Icon(chatIcon(kind),"Choose message or thank-you style")}
                        DropdownMenu(menu,{menu=false}) {listOf("Message","Thank you","You're the best","Purr-fect").forEach {value->DropdownMenuItem(text={PText(value)},leadingIcon={Icon(chatIcon(value),null,Modifier.size(20.dp))},trailingIcon={if(kind==value) Icon(Icons.Outlined.Check,null,Modifier.size(18.dp))},onClick={kind=value;draftId=newId();menu=false})}}
                    }
                    OutlinedTextField(message,{message=it.take(240);draftId=newId()},Modifier.weight(1f),enabled=!sending,placeholder={PText(if(kind=="Message") "A little message…" else "Add a note…")},maxLines=4,shape=RoundedCornerShape(22.dp),leadingIcon={IconButton(picker,enabled=!sending) {Icon(Icons.Outlined.AddPhotoAlternate,"Add a picture",Modifier.size(20.dp))}})
                    FilledIconButton({send()},Modifier.size(48.dp),enabled=canSend,shape=CircleShape) {if(sending) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Icon(Icons.Outlined.Send,"Send ${if(kind=="Message") "message" else "thank-you"}",Modifier.size(20.dp))}
                }
                failure?.let {PText(it,Modifier.padding(horizontal=4.dp),MaterialTheme.typography.bodySmall)}
                if(!connection.connected || !connection.paired || !connection.online) PText(if(!connection.connected) "Saved on this phone until you connect a partner." else if(!connection.paired) "Waiting for your partner to join." else "Offline · Your draft stays here.",Modifier.padding(horizontal=4.dp),MaterialTheme.typography.labelSmall,true)
            }
        }
    }
}

package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.cloud.LocalPartnerConnection
import ai.mwm.client.cloud.PipoFirebase
import ai.mwm.client.cloud.NoMembership
import ai.mwm.client.cloud.SignInRequired
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
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

@Composable internal fun CuteChatScreen(state: PipoState,taskTitle: String,onBack: ()->Unit,onDeleteNote: (String)->Unit,onSend: (String,String,String,String,String,String,(String?)->Unit)->Unit) {
    val c=AppTheme.colors
    val connection=LocalPartnerConnection.current
    val currentConnection by rememberUpdatedState(connection)
    val p=state.preferences
    val context=LocalContext.current
    val keyboard=LocalSoftwareKeyboardController.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    val scope=rememberCoroutineScope()
    val owner=AccountScope.current(context)
    val inspection=LocalInspectionMode.current
    val storage=remember(owner,connection.spaceId,inspection) {
        if(inspection)ChatConversationStore.preview(context,if(taskTitle.isBlank())"Message" else "Thank you")
        else ChatConversationStore.open(context,owner,connection.spaceId,if(taskTitle.isBlank())"Message" else "Thank you")
    }
    val stored by storage.state.collectAsState()
    LaunchedEffect(storage,stored.ready,taskTitle) {
        if(stored.ready)storage.editDraft {d->
            if(d.text.isEmpty() && d.picture.isEmpty() && d.replyTo.isEmpty())
                d.copy(kind=if(taskTitle.isBlank())"Message" else "Thank you") else d
        }
    }
    val draft=stored.data.draft
    val pending=stored.data.pending
    val sending=stored.sending
    val kind=draft.kind
    val picture=draft.picture
    val replyTo=draft.replyTo
    var messageDraft by rememberSaveable(owner,connection.spaceId,stateSaver=TextFieldValue.Saver) {mutableStateOf(TextFieldValue(draft.text))}
    LaunchedEffect(draft.text) {
        if(storage.state.value.data.draft.text==draft.text && messageDraft.text!=draft.text)
            messageDraft=TextFieldValue(draft.text,TextRange(draft.text.length))
    }
    fun editDraft(transform:(ChatComposerDraft)->ChatComposerDraft)=storage.editDraft {old->transform(old).copy(id=newId())}
    var reactions by remember(connection.uid,connection.spaceId) {mutableStateOf(emptyList<ChatReaction>())}
    var reactionEpoch by remember(connection.uid,connection.spaceId) {mutableIntStateOf(0)}
    var reactionBusy by remember(connection.uid,connection.spaceId) {mutableStateOf(false)}
    var reactionTarget by remember(connection.uid,connection.spaceId) {mutableStateOf<String?>(null)}
    var emojiOpen by remember(connection.uid,connection.spaceId) {mutableStateOf(false)}
    var serverNotes by remember(connection.uid,connection.spaceId) {mutableStateOf<List<Appreciation>?>(null)}
    var readIds by remember(connection.uid,connection.spaceId) {mutableStateOf(emptySet<String>())}
    var acknowledged by remember(connection.uid,connection.spaceId) {mutableStateOf(emptySet<String>())}
    var refreshKey by remember {mutableIntStateOf(0)}
    var pollTick by remember {mutableIntStateOf(0)}
    var chatStatus by remember(connection.uid,connection.spaceId) {mutableStateOf("")}
    var openPhoto by remember(connection.uid,connection.spaceId) {mutableStateOf<String?>(null)}
    LaunchedEffect(connection.uid,connection.spaceId,connection.paired,foreground,refreshKey) {
        if(!connection.paired || !foreground) return@LaunchedEffect
        val api=PipoFirebase.get(context)
        while(isActive) {
            try {
                val messages=withContext(Dispatchers.IO) {api.chatSnapshot(connection.uid,connection.spaceId)}
                serverNotes=messages
                storage.reconcile(messages)
                chatStatus=""
                try {
                    readIds=withContext(Dispatchers.IO) {api.chatReceipts(connection.uid,connection.spaceId)}
                } catch(e: CancellationException) {throw e} catch(_: Exception) {chatStatus="Read status is reconnecting…"}
                try {
                    val epoch=reactionEpoch
                    val fetched=withContext(Dispatchers.IO) {api.chatReactions(connection.uid,connection.spaceId)}
                    if(!reactionBusy && epoch==reactionEpoch) reactions=fetched
                } catch(e:CancellationException) {throw e} catch(_:Exception) {chatStatus="Reactions are reconnecting…"}
                pollTick++
            } catch(e: CancellationException) {throw e} catch(e: Exception) {
                if(e is NoMembership || e is SignInRequired || e is ai.mwm.client.cloud.SupabaseFailure && e.status in listOf(401,403)) {serverNotes=emptyList();readIds=emptySet();reactions=emptyList();reactionTarget=null;openPhoto=null}
                chatStatus="Chat reconnecting…"
            }
            delay(if(chatStatus=="Chat reconnecting…") 3000 else 1500)
        }
    }
    val message=messageDraft.text
    var menu by remember {mutableStateOf(false)}
    var reactionFailure by remember(connection.uid,connection.spaceId) {mutableStateOf<String?>(null)}
    val failure=stored.error ?: reactionFailure
    val picker=rememberPhotoPicker {photo->editDraft {it.copy(picture=photo)}}
    LaunchedEffect(storage,state.notes,connection.connected) {
        if(!connection.connected)storage.reconcile(state.notes)
    }
    val notes=mergeChatNotes((serverNotes ?: state.notes).filter {it.kind!="Locket"},pending)
    val listState=rememberLazyListState()
    var firstScroll by remember(connection.uid,connection.spaceId) {mutableStateOf(true)}
    LaunchedEffect(notes.lastOrNull()?.id) {
        if(notes.isNotEmpty()) {
            val nearBottom=(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0)>=notes.size-3
            if(firstScroll || nearBottom || notes.last().owner==Owner.Me) listState.scrollToItem(notes.lastIndex)
            firstScroll=false
        }
    }
    val canSend=stored.ready && !sending && !connection.busy && (!connection.connected || connection.paired && connection.online) && (kind!="Message" || message.isNotBlank() || picture.isNotBlank())
    fun submit(note: Appreciation) {
        if(sending || !stored.ready)return
        reactionFailure=null
        storage.submit(note) {queued,done->onSend(queued.id,queued.kind,queued.message,queued.taskTitle,queued.picture,queued.replyTo,done)}
    }
    fun send() {
        if(!canSend)return
        // Read one current store snapshot: a last keystroke may precede recomposition.
        val current=storage.state.value.data.draft
        if(current.kind=="Message" && current.text.isBlank() && current.picture.isBlank())return
        submit(Appreciation(id=current.id,kind=current.kind,message=current.text.trim(),taskTitle=taskTitle,picture=current.picture,replyTo=current.replyTo))
    }
    val visibleIds by remember {
        derivedStateOf {listState.layoutInfo.visibleItemsInfo.mapNotNull {it.key as? String}.toSet()}
    }
    LaunchedEffect(visibleIds,foreground,pollTick,connection.uid,connection.spaceId,openPhoto,emojiOpen,reactionTarget,menu) {
        if(!foreground || !connection.paired || serverNotes==null || openPhoto!=null || emojiOpen || reactionTarget!=null || menu) return@LaunchedEffect
        val incoming=serverNotes.orEmpty().filter {it.owner==Owner.You && !it.sample && it.id in visibleIds && it.id !in acknowledged}.map {it.id}.toSet()
        if(incoming.isNotEmpty()) {
            // Local unread badges, notification clearing and server Seen share one visibility rule.
            if(owner==AccountScope.current(context) && currentConnection.spaceId==connection.spaceId) {
                ChatReadState.mark(context,connection.spaceId,incoming)
                val manager=context.getSystemService(android.app.NotificationManager::class.java)
                incoming.forEach {manager.cancel("partner_note",it.hashCode())}
            }
            try {
                withContext(Dispatchers.IO) {PipoFirebase.get(context).markChatRead(connection.uid,connection.spaceId,incoming)}
                acknowledged=acknowledged+incoming
            } catch(e: CancellationException) {throw e} catch(_: Exception) { /* Retry on the next successful refresh. */ }
        }
    }
    fun react(messageId:String,chosen:String) {
        if(reactionBusy || !connection.paired || !connection.online) return
        val account=connection.uid;val space=connection.spaceId
        val desired=nextChatReaction(reactions.find {it.messageId==messageId && it.userId==account}?.emoji,chosen)
        reactionEpoch++;reactionBusy=true;reactionFailure=null;reactionTarget=null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {PipoFirebase.get(context).setChatReaction(account,space,messageId,desired)}
                if(currentConnection.uid==account && currentConnection.spaceId==space) {
                    reactions=reactions.filterNot {it.messageId==messageId && it.userId==account}+
                        if(desired.isEmpty()) emptyList() else listOf(ChatReaction(messageId,account,desired))
                }
            } catch(e:CancellationException) {throw e} catch(_:Exception) {
                if(currentConnection.uid==account && currentConnection.spaceId==space) reactionFailure="Couldn't confirm the reaction. Check your connection and try again."
            } finally {
                if(currentConnection.uid==account && currentConnection.spaceId==space) {reactionEpoch++;reactionBusy=false;refreshKey++}
            }
        }
    }
    if(emojiOpen) ChatEmojiPicker("A little emoji ♡",onDismiss={emojiOpen=false}) {emoji->
        val inserted=insertChatEmoji(message,messageDraft.selection.start,messageDraft.selection.end,emoji)
        if(inserted!=null) {messageDraft=TextFieldValue(inserted.text,TextRange(inserted.cursor));editDraft {it.copy(text=inserted.text)};reactionFailure=null}
        else reactionFailure="Your message is full. Make a little room for the emoji."
        emojiOpen=false
    }
    reactionTarget?.let {id->ChatEmojiPicker("React with a little feeling",reactions.find {it.messageId==id && it.userId==connection.uid}?.emoji ?: "",{reactionTarget=null}) {react(id,it)}}
    openPhoto?.let {ChatPhotoViewer(it) {openPhoto=null}}
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            IconButton(onBack) {Icon(Icons.Outlined.ArrowBack,"Back")}
            ProfilePhoto(p.partnerPhoto,false,Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {Text(p.partnerName,style=MaterialTheme.typography.titleMedium,color=c.textPrimary,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);PText(if(chatStatus.isNotBlank()) chatStatus else if(connection.paired) if(connection.online) "Our little chat ♡" else "Reconnecting…" else "Notes on this phone",style=MaterialTheme.typography.labelSmall,muted=true)}
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
                                if(note.replyTo.isNotBlank()) {
                                    val quoted=notes.find {it.id==note.replyTo}
                                    Surface(shape=RoundedCornerShape(10.dp),color=c.surface.copy(alpha=.7f)) {Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                        PText(quoted?.let {if(it.owner==Owner.Me) p.myName else p.partnerName} ?: "Reply",style=MaterialTheme.typography.labelSmall,muted=true,preserveContent=true)
                                        Text(quoted?.message?.ifBlank {"Photo"} ?: "Original message unavailable",maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=c.textSecondary)
                                    }}
                                }
                                if(note.message.isNotBlank()) {
                                    if(CHAT_EMOJI_CHOICES.any {it.glyph==note.message.trim()}) FlatChatEmoji(note.message.trim(),Modifier.size(44.dp),note.message.trim())
                                    else PText(note.message,preserveContent=true)
                                }
                                if(note.picture.isNotBlank()) LocalPicture(note.picture,Modifier.fillMaxWidth().height(172.dp).clip(RoundedCornerShape(14.dp)).clickable(onClickLabel="Open picture") {openPhoto=note.picture})
                                chatContext(note,state)?.let {label->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {Icon(if(note.kind=="Heart") Icons.Outlined.Image else Icons.Outlined.AttachFile,null,Modifier.size(14.dp));PText(label,Modifier.weight(1f),MaterialTheme.typography.labelSmall,true,preserveContent=true)}}
                                if(note.sample) TextButton({onDeleteNote(note.id)},contentPadding=PaddingValues(0.dp)) {PText("Delete example",style=MaterialTheme.typography.labelSmall)}
                            }
                        }
                        val noteReactions=reactions.filter {it.messageId==note.id}.groupBy {it.emoji}
                        if(noteReactions.isNotEmpty()) Row(Modifier.padding(horizontal=4.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            noteReactions.forEach {(emoji,people)->
                                val selected=people.any {it.userId==connection.uid}
                                FilterChip(selected=selected,onClick={react(note.id,emoji)},enabled=!reactionBusy && connection.paired && connection.online,
                                    label={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {FlatChatEmoji(emoji,Modifier.size(18.dp),emoji);Text(people.size.toString())}},modifier=Modifier.heightIn(min=40.dp))
                            }
                        }
                        Row(Modifier.padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            PText(Instant.ofEpochMilli(note.at).atZone(clockZone(p.clockZone)).format(DateTimeFormatter.ofPattern("HH:mm",Locale.getDefault())),style=MaterialTheme.typography.labelSmall,muted=true)
                            TextButton({editDraft {it.copy(replyTo=note.id)}},enabled=!sending && !note.sample && (!connection.connected || serverNotes.orEmpty().any {it.id==note.id}),contentPadding=PaddingValues(horizontal=4.dp)) {Icon(Icons.Outlined.Reply,null,Modifier.size(16.dp));Spacer(Modifier.width(3.dp));Text("Reply",style=MaterialTheme.typography.labelSmall)}
                            IconButton({keyboard?.hide();reactionTarget=note.id},Modifier.size(40.dp),enabled=!reactionBusy && connection.paired && connection.online && serverNotes.orEmpty().any {it.id==note.id} && !note.sample) {Icon(Icons.Outlined.AddReaction,"React to message",Modifier.size(18.dp),tint=c.textSecondary)}
                        }
                        if(mine && !note.sample) {
                            Row(Modifier.padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                val delivery=chatDelivery(note,pending[note.id],readIds,connection.connected)
                                Icon(when(delivery) {"Sending"->Icons.Outlined.Schedule;"Seen"->Icons.Outlined.DoneAll;"Failed · Retry"->Icons.Outlined.ErrorOutline;else->Icons.Outlined.Check},delivery,Modifier.size(14.dp),tint=if(delivery=="Seen") c.primary else c.textSecondary)
                                PText(delivery,style=MaterialTheme.typography.labelSmall,muted=delivery!="Seen")
                                if(pending[note.id]?.state=="Failed") TextButton({submit(note)},enabled=!sending,contentPadding=PaddingValues(0.dp)) {PText("Retry",style=MaterialTheme.typography.labelSmall)}
                            }
                        }
                    }
                }
            }
        }
        Surface(color=c.surfaceElevated,shape=RoundedCornerShape(24.dp,24.dp,0.dp,0.dp),shadowElevation=5.dp) {
            Column(Modifier.fillMaxWidth().padding(14.dp,12.dp,14.dp,14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(replyTo.isNotBlank()) {
                    val quoted=notes.find {it.id==replyTo}
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Outlined.Reply,null,Modifier.size(18.dp),tint=c.textSecondary);Column(Modifier.weight(1f).padding(horizontal=8.dp)) {PText("Replying to "+(quoted?.let {if(it.owner==Owner.Me)p.myName else p.partnerName} ?: "message"),style=MaterialTheme.typography.labelSmall,muted=true);Text(quoted?.message?.ifBlank {"Photo"} ?: "Original message unavailable",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=c.textPrimary)};IconButton({editDraft {it.copy(replyTo="")}},Modifier.size(32.dp),enabled=!sending) {Icon(Icons.Outlined.Close,"Cancel reply",Modifier.size(18.dp))}}
                }
                if(taskTitle.isNotBlank() && runCatching {java.util.UUID.fromString(taskTitle)}.isFailure) PText("For $taskTitle",style=MaterialTheme.typography.labelSmall,muted=true,preserveContent=true)
                if(kind!="Message") PText(kind,Modifier.padding(start=4.dp),MaterialTheme.typography.labelSmall,true)
                if(picture.isNotBlank()) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {LocalPicture(picture,Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).clickable {openPhoto=picture});TextButton({editDraft {it.copy(picture="")}},enabled=!sending) {PText("Remove photo")}}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    Box {
                        FilledTonalIconButton({menu=true},Modifier.size(40.dp),enabled=!sending,shape=CircleShape) {Icon(chatIcon(kind),"Choose message or thank-you style")}
                        DropdownMenu(menu,{menu=false}) {listOf("Message","Thank you","You're the best","Purr-fect").forEach {value->DropdownMenuItem(text={PText(value)},leadingIcon={Icon(chatIcon(value),null,Modifier.size(20.dp))},trailingIcon={if(kind==value) Icon(Icons.Outlined.Check,null,Modifier.size(18.dp))},onClick={editDraft {it.copy(kind=value)};menu=false})}}
                    }
                    OutlinedIconButton(picker,Modifier.size(40.dp),enabled=!sending) {Icon(Icons.Outlined.AddPhotoAlternate,"Add a picture",Modifier.size(20.dp))}
                    // Keep the editor enabled during sending so its keyboard/focus is not discarded.
                    OutlinedTextField(messageDraft,{val text=limitChatText(it.text);val changed=text!=messageDraft.text;messageDraft=it.copy(text=text,selection=TextRange(it.selection.start.coerceAtMost(text.length),it.selection.end.coerceAtMost(text.length)));if(changed){editDraft {d->d.copy(text=text)};reactionFailure=null}},Modifier.weight(1f),enabled=stored.ready,placeholder={PText(if(kind=="Message") "A little message…" else "Add a note…")},maxLines=4,shape=RoundedCornerShape(22.dp),trailingIcon={IconButton({keyboard?.hide();emojiOpen=true},enabled=!sending) {Icon(Icons.Outlined.SentimentSatisfiedAlt,"Add emoji",Modifier.size(20.dp))}})
                    FilledIconButton({send()},Modifier.size(40.dp),enabled=canSend,shape=CircleShape) {if(sending) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Icon(Icons.Outlined.Send,"Send ${if(kind=="Message") "message" else "thank-you"}",Modifier.size(20.dp))}
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    if(reactionBusy) PText("Saving reaction…",style=MaterialTheme.typography.labelSmall,muted=true)
                }
                failure?.let {PText(it,Modifier.padding(horizontal=4.dp),MaterialTheme.typography.bodySmall)}
                if(!connection.connected || !connection.paired || !connection.online) PText(if(!connection.connected) "Saved only on this phone in local mode." else if(!connection.paired) "Waiting for your partner to join." else "Offline · Your draft stays here.",Modifier.padding(horizontal=4.dp),MaterialTheme.typography.labelSmall,true)
            }
        }
    }
}

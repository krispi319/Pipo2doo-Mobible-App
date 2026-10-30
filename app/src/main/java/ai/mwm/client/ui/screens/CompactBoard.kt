package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import ai.mwm.client.cloud.LocalPartnerConnection
import ai.mwm.client.cloud.canEditTask
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.dp

@Composable internal fun BoardView(tasks:List<Task>,state:PipoState,modifier:Modifier,onOpen:(String)->Unit,onToggle:(Task)->Unit,onStatus:(String,TaskStatus)->Unit,canAdd:Boolean,horizontalScroll:LazyListState=rememberLazyListState(),todoScroll:LazyListState=rememberLazyListState(),doneScroll:LazyListState=rememberLazyListState(),onAdd:(TaskStatus)->Unit) {
    // Placement is gesture bookkeeping, not UI state. Scrolling must not restart handlers.
    val bounds=remember {mutableMapOf<TaskStatus,Rect>()}
    val latestStatus by rememberUpdatedState(onStatus)
    val c=AppTheme.colors;val fontScale=LocalDensity.current.fontScale
    BoxWithConstraints(modifier) {
        val width=compactBoardWidth(maxWidth.value,fontScale).dp
        LazyRow(Modifier.fillMaxSize(),state=horizontalScroll,contentPadding=PaddingValues(12.dp,12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            items(SIMPLE_TASK_STATUSES,key={it.name}) {status->
                DisposableEffect(status) {onDispose {bounds.remove(status)}}
                Surface(Modifier.width(width).fillMaxHeight().onGloballyPositioned {bounds[status]=it.boundsInRoot()},color=when(status){TaskStatus.Todo->c.surface;TaskStatus.Doing->c.primary.copy(alpha=.1f).compositeOver(c.surface);TaskStatus.Done->c.secondary.copy(alpha=.14f).compositeOver(c.surface)},shape=RoundedCornerShape(6.dp,6.dp,22.dp,22.dp),border=BorderStroke(1.dp,c.separator),shadowElevation=2.dp) {
                    LazyColumn(state=if(status==TaskStatus.Done)doneScroll else todoScroll,contentPadding=PaddingValues(9.dp,10.dp,9.dp,16.dp),verticalArrangement=Arrangement.spacedBy(7.dp),overscrollEffect=null) {
                        item(key="header_${status.name}") {Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {Box(Modifier.size(56.dp,10.dp).background(c.surfaceElevated).border(1.dp,c.separator))};Spacer(Modifier.height(8.dp));Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {PText("${status.label} ${tasks.count {it.boardStatus()==status}}",Modifier.weight(1f),MaterialTheme.typography.titleSmall);OutlinedIconButton({onAdd(status)},enabled=canAdd,modifier=Modifier.size(40.dp)) {Icon(Icons.Outlined.Add,"Add to ${status.label}",Modifier.size(18.dp))}};DashedRule()}
                        items(tasks.filter {it.boardStatus()==status}.let {if(status==TaskStatus.Done)it.sortedByDescending {t->t.completedAt} else it},key={it.id}) {task->
                            val editable=canEditTask(task,state,LocalPartnerConnection.current)
                            val placement=remember(task.id) {arrayOf(Rect.Zero)}
                            var origin by remember {mutableStateOf(Offset.Zero)}
                            var delta by remember {mutableStateOf(Offset.Zero)}
                            var dragging by remember {mutableStateOf(false)}
                            var menu by remember {mutableStateOf(false)}
                            Surface(onClick={onOpen(task.id)},modifier=Modifier.onGloballyPositioned {placement[0]=it.boundsInRoot()}.graphicsLayer {translationX=delta.x;translationY=delta.y;rotationZ=if(dragging)2f else 0f},shape=RoundedCornerShape(15.dp),color=c.surfaceElevated,border=BorderStroke(1.dp,ownerColor(task.owner).copy(alpha=.38f))) {
                                Column(Modifier.padding(9.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                    Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                        CompactCheck(task.done,"Complete ${task.title}",ownerColor(task.owner),onClick={onToggle(task)},enabled=editable)
                                        Text(task.title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold),color=c.textPrimary,maxLines=3,overflow=TextOverflow.Ellipsis,textDecoration=if(task.done)TextDecoration.LineThrough else null)
                                        Box {
                                            IconButton({menu=true},enabled=editable,modifier=Modifier.size(40.dp).pointerInput(task.id,editable) {
                                                if(editable) detectDragGesturesAfterLongPress(
                                                    onDragStart={origin=placement[0].center;dragging=true},
                                                    onDrag={change,amount->change.consume();delta+=amount},
                                                    onDragCancel={delta=Offset.Zero;dragging=false},
                                                    onDragEnd={val target=origin+delta;bounds.entries.find {it.value.contains(target)}?.key?.let {if(it!=task.boardStatus())latestStatus(task.id,it)};delta=Offset.Zero;dragging=false}
                                                )
                                            }) {Icon(Icons.Outlined.DragIndicator,"Move task: tap for choices or hold to drag",Modifier.size(18.dp))}
                                            DropdownMenu(menu,{menu=false}) {SIMPLE_TASK_STATUSES.forEach {target->DropdownMenuItem({PText(target.label)},{menu=false;latestStatus(task.id,target)})}}
                                        }
                                    }
                                    Row(Modifier.padding(start=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {Box(Modifier.size(8.dp).background(ownerColor(task.owner),CircleShape));PText("${ownerName(task.owner,state.preferences)} · ${java.time.LocalDate.ofEpochDay(task.day).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))} · ${timeText(task.minute)}",Modifier.weight(1f),MaterialTheme.typography.labelSmall,true)}
                                    if(task.repeat!=Repeat.Once) PText(task.repeat.label,style=MaterialTheme.typography.labelSmall,muted=true)
                                    val tags=task.tags.mapNotNull {id->state.tags.find {it.id==id}?.name}
                                    if(tags.isNotEmpty()) LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {items(tags) {tag->Surface(shape=CircleShape,color=c.surfaceSunken) {Text("#$tag",Modifier.padding(7.dp,4.dp),style=MaterialTheme.typography.labelSmall,color=c.textSecondary,maxLines=1)}}}
                                }
                            }
                        }
                        item(key="add_${status.name}") {OutlinedButton({onAdd(status)},enabled=canAdd,modifier=Modifier.fillMaxWidth()) {Icon(Icons.Outlined.Add,null,Modifier.size(16.dp));Spacer(Modifier.width(4.dp));PText("Add task",style=MaterialTheme.typography.labelMedium)}}
                    }
                }
            }
        }
    }
}

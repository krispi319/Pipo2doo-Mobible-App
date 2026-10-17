package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.cloud.LocalPartnerConnection
import ai.mwm.client.cloud.canEditTask
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun UpcomingNotes(state:PipoState,onOpen:(String)->Unit,onToggle:(Task)->Unit,onEdit:(String)->Unit,onCalendar:()->Unit) {
    val c=AppTheme.colors;val today=LocalDate.now().toEpochDay()
    var selected by rememberSaveable {mutableStateOf<Long?>(null)}
    val groups=planningGroups(state.tasks,today,selected)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp,10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {PText("Upcoming",style=MaterialTheme.typography.headlineLarge);PText("${state.tasks.count {!it.done}} little plans · One note at a time",style=MaterialTheme.typography.bodySmall,muted=true)}
            FilledTonalIconButton(onCalendar,Modifier.size(48.dp),shape=RoundedCornerShape(16.dp)) {Icon(Icons.Outlined.CalendarMonth,"Open completed & missed calendar")}
        }
        LazyRow(contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            item {FilterChip(selected==null,{selected=null},label={PText("All notes")},leadingIcon={Icon(Icons.Outlined.Dashboard,null,Modifier.size(16.dp))})}
            items((-1L..13L).toList(),key={it}) {offset->val day=today+offset
                FilterChip(selected==day,{selected=if(selected==day)null else day},label={PText(when(offset){-1L->"Yesterday";0L->"Today";1L->"Tomorrow";else->LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEE d"))})},leadingIcon={Icon(Icons.Outlined.EventNote,null,Modifier.size(15.dp))})
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp,6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {Icon(Icons.Outlined.Swipe,null,Modifier.size(17.dp),tint=c.textSecondary);PText(if(selected==null) "Swipe notes · Scroll tasks inside" else dayText(selected!!),Modifier.weight(1f),MaterialTheme.typography.labelSmall,true)}
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val width=(maxWidth-36.dp).coerceIn(244.dp,360.dp).coerceAtMost(maxWidth-24.dp)
            LazyRow(Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp,10.dp,12.dp,12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                items(groups,key={it.key}) {group->
                    val tone=when(group.key){"overdue"->c.accent;"today"->c.primary;"tomorrow"->c.secondary;else->c.warning}
                    Box(Modifier.width(width).fillMaxHeight().padding(top=8.dp)) {
                        Surface(Modifier.fillMaxSize(),shape=RoundedCornerShape(6.dp,6.dp,22.dp,6.dp),color=tone.copy(alpha=.14f).compositeOver(c.surface),border=BorderStroke(1.dp,c.separator),shadowElevation=2.dp) {
                            Column(Modifier.fillMaxSize()) {
                                Row(Modifier.fillMaxWidth().padding(14.dp,20.dp,14.dp,8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {Icon(when(group.key){"overdue"->Icons.Outlined.History;"today"->Icons.Outlined.WbSunny;"tomorrow"->Icons.Outlined.LocalFlorist;else->Icons.Outlined.AutoAwesome},null,Modifier.size(22.dp));Column(Modifier.weight(1f)) {PText(if(selected!=null)dayText(selected!!) else group.title,style=MaterialTheme.typography.titleMedium);PText(if(group.key=="overdue")"A fresh chance · No stress" else "Little steps, together",style=MaterialTheme.typography.labelSmall,muted=true)};Badge(group.tasks.size.toString(),tone)}
                                DashedRule(Modifier.padding(horizontal=14.dp))
                                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(10.dp,10.dp,10.dp,116.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    if(group.tasks.isEmpty()) item {Column(Modifier.fillMaxWidth().padding(16.dp,24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {ApprovedCat(Modifier.size(88.dp,58.dp),"nap");PText("A quiet little note",style=MaterialTheme.typography.titleSmall);PText("Nothing due here",style=MaterialTheme.typography.bodySmall,muted=true)}}
                                    items(group.tasks,key={it.id}) {task->PlanningTaskCard(task,state,onOpen,onToggle,onEdit)}
                                }
                            }
                        }
                        Box(Modifier.align(Alignment.TopCenter).offset(y=(-7).dp).size(56.dp,16.dp).background(c.surfaceElevated.copy(alpha=.85f)).border(1.dp,c.separator))
                    }
                }
            }
        }
    }
}
@Composable private fun PlanningTaskCard(task:Task,state:PipoState,onOpen:(String)->Unit,onToggle:(Task)->Unit,onEdit:(String)->Unit) {
    val c=AppTheme.colors;val editable=canEditTask(task,state,LocalPartnerConnection.current)
    Surface(onClick={onOpen(task.id)},shape=RoundedCornerShape(15.dp),color=c.surface,border=BorderStroke(1.dp,c.separator)) {
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                CompactCheck(false,"Complete ${task.title}",ownerColor(task.owner),onClick={onToggle(task)},enabled=editable)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(task.title,style=MaterialTheme.typography.titleSmall,color=c.textPrimary,maxLines=3,overflow=TextOverflow.Ellipsis);PText(ownerName(task.owner,state.preferences),style=MaterialTheme.typography.labelSmall,muted=true,preserveContent=true)}
                OutlinedIconButton({onEdit(task.id)},enabled=editable,modifier=Modifier.size(40.dp)) {Icon(Icons.Outlined.Edit,"Edit ${task.title}",Modifier.size(16.dp))}
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {Icon(Icons.Outlined.EventNote,null,Modifier.size(14.dp),tint=c.textSecondary);PText("${LocalDate.ofEpochDay(task.day).format(DateTimeFormatter.ofPattern("d MMM"))} · ${timeText(task.minute)}",Modifier.weight(1f),MaterialTheme.typography.labelSmall,true);Icon(Icons.Outlined.ChevronRight,null,Modifier.size(16.dp),tint=c.textSecondary)}
            if(task.repeat!=Repeat.Once) PText(task.repeat.label,style=MaterialTheme.typography.labelSmall,muted=true)
            if(task.owner!=Owner.Me) PokeButton(state.preferences.partnerName,task)
        }
    }
}

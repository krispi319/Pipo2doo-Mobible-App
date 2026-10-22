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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun UpcomingNotes(state:PipoState,onOpen:(String)->Unit,onToggle:(Task)->Unit,onEdit:(String)->Unit,onCalendar:()->Unit) {
    val c=AppTheme.colors;val today=LocalDate.now().toEpochDay()
    var selected by rememberSaveable {mutableStateOf<Long?>(null)}
    val open=state.tasks.filterNot {it.done}.sortedWith(compareBy<Task> {it.day}.thenBy {it.minute ?: 1440}.thenBy {it.id})
    val groups=upcomingSections(open,today,selected)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,10.dp,16.dp,144.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    PText(LocalDate.ofEpochDay(today).format(DateTimeFormatter.ofPattern("EEE d MMM")),style=MaterialTheme.typography.labelSmall,muted=true)
                    PText("Upcoming",style=MaterialTheme.typography.headlineLarge)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {PText("${open.size} tasks ahead",style=MaterialTheme.typography.labelMedium,muted=true);if(open.any {it.day<today}) Badge("${open.count {it.day<today}} overdue",c.accent)}
                }
                FilledTonalIconButton(onCalendar,Modifier.size(48.dp),shape=RoundedCornerShape(16.dp)) {Icon(Icons.Outlined.CalendarMonth,"Open completed & missed calendar")}
            }
        }
        item {
            LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp),contentPadding=PaddingValues(vertical=5.dp)) {
                item {Surface(onClick={selected=null},shape=RoundedCornerShape(20.dp),color=if(selected==null)c.secondary.copy(alpha=.22f).compositeOver(c.surface) else c.surface,border=BorderStroke(1.dp,c.separator)) {Column(Modifier.width(54.dp).padding(6.dp,12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {Icon(Icons.Outlined.ViewAgenda,null,Modifier.size(22.dp));PText("All",style=MaterialTheme.typography.labelSmall)}}}
                items((-1L..20L).toList(),key={it}) {offset->val day=today+offset;val date=LocalDate.ofEpochDay(day);val due=open.filter {it.day==day}
                    Surface(onClick={selected=if(selected==day)null else day},shape=RoundedCornerShape(20.dp),color=if(selected==day)c.primary.copy(alpha=.25f).compositeOver(c.surface) else c.surface,border=BorderStroke(if(day==today)2.dp else 1.dp,if(day==today)c.secondary else c.separator)) {
                        Column(Modifier.width(54.dp).padding(5.dp,8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            PText(date.format(DateTimeFormatter.ofPattern("EEE")),style=MaterialTheme.typography.labelSmall,muted=true)
                            PText(date.dayOfMonth.toString(),style=MaterialTheme.typography.titleLarge)
                            Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) {due.map {it.owner}.distinct().forEach {owner->Box(Modifier.size(5.dp).background(ownerColor(owner),CircleShape))}}
                            PText(if(day==today)"Today" else if(due.isEmpty())" " else "${due.size} due",style=MaterialTheme.typography.labelSmall,muted=true)
                        }
                    }
                }
            }
        }
        groups.forEach {group->
            if(group.tasks.isNotEmpty()) {
                item(key="header_${group.key}") {Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {PText(group.title,Modifier.weight(1f),MaterialTheme.typography.titleLarge);group.tasks.map {it.owner}.distinct().forEach {Box(Modifier.size(6.dp).background(ownerColor(it),CircleShape))};PText("${group.tasks.size}",style=MaterialTheme.typography.labelSmall,muted=true)}}
                items(group.tasks,key={it.id}) {task->PlanningTaskCard(task,state,onOpen,onToggle,onEdit)}
            }
        }
        if(groups.all {it.tasks.isEmpty()}) item {Surface(shape=RoundedCornerShape(20.dp),color=c.surface,border=BorderStroke(1.dp,c.separator)) {Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {Icon(Icons.Outlined.EventAvailable,null,Modifier.size(32.dp),tint=c.secondary);PText("A little breathing room",style=MaterialTheme.typography.titleMedium);PText("No tasks due here",muted=true)}}}
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PlanningTaskCard(task:Task,state:PipoState,onOpen:(String)->Unit,onToggle:(Task)->Unit,onEdit:(String)->Unit) {
    val c=AppTheme.colors;val editable=canEditTask(task,state,LocalPartnerConnection.current);val tone=ownerColor(task.owner)
    Surface(onClick={onOpen(task.id)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=if(task.owner==Owner.Together)tone.copy(alpha=.07f).compositeOver(c.surface) else c.surface,border=BorderStroke(1.dp,c.separator),shadowElevation=1.dp) {
        Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(tone))
            CompactCheck(false,"Complete ${task.title}",tone,onClick={onToggle(task)},enabled=editable)
            Column(Modifier.weight(1f).padding(top=12.dp,bottom=12.dp,end=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(task.title,style=MaterialTheme.typography.titleSmall,color=c.textPrimary,maxLines=3,overflow=TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Badge(ownerName(task.owner,state.preferences),tone)
                    PText("${LocalDate.ofEpochDay(task.day).format(DateTimeFormatter.ofPattern("EEE d MMM"))} · ${timeText(task.minute)}",style=MaterialTheme.typography.labelSmall,muted=true)
                    Badge(task.repeat.label,c.surfaceSunken)
                    task.tags.mapNotNull {id->state.tags.find {it.id==id}?.name}.forEach {Badge("#$it",c.surfaceSunken)}
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(task.owner!=Owner.Me) PokeButton(state.preferences.partnerName,task)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton({onEdit(task.id)},enabled=editable,contentPadding=PaddingValues(horizontal=10.dp,vertical=4.dp)) {Icon(Icons.Outlined.Event,null,Modifier.size(14.dp));Spacer(Modifier.width(4.dp));PText("Move",style=MaterialTheme.typography.labelSmall)}
                }
            }
        }
    }
}

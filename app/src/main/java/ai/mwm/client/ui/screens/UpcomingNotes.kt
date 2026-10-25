package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.cloud.LocalPartnerConnection
import ai.mwm.client.cloud.canEditTask
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalDensity
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun UpcomingNotes(state:PipoState,onOpen:(String)->Unit,onToggle:(Task)->Unit,onEdit:(String)->Unit,onCalendar:()->Unit) {
    ApprovedDesign {
    val c=AppTheme.colors;val today=currentTaskDay(state.preferences.clockZone)
    var selected by rememberSaveable {mutableStateOf<Long?>(null)}
    val open=state.tasks.filterNot {it.done}.sortedWith(compareBy<Task> {it.day}.thenBy {it.minute ?: 1440}.thenBy {it.id})
    val groups=upcomingSections(open,today)
    val pager=rememberPagerState(initialPage=1,pageCount={groups.size})
    val scope=rememberCoroutineScope()
    val tabScroll=rememberLazyListState()
    val sectionScrolls=listOf(rememberLazyListState(),rememberLazyListState(),rememberLazyListState(),rememberLazyListState(),rememberLazyListState())
    val dayScroll=rememberLazyListState()
    LaunchedEffect(pager.currentPage) {tabScroll.animateScrollToItem((pager.currentPage-1).coerceAtLeast(0))}
    LaunchedEffect(selected) {dayScroll.scrollToItem(0)}
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp,10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        run {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    PText(LocalDate.ofEpochDay(today).format(DateTimeFormatter.ofPattern("EEE d MMM")),style=MaterialTheme.typography.labelSmall,muted=true)
                    PText("Plan",style=MaterialTheme.typography.headlineLarge)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {PText("${open.size} tasks ahead",style=MaterialTheme.typography.labelMedium,muted=true);if(open.any {it.day<today}) Badge("${open.count {it.day<today}} overdue",c.accent)}
                }
                FilledTonalIconButton(onCalendar,Modifier.size(48.dp).border(1.dp,c.separator,RoundedCornerShape(16.dp)),shape=RoundedCornerShape(16.dp)) {Icon(Icons.Outlined.CalendarMonth,"Open completed & missed calendar")}
            }
        }
        run {
            LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp),contentPadding=PaddingValues(vertical=5.dp)) {
                item {Surface(onClick={selected=null},shape=RoundedCornerShape(20.dp),color=if(selected==null)c.secondary.copy(alpha=.22f).compositeOver(c.surface) else c.surface,border=BorderStroke(1.dp,c.separator)) {Column(Modifier.width(54.dp).padding(6.dp,12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {Icon(Icons.Outlined.ViewAgenda,null,Modifier.size(22.dp));PText("All",style=MaterialTheme.typography.labelSmall)}}}
                items((-1L..20L).toList(),key={it}) {offset->val day=today+offset;val date=LocalDate.ofEpochDay(day);val due=open.filter {it.day==day}
                    Surface(onClick={selected=if(selected==day)null else day},shape=RoundedCornerShape(20.dp),color=if(selected==day)c.primary.copy(alpha=.25f).compositeOver(c.surface) else if(day==today)c.secondary.copy(alpha=.2f).compositeOver(c.surface) else c.surface,border=BorderStroke(if(day==today)2.dp else 1.dp,c.separator)) {
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
        }
        if(selected==null) {
            LazyRow(state=tabScroll,contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                items(groups.indices.toList(),key={groups[it].key}) {index->
                    val group=groups[index]
                    FilterChip(selected=pager.currentPage==index,onClick={scope.launch {pager.animateScrollToPage(index)}},label={PText("${group.title} · ${group.tasks.size}")},border=BorderStroke(1.dp,c.separator),colors=FilterChipDefaults.filterChipColors(containerColor=c.surface,selectedContainerColor=c.primary.copy(alpha=.5f).compositeOver(c.surface)))
                }
            }
            HorizontalPager(state=pager,modifier=Modifier.weight(1f).fillMaxWidth(),key={groups[it].key},verticalAlignment=Alignment.Top) {index->
                val group=groups[index]
                Column(Modifier.fillMaxSize()) {
                    Surface(Modifier.fillMaxWidth().padding(16.dp,8.dp),shape=RoundedCornerShape(18.dp),color=c.surfaceSunken,border=BorderStroke(1.dp,c.separator)) {
                        Box(Modifier.fillMaxWidth()) {
                            Icon(when(group.key){"overdue"->Icons.Outlined.History;"today"->Icons.Outlined.WbSunny;"tomorrow"->Icons.Outlined.LocalFlorist;else->Icons.Outlined.EventNote},null,Modifier.align(Alignment.CenterEnd).padding(end=18.dp).size(28.dp).graphicsLayer {
                                translationX=(pager.currentPage-index+pager.currentPageOffsetFraction)*24.dp.toPx()
                                alpha=.14f
                            },tint=c.textPrimary)
                            Column(Modifier.padding(12.dp,8.dp,48.dp,8.dp)) {
                                PText("${group.title} · ${group.tasks.size} · Swipe sections",style=MaterialTheme.typography.labelSmall,muted=true)
                            }
                        }
                    }
                    UpcomingTaskPane(group.tasks,state,sectionScrolls[index],Modifier.weight(1f),onOpen,onToggle,onEdit)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                PText(dayText(selected!!),Modifier.weight(1f),MaterialTheme.typography.titleMedium)
                OutlinedButton({selected=null}) {PText("All sections")}
            }
            UpcomingTaskPane(open.filter {it.day==selected},state,dayScroll,Modifier.weight(1f),onOpen,onToggle,onEdit)
        }
    }
}
}

@Composable private fun UpcomingTaskPane(tasks:List<Task>,state:PipoState,scroll:LazyListState,modifier:Modifier,onOpen:(String)->Unit,onToggle:(Task)->Unit,onEdit:(String)->Unit) {
    val c=AppTheme.colors
    LazyColumn(modifier.fillMaxWidth(),state=scroll,contentPadding=PaddingValues(16.dp,4.dp,16.dp,144.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(tasks.isEmpty()) item {Surface(shape=RoundedCornerShape(20.dp),color=c.surface,border=BorderStroke(1.dp,c.separator)) {Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {Icon(Icons.Outlined.EventAvailable,null,Modifier.size(32.dp),tint=c.textSecondary);PText("A little breathing room",style=MaterialTheme.typography.titleMedium);PText("No tasks due here",muted=true)}}}
        items(tasks,key={it.id}) {task->PlanningTaskCard(task,state,onOpen,onToggle,onEdit)}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PlanningTaskCard(task:Task,state:PipoState,onOpen:(String)->Unit,onToggle:(Task)->Unit,onEdit:(String)->Unit) {
    val c=AppTheme.colors;val editable=canEditTask(task,state,LocalPartnerConnection.current);val tone=ownerColor(task.owner)
    Surface(onClick={onOpen(task.id)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=c.surface,border=BorderStroke(1.dp,c.separator),shadowElevation=1.dp) {
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            IconButton({onToggle(task)},enabled=editable,modifier=Modifier.size(40.dp)) {Icon(Icons.Outlined.RadioButtonUnchecked,"Complete ${task.title}",Modifier.size(26.dp),tint=tone.copy(alpha=.6f).compositeOver(c.textPrimary))}
            val titleInset=with(LocalDensity.current) {((40.dp-MaterialTheme.typography.titleSmall.lineHeight.toDp())/2).coerceAtLeast(0.dp)}
            Column(Modifier.weight(1f).padding(top=titleInset),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(task.title,style=MaterialTheme.typography.titleSmall,color=c.textPrimary,maxLines=3,overflow=TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Badge(ownerName(task.owner,state.preferences),tone)
                    PText("${LocalDate.ofEpochDay(task.day).format(DateTimeFormatter.ofPattern("EEE d MMM"))} · ${timeText(task.minute)}",style=MaterialTheme.typography.labelSmall,muted=true)
                    if(task.repeat!=Repeat.Once) Badge(task.repeat.label,c.surfaceSunken)
                    task.tags.mapNotNull {id->state.tags.find {it.id==id}?.name}.forEach {Badge("#$it",c.surfaceSunken)}
                }
                if(task.owner!=Owner.Me) PokeButton(state.preferences.partnerName,task)
            }
            OutlinedIconButton({onEdit(task.id)},enabled=editable,modifier=Modifier.size(40.dp),border=BorderStroke(1.dp,c.separator),colors=IconButtonDefaults.outlinedIconButtonColors(containerColor=c.surface,contentColor=c.textPrimary)) {Icon(Icons.Outlined.EditCalendar,"Move or edit ${task.title}",Modifier.size(20.dp))}
        }
    }
}

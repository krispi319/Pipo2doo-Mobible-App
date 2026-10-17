package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.time.*
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

@Composable fun TaskCalendarScreen(state: PipoState,onBack: ()->Unit,onOpen: (String)->Unit) {
    val c=AppTheme.colors
    val today=LocalDate.now()
    var selected by rememberSaveable {mutableLongStateOf(today.toEpochDay())}
    var monthText by rememberSaveable {mutableStateOf(YearMonth.now().toString())}
    val month=YearMonth.parse(monthText)
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit) {while(true) {now=System.currentTimeMillis();delay(30_000)}}
    val completed=state.tasks.filter {completionDay(it)==selected}
    val missed=state.tasks.filter {it.day==selected && missedDeadline(it,now)}
    val planned=state.tasks.filter {it.day==selected && it !in missed && completionDay(it)!=selected}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,12.dp,16.dp,100.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Row(verticalAlignment=Alignment.CenterVertically) {OutlinedIconButton(onBack) {Icon(Icons.Outlined.ArrowBack,"Back")};Column(Modifier.weight(1f)) {PText("Our calendar",style=MaterialTheme.typography.headlineSmall);PText("Little wins, one day at a time",style=MaterialTheme.typography.bodySmall,muted=true)};PipoBrand(Modifier.size(42.dp))}}
        item {Paper(color=c.primary.copy(alpha=.08f).compositeOver(c.surface)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {OutlinedIconButton({monthText=month.minusMonths(1).toString();selected=month.minusMonths(1).atDay(1).toEpochDay()}) {Icon(Icons.Outlined.ChevronLeft,"Previous month")};PText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),Modifier.weight(1f),MaterialTheme.typography.titleMedium);OutlinedIconButton({monthText=month.plusMonths(1).toString();selected=month.plusMonths(1).atDay(1).toEpochDay()}) {Icon(Icons.Outlined.ChevronRight,"Next month")}}
            Row(Modifier.fillMaxWidth()) {listOf("M","T","W","T","F","S","S").forEach {Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {PText(it,style=MaterialTheme.typography.labelSmall,muted=true)}}}
            val first=month.atDay(1).dayOfWeek.value-1
            val cells=((first+month.lengthOfMonth()+6)/7)*7
            (0 until cells step 7).forEach {week->Row(Modifier.fillMaxWidth()) {
                (week until week+7).forEach {cell->
                    val day=cell-first+1
                    if(day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(56.dp))
                    else {
                        val date=month.atDay(day);val epoch=date.toEpochDay()
                        val green=state.tasks.any {completionDay(it)==epoch}
                        val red=state.tasks.any {it.day==epoch && missedDeadline(it,now)}
                        Column(Modifier.weight(1f).heightIn(min=56.dp).padding(2.dp).background(if(epoch==selected) c.primary.copy(alpha=.28f).compositeOver(c.surface) else c.surface,RoundedCornerShape(12.dp)).border(if(date==today || epoch==selected) 1.5.dp else 1.dp,if(date==today || epoch==selected)c.primary else c.separator.copy(alpha=.5f),RoundedCornerShape(12.dp)).clickable {selected=epoch}.semantics {this.selected=epoch==selected;contentDescription=date.toString()+(if(green) ", completed tasks" else "")+(if(red) ", missed deadline" else "")},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            PText(day.toString(),style=MaterialTheme.typography.bodyMedium)
                            Row(Modifier.height(8.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {if(green) Box(Modifier.size(5.dp).background(c.success,CircleShape));if(red) Box(Modifier.size(5.dp).background(c.error,CircleShape))}
                        }
                    }
                }
            }}
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {Text("● Completed",color=c.success,style=MaterialTheme.typography.labelSmall);Text("● Missed deadline",color=c.error,style=MaterialTheme.typography.labelSmall)}
            OutlinedButton({monthText=YearMonth.now().toString();selected=today.toEpochDay()}) {Icon(Icons.Outlined.Today,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));PText("Back to today")}
        }}
        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Completed",completed.size,c.success),Triple("Missed",missed.size,c.error),Triple("Planned",planned.size,c.primary)).forEach {(title,count,tone)->
                Surface(Modifier.weight(1f),shape=RoundedCornerShape(18.dp),color=tone.copy(alpha=.12f).compositeOver(c.surface),border=BorderStroke(1.dp,c.separator)) {Column(Modifier.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {Icon(when(title){"Completed"->Icons.Outlined.LocalFlorist;"Missed"->Icons.Outlined.FavoriteBorder;else->Icons.Outlined.EventNote},null,Modifier.size(22.dp),tint=tone);PText(count.toString(),style=MaterialTheme.typography.titleLarge);PText(title,style=MaterialTheme.typography.labelSmall)}}
            }
        }}
        item {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(19.dp));PText(dayText(selected),style=MaterialTheme.typography.titleMedium)}}
        if(completed.isNotEmpty()) item {PText("✓ Completed · ${completed.size}",style=MaterialTheme.typography.titleSmall)}
        items(completed,key={"done_${it.id}"}) {task->CalendarTaskRow(task,state,"Completed at "+Instant.ofEpochMilli(task.completedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),false) {onOpen(task.id)}}
        if(missed.isNotEmpty()) item {Text("● Missed · ${missed.size}",color=c.error,style=MaterialTheme.typography.titleSmall)}
        items(missed,key={"missed_${it.id}"}) {task->CalendarTaskRow(task,state,if(task.done) "Completed after its deadline" else "Not completed by its deadline",true) {onOpen(task.id)}}
        if(planned.isNotEmpty()) item {PText("Scheduled · ${planned.size}",style=MaterialTheme.typography.titleSmall)}
        items(planned,key={"planned_${it.id}"}) {task->CalendarTaskRow(task,state,if(task.done && task.completedAt<=0) "Completed · Date unavailable" else if(task.done) "Completed on another day" else timeText(task.minute),false) {onOpen(task.id)}}
        if(completed.isEmpty() && missed.isEmpty() && planned.isEmpty()) item {PText("A quiet day · Nothing recorded here",Modifier.padding(16.dp),muted=true)}
    }
}
@Composable private fun CalendarTaskRow(task: Task,state: PipoState,caption: String,missed: Boolean,onOpen: ()->Unit) {
    val c=AppTheme.colors
    Surface(onClick=onOpen,shape=RoundedCornerShape(16.dp),color=if(missed)c.error.copy(alpha=.08f).compositeOver(c.surface) else c.surface,border=BorderStroke(1.dp,if(missed)c.error.copy(alpha=.35f) else c.separator)) {
        Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {Icon(if(missed) Icons.Outlined.ErrorOutline else if(task.done) Icons.Outlined.CheckCircleOutline else Icons.Outlined.Schedule,null,tint=if(missed)c.error else c.textSecondary);Column(Modifier.weight(1f)) {PText(task.title,preserveContent=true);PText("${ownerName(task.owner,state.preferences)} · $caption",style=MaterialTheme.typography.bodySmall,muted=true)}}
    }
}

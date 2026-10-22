package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CycleCalendar(state:CareState,month:YearMonth,selected:Long,onMonth:(YearMonth)->Unit,onSelect:(Long)->Unit) {
    val c=AppTheme.colors;val today=LocalDate.now()
    Paper(color=c.primary.copy(alpha=.08f).compositeOver(c.surface)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            OutlinedIconButton({onMonth(month.minusMonths(1))}) {Icon(Icons.Outlined.ChevronLeft,"Previous month")}
            PText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),Modifier.weight(1f).padding(horizontal=6.dp),MaterialTheme.typography.titleMedium)
            OutlinedIconButton({onMonth(month.plusMonths(1))}) {Icon(Icons.Outlined.ChevronRight,"Next month")}
        }
        Row(Modifier.fillMaxWidth()) {listOf("M","T","W","T","F","S","S").forEach {Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {PText(it,style=MaterialTheme.typography.labelSmall,muted=true)}}}
        val first=month.atDay(1).dayOfWeek.value-1;val cells=((first+month.lengthOfMonth()+6)/7)*7
        (0 until cells step 7).forEach {week->Row(Modifier.fillMaxWidth()) {(week until week+7).forEach {cell->
            val number=cell-first+1
            if(number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(56.dp))
            else {
                val date=month.atDay(number);val day=date.toEpochDay();val entry=state.logs.find {it.day==day}
                val period=entry?.let {it.start || it.flow!="None"}==true;val estimate=state.predicted(day)
                Surface(onClick={onSelect(day)},modifier=Modifier.weight(1f).heightIn(min=56.dp).padding(2.dp).semantics {this.selected=selected==day;contentDescription=dayText(day)+(if(period)", logged period" else if(estimate)", estimated period" else "")},shape=RoundedCornerShape(12.dp),color=when {selected==day->c.primary.copy(alpha=.28f).compositeOver(c.surface);period->c.accent.copy(alpha=.24f).compositeOver(c.surface);estimate->c.accent.copy(alpha=.09f).compositeOver(c.surface);else->c.surface},border=BorderStroke(if(selected==day || date==today)1.5.dp else 1.dp,if(selected==day || date==today)c.primary else c.separator.copy(alpha=.6f))) {
                    Column(Modifier.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {PText(number.toString(),style=MaterialTheme.typography.bodyMedium);Row(Modifier.height(8.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {if(period)Box(Modifier.size(5.dp).background(c.accent,CircleShape));if(entry!=null)Box(Modifier.size(5.dp).background(c.secondary,CircleShape));if(estimate && !period)Box(Modifier.size(5.dp).border(1.dp,c.accent,CircleShape))}}
                }
            }
        }}}
        FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {Text("● Logged period",color=c.accent,style=MaterialTheme.typography.labelSmall);Text("○ Estimated",color=c.textSecondary,style=MaterialTheme.typography.labelSmall);Text("● Day log",color=c.secondary,style=MaterialTheme.typography.labelSmall)}
        OutlinedButton({onMonth(YearMonth.now());onSelect(today.toEpochDay())},contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp)) {Icon(Icons.Outlined.Today,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));PText("Back to today")}
    }
}

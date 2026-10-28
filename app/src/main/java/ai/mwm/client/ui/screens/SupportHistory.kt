package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun CareTool(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,onClick:()->Unit) {
    val c=AppTheme.colors
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        OutlinedIconButton(onClick,Modifier.size(44.dp),border=BorderStroke(1.dp,c.separator),colors=IconButtonDefaults.outlinedIconButtonColors(containerColor=c.surface)) {Icon(icon,label,Modifier.size(20.dp))}
        PText(label,style=MaterialTheme.typography.labelSmall,muted=true)
    }
}
@Composable internal fun SupportBubble(entry:Appreciation,name:String,partner:String,zone:ZoneId) {
    val c=AppTheme.colors
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=(if(entry.owner==Owner.Me)c.secondary else c.accent).copy(alpha=.15f).compositeOver(c.surface),border=BorderStroke(1.dp,c.separator.copy(alpha=.6f))) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                PText(if(entry.owner==Owner.Me)name else partner,Modifier.weight(1f),MaterialTheme.typography.labelMedium,preserveContent=true)
                if(entry.at>0)PText(Instant.ofEpochMilli(entry.at).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")),style=MaterialTheme.typography.labelSmall,muted=true)
            }
            PText(entry.message,preserveContent=true)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SupportHistorySheet(notes:List<Appreciation>,name:String,partner:String,selection:String,onClose:()->Unit) {
    val zone=clockZone(selection);val today=currentTaskDay(selection);val c=AppTheme.colors
    val groups=remember(notes,selection){supportTimeline(notes,zone)}
    var month by remember {mutableStateOf<YearMonth?>(null)}
    var chooseMonth by remember {mutableStateOf(false)}
    val months=groups.mapNotNull {it.day?.let {day->YearMonth.from(LocalDate.ofEpochDay(day))}}.distinct()
    val shown=groups.filter {month==null || it.day?.let {d->YearMonth.from(LocalDate.ofEpochDay(d))==month}==true}
    ModalBottomSheet(onDismissRequest=onClose,containerColor=c.background,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal=20.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {PText("Support history",style=MaterialTheme.typography.headlineSmall);PText("Little notes, kept by day",style=MaterialTheme.typography.bodySmall,muted=true)}
                IconButton(onClose){Icon(Icons.Outlined.Close,"Close support history")}
            }
            Box(Modifier.padding(vertical=12.dp)) {
                OutlinedButton({chooseMonth=true}) {Icon(Icons.Outlined.CalendarMonth,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));PText(month?.format(DateTimeFormatter.ofPattern("MMMM yyyy")) ?: "All months");Icon(Icons.Outlined.ExpandMore,null)}
                DropdownMenu(chooseMonth,{chooseMonth=false}) {
                    DropdownMenuItem(text={PText("All months")},onClick={month=null;chooseMonth=false})
                    months.forEach {m->DropdownMenuItem(text={PText(m.format(DateTimeFormatter.ofPattern("MMMM yyyy")))},onClick={month=m;chooseMonth=false})}
                }
            }
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                if(shown.isEmpty())item {PText("No support notes for these dates yet",Modifier.padding(20.dp),muted=true)}
                shown.forEach {group->
                    item(key="day_${group.day}") {
                        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(21.dp),tint=c.success)
                            val label=when(group.day){null->"Date unavailable";today->"Today · "+dayText(today);today-1->"Yesterday · "+dayText(today-1);else->dayText(group.day)}
                            PText(label,Modifier.weight(1f),MaterialTheme.typography.titleSmall)
                            PText("${group.entries.size} notes",style=MaterialTheme.typography.labelSmall,muted=true)
                        }
                    }
                    items(group.entries,key={"support_${it.id}"}) {SupportBubble(it,name,partner,zone)}
                }
                item {PText("Past notes stay here. Each new day starts fresh.",style=MaterialTheme.typography.labelSmall,muted=true)}
            }
        }
    }
}

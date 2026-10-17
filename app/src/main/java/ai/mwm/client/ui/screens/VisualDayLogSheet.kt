package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun VisualDayLogSheet(initial: DayLog,onClose: ()->Unit,onSave: (DayLog)->Unit,onDelete: ()->Unit) {
    var log by remember(initial.day) {mutableStateOf(initial)}
    val c=AppTheme.colors
    ModalBottomSheet(onDismissRequest=onClose,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=c.surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {PText("Day log",style=MaterialTheme.typography.titleLarge);PText(dayText(log.day),style=MaterialTheme.typography.bodySmall,muted=true)}
                IconButton(onClose) {Icon(Icons.Outlined.Close,"Close day log")}
            }
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    PeriodMarker("Period start",true,log.start,{log=log.copy(start=it)},Modifier.weight(1f))
                    PeriodMarker("Period end",false,log.end,{log=log.copy(end=it)},Modifier.weight(1f))
                }}
                item {VisualLogChoices("Flow",listOf("None" to "○","Spotting" to "·","Light" to "💧","Medium" to "💧💧","Heavy" to "💧💧💧"),setOf(log.flow),false) {log=log.copy(flow=it)}}
                item {VisualLogChoices("Cramps",listOf("None" to "☀️","Mild" to "🌤️","Moderate" to "🌧️","Severe" to "⚡"),setOf(log.cramps),false) {log=log.copy(cramps=it)}}
                item {Paper {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {Icon(Icons.Outlined.Mood,null,Modifier.size(20.dp));PText("Mood",style=MaterialTheme.typography.titleMedium)}
                    val labels=listOf("Very low","Low","Okay","Good","Great")
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(5.dp)) {labels.forEachIndexed {index,label->
                        Surface(onClick={log=log.copy(mood=if(log.mood==index) null else index)},modifier=Modifier.weight(1f).fillMaxHeight().semantics {selected=log.mood==index;contentDescription=label+" mood"},shape=RoundedCornerShape(16.dp),color=if(log.mood==index)c.accent.copy(alpha=.2f).compositeOver(c.surface) else c.surfaceSunken,border=BorderStroke(1.dp,if(log.mood==index)c.accent else c.separator)) {
                            Column(Modifier.padding(4.dp,10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)) {MoodFace(index,Modifier.size(26.dp));PText(label,style=MaterialTheme.typography.labelSmall,align=TextAlign.Center)}
                        }
                    }}
                }}
                item {VisualLogChoices("Cravings",listOf("Chocolate" to "🍫","Sweet" to "🧁","Salty" to "🥨","Spicy" to "🌶️","Fruit" to "🍓","Comfort food" to "🍜","No appetite" to "🍽️"),log.cravings,true) {value->log=log.copy(cravings=if(value in log.cravings)log.cravings-value else log.cravings+value)}}
                item {VisualLogChoices("Symptoms",listOf("Back ache" to "🩹","Headache" to "🤕","Bloating" to "🫧","Fatigue" to "🪫","Tender breasts" to "🌸","Acne" to "🪞","Nausea" to "🌊","Poor sleep" to "🌙"),log.symptoms,true) {value->log=log.copy(symptoms=if(value in log.symptoms)log.symptoms-value else log.symptoms+value)}}
                item {Paper {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {Icon(Icons.Outlined.EditNote,null,Modifier.size(20.dp));PText("A little note",style=MaterialTheme.typography.titleMedium)};OutlinedTextField(log.note,{log=log.copy(note=it.take(1000))},Modifier.fillMaxWidth(),placeholder={PText("Energy, feelings, care that helps…")},minLines=3,maxLines=6,shape=RoundedCornerShape(16.dp));PText("${log.note.length} / 1000",style=MaterialTheme.typography.labelSmall,muted=true)}}
                item {TextButton(onDelete) {Icon(Icons.Outlined.DeleteOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));PText("Delete day log")}}
            }
            Button({onSave(log)},Modifier.fillMaxWidth().padding(16.dp,10.dp).heightIn(min=48.dp),shape=CircleShape) {Icon(Icons.Outlined.Check,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));PText("Save day log")}
        }
    }
}

@Composable private fun PeriodMarker(label: String,start: Boolean,checked: Boolean,onChange: (Boolean)->Unit,modifier: Modifier) {
    val c=AppTheme.colors
    Surface(modifier,shape=RoundedCornerShape(20.dp),color=(if(checked)c.accent.copy(alpha=.18f).compositeOver(c.surface) else c.surfaceSunken),border=BorderStroke(1.dp,if(checked)c.accent else c.separator)) {
        Column(Modifier.padding(14.dp,10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Icon(if(start) Icons.Outlined.WaterDrop else Icons.Outlined.CheckCircle,null,Modifier.size(22.dp));Spacer(Modifier.weight(1f));Switch(checked,onChange,modifier=Modifier.semantics {contentDescription=label})}
            PText(label,style=MaterialTheme.typography.titleSmall)
            PText("On this date",style=MaterialTheme.typography.labelSmall,muted=true)
        }
    }
}

@Composable private fun VisualLogChoices(title: String,choices: List<Pair<String,String>>,selectedValues: Set<String>,multiple: Boolean,onSelect: (String)->Unit) {
    val c=AppTheme.colors
    Paper {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {Icon(when(title){"Flow"->Icons.Outlined.WaterDrop;"Cramps"->Icons.Outlined.Healing;"Cravings"->Icons.Outlined.Restaurant;else->Icons.Outlined.Spa},null,Modifier.size(20.dp));PText(title,Modifier.weight(1f),MaterialTheme.typography.titleMedium);if(multiple) PText("Pick any",style=MaterialTheme.typography.labelSmall,muted=true)}
        choices.chunked(3).forEach {row->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                row.forEach {(label,symbol)->
                    val chosen=label in selectedValues
                    Surface(onClick={onSelect(label)},modifier=Modifier.weight(1f).fillMaxHeight().heightIn(min=92.dp).semantics {selected=chosen;contentDescription=label;role=if(multiple) Role.Checkbox else Role.RadioButton},shape=RoundedCornerShape(17.dp),color=if(chosen)c.secondary.copy(alpha=.22f).compositeOver(c.surface) else c.surfaceSunken,border=BorderStroke(if(chosen) 1.5.dp else 1.dp,if(chosen)c.secondary else c.separator)) {
                        Column(Modifier.padding(6.dp,10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(symbol,fontSize=22.sp);PText(label,style=MaterialTheme.typography.labelMedium,align=TextAlign.Center);Box(Modifier.size(14.dp)) {if(chosen) Icon(Icons.Outlined.Check,null,Modifier.size(13.dp))}}
                    }
                }
                repeat(3-row.size) {Spacer(Modifier.weight(1f))}
            }
        }
    }
}

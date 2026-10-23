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
    ModalBottomSheet(onDismissRequest=onClose,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),sheetGesturesEnabled=false,dragHandle=null,containerColor=c.surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {PText("Day log",style=MaterialTheme.typography.titleLarge);PText(dayText(log.day),style=MaterialTheme.typography.bodySmall,muted=true)}
                IconButton(onClose) {Icon(Icons.Outlined.Close,"Close day log")}
            }
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                item {DayLogFields(log) {log=it}}
                item {OutlinedButton(onDelete,Modifier.fillMaxWidth()) {Icon(Icons.Outlined.DeleteOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));PText("Delete day log")}}
            }
            Button({onSave(log)},Modifier.fillMaxWidth().padding(16.dp,10.dp).heightIn(min=48.dp),shape=CircleShape) {Icon(Icons.Outlined.Check,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));PText("Save day log")}
        }
    }
}

@Composable internal fun DayLogFields(log:DayLog,showPeriodControls:Boolean=true,onChange:(DayLog)->Unit) {
    val c=AppTheme.colors
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(showPeriodControls) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            PeriodMarker("Period start",true,log.start,{onChange(log.copy(start=it))},Modifier.weight(1f))
            PeriodMarker("Period end",false,log.end,{onChange(log.copy(end=it))},Modifier.weight(1f))
        }
        VisualLogChoices("Flow",listOf("None" to "○","Spotting" to "·","Light" to "💧","Medium" to "💧💧","Heavy" to "💧💧💧"),setOf(log.flow),false) {onChange(log.copy(flow=it))}
        VisualLogChoices("Cramps",listOf("None" to "☀️","Mild" to "🌤️","Moderate" to "🌧️","Severe" to "⚡"),setOf(log.cramps),false) {onChange(log.copy(cramps=it))}
        Paper {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {Icon(Icons.Outlined.Mood,null,Modifier.size(20.dp));PText("Mood",style=MaterialTheme.typography.titleSmall)}
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                listOf("Very low","Low","Okay","Good","Great").forEachIndexed {index,label->
                    Surface(onClick={onChange(log.copy(mood=if(log.mood==index)null else index))},modifier=Modifier.weight(1f).fillMaxHeight().heightIn(min=64.dp).semantics {selected=log.mood==index;contentDescription=label+" mood"},shape=RoundedCornerShape(14.dp),color=if(log.mood==index)c.accent.copy(alpha=.2f).compositeOver(c.surface) else c.surfaceSunken,border=BorderStroke(1.dp,if(log.mood==index)c.accent else c.separator)) {
                        Column(Modifier.padding(3.dp,8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {MoodFace(index,Modifier.size(25.dp));PText(label,style=MaterialTheme.typography.labelSmall,align=TextAlign.Center)}
                    }
                }
            }
        }
        VisualLogChoices("Cravings",listOf("Chocolate" to "🍫","Sweet" to "🧁","Salty" to "🥨","Spicy" to "🌶️","Fruit" to "🍓","Comfort food" to "🍜","No appetite" to "🍽️"),log.cravings,true) {v->onChange(log.copy(cravings=if(v in log.cravings)log.cravings-v else log.cravings+v))}
        VisualLogChoices("Symptoms",listOf("Back ache" to "🩹","Headache" to "🤕","Bloating" to "🫧","Fatigue" to "🪫","Tender breasts" to "🌸","Acne" to "🪞","Nausea" to "🌊","Poor sleep" to "🌙"),log.symptoms,true) {v->onChange(log.copy(symptoms=if(v in log.symptoms)log.symptoms-v else log.symptoms+v))}
        Paper {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {Icon(Icons.Outlined.EditNote,null,Modifier.size(20.dp));PText("A little note",style=MaterialTheme.typography.titleSmall)};OutlinedTextField(log.note,{onChange(log.copy(note=it.take(1000)))},Modifier.fillMaxWidth(),placeholder={PText("Energy, feelings, care that helps…")},minLines=3,maxLines=6,shape=RoundedCornerShape(16.dp));PText("${log.note.length} / 1000",style=MaterialTheme.typography.labelSmall,muted=true)}
    }
}

@Composable private fun PeriodMarker(label:String,start:Boolean,checked:Boolean,onChange:(Boolean)->Unit,modifier:Modifier) {
    val c=AppTheme.colors
    Surface(onClick={onChange(!checked)},modifier=modifier.semantics {selected=checked;contentDescription=label;role=Role.Checkbox},shape=RoundedCornerShape(16.dp),color=if(checked)c.accent.copy(alpha=.18f).compositeOver(c.surface) else c.surfaceSunken,border=BorderStroke(1.dp,if(checked)c.accent else c.separator)) {
        Row(Modifier.padding(10.dp,12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Icon(if(start)Icons.Outlined.WaterDrop else Icons.Outlined.CheckCircleOutline,null,Modifier.size(22.dp),tint=c.accent)
            PText(label,Modifier.weight(1f),MaterialTheme.typography.labelLarge)
            Icon(if(checked)Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,null,Modifier.size(18.dp),tint=if(checked)c.accent else c.textSecondary)
        }
    }
}

@Composable private fun VisualLogChoices(title:String,choices:List<Pair<String,String>>,selectedValues:Set<String>,multiple:Boolean,onSelect:(String)->Unit) {
    val c=AppTheme.colors
    Surface(shape=RoundedCornerShape(18.dp),color=c.surface,border=BorderStroke(1.dp,c.separator)) {
        Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {Icon(when(title){"Flow"->Icons.Outlined.WaterDrop;"Cramps"->Icons.Outlined.Healing;"Cravings"->Icons.Outlined.Restaurant;else->Icons.Outlined.Spa},null,Modifier.size(18.dp));PText(title,Modifier.weight(1f),MaterialTheme.typography.titleSmall);if(multiple)PText("Pick any",style=MaterialTheme.typography.labelSmall,muted=true)}
            val columns=if(title=="Flow" || title=="Cramps")choices.size else 2
            choices.chunked(columns).forEach {row->Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                row.forEach {(label,symbol)->val chosen=label in selectedValues
                    Surface(onClick={onSelect(label)},modifier=Modifier.weight(1f).fillMaxHeight().heightIn(min=if(multiple)48.dp else 64.dp).semantics {selected=chosen;contentDescription=label;role=if(multiple)Role.Checkbox else Role.RadioButton},shape=RoundedCornerShape(13.dp),color=if(chosen)c.secondary.copy(alpha=.22f).compositeOver(c.surface) else c.surfaceSunken,border=BorderStroke(if(chosen)1.5.dp else 1.dp,if(chosen)c.secondary else c.separator)) {
                        if(multiple) Row(Modifier.padding(7.dp,8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {Text(symbol,fontSize=19.sp);PText(label,Modifier.weight(1f),MaterialTheme.typography.labelSmall);if(chosen)Icon(Icons.Outlined.Check,null,Modifier.size(13.dp))}
                        else Column(Modifier.padding(3.dp,7.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.height(24.dp),contentAlignment=Alignment.Center) {
                                if(title=="Flow" && label !in listOf("None","Spotting")) Row {repeat(when(label){"Light"->1;"Medium"->2;else->3}) {Icon(Icons.Outlined.WaterDrop,null,Modifier.size(11.dp,19.dp),tint=c.accent)}}
                                else Text(symbol,fontSize=20.sp)
                            }
                            PText(label,style=MaterialTheme.typography.labelSmall,align=TextAlign.Center)
                        }
                    }
                }
                repeat(columns-row.size) {Spacer(Modifier.weight(1f))}
            }}
        }
    }
}

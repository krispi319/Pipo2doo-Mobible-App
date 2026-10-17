package ai.mwm.client.ui.screens

import ai.mwm.client.data.CareState
import ai.mwm.client.data.dayText
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import java.time.LocalDate

/** Decorative shapes use the app palette so both light and dark themes stay readable. */
@Composable private fun CozyConfetti(modifier: Modifier = Modifier) {
    val c=AppTheme.colors
    Canvas(modifier.clearAndSetSemantics {}) {
        listOf(.08f to .2f,.86f to .18f,.92f to .73f,.15f to .82f).forEachIndexed {i,(x,y)->
            val p=Offset(size.width*x,size.height*y)
            val ink=if(i%2==0) c.accent.copy(alpha=.45f) else c.secondary.copy(alpha=.55f)
            drawLine(ink,p-Offset(5.dp.toPx(),0f),p+Offset(5.dp.toPx(),0f),2.dp.toPx(),StrokeCap.Round)
            drawLine(ink,p-Offset(0f,5.dp.toPx()),p+Offset(0f,5.dp.toPx()),2.dp.toPx(),StrokeCap.Round)
        }
        drawCircle(c.primary.copy(alpha=.28f),4.dp.toPx(),Offset(size.width*.76f,size.height*.85f))
        drawCircle(c.secondary.copy(alpha=.25f),6.dp.toPx(),Offset(size.width*.27f,size.height*.14f))
    }
}

@Composable fun CozyTodayHero(done: Int,total: Int,overdue: Int) {
    val c=AppTheme.colors
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),color=c.primary.copy(alpha=.15f).compositeOver(c.surface)) {
        Box {
            CozyConfetti(Modifier.matchParentSize())
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        PText("OUR LITTLE EVERYDAY",style=MaterialTheme.typography.labelSmall,muted=true)
                        PText(if(total>0 && done==total) "You did it,\nteam us!" else "Little things,\nlots of love.",style=MaterialTheme.typography.headlineSmall)
                        PText(if(overdue>0) "$overdue carried over · take your time" else "One tiny step at a time ♡",style=MaterialTheme.typography.bodySmall,muted=true)
                    }
                    Box(Modifier.size(94.dp).semantics(mergeDescendants=true) {contentDescription="$done of $total tasks completed"},contentAlignment=Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                            val stroke=8.dp.toPx();val inset=stroke/2
                            drawArc(c.surface,-90f,360f,false,Offset(inset,inset),androidx.compose.ui.geometry.Size(size.width-stroke,size.height-stroke),style=Stroke(stroke,cap=StrokeCap.Round))
                            if(total>0 && done>0) drawArc(c.secondary,-90f,360f*done/total,false,Offset(inset,inset),androidx.compose.ui.geometry.Size(size.width-stroke,size.height-stroke),style=Stroke(stroke,cap=StrokeCap.Round))
                        }
                        Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.clearAndSetSemantics {}) {PText("$done / $total",style=MaterialTheme.typography.titleLarge);PText("done",style=MaterialTheme.typography.labelSmall,muted=true)}
                    }
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.SpaceBetween) {
                    Surface(shape=RoundedCornerShape(12.dp),color=c.surface.copy(alpha=.75f)) {PText(if(total==0) "A fresh little page" else "${total-done} little things left",Modifier.padding(12.dp,8.dp),MaterialTheme.typography.labelMedium)}
                    ApprovedCat(Modifier.size(118.dp,62.dp),if(total>0 && done==total) "nap" else "stretch")
                }
            }
        }
    }
}

@Composable fun CozyCycleDashboard(records: CareState,active: Long?,owner: String,readOnly: Boolean,onMark: ()->Unit,onSettings: ()->Unit) {
    val c=AppTheme.colors
    val today=LocalDate.now().toEpochDay()
    val next=records.nextStart()
    val days=next?.let {(it-today).coerceAtLeast(0)}
    val last=records.starts.lastOrNull {it<=today}
    val cycleDay=last?.let {today-it+1}
    Surface(shape=RoundedCornerShape(30.dp),color=c.accent.copy(alpha=.14f).compositeOver(c.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                PText("$owner’s cycle",Modifier.weight(1f),MaterialTheme.typography.titleSmall,preserveContent=true)
                if(!readOnly) TextButton(onSettings) {PText("Settings",style=MaterialTheme.typography.labelMedium)}
            }
            Box(Modifier.size(222.dp),contentAlignment=Alignment.Center) {
                CozyConfetti(Modifier.matchParentSize())
                Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                    val stroke=12.dp.toPx();val inset=18.dp.toPx()
                    val bounds=androidx.compose.ui.geometry.Size(size.width-inset*2,size.height-inset*2)
                    drawArc(c.surface,-90f,360f,false,Offset(inset,inset),bounds,style=Stroke(stroke,cap=StrokeCap.Round))
                    val ratio=if(active!=null) ((today-active+1).toFloat()/records.duration).coerceIn(.05f,1f) else if(days!=null) (1f-days.toFloat()/records.estimateCycle).coerceIn(.05f,1f) else 0f
                    if(ratio>0f) drawArc(c.accent,-90f,360f*ratio,false,Offset(inset,inset),bounds,style=Stroke(stroke,cap=StrokeCap.Round))
                }
                Column(Modifier.width(162.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    PText(if(active!=null) "Period day" else if(days!=null) "Estimated period in" else "Let's get to know you",style=MaterialTheme.typography.labelMedium,align=TextAlign.Center)
                    PText(if(active!=null) "${today-active+1}" else days?.toString() ?: "♡",style=MaterialTheme.typography.displayMedium)
                    PText(if(active!=null) "Logged start" else if(days!=null) "days" else "Log your first start",style=MaterialTheme.typography.bodySmall,muted=true)
                    ApprovedCat(Modifier.size(52.dp,38.dp),"note")
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                CycleStat("Cycle day",cycleDay?.toString() ?: "—",Modifier.weight(1f))
                CycleStat("Cycle length","${records.estimateCycle} days",Modifier.weight(1f))
                CycleStat("Period length","${records.duration} days",Modifier.weight(1f))
            }
            if(!readOnly) Button(onMark,Modifier.fillMaxWidth().heightIn(min=48.dp),shape=CircleShape) {PText(if(active==null) "My period started today" else "My period ended today")}
            PText(if(next==null) "Add a period start to see your estimate." else "Next estimated start · ${dayText(next)}\nEstimates may vary from your actual dates.",style=MaterialTheme.typography.bodySmall,muted=true,align=TextAlign.Center)
        }
    }
}

@Composable private fun CycleStat(label: String,value: String,modifier: Modifier) {
    Surface(modifier,shape=RoundedCornerShape(16.dp),color=AppTheme.colors.surface.copy(alpha=.8f)) {
        Column(Modifier.padding(8.dp,12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {PText(value,style=MaterialTheme.typography.titleSmall,align=TextAlign.Center);PText(label,style=MaterialTheme.typography.labelSmall,muted=true,align=TextAlign.Center)}
    }
}

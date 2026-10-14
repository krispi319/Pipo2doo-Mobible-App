package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun TodayRings(p: Preferences,tasks: List<Task>,filter: String,onFilter: (String)->Unit) {
    val c=AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(20.dp,8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(184.dp,104.dp)) {
            listOf(Owner.Me,Owner.You).forEach {owner ->
                val color=ownerColor(owner)
                val owned=tasks.filter {it.owner==owner}
                val progress=if(owned.isEmpty()) 0f else owned.count {it.done}.toFloat()/owned.size
                Box(Modifier.align(if(owner==Owner.Me) Alignment.CenterStart else Alignment.CenterEnd).size(104.dp).selectable(filter==owner.label,role=Role.RadioButton) {onFilter(owner.label)}.semantics {contentDescription="Show ${ownerName(owner,p)} tasks, ${owned.count {it.done}} of ${owned.size} done"}) {
                    ProfilePhoto(if(owner==Owner.Me) p.myPhoto else p.partnerPhoto,owner==Owner.Me,Modifier.fillMaxSize().padding(8.dp),if(owner==Owner.Me) p.avatar else -1)
                    Canvas(Modifier.fillMaxSize()) {
                        val inset=3.dp.toPx()
                        drawCircle(c.surfaceSunken,size.width/2-inset,style=Stroke(5.dp.toPx()))
                        drawArc(color,-90f,360f*progress,false,Offset(inset,inset),Size(size.width-2*inset,size.height-2*inset),style=Stroke(5.dp.toPx(),cap=StrokeCap.Round))
                    }
                }
            }
            Surface(Modifier.align(Alignment.Center),shape=CircleShape,color=c.surfaceElevated,border=BorderStroke(1.5.dp,c.secondary),shadowElevation=2.dp) {Column(Modifier.padding(10.dp,6.dp),horizontalAlignment=Alignment.CenterHorizontally) {PText(LocalDate.now().dayOfMonth.toString(),style=MaterialTheme.typography.titleLarge);PText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMM",Locale.ENGLISH)),style=MaterialTheme.typography.labelSmall)}}
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {PText(p.myName,style=MaterialTheme.typography.titleMedium);PText("${tasks.count {it.owner==Owner.Me&&it.done}} of ${tasks.count {it.owner==Owner.Me}} done",style=MaterialTheme.typography.bodySmall,muted=true)}
            Column(horizontalAlignment=Alignment.CenterHorizontally) {Badge("${tasks.count {it.owner==Owner.Together&&it.done}}/${tasks.count {it.owner==Owner.Together}} Together",c.secondary);PText("${tasks.count {it.done}} of ${tasks.size} overall",style=MaterialTheme.typography.labelSmall,muted=true)}
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {PText(p.partnerName,style=MaterialTheme.typography.titleMedium);PText("${tasks.count {it.owner==Owner.You&&it.done}} of ${tasks.count {it.owner==Owner.You}} done",style=MaterialTheme.typography.bodySmall,muted=true)}
        }
    }
}

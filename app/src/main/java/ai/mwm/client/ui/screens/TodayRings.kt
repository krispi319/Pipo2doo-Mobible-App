package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable fun TodayRings(p:Preferences,tasks:List<Task>,filter:String,onClock:(Owner)->Unit={},onFilter:(String)->Unit) {
    val c=AppTheme.colors
    var tick by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit) {while(true) {tick=System.currentTimeMillis();delay(30_000)}}
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            listOf(Owner.Me,Owner.You).forEach {owner->
                val color=ownerColor(owner)
                val owned=tasks.filter {it.owner==owner}
                val done=owned.count {it.done}
                val progress=if(owned.isEmpty())0f else done.toFloat()/owned.size
                val zone=if(owner==Owner.Me)clockZone(p.clockZone) else partnerClockZone(p)
                val time=zone?.let {Instant.ofEpochMilli(tick).atZone(it)}
                Surface(onClick={onFilter(owner.label)},modifier=Modifier.weight(1f).fillMaxHeight().semantics {selected=filter==owner.label},shape=RoundedCornerShape(20.dp),color=color.copy(alpha=.08f).compositeOver(c.surface),border=BorderStroke(if(filter==owner.label)2.dp else 1.dp,c.separator)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(40.dp)) {
                                ProfilePhoto(if(owner==Owner.Me)p.myPhoto else p.partnerPhoto,owner==Owner.Me,Modifier.fillMaxSize().padding(4.dp),if(owner==Owner.Me)p.avatar else -1)
                                Canvas(Modifier.fillMaxSize()) {
                                    val inset=2.dp.toPx()
                                    drawCircle(c.surfaceSunken,size.width/2-inset,style=Stroke(2.dp.toPx()))
                                    drawArc(color,-90f,360f*progress,false,Offset(inset,inset),Size(size.width-2*inset,size.height-2*inset),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
                                }
                            }
                            Text(ownerName(owner,p),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=c.textPrimary,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Surface(onClick={onClock(owner)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).semantics {contentDescription="Set ${ownerName(owner,p)} clock timezone"},shape=RoundedCornerShape(12.dp),color=c.surfaceElevated,border=BorderStroke(1.dp,c.separator)) {
                            Column(Modifier.padding(horizontal=8.dp,vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                BasicText(time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--",Modifier.fillMaxWidth(),
                                    style=MaterialTheme.typography.titleLarge.copy(color=c.textPrimary,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center),
                                    maxLines=1,softWrap=false,autoSize=TextAutoSize.StepBased(minFontSize=14.sp,maxFontSize=22.sp,stepSize=1.sp))
                                Text(zone?.id?.substringAfterLast('/')?.replace('_',' ') ?: "Set partner clock",style=MaterialTheme.typography.labelSmall,color=c.textSecondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                        }
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.CheckCircleOutline,null,Modifier.size(14.dp),tint=color)
                            PText("$done/${owned.size} done",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
                            Icon(Icons.Outlined.ChevronRight,null,Modifier.size(14.dp),tint=c.textSecondary)
                        }
                    }
                }
            }
        }
        Surface(onClick={onFilter(Owner.Together.label)},shape=RoundedCornerShape(14.dp),color=c.secondary.copy(alpha=.14f).compositeOver(c.surface),border=BorderStroke(1.dp,c.separator),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics {selected=filter==Owner.Together.label}) {
            Row(Modifier.padding(horizontal=12.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(18.dp))
                PText("Together · ${tasks.count {it.owner==Owner.Together && it.done}}/${tasks.count {it.owner==Owner.Together}}",Modifier.weight(1f),MaterialTheme.typography.labelMedium)
                PText("${tasks.count {it.done}}/${tasks.size} overall",style=MaterialTheme.typography.labelSmall,muted=true)
                Icon(Icons.Outlined.ChevronRight,null,Modifier.size(16.dp))
            }
        }
    }
}

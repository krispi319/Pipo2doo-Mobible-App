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
    Column(Modifier.fillMaxWidth().padding(16.dp,8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(Owner.Me,Owner.You).forEach {owner->
                val color=ownerColor(owner);val owned=tasks.filter {it.owner==owner}
                val done=owned.count {it.done};val progress=if(owned.isEmpty())0f else done.toFloat()/owned.size
                val zone=if(owner==Owner.Me)clockZone(p.clockZone) else partnerClockZone(p)
                val time=zone?.let {Instant.ofEpochMilli(tick).atZone(it)}
                Surface(onClick={onFilter(owner.label)},modifier=Modifier.weight(1f).semantics {selected=filter==owner.label},shape=RoundedCornerShape(22.dp),color=color.copy(alpha=.08f).compositeOver(c.surface),border=BorderStroke(if(filter==owner.label)2.dp else 1.dp,c.separator)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val stacked=maxWidth<150.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.25f
                            if(stacked) Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(56.dp)) {
                                ProfilePhoto(if(owner==Owner.Me)p.myPhoto else p.partnerPhoto,owner==Owner.Me,Modifier.fillMaxSize().padding(6.dp),if(owner==Owner.Me)p.avatar else -1)
                                Canvas(Modifier.fillMaxSize()) {val inset=3.dp.toPx();drawCircle(c.surfaceSunken,size.width/2-inset,style=Stroke(3.dp.toPx()));drawArc(color,-90f,360f*progress,false,Offset(inset,inset),Size(size.width-2*inset,size.height-2*inset),style=Stroke(3.dp.toPx(),cap=StrokeCap.Round))}
                            }
                            Surface(onClick={onClock(owner)},modifier=Modifier.widthIn(max=96.dp).fillMaxWidth().height(48.dp).semantics {contentDescription="Set ${ownerName(owner,p)} clock timezone"},shape=RoundedCornerShape(14.dp),color=c.surfaceElevated,border=BorderStroke(1.dp,c.separator)) {
                                Box(Modifier.fillMaxSize().padding(horizontal=5.dp,vertical=8.dp),contentAlignment=Alignment.Center) {
                                    BasicText(time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--",
                                        modifier=Modifier.fillMaxWidth(),
                                        style=MaterialTheme.typography.titleLarge.copy(color=c.textPrimary,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center),
                                        maxLines=1,softWrap=false,
                                        autoSize=TextAutoSize.StepBased(minFontSize=12.sp,maxFontSize=22.sp,stepSize=1.sp))
                                }
                            }
                            } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally)) {
                            Box(Modifier.size(56.dp)) {
                                ProfilePhoto(if(owner==Owner.Me)p.myPhoto else p.partnerPhoto,owner==Owner.Me,Modifier.fillMaxSize().padding(6.dp),if(owner==Owner.Me)p.avatar else -1)
                                Canvas(Modifier.fillMaxSize()) {val inset=3.dp.toPx();drawCircle(c.surfaceSunken,size.width/2-inset,style=Stroke(3.dp.toPx()));drawArc(color,-90f,360f*progress,false,Offset(inset,inset),Size(size.width-2*inset,size.height-2*inset),style=Stroke(3.dp.toPx(),cap=StrokeCap.Round))}
                            }
                                Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                            Surface(onClick={onClock(owner)},modifier=Modifier.widthIn(max=96.dp).fillMaxWidth().height(48.dp).semantics {contentDescription="Set ${ownerName(owner,p)} clock timezone"},shape=RoundedCornerShape(14.dp),color=c.surfaceElevated,border=BorderStroke(1.dp,c.separator)) {
                                Box(Modifier.fillMaxSize().padding(horizontal=5.dp,vertical=8.dp),contentAlignment=Alignment.Center) {
                                    BasicText(time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--",
                                        modifier=Modifier.fillMaxWidth(),
                                        style=MaterialTheme.typography.titleLarge.copy(color=c.textPrimary,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center),
                                        maxLines=1,softWrap=false,
                                        autoSize=TextAutoSize.StepBased(minFontSize=12.sp,maxFontSize=22.sp,stepSize=1.sp))
                                }
                            }
                                }
                            }
                        }
                        Text(ownerName(owner,p),style=MaterialTheme.typography.titleSmall,color=c.textPrimary,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center)
                        Text(zone?.id?.substringAfterLast('/')?.replace('_',' ') ?: "Set partner clock",style=MaterialTheme.typography.labelSmall,color=c.textSecondary,maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center)
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {Icon(Icons.Outlined.CheckCircleOutline,null,Modifier.size(14.dp),tint=color);Spacer(Modifier.width(4.dp));PText("$done/${owned.size} done",style=MaterialTheme.typography.labelSmall);Spacer(Modifier.width(4.dp));Icon(Icons.Outlined.ChevronRight,null,Modifier.size(14.dp),tint=c.textSecondary)}
                    }
                }
            }
        }
        Surface(onClick={onFilter(Owner.Together.label)},shape=RoundedCornerShape(14.dp),color=c.secondary.copy(alpha=.14f).compositeOver(c.surface),border=BorderStroke(1.dp,c.separator),modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.padding(10.dp,9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(18.dp));PText("Together · ${tasks.count {it.owner==Owner.Together && it.done}}/${tasks.count {it.owner==Owner.Together}}",Modifier.weight(1f),MaterialTheme.typography.labelMedium);PText("${tasks.count {it.done}}/${tasks.size} overall",style=MaterialTheme.typography.labelSmall,muted=true);Icon(Icons.Outlined.ChevronRight,null,Modifier.size(16.dp))}
        }
    }
}

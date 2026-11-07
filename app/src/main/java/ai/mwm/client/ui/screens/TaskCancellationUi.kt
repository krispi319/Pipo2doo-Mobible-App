package ai.mwm.client.ui.screens

import ai.mwm.client.cloud.LocalPartnerConnection
import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

val LocalTaskCancel = staticCompositionLocalOf<(Task,TaskCancelScope,String,(String?)->Unit)->Unit> {
    {_,_,_,result->result("Cancellation unavailable. Reopen this task.")}
}

@Composable private fun CancelScopePill(label:String,active:Boolean,enabled:Boolean,onClick:()->Unit) {
    Surface(onClick=onClick,enabled=enabled,shape=CircleShape,
        color=if(active)AppTheme.colors.secondary else AppTheme.colors.surface,
        border=BorderStroke(1.dp,AppTheme.colors.separator),modifier=Modifier.heightIn(min=28.dp).semantics {selected=active}) {
        Box(Modifier.padding(horizontal=8.dp,vertical=6.dp),contentAlignment=Alignment.Center) {
            PText(label,style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable internal fun CompactTaskPill(label:String,icon:ImageVector,color:Color,enabled:Boolean=true,onClick:()->Unit) {
    Surface(onClick=onClick,enabled=enabled,shape=CircleShape,color=color,
        border=BorderStroke(1.dp,AppTheme.colors.separator),modifier=Modifier.widthIn(min=60.dp).heightIn(min=27.dp)) {
        Row(Modifier.padding(horizontal=6.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp,Alignment.CenterHorizontally)) {
            Icon(icon,null,Modifier.size(12.dp))
            Text(label,style=MaterialTheme.typography.labelSmall.copy(fontSize=10.sp,lineHeight=12.sp),maxLines=1)
        }
    }
}

@Composable fun TaskActionPills(task:Task,state:PipoState) {
    if(task.closed)return
    val connection=LocalPartnerConnection.current
    val editable=ai.mwm.client.cloud.canEditTask(task,state,connection)
    var open by rememberSaveable(task.id) {mutableStateOf(false)}
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
        if(task.owner!=Owner.Me)PokeButton(state.preferences.partnerName,task)
        if(editable)CompactTaskPill("Cancel",Icons.Outlined.Block,AppTheme.colors.surfaceSunken,!connection.busy) {open=true}
    }
    if(open)TaskCancellationDialog(task,state,{open=false})
}

@Composable internal fun TaskCancellationDialog(task:Task,state:PipoState,onDismiss:()->Unit) {
    var scope by rememberSaveable(task.id) {mutableStateOf(TaskCancelScope.Occurrence)}
    var reason by rememberSaveable(task.id) {mutableStateOf("")}
    var saving by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val cancel=LocalTaskCancel.current
    androidx.compose.ui.window.Dialog(onDismissRequest={if(!saving)onDismiss()}) {
        Surface(modifier=Modifier.widthIn(max=312.dp).fillMaxWidth(),shape=RoundedCornerShape(16.dp),
            color=AppTheme.colors.surface,border=BorderStroke(1.dp,AppTheme.colors.separator)) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Block,null,Modifier.size(16.dp),tint=AppTheme.colors.textSecondary)
                    PText("Cancel task?",style=MaterialTheme.typography.titleSmall)
                }
                PText(task.title,style=MaterialTheme.typography.bodyMedium,preserveContent=true)
                if(state.hasRecurrence(task.id))Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    CancelScopePill("This date",scope==TaskCancelScope.Occurrence,!saving) {scope=TaskCancelScope.Occurrence}
                    CancelScopePill("Stop series",scope==TaskCancelScope.Future,!saving) {scope=TaskCancelScope.Future}
                }
                PText(if(scope==TaskCancelScope.Future)"Current and future dates · History stays" else if(state.hasRecurrence(task.id))"Next date stays · History kept" else "Kept in History",style=MaterialTheme.typography.bodySmall,muted=true)
                Surface(shape=RoundedCornerShape(12.dp),color=AppTheme.colors.surface,border=BorderStroke(1.dp,AppTheme.colors.separator)) {
                    BasicTextField(reason,{reason=it.take(200)},enabled=!saving,maxLines=3,
                        textStyle=MaterialTheme.typography.bodySmall.copy(color=AppTheme.colors.textPrimary),
                        cursorBrush=SolidColor(AppTheme.colors.textPrimary),
                        modifier=Modifier.fillMaxWidth().padding(10.dp,8.dp).heightIn(min=16.dp).semantics {contentDescription="Cancellation reason"},
                        decorationBox={field->Box {if(reason.isEmpty())PText("Reason · Optional",style=MaterialTheme.typography.bodySmall,muted=true);field()}})
                }
                error?.let {Text(it,color=AppTheme.colors.error,style=MaterialTheme.typography.bodySmall)}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    PText("Reminders stop",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,muted=true)
                    CancelScopePill("Keep",false,!saving,onDismiss)
                    CompactTaskPill(if(saving)"Saving…" else "Cancel",Icons.Outlined.Block,AppTheme.colors.primary,!saving) {
                        saving=true;error=null;cancel(task,scope,reason) {failure->saving=false;if(failure==null)onDismiss() else error=failure}
                    }
                }
            }
        }
    }
}

@Composable internal fun CancelledHistoryRow(task:Task,state:PipoState,onTask:(String)->Unit) {
    Surface(onClick={onTask(task.id)},shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),color=AppTheme.colors.surfaceSunken) {
        Row(Modifier.padding(12.dp,10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Block,"Cancelled",Modifier.size(30.dp),tint=AppTheme.colors.textSecondary)
            Column(Modifier.weight(1f)) {
                PText(task.title,style=MaterialTheme.typography.bodyMedium,preserveContent=true)
                PText("Cancelled by ${task.cancelledByName.ifBlank {"you"}}",style=MaterialTheme.typography.bodySmall,muted=true,preserveContent=true)
            }
            PText(java.time.Instant.ofEpochMilli(task.cancelledAt).atZone(clockZone(state.preferences.clockZone)).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")),style=MaterialTheme.typography.bodySmall,muted=true)
        }
    }
}

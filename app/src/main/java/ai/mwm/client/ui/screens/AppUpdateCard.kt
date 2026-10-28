package ai.mwm.client.ui.screens

import ai.mwm.client.updates.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable internal fun AppUpdateCard(modifier:Modifier=Modifier) {
    val vm:AppUpdater?=if(LocalInspectionMode.current)null else viewModel()
    val state=vm?.state?.collectAsState()?.value ?: UpdateState(installed="1.4.0")
    val c=AppTheme.colors
    var open by remember {mutableStateOf(false)}
    LaunchedEffect(Unit){vm?.checkOnce()}
    Surface(onClick={open=true;if(state.repository.isNotBlank())vm?.checkOnce()},modifier=modifier,shape=RoundedCornerShape(20.dp),color=c.surface,border=BorderStroke(1.dp,c.separator),shadowElevation=1.dp) {
        Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterVertically)) {
            Surface(shape=RoundedCornerShape(15.dp),color=c.secondary.copy(alpha=.5f).compositeOver(c.surface)) {
                Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
                    if(state.busy)CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
                    else BadgedBox(badge={if(state.release!=null)Badge(containerColor=c.primary)}) {Icon(Icons.Outlined.SystemUpdate,"Check for app updates",Modifier.size(24.dp))}
                }
            }
            PText("Check for updates",style=MaterialTheme.typography.labelMedium)
            PText("v${state.installed}",style=MaterialTheme.typography.labelSmall,muted=true)
        }
    }
    if(open && vm!=null)UpdateDialog(vm,state){open=false}
}
@Composable private fun UpdateDialog(vm:AppUpdater,state:UpdateState,onClose:()->Unit) {
    val c=AppTheme.colors
    var sourceOpen by remember {mutableStateOf(state.repository.isBlank())}
    var source by remember {mutableStateOf(state.repository)}
    AlertDialog(onDismissRequest=onClose,icon={Icon(Icons.Outlined.SystemUpdate,null,Modifier.size(34.dp))},
        title={PText(if(sourceOpen)"Update source" else if(state.release!=null)"A little update is here" else "App updates")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            PText("Installed: v${state.installed}",style=MaterialTheme.typography.bodySmall,muted=true)
            if(sourceOpen) {
                PText("Add this app’s public GitHub repository or Releases link once.",style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(source,{source=it},label={PText("GitHub Releases link")},placeholder={PText("https://github.com/owner/app/releases")},modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=!state.busy)
            } else {
                state.release?.let {release->
                    Badge(release.tag,c.secondary)
                    if(release.notes.isNotBlank()) androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max=190.dp)) {item {PText(release.notes,style=MaterialTheme.typography.bodySmall,preserveContent=true)}}
                }
                if(state.busy) {LinearProgressIndicator(modifier=Modifier.fillMaxWidth());state.progress?.let {PText("$it%",style=MaterialTheme.typography.labelSmall)}}
            }
            if(state.message.isNotBlank())Text(state.message,color=if(state.error)c.error else c.textSecondary,style=MaterialTheme.typography.bodySmall)
            if(!sourceOpen) {
                PText("Android will ask before installing.",style=MaterialTheme.typography.labelSmall,muted=true)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    TextButton({vm.check()},enabled=!state.busy){PText("Check again",style=MaterialTheme.typography.labelSmall)}
                    TextButton({sourceOpen=true;source=state.repository},enabled=!state.busy){PText("Release source",style=MaterialTheme.typography.labelSmall)}
                }
            }
        }},confirmButton={
            Button(onClick={if(sourceOpen){val valid=runCatching {publicRepository(source)}.isSuccess;vm.configure(source);if(valid)sourceOpen=false}
                else if(state.ready)vm.install() else if(state.release!=null)vm.download() else vm.check()},
                enabled=!state.busy && (sourceOpen || state.repository.isNotBlank())) {
                PText(if(sourceOpen)"Save & check" else if(state.ready)"Install update" else if(state.release!=null)"Download update" else "Check for updates")
            }
        },dismissButton={OutlinedButton(onClose){PText("Not now")}},containerColor=c.surface,shape=RoundedCornerShape(28.dp))
}

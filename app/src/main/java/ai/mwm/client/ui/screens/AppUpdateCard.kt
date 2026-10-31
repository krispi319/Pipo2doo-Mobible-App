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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

@Composable internal fun AppUpdateCard(modifier:Modifier=Modifier) {
    val vm:AppUpdater?=if(LocalInspectionMode.current)null else viewModel()
    val state=vm?.state?.collectAsState()?.value ?: UpdateState(installed=ai.mwm.client.BuildConfig.VERSION_NAME)
    val c=AppTheme.colors
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var open by remember {mutableStateOf(false)}
    LaunchedEffect(Unit){vm?.checkOnce()}
    DisposableEffect(lifecycle,vm) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME)vm?.resumeInstaller()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    Surface(onClick={open=true;vm?.check()},modifier=modifier,shape=RoundedCornerShape(20.dp),color=c.surface,border=BorderStroke(1.dp,c.separator),shadowElevation=1.dp) {
        Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterVertically)) {
            Surface(shape=RoundedCornerShape(15.dp),color=c.secondary.copy(alpha=.5f).compositeOver(c.surface)) {
                Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
                    if(state.busy)CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
                    else BadgedBox(badge={if(state.release!=null)Badge(containerColor=c.primary)}) {Icon(Icons.Outlined.SystemUpdate,"Check for app updates",Modifier.size(24.dp))}
                }
            }
            PText(when {state.busy && state.progress!=null->"Downloading ${state.progress}%";state.busy->"Checking…";state.ready->"Ready to install";state.error->"Retry update";state.release!=null->"Update available";else->"Check for updates"},style=MaterialTheme.typography.labelMedium)
            PText("v${state.installed}",style=MaterialTheme.typography.labelSmall,muted=true)
        }
    }
    if(open && vm!=null)UpdateDialog(vm,state){open=false;if(!state.busy)vm.deferInstallation()}
}
@Composable private fun UpdateDialog(vm:AppUpdater,state:UpdateState,onClose:()->Unit) {
    val c=AppTheme.colors
    AlertDialog(onDismissRequest=onClose,icon={Icon(Icons.Outlined.SystemUpdate,null,Modifier.size(34.dp))},
        title={PText(when {state.ready->"Ready for a little update";state.busy && state.progress!=null->"Your update is on its way";state.release!=null->"A little update is here";else->"App updates"})},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            PText("Installed: v${state.installed}",style=MaterialTheme.typography.bodySmall,muted=true)
            state.release?.let {release->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Badge(release.tag,c.secondary)
                    PText(String.format(Locale.getDefault(),"%.1f MB",release.bytes/1_048_576.0),style=MaterialTheme.typography.labelSmall,muted=true)
                }
                if(release.notes.isNotBlank())androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max=190.dp)) {item {PText(release.notes,style=MaterialTheme.typography.bodySmall,preserveContent=true)}}
            }
            if(state.busy) {LinearProgressIndicator(modifier=Modifier.fillMaxWidth());state.progress?.let {PText("$it%",style=MaterialTheme.typography.labelSmall)}}
            if(state.message.isNotBlank())Text(state.message,color=if(state.error)c.error else c.textSecondary,style=MaterialTheme.typography.bodySmall)
            PText(if(state.busy && state.progress!=null)"You can close this window while the download continues." else "Your account and data stay. Android asks before installing.",style=MaterialTheme.typography.labelSmall,muted=true)
            if(!state.busy && state.ready)TextButton({vm.check()}) {PText("Check for a newer release",style=MaterialTheme.typography.labelSmall)}
        }},confirmButton={
            if(state.busy)OutlinedButton({vm.cancel()}) {PText(if(state.progress!=null)"Cancel download" else "Cancel check")}
            else Button(onClick={if(state.ready)vm.install() else if(state.release!=null)vm.startUpdate() else vm.check()}) {
                PText(when {state.ready->"Install update";state.error->"Retry";state.release!=null->"Update now";else->"Check for updates"})
            }
        },dismissButton={OutlinedButton(onClose){PText(if(state.busy)"Close" else "Not now")}},containerColor=c.surface,shape=RoundedCornerShape(28.dp))
}

/** Shares the settings updater. Only an explicit update request may open the installer. */
@Composable internal fun AppUpdatePrompt(showNotice:Boolean) {
    if(LocalInspectionMode.current)return
    val vm:AppUpdater=viewModel()
    val state by vm.state.collectAsState()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    var dismissedTag by rememberSaveable {mutableStateOf("")}
    LaunchedEffect(Unit){vm.checkOnce()}
    DisposableEffect(lifecycle,vm) {
        val observer=LifecycleEventObserver {_,event->
            foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if(event==Lifecycle.Event.ON_RESUME)vm.resumeInstaller()
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(state.installPending,state.ready,state.busy,state.error,foreground) {
        if(shouldOpenUpdateInstaller(state,foreground))vm.install()
    }
    if(showNotice && foreground && state.release!=null && !state.error && state.release!!.tag!=dismissedTag)
        UpdateDialog(vm,state){dismissedTag=state.release?.tag.orEmpty();if(!state.busy)vm.deferInstallation()}
}

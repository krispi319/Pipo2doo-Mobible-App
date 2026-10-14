package ai.mwm.client

import ai.mwm.client.data.PipoRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.isSystemInDarkTheme
import ai.mwm.client.ui.screens.LocalizedApp
import ai.mwm.client.ui.screens.HomeScreen
import ai.mwm.client.ui.theme.MyApplicationTheme
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ai.mwm.client.cloud.PipoFirebase
import ai.mwm.client.cloud.CloudRelease
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var notificationTaskId by mutableStateOf<String?>(null)
    private var privateReminder by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route(intent)
        // Notification account ownership is validated by route().
        enableEdgeToEdge()
        setContent {
            val state by PipoRepository.get(this).state.collectAsState()
            val dark = when(state.preferences.themeMode) { "Night" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
            MyApplicationTheme(darkTheme = dark) {
                LocalizedApp(state.preferences.language) {
                    HomeScreen(openTaskId = notificationTaskId, onTaskLinkHandled = { notificationTaskId = null }, openCare = privateReminder, onCareLinkHandled = { privateReminder = false })
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route(intent)
        // Notification account ownership is validated by route().
    }
    private fun route(intent: Intent) {
        val matches=intent.getStringExtra("account_scope")?.let {it==ai.mwm.client.data.AccountScope.current(this)} ?: (intent.data?.host!="private-reminder")
        privateReminder=matches && intent.data?.host=="private-reminder"
        if(!matches) {notificationTaskId=null;return}
        val event=intent.getStringExtra("poke_event_id")
        if(event==null) { notificationTaskId=intent.getStringExtra("task_id"); return }
        notificationTaskId=null
        if(!CloudRelease.ENABLED) return
        lifecycleScope.launch {
            val data=runCatching { PipoFirebase.get(this@MainActivity).call("validatePoke",mapOf("eventId" to event)) }.getOrNull()
            notificationTaskId=data?.get("taskId") as? String
        }
    }
}

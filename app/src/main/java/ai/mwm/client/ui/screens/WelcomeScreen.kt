package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import android.content.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import java.util.Locale

fun shareLocalCode(context: Context, code: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Pipo2doo partner invitation: $code. Enter this code in Pipo2doo before it expires. Pairing does not share health records."), "Share code"))
}

@Composable fun WelcomeScreen(state: PipoState, onStart: () -> Unit, onRegenerate: () -> Unit, onSetCode: (String) -> Unit, onConnect: (String,String) -> Unit = {_,_->}, onCheck: () -> Unit = {}, onSignOut: () -> Unit = {}, onChooseRole: (String) -> Unit = {}) {
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    val connection=ai.mwm.client.cloud.LocalPartnerConnection.current
    val display=state.copy(preferences=state.preferences.copy(code=connection.code.ifBlank {"------"},codeExpiresAt=connection.expiresAt))
    val canUseCode = connection.code.isNotBlank() && connection.expiresAt>System.currentTimeMillis() && connection.online && !connection.busy
    LazyWelcomeContent(display, code, { code = it.uppercase(Locale.ENGLISH).filter { char -> char in 'A'..'Z' || char in '0'..'9' }.take(6); error = "" }, error,
        { if(code.length!=6)error="That code has ${code.length} characters — space codes have 6." else if(!code.matches(Regex("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}")))error="Use the six characters from your partner's invitation." else onConnect("join",code) },
        { if(canUseCode) { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Partner invitation", connection.code)); android.widget.Toast.makeText(context, "Code copied", android.widget.Toast.LENGTH_SHORT).show() } },
        { if(canUseCode) shareLocalCode(context, connection.code) }, onRegenerate, {onConnect("create","")}, onStart, onSignOut, onChooseRole, onCheck)

}

@Composable private fun LazyWelcomeContent(
    state: PipoState, code: String, onCode: (String) -> Unit, error: String,
    onJoin: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit,
    onRegenerate: () -> Unit, onCreate: () -> Unit, onLocal: () -> Unit,
    onSignOut: () -> Unit, onChooseRole: (String) -> Unit, onCheck: () -> Unit
) {
    val c = AppTheme.colors
    val connection = ai.mwm.client.cloud.LocalPartnerConnection.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connection.expiresAt, connection.retryAt) {
        while(true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val memberRole = connection.role.ifBlank { connection.selectedRole }.ifBlank { "user_1" }
    val host = memberRole == "user_1"
    LaunchedEffect(connection.selectedRole, connection.role, connection.busy, connection.connected) {
        if(connection.selectedRole.isBlank() && connection.role.isBlank() && !connection.busy && !connection.connected) onChooseRole("user_1")
    }
    val expired = connection.expiresAt <= now
    val hasCode = connection.code.length == 6
    val canShare = hasCode && !expired && connection.online && !connection.busy
    val seconds = ((connection.expiresAt - now).coerceAtLeast(0) / 1000)
    val cooldown = ((connection.retryAt - now).coerceAtLeast(0) + 999) / 1000
    val chosen = connection.role.ifBlank { connection.selectedRole }.isNotBlank()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Duo(20.dp)
                Spacer(Modifier.width(8.dp))
                PText("Pipo2doo", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if(connection.authenticated) {
                    IconButton(onSignOut, enabled = !connection.busy) {
                        Icon(Icons.Outlined.Logout, "Sign out and keep local data", tint = c.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.size(104.dp, 84.dp).background(c.primary.copy(alpha = .13f), RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center
            ) { ApprovedCat(Modifier.size(88.dp, 66.dp), "welcome") }
            Spacer(Modifier.height(18.dp))
            PText(
                if(connection.paired) "Your person is here" else "A little space for two",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                align = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            PText(
                if(connection.paired) "One shared space. All your little plans." else "Share little tasks with your favorite person.",
                style = MaterialTheme.typography.bodyMedium, muted = true, align = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            if(!connection.connected) {
                Row(
                    Modifier.fillMaxWidth().background(c.surfaceSunken, RoundedCornerShape(18.dp)).padding(5.dp).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(true, false).forEach { isHost ->
                        val selected = host == isHost
                        Surface(
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                            color = if(selected) c.surface else Color.Transparent,
                            border = if(selected) BorderStroke(1.dp, c.separator) else null
                        ) {
                            Row(
                                Modifier.selectable(selected, enabled = !connection.busy, role = Role.RadioButton,
                                    onClick = { onChooseRole(if(isHost) "user_1" else "user_2") })
                                    .heightIn(min = 48.dp).padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(if(isHost) Icons.Outlined.Home else Icons.Outlined.Key, null,
                                    Modifier.size(18.dp), tint = if(selected) c.textPrimary else c.textSecondary)
                                Spacer(Modifier.width(8.dp))
                                PText(if(isHost) "Host" else "Joiner", style = MaterialTheme.typography.labelLarge, muted = !selected)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Surface(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                color = c.surface, border = BorderStroke(1.dp, c.separator)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if(connection.paired) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = c.success, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            PText("Connected together", style = MaterialTheme.typography.titleMedium)
                        }
                        PText("Your tasks and lists are ready to share.", muted = true)
                        Button(onLocal, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !connection.busy, shape = RoundedCornerShape(16.dp)) {
                            Text("Open our space")
                        }
                    } else if(host) {
                        PText(if(connection.connected) "Invite your person" else "Start your shared space", style = MaterialTheme.typography.titleMedium)
                        PText(
                            if(connection.connected) "Send them this code. They'll join as User 2." else "Create a space, then send your partner an invite.",
                            style = MaterialTheme.typography.bodyMedium, muted = true
                        )
                        if(connection.connected) {
                            Row(
                                Modifier.fillMaxWidth().semantics { contentDescription = if(hasCode) "Invitation code ${connection.code}" else "No invitation code issued" },
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                (if(hasCode) connection.code else "------").forEach { char ->
                                    Surface(Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(12.dp), color = c.surfaceSunken) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(char.toString(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                color = if(canShare) c.textPrimary else c.textSecondary)
                                        }
                                    }
                                }
                            }
                            PText(
                                when {
                                    !hasCode -> "Generate an invite to get started."
                                    expired -> "This code expired. Generate a new one."
                                    !connection.online -> "Reconnect to verify your code."
                                    else -> "Valid for %02d:%02d".format(Locale.ENGLISH, seconds / 60, seconds % 60)
                                }, Modifier.fillMaxWidth(), MaterialTheme.typography.bodySmall, muted = true, align = TextAlign.Center
                            )
                            if(canShare) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onCopy, Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Copy")
                                    }
                                    Button(onShare, Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                                        Icon(Icons.Outlined.Share, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Share")
                                    }
                                }
                            }
                            if(hasCode && !expired) {
                                TextButton(onRegenerate, Modifier.align(Alignment.CenterHorizontally), enabled = !connection.busy && chosen && cooldown == 0L) {
                                    Text(if(connection.busy) "Generating…" else if(cooldown > 0) "New code in ${cooldown}s" else "Generate a new code")
                                }
                            } else {
                                Button(onRegenerate, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !connection.busy && chosen && cooldown == 0L, shape = RoundedCornerShape(16.dp)) {
                                    Text(if(connection.busy) "Generating…" else if(cooldown > 0) "Try again in ${cooldown}s" else "Generate code")
                                }
                            }
                            PText("A new code replaces the previous one.", style = MaterialTheme.typography.bodySmall, muted = true)
                        } else {
                            Button(onCreate, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !connection.busy && chosen, shape = RoundedCornerShape(16.dp)) {
                                if(connection.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("Create our space")
                            }
                        }
                    } else {
                        PText("Join your person", style = MaterialTheme.typography.titleMedium)
                        PText("Enter the 6-character invite they shared with you.", style = MaterialTheme.typography.bodyMedium, muted = true)
                        OutlinedTextField(
                            value = code, onValueChange = onCode, modifier = Modifier.fillMaxWidth(),
                            enabled = !connection.busy && !connection.connected, singleLine = true,
                            label = { Text("Partner's code") }, placeholder = { Text("6 characters") },
                            textStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp, fontWeight = FontWeight.SemiBold),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false
                            ),
                            isError = error.isNotBlank(), shape = RoundedCornerShape(14.dp)
                        )
                        if(error.isNotBlank()) Text(appDisplayText(error), color = c.error, style = MaterialTheme.typography.bodySmall)
                        Button(onJoin, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = code.length == 6 && !connection.busy && !connection.connected && chosen, shape = RoundedCornerShape(16.dp)) {
                            if(connection.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Join our space")
                        }
                    }
                }
            }

            val message = connection.pairingMessage.ifBlank { connection.message }
            if(message.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(if(connection.connected && connection.online) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                        null, Modifier.padding(top = 2.dp).size(16.dp), tint = c.textSecondary)
                    Spacer(Modifier.width(8.dp))
                    PText(message, Modifier.weight(1f), MaterialTheme.typography.bodySmall, muted = true)
                }
            }
            if(connection.connected && !connection.online) {
                TextButton(onCheck, enabled = !connection.busy) { Text("Retry connection") }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(13.dp), tint = c.textSecondary)
                Spacer(Modifier.width(6.dp))
                PText("Health records stay private.", style = MaterialTheme.typography.bodySmall, muted = true)
            }
            if(connection.authenticated && connection.accountEmail.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                PText(connection.accountEmail, style = MaterialTheme.typography.bodySmall, muted = true, align = TextAlign.Center)
            }
            Spacer(Modifier.height(8.dp))
            if(!connection.paired) TextButton(onLocal, enabled = !connection.busy) {
                Text(if(connection.connected) "Back to my space" else "Continue on this device")
            }
        }
    }
}

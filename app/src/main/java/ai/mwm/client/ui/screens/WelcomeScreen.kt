package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import android.content.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
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
        { if(canUseCode) shareLocalCode(context, connection.code) }, onRegenerate, {onConnect("create","")}, onStart, onSignOut, onChooseRole)

}

@Composable private fun LazyWelcomeContent(state: PipoState, code: String, onCode: (String) -> Unit, error: String, onJoin: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit, onRegenerate: () -> Unit, onCreate: () -> Unit, onLocal: () -> Unit, onSignOut: () -> Unit, onChooseRole: (String) -> Unit) {
    val c = AppTheme.colors
    var now by remember {mutableStateOf(System.currentTimeMillis())}
    LaunchedEffect(state.preferences.codeExpiresAt) {while(true) {now=System.currentTimeMillis();delay(1000)}}
    val connection=ai.mwm.client.cloud.LocalPartnerConnection.current
    val memberRole = connection.role.ifBlank { connection.selectedRole }.ifBlank { "user_1" }
    val setupRole = if(memberRole == "user_2") "Joiner" else "Host"
    LaunchedEffect(connection.selectedRole, connection.role, connection.busy, connection.connected) {
        if(connection.selectedRole.isBlank() && connection.role.isBlank() && !connection.busy && !connection.connected) onChooseRole("user_1")
    }
    val isSample = connection.code.isBlank()
    val expired = state.preferences.codeExpiresAt <= now || connection.code.isBlank()
    val canShare = !expired && connection.online && !connection.busy
    val remaining = ((state.preferences.codeExpiresAt-now).coerceAtLeast(0)/1000)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(208.dp).background(Brush.verticalGradient(listOf(c.primary.copy(alpha = .16f).compositeOver(c.background), c.background)))) {
            val heroWidth = maxWidth
            Canvas(Modifier.fillMaxSize()) {
                val d = 1.dp.toPx()
                val pane = Offset(size.width/2-85*d,35*d)
                val paneShape = Path().apply {
                    moveTo(pane.x+28*d,pane.y); lineTo(pane.x+142*d,pane.y)
                    quadraticTo(pane.x+170*d,pane.y,pane.x+170*d,pane.y+28*d)
                    lineTo(pane.x+170*d,pane.y+97*d);quadraticTo(pane.x+170*d,pane.y+100*d,pane.x+167*d,pane.y+100*d)
                    lineTo(pane.x+3*d,pane.y+100*d);quadraticTo(pane.x,pane.y+100*d,pane.x,pane.y+97*d)
                    lineTo(pane.x,pane.y+28*d);quadraticTo(pane.x,pane.y,pane.x+28*d,pane.y);close()
                }
                drawPath(paneShape,c.surfaceElevated.copy(alpha=.5f))
                drawPath(paneShape,c.separator,style=Stroke(1.5f*d))
                drawLine(c.separator,Offset(size.width/2,35*d),Offset(size.width/2,135*d),1.5f*d)
                drawRect(c.surfaceSunken,Offset(0f,size.height-22*d),Size(size.width,22*d))
                drawLine(c.separator,Offset(0f,size.height-22*d),Offset(size.width,size.height-22*d),1.5f*d)
                val cushion = Path().apply {
                    moveTo(-24*d,size.height-48*d)
                    cubicTo(-24*d,size.height-102*d,size.width+24*d,size.height-102*d,size.width+24*d,size.height-48*d)
                    lineTo(size.width+24*d,size.height-40*d)
                    quadraticTo(size.width+24*d,size.height-14*d,size.width-10*d,size.height-14*d)
                    lineTo(10*d,size.height-14*d);quadraticTo(-24*d,size.height-14*d,-24*d,size.height-40*d);close()
                }
                drawPath(cushion,c.primary.copy(alpha=.3f).compositeOver(c.surface))
                drawRoundRect(c.textPrimary.copy(alpha=.22f),Offset(16*d,size.height-78*d),Size(size.width-32*d,50*d),CornerRadius(32*d),style=Stroke(1.5f*d,pathEffect=PathEffect.dashPathEffect(floatArrayOf(4*d,4*d))))
                drawLine(c.secondary,Offset(size.width/2,size.height-76*d),Offset(size.width/2,size.height-30*d),1.5f*d,pathEffect=PathEffect.dashPathEffect(floatArrayOf(4*d,4*d)))
                drawOval(c.primary.copy(alpha=.22f).compositeOver(c.surface),Offset(size.width*.87f-112*d,size.height-80*d),Size(112*d,34*d))
                drawOval(c.accent.copy(alpha=.7f),Offset(size.width*.87f-116*d,size.height-84*d),Size(120*d,42*d),style=Stroke(1.5f*d,pathEffect=PathEffect.dashPathEffect(floatArrayOf(4*d,4*d))))
            }
            ApprovedCat(Modifier.align(Alignment.BottomStart).padding(start = heroWidth * .12f,bottom = 44.dp).size(122.dp,92.dp), "welcome")
            PText("Partner's spot · Still warm", Modifier.align(Alignment.BottomEnd).padding(end = heroWidth * .13f,bottom = 90.dp).background(c.background.copy(alpha=.8f),CircleShape).padding(horizontal=8.dp,vertical=2.dp), MaterialTheme.typography.labelSmall, true)
        }
        Column(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=14.dp), horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            PText("Pipo2doo", style = MaterialTheme.typography.displayLarge)
            PText("Little tasks, shared with your favorite person", align = TextAlign.Center, muted = true)
        }
        Column(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=16.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(8.dp)) {
            PText("Which one are you?", style=MaterialTheme.typography.labelLarge, muted=true)
            Box(Modifier.widthIn(max=320.dp).fillMaxWidth()) {
                Canvas(Modifier.matchParentSize()) {
                    drawLine(c.secondary.copy(alpha=.75f),Offset(size.width/2,6.dp.toPx()),Offset(size.width/2,size.height-6.dp.toPx()),1.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),4.dp.toPx())))
                }
                Row(Modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                    listOf("Host","Joiner").forEach { value ->
                        val chosen=setupRole==value
                        val tint=if(value=="Host") c.primary else c.accent
                        Column(Modifier.weight(1f).selectable(selected=chosen,enabled=!connection.busy && !connection.connected,role=Role.RadioButton,onClick={onChooseRole(if(value=="Host") "user_1" else "user_2")}).semantics { contentDescription="$value, ${if(value=="Host") "User 1" else "User 2"}" }.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(70.dp),contentAlignment=Alignment.Center) {
                                Surface(Modifier.size(60.dp).then(if(chosen) Modifier.border(2.dp,tint,CircleShape) else Modifier),shape=CircleShape,color=if(chosen) tint.copy(alpha=.42f).compositeOver(c.surface) else c.surface,border=if(chosen) null else BorderStroke(1.5.dp,c.separator),shadowElevation=1.dp) {
                                    Box(contentAlignment=Alignment.Center) {Icon(if(value=="Host") Icons.Outlined.Home else Icons.Outlined.Key,null,Modifier.size(28.dp))}
                                }
                                if(chosen) Surface(Modifier.align(Alignment.BottomEnd).size(18.dp),shape=CircleShape,color=tint,border=BorderStroke(2.dp,c.background)) {Icon(Icons.Outlined.Check,null,Modifier.padding(2.dp))}
                            }
                            PText(value,style=MaterialTheme.typography.titleMedium)
                            PText(if(value=="Host") "Create the space and share your code." else "Enter your partner’s code to join.",style=MaterialTheme.typography.bodySmall,muted=true,align=TextAlign.Center)
                        }
                    }
                }
            }
            PText("Host is User 1. Joiner is User 2. Pairing does not share health records.",style=MaterialTheme.typography.bodySmall,muted=true,align=TextAlign.Center)
            if(connection.connected) PText("Your established identity is ${ai.mwm.client.cloud.roleLabel(connection.role)}. It cannot be changed here.",style=MaterialTheme.typography.bodySmall,muted=true,align=TextAlign.Center)
            if(connection.authenticated) PText("Signed in as ${connection.accountEmail.ifBlank {"your account"}}",style=MaterialTheme.typography.bodySmall,muted=true,align=TextAlign.Center)
        }
        Paper(Modifier.padding(start=20.dp,end=20.dp,top=14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { PText("Your space code", style = MaterialTheme.typography.titleMedium); PText(if(connection.code.isBlank()) "Not issued" else if(expired) "Expired · 00:00" else if(connection.busy) "Checking…" else if(!connection.online) "Offline · Unverified" else "%02d:%02d left".format(Locale.ENGLISH,remaining/60,remaining%60), style = MaterialTheme.typography.bodySmall, muted = true) }
            Row(Modifier.fillMaxWidth().padding(top=16.dp,bottom=16.dp).then(if(isSample) Modifier.clearAndSetSemantics { contentDescription="Example pairing code, not usable" } else Modifier), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (if(isSample) "K7MP2Q" else connection.code).forEachIndexed { i, char ->
                    val degrees = listOf(-7f,-3f,0f,0f,3f,7f)[i.coerceAtMost(5)]
                    Surface(Modifier.weight(1f).height(52.dp).offset(y = if(i == 0 || i == 5) 12.dp else if(i==1 || i==4) 4.dp else 0.dp).rotate(degrees), color = if(i in 2..3) c.secondary.copy(alpha = .22f).compositeOver(c.surface) else c.surfaceSunken, shape = RoundedCornerShape(14.dp),border=BorderStroke(1.5.dp,c.separator)) {
                        Box(contentAlignment = Alignment.Center) {
                            if(isSample) BlurredCodeExample(char.toString())
                            else PText(char.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontSize=24.sp), muted=expired)
                        }
                    }
                }
            }
            if(isSample) PText("Example only · Not a usable code",Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall,muted=true,align=TextAlign.Center)
            Button(onRegenerate,Modifier.fillMaxWidth().height(52.dp),enabled=!connection.busy && (setupRole=="Host" || connection.connected) && connection.role.ifBlank {connection.selectedRole}.isNotBlank() && !connection.paired && now>=connection.retryAt) {PText(if(connection.busy) "Generating…" else if(now<connection.retryAt) "Regenerate in ${((connection.retryAt-now+999)/1000)}s" else "Regenerate code")}
            PText("Server invitations last 10 minutes. Successful regeneration invalidates the old code. Never share an expired code.",style=MaterialTheme.typography.bodySmall,muted=true)
            if(connection.pairingMessage.isNotBlank()) PText(connection.pairingMessage,style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onCopy, Modifier.weight(1f).height(44.dp),enabled=canShare,border=BorderStroke(1.5.dp,c.separator)) { PText("Copy code") }; OutlinedButton(onShare, Modifier.weight(1f).height(44.dp),enabled=canShare,border=BorderStroke(1.5.dp,c.separator)) { PText("Share code") } }
            Spacer(Modifier.height(6.dp)); DashedRule()
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) { KittyAvatar(Modifier.size(30.dp));PText(if(connection.paired) "Partner connected. Health records stay private unless you separately consent." else "Waiting for an authenticated partner. Pairing alone never shares health records.", style = MaterialTheme.typography.labelLarge.copy(fontWeight=FontWeight.Medium), muted = true) }
        }
        Column(Modifier.padding(start=20.dp,end=20.dp,top=20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PText("Got a code from them instead?", style=MaterialTheme.typography.labelLarge, muted = true)
            Surface(shape = CircleShape, color = c.surface, border = BorderStroke(1.5.dp, if(error.isNotBlank()) c.error else c.separator)) {
                Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(code, onCode, Modifier.weight(1f).heightIn(min=56.dp).padding(start=10.dp).semantics {contentDescription="Partner's 6-character code"}, singleLine = true, textStyle = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, letterSpacing = 9.sp, textAlign = TextAlign.Center, color = c.textPrimary, fontWeight = FontWeight.SemiBold), decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { if(code.isEmpty()) BlurredCodeExample("ABC123",16.sp); inner() } })
                    Button(onJoin, Modifier.height(52.dp),enabled=code.length==6 && setupRole=="Joiner" && !connection.busy && !connection.connected && connection.selectedRole.isNotBlank(),contentPadding=PaddingValues(horizontal=22.dp)) { PText("Join") }
                }
            }
            PText(if(setupRole=="Host" && !connection.connected) "Select Joiner to enter your partner’s 6-character code" else "Enter your partner’s 6-character code",style=MaterialTheme.typography.bodySmall,muted=true)
            if(error.isNotBlank()) Text(appDisplayText("ⓘ  $error"), color = c.error, style = MaterialTheme.typography.bodySmall)
        }
        Surface(Modifier.padding(start=20.dp,end=20.dp,top=20.dp),shape=RoundedCornerShape(14.dp),color=c.warning.copy(alpha=.14f).compositeOver(c.surface),border=BorderStroke(1.dp,c.warning.copy(alpha=.4f))) {
            Row(Modifier.padding(16.dp,14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.padding(top=5.dp).size(12.dp).background(c.warning,CircleShape))
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) { PText(if(connection.paired) "Partner connected" else if(connection.connected) "Your shared space" else "Local mode · Not connected", style = MaterialTheme.typography.titleMedium); PText(connection.message, muted = true) }
            }
        }
        if(connection.authenticated) TextButton(onSignOut,Modifier.fillMaxWidth(),enabled=!connection.busy) { PText("Sign out · Keep local data") }
        Column(Modifier.padding(start=20.dp,end=20.dp,top=24.dp)) {
            Button(if(setupRole=="Host") onCreate else onJoin, Modifier.fillMaxWidth().height(52.dp),enabled=!connection.busy && connection.selectedRole.isNotBlank() && !connection.connected && (setupRole=="Host" || code.length==6)) { PText(if(setupRole=="Host") "Create space" else "Join space") }
            TextButton(onLocal, Modifier.fillMaxWidth().heightIn(min=44.dp)) { PText(if(connection.connected) "Back to my space" else "Continue in local mode",decoration=TextDecoration.Underline) }
        }
    }
}

/** Software blur also works below API 31. Only illustrative text uses this renderer. */
@Composable private fun BlurredCodeExample(text: String, fontSize: TextUnit = 14.sp) {
    val density = LocalDensity.current
    val color = AppTheme.colors.textSecondary.copy(alpha=.5f)
    val image = remember(text, fontSize, density.density, density.fontScale, color) {
        val pixels = with(density) { fontSize.toPx() }
        val pad = with(density) { 6.dp.toPx() }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = pixels
            this.color = color.toArgb()
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            maskFilter = android.graphics.BlurMaskFilter(with(density) { 2.dp.toPx() }, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        val metrics = paint.fontMetrics
        val width = kotlin.math.ceil(paint.measureText(text)+pad*2).toInt().coerceAtLeast(1)
        val height = kotlin.math.ceil(metrics.descent-metrics.ascent+pad*2).toInt().coerceAtLeast(1)
        android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888).also {
            android.graphics.Canvas(it).drawText(text,pad,pad-metrics.ascent,paint)
        }.asImageBitmap()
    }
    Image(image,null,Modifier.size(with(density) { image.width.toDp() },with(density) { image.height.toDp() }).clearAndSetSemantics {})
}

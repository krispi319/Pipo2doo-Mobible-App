package ai.mwm.client.ui.screens

import ai.mwm.client.R
import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*

@Composable fun PText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium, muted: Boolean = false, align: TextAlign? = null, decoration: TextDecoration? = null, preserveContent: Boolean = false) {
    val localInk=LocalContentColor.current
    val scheme=MaterialTheme.colorScheme
    val fillInk=localInk==scheme.onPrimary || localInk==scheme.onSecondary || localInk==scheme.onTertiary
    Text(if(preserveContent) text else appDisplayText(text), modifier, color = if(muted) AppTheme.colors.textSecondary else if(fillInk)localInk else AppTheme.colors.textPrimary, style = style, textAlign = align, textDecoration = decoration)
}
@Composable fun ownerColor(owner: Owner): Color = when(owner) { Owner.Me -> AppTheme.colors.primary; Owner.You -> AppTheme.colors.accent; Owner.Together -> AppTheme.colors.secondary }
@Composable fun Pill(label: String, selected: Boolean = false, color: Color = AppTheme.colors.primary, onClick: () -> Unit) {
    val c=AppTheme.colors
    val fill=if(selected) color.compositeOver(c.surface) else c.surface
    fun contrast(ink:Color):Float {val a=ink.luminance();val b=fill.luminance();return (maxOf(a,b)+.05f)/(minOf(a,b)+.05f)}
    val ink=listOf(c.textPrimary,c.onPrimary).maxBy {contrast(it)}
    Surface(onClick = onClick, shape = CircleShape, color = fill,
        border = BorderStroke(if(selected) 2.dp else 1.dp, c.separator),
        modifier = Modifier.heightIn(min = 36.dp).semantics { this.selected = selected }) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(appDisplayText(label),color=ink, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium))
        }
    }
}
@Composable fun Paper(modifier: Modifier = Modifier, color: Color = AppTheme.colors.surface, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(AppTheme.radius.md.dp), color = color, border = BorderStroke(1.dp,AppTheme.colors.separator), shadowElevation = 1.dp) { Column(Modifier.padding(AppTheme.spacing.s4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}
@Composable fun Sticky(modifier: Modifier = Modifier, color: Color = AppTheme.colors.primary, rotation: Float = 0f, content: @Composable ColumnScope.() -> Unit) {
    val colors = AppTheme.colors
    Box(modifier.rotate(rotation).padding(top = 9.dp)) {
        Surface(Modifier.fillMaxWidth(), color = color.copy(alpha = 0.28f).compositeOver(colors.surface), shape = RoundedCornerShape(3.dp,3.dp,14.dp,3.dp), shadowElevation = 5.dp) {
            Column(Modifier.padding(18.dp, 24.dp, 18.dp, 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-8).dp).size(64.dp, 20.dp).rotate(2f).background(colors.surfaceElevated.copy(alpha = 0.7f)).border(0.5.dp, colors.separator))
    }
}
@Composable fun Seam(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val color = AppTheme.colors.secondary.copy(alpha = 0.65f)
    Box(modifier.drawBehind { drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))) }, content = content)
}
@Composable fun Header(title: String, subtitle: String, action: @Composable () -> Unit = { Duo(22.dp) }) {
    Row(Modifier.fillMaxWidth().padding(20.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { PText(title, style = MaterialTheme.typography.headlineLarge); PText(subtitle, style = MaterialTheme.typography.bodySmall, muted = true) }
        action()
    }
}
@Composable fun BackBar(label: String, onBack: () -> Unit, end: @Composable () -> Unit = { Duo(22.dp) }) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onBack,contentPadding=PaddingValues(10.dp,0.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back",Modifier.size(22.dp)); Spacer(Modifier.width(6.dp)); PText(label) }
        Spacer(Modifier.weight(1f)); end()
    }
}
@Composable fun Duo(diameter: Dp = 104.dp, myLabel: String = "", partnerLabel: String = "", middle: String = "", modifier: Modifier = Modifier) {
    if(diameter<=32.dp && myLabel.isBlank() && partnerLabel.isBlank() && middle.isBlank()) {
        PipoBrand(modifier.size(diameter * 1.25f))
        return
    }
    val colors = AppTheme.colors
    Box(modifier.size(diameter * 1.62f, diameter)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.height / 2
            val a = Path().apply { addOval(Rect(0f, 0f, size.height, size.height)) }
            val b = Path().apply { addOval(Rect(size.width - size.height, 0f, size.width, size.height)) }
            drawPath(a, colors.primary); drawPath(b, colors.accent)
            drawPath(Path.combine(PathOperation.Intersect, a, b), colors.secondary)
        }
        if(myLabel.isNotEmpty()) PText(myLabel, Modifier.align(Alignment.CenterStart).padding(start = diameter * .2f), MaterialTheme.typography.titleLarge)
        if(partnerLabel.isNotEmpty()) PText(partnerLabel, Modifier.align(Alignment.CenterEnd).padding(end = diameter * .2f), MaterialTheme.typography.titleLarge)
        if(middle.isNotEmpty()) PText(middle, Modifier.align(Alignment.Center).width(diameter * .34f), MaterialTheme.typography.labelSmall, align = TextAlign.Center)
    }
}
@Composable fun CatDrawing(modifier: Modifier = Modifier, cushion: Boolean = false, double: Boolean = false, variant: Int = 0) {
    val c = AppTheme.colors
    Canvas(modifier.semantics { contentDescription = "Compact sketch cat" }) {
        val w = size.width; val h = size.height
        val unit = minOf(w / if(double) 130f else 80f, h / 60f)
        translate((w - unit * if(double) 130f else 80f) / 2, (h - unit * 60f) / 2) {
            scale(unit, unit, Offset.Zero) {
                fun ink(p: Path) = drawPath(p, c.textPrimary, style = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                if(cushion) { drawRoundRect(c.primary.copy(alpha = .35f), Offset(0f, 42f), Size(80f,18f), CornerRadius(12f)); drawOval(c.accent.copy(alpha = .3f), Offset(48f,44f), Size(28f,8f)) }
                fun cat(x: Float) {
                    val p = Path().apply { moveTo(x+8,44f); cubicTo(x+3,36f,x+7,25f,x+17,25f); lineTo(x+18,12f); lineTo(x+27,19f); quadraticTo(x+34,17f,x+41,19f); lineTo(x+49,12f); lineTo(x+50,27f); cubicTo(x+64,31f,x+65,47f,x+51,49f); lineTo(x+16,49f); close() }
                    val fill = when(variant % 4) { 1 -> c.accent; 2 -> c.secondary; 3 -> c.surfaceElevated; else -> c.primary }
                    drawPath(p, fill.copy(alpha = .2f).compositeOver(c.surface)); ink(p)
                    ink(Path().apply { moveTo(x+23,29f); quadraticTo(x+26,32f,x+29,29f); moveTo(x+37,29f); quadraticTo(x+40,32f,x+43,29f); moveTo(x+32,35f); lineTo(x+34,37f); lineTo(x+36,35f); moveTo(x+14,33f); lineTo(x+7,31f); moveTo(x+14,36f); lineTo(x+7,37f); moveTo(x+51,33f); lineTo(x+58,31f); moveTo(x+60,44f); cubicTo(x+74,45f,x+76,29f,x+67,30f) })
                    drawOval(c.accent.copy(alpha = .5f), Offset(x+20,34f), Size(6f,3f)); drawOval(c.accent.copy(alpha = .5f), Offset(x+40,34f), Size(6f,3f))
                    drawLine(c.accent, Offset(x+20,16f), Offset(x+23,21f), 1.5f); drawLine(c.accent, Offset(x+47,16f), Offset(x+44,21f), 1.5f)
                }
                cat(0f); if(double) cat(60f)
            }
        }
    }
}
fun ownerName(owner: Owner, p: Preferences): String = when(owner) { Owner.Me -> p.myName; Owner.You -> p.partnerName; Owner.Together -> "Together" }

@Composable fun Badge(label: String, color: Color = AppTheme.colors.secondary) {
    Surface(color = color.copy(alpha = .23f).compositeOver(AppTheme.colors.surface), shape = CircleShape) { PText(label, Modifier.padding(9.dp, 3.dp), MaterialTheme.typography.labelSmall) }
}

@Composable fun RoundCheck(checked: Boolean, label: String, color: Color = AppTheme.colors.secondary, enabled: Boolean=true, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp).semantics { contentDescription = label; stateDescription = if(checked) "Done" else "Not done"; role = Role.Checkbox },enabled=enabled) {
        Icon(if(checked) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(27.dp), tint = color)
    }
}

@Composable fun PokeButton(partner: String, task: Task? = null) {
    val connection=ai.mwm.client.cloud.LocalPartnerConnection.current
    var now by remember {mutableStateOf(System.currentTimeMillis())}
    val until=connection.pokeCooldowns[task?.id] ?: 0L
    LaunchedEffect(until) {while(now<until){kotlinx.coroutines.delay(1000);now=System.currentTimeMillis()}}
    val cooling=until>now
    val send=ai.mwm.client.cloud.LocalPartnerPoke.current
    var explain by remember { mutableStateOf(false) }
    OutlinedButton({ if(task!=null && connection.paired && connection.online) send(task) else explain = true }, enabled=!connection.busy && !cooling, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 12.dp), border = BorderStroke(1.5.dp, AppTheme.colors.accent.copy(alpha = .55f)), shape = CircleShape, colors = ButtonDefaults.outlinedButtonColors(containerColor = AppTheme.colors.accent.copy(alpha = .22f).compositeOver(AppTheme.colors.surface))) { Icon(Icons.Outlined.Pets, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); PText(if(cooling) "Again in ${(until-now)/60000+1} min" else "Poke", style = MaterialTheme.typography.labelSmall) }
    if(explain) AlertDialog(onDismissRequest = { explain = false }, icon = { Icon(Icons.Outlined.Pets, null, tint = AppTheme.colors.accent) }, title = { PText("A little setup first") }, text = { PText("We couldn't send a nudge to $partner. Partner Pokes need a connected shared space and phone push notifications. Connect both accounts, then let your partner enable Receive pokes in Settings.") }, confirmButton = { OutlinedButton({ explain = false }) { PText("Got it") } })
}

@Composable fun OwnerPicker(owner: Owner, partner: String, onChange: (Owner) -> Unit, large: Boolean = false, preferences: Preferences? = null) {
    if(large) Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(22.dp,Alignment.CenterHorizontally)) {
        listOf(Owner.Me,Owner.Together,Owner.You).forEach {value ->
            val color=ownerColor(value)
            Column(Modifier.width(76.dp).selectable(value==owner,role=Role.RadioButton) {onChange(value)},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Surface(Modifier.size(70.dp),shape=CircleShape,color=color,border=if(value==owner) BorderStroke(2.dp,color) else null) {
                    Column(Modifier.padding(4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        if(value==Owner.Together) Duo(16.dp)
                        PText(if(value==Owner.Together) "Us" else value.label,style=MaterialTheme.typography.titleSmall)
                        PText(if(value==Owner.Together) "Both" else if(value==Owner.You) partner else preferences?.myName ?: "User 1",style=MaterialTheme.typography.labelSmall,align=TextAlign.Center)
                    }
                }
                Box(Modifier.size(22.dp).background(if(value==owner) color else Color.Transparent,CircleShape).border(1.dp,AppTheme.colors.separator,CircleShape),contentAlignment=Alignment.Center) {if(value==owner) Icon(Icons.Outlined.Check,null,Modifier.size(14.dp))}
            }
        }
    } else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Owner.entries.forEach { value -> Pill(if(value == Owner.You) partner else if(value == Owner.Me) preferences?.myName ?: "User 1" else value.label, owner == value, ownerColor(value)) { onChange(value) } } }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun TaskRow(task: Task, state: PipoState, onOpen: () -> Unit, onToggle: () -> Unit, aligned: Boolean = true, extra: @Composable (() -> Unit)? = null) {
    val colors = AppTheme.colors
    val editable=ai.mwm.client.cloud.canEditTask(task,state,ai.mwm.client.cloud.LocalPartnerConnection.current)
    val color = ownerColor(task.owner)
    val partner = aligned && task.owner == Owner.You
    val together = aligned && task.owner == Owner.Together
    val start = if(!aligned) 0.dp else if(partner) 52.dp else if(together) 12.dp else 20.dp
    val end = if(!aligned) 0.dp else if(task.owner == Owner.Me) 52.dp else if(together) 12.dp else 20.dp
    val alignment = if(partner) Alignment.End else if(together) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if(partner) TextAlign.End else if(together) TextAlign.Center else TextAlign.Start
    Box(Modifier.fillMaxWidth().padding(start = start, end = end, bottom = 12.dp).drawBehind {
        if(aligned) {
            if(together) drawRoundRect(color, Offset(size.width/2 - 16.dp.toPx(), -6.dp.toPx()), Size(32.dp.toPx(), 3.dp.toPx()), CornerRadius(2.dp.toPx()))
            else drawRoundRect(color, Offset(if(partner) size.width + 5.dp.toPx() else -8.dp.toPx(), 16.dp.toPx()), Size(3.dp.toPx(), (size.height-32.dp.toPx()).coerceAtLeast(0f)), CornerRadius(2.dp.toPx()))
        }
    }) {
        Surface(Modifier.fillMaxWidth().clickable(onClick = onOpen), color = if(task.done) colors.surfaceSunken else if(together) color.copy(alpha = .14f).compositeOver(colors.surface) else colors.surface,
            shape = RoundedCornerShape(20.dp), border = if(task.done) null else BorderStroke(1.dp, colors.separator), shadowElevation = if(task.done) 0.dp else 1.dp) {
            Row(Modifier.then(if(!aligned) Modifier.drawBehind { drawRect(color, size = Size(4.dp.toPx(), size.height)) } else Modifier).padding(horizontal=16.dp,vertical=if(aligned) 16.dp else 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                CompactCheck(task.done, "Complete ${task.title}", color, onToggle,enabled=editable)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = alignment) {
                    PText(task.title, Modifier.fillMaxWidth(), MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 22.sp), align = textAlign, decoration = if(task.done) TextDecoration.LineThrough else null, muted = task.done)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = if(partner) Arrangement.End else if(together) Arrangement.Center else Arrangement.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if(!editable) Badge("Read-only",colors.surfaceSunken)
                        Badge(ownerName(task.owner, state.preferences), color)
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = colors.textSecondary)
                            PText(if(task.done && task.completedAt > 0) "Done ${java.time.Instant.ofEpochMilli(task.completedAt).atZone(clockZone(state.preferences.clockZone)).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault()))}" else timeText(task.minute), style = MaterialTheme.typography.bodySmall, muted = true)
                        }
                        Badge(task.repeat.label, colors.secondary)
                        task.tags.mapNotNull { id -> state.tags.find { it.id == id } }.forEach { tag -> Surface(Modifier.padding(end = 5.dp), color = colors.surfaceSunken, shape = CircleShape) { PText(tag.name, Modifier.padding(8.dp, 3.dp), MaterialTheme.typography.bodySmall, true) } }
                        if(task.note.isNotBlank()) Icon(Icons.Outlined.StickyNote2, "Has a note", Modifier.padding(3.dp).size(14.dp), tint = colors.textSecondary)
                    }
                    if(aligned && task.owner != Owner.Me && !task.done) PokeButton(state.preferences.partnerName,task)
                    extra?.invoke()
                }
            }
        }
        if(task.done && aligned) PawStamp(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-10).dp).size(28.dp), color)
    }
}

@Composable fun CompactCheck(checked: Boolean, label: String, color: Color, onClick: () -> Unit, enabled: Boolean=true) {
    Box(Modifier.size(26.dp).semantics { contentDescription = label; stateDescription = if(checked) "Done" else "Not done"; role = Role.Checkbox }.clip(CircleShape).background(if(checked) color else Color.Transparent).border(if(checked) 0.dp else 2.dp, color, CircleShape).clickable(enabled=enabled,onClick = onClick), contentAlignment = Alignment.Center) {
        if(checked) Icon(Icons.Outlined.Check, null, Modifier.size(17.dp), tint = AppTheme.colors.onPrimary)
    }
}

@Composable fun PawStamp(modifier: Modifier = Modifier.size(32.dp), color: Color = AppTheme.colors.accent) {
    val ink = AppTheme.colors.onPrimary
    Canvas(modifier.rotate(-8f)) {
        drawCircle(color)
        val u = size.width / 24f
        scale(u, u, Offset.Zero) {
            drawOval(ink, Offset(7.8f,12f), Size(8.4f,7f))
            listOf(Offset(6.8f,10f), Offset(10.2f,6.8f), Offset(13.8f,6.8f), Offset(17.2f,10f)).forEach { drawCircle(ink, 1.9f, it) }
        }
    }
}

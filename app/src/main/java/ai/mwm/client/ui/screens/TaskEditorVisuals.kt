package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.*

// Screen-specific geometry from the selected New Task HTML. Do not substitute
// the app's generic pills/cards: their padding and typography differ here.
@Composable internal fun TaskCaption(text: String, modifier: Modifier = Modifier) {
    PText(text, modifier, MaterialTheme.typography.labelSmall.copy(letterSpacing = .6.sp), muted = true)
}
@Composable internal fun TaskSectionLabel(label: String, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if(label == "When") PText(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp), muted = true) else TaskCaption(label); Spacer(Modifier.weight(1f)); action()
    }
}
@Composable internal fun TaskQuietAction(label:String,onClick:()->Unit,modifier:Modifier=Modifier) {
    OutlinedButton(onClick,modifier.heightIn(min=44.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp)) {
        Icon(when {label.contains("Cancel",true)->Icons.Outlined.Close;label.contains("New",true)->Icons.Outlined.Add;label.contains("notification",true)->Icons.Outlined.NotificationsNone;else->Icons.Outlined.Tune},null,Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp));PText(label,style=MaterialTheme.typography.labelLarge)
    }
}
@Composable internal fun TaskChip(label: String, selected: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = AppTheme.colors
    Surface(onClick = onClick, color = if(selected) c.secondary else c.surface, shape = CircleShape,
        border = if(selected) null else BorderStroke(1.5.dp, c.separator)) {
        Row(Modifier.heightIn(min = 36.dp).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if(icon != null) Icon(icon, null, Modifier.size(16.dp), tint = c.textSecondary)
            PText(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium))
        }
    }
}
@Composable internal fun TaskOwnerCircles(owner: Owner, preferences: Preferences, onChange: (Owner) -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally)) {
        listOf(Owner.Me, Owner.Together, Owner.You).forEach { value ->
            val color = ownerColor(value)
            Column(Modifier.width(76.dp).selectable(value == owner, role = Role.RadioButton) { onChange(value) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(70.dp).drawBehind {
                    // The ring sits outside the fixed circle; selection never changes its bounds.
                    if(value == owner) drawCircle(color, size.width / 2 + 4.dp.toPx(), style = Stroke(2.dp.toPx()))
                }.background(color, CircleShape), contentAlignment = Alignment.Center) {
                    if(value == Owner.Together) Canvas(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(22.dp, 12.dp)) {
                        val ink = c.textPrimary.copy(alpha = .7f)
                        drawCircle(ink, 6.dp.toPx() - .75.dp.toPx(), Offset(6.dp.toPx(), 6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                        drawCircle(ink, 6.dp.toPx() - .75.dp.toPx(), Offset(16.dp.toPx(), 6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    }
                    Column(Modifier.padding(top = if(value == Owner.Together) 10.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PText(if(value == Owner.Together) "Us" else value.label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = if(value == owner) FontWeight.SemiBold else FontWeight.Medium))
                        PText(when(value) { Owner.Me -> preferences.myName; Owner.You -> preferences.partnerName; Owner.Together -> "Both" }, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp), align = TextAlign.Center)
                    }
                }
                Box(Modifier.size(22.dp).background(if(value == owner) color else Color.Transparent, CircleShape).then(if(value != owner) Modifier.border(1.5.dp, c.separator, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                    if(value == owner) Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = c.textPrimary)
                }
            }
        }
    }
}
@Composable internal fun TaskSegments(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Surface(modifier, color = AppTheme.colors.surfaceSunken, shape = CircleShape) {
        Row(Modifier.padding(3.dp)) {
            labels.forEachIndexed { index, label ->
                Surface(onClick = { onSelect(index) }, modifier = Modifier.weight(1f), shape = CircleShape, color = if(index == selected) AppTheme.colors.surface else Color.Transparent, shadowElevation = if(index == selected) 1.dp else 0.dp) {
                    Box(Modifier.heightIn(min = 44.dp).padding(horizontal = 4.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                        val repeatLabel = labels == Repeat.entries.map { it.label }
                        if(repeatLabel) androidx.compose.foundation.text.BasicText(appDisplayText(label),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
                                color = if(index == selected) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary, textAlign = TextAlign.Center),
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = .5.sp))
                        else PText(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), muted = index != selected)
                    }
                }
            }
        }
    }
}
@Composable internal fun TaskTime(minute: Int?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), color = AppTheme.colors.surfaceSunken) {
        Row(Modifier.heightIn(min = 50.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, null, Modifier.size(20.dp), tint = AppTheme.colors.textPrimary)
            val text = timeText(minute)
            PText(text.substringBeforeLast(" ", text), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal, letterSpacing = (-.4).sp))
            if(minute != null) PText(text.substringAfterLast(" "), style = MaterialTheme.typography.labelLarge, muted = true)
        }
    }
}
@Composable internal fun TaskReminderPreset(title: String, sub: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), color = if(selected) AppTheme.colors.primary else AppTheme.colors.surface, border = if(selected) null else BorderStroke(1.5.dp, AppTheme.colors.separator)) {
        Column(Modifier.heightIn(min = 56.dp).padding(2.dp, 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if(title == "Custom") Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp), tint = AppTheme.colors.textPrimary)
            PText(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 20.sp))
            if(sub.isNotEmpty()) PText(sub, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), muted = !selected)
        }
    }
}
@Composable internal fun TaskNotePaper(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    Box(modifier.padding(top = 9.dp).rotate(-1.6f)) {
        Surface(Modifier.fillMaxWidth(), color = c.primary.copy(alpha = .30f).compositeOver(c.surface), shape = RoundedCornerShape(3.dp, 3.dp, 14.dp, 3.dp), shadowElevation = 5.dp) {
            Column(Modifier.padding(16.dp, 18.dp, 16.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-9).dp).size(64.dp, 20.dp).rotate(2f).background(c.surfaceElevated.copy(alpha = .62f)).border(.5.dp, c.separator))
    }
}
@Composable internal fun TaskNoteCat() {
    val color = AppTheme.colors.textPrimary
    Canvas(Modifier.size(44.dp, 26.dp)) {
        val s = size.width / 52f
        val p = Path().apply { moveTo(4*s,28*s); cubicTo(6*s,19*s,14*s,14*s,24*s,14*s); cubicTo(28*s,14*s,31*s,15*s,33*s,16*s); lineTo(36*s,10*s); lineTo(38*s,16*s); cubicTo(40*s,17*s,41*s,19*s,41*s,21*s); cubicTo(44*s,21*s,47*s,23*s,48*s,28*s); moveTo(33*s,22*s); cubicTo(30*s,24*s,27*s,24*s,24*s,23*s) }
        drawPath(p, color, style = Stroke(1.6.dp.toPx()))
    }
}

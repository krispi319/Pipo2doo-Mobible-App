package ai.mwm.client.ui.screens

import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*

@Composable fun DashedRule(modifier: Modifier = Modifier) {
    val c = AppTheme.colors.separator
    Canvas(modifier.fillMaxWidth().height(2.dp)) { drawLine(c, Offset.Zero, Offset(size.width,0f), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),4.dp.toPx()))) }
}

@Composable fun PreviewNavigation(selected: String, onSelect: (String) -> Unit) {
    val c = AppTheme.colors
    val slim = selected == "Today" || selected == "Upcoming"
    Surface(Modifier.padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = if(slim) 2.dp else 10.dp), shape = RoundedCornerShape(28.dp,28.dp,34.dp,34.dp), color = c.surfaceElevated, border = BorderStroke(1.dp,c.separator), shadowElevation = 8.dp) {
        Box {
            DashedRule(Modifier.padding(horizontal = 26.dp).offset(y = 5.dp))
            Row(Modifier.fillMaxWidth().padding(6.dp, if(slim) 8.dp else 10.dp,6.dp,if(slim) 4.dp else 8.dp)) {
                listOf("Today" to Icons.Outlined.Home, "Upcoming" to Icons.Outlined.CalendarMonth, "Lists" to Icons.Outlined.Checklist, "Progress" to Icons.Outlined.BarChart, "Us" to Icons.Outlined.PersonOutline).forEach { (name, icon) ->
                    Column(Modifier.weight(1f).selectable(selected == name, role = Role.Tab) { onSelect(name) }.padding(top = 4.dp,bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(56.dp,32.dp).offset(y = if(selected == name) (-2).dp else 0.dp).background(if(selected == name) c.primary.copy(alpha = .38f).compositeOver(c.surfaceElevated) else Color.Transparent,CircleShape), contentAlignment = Alignment.Center) {
                            Icon(icon,name,Modifier.size(22.dp),tint = if(selected == name) c.textPrimary else c.textSecondary)
                            if(selected == name) Row(Modifier.align(Alignment.TopEnd).offset(x = (-9).dp,y = (-2).dp)) { Box(Modifier.size(8.dp).background(c.secondary,CircleShape)); Box(Modifier.offset(x = (-3).dp).size(8.dp).background(c.accent,CircleShape)) }
                        }
                        PText(name,style = MaterialTheme.typography.labelSmall,muted = selected != name)
                    }
                }
            }
        }
    }
}

@Composable fun ApprovedCat(modifier: Modifier, variant: String = "divider") {
    if(variant == "welcome") {
        WelcomePreviewCat(modifier)
        return
    }
    val c = AppTheme.colors
    val dimensions = when(variant) { "care" -> Size(92f,84f); "welcome" -> Size(122f,92f); "nap" -> Size(96f,64f); "stretch" -> Size(196f,92f); "note" -> Size(64f,38f); else -> Size(58f,44f) }
    val paths = when(variant) {
        "care" -> listOf("M22 78c-6-10-6-26 4-36 3-3 3-10 1-17l9 7c6-2 12-2 18 0l9-7c-2 7-2 14 1 17 10 10 10 26 4 36", "M36 50c1 1.5 3 1.5 4 0M52 50c1 1.5 3 1.5 4 0M44 56l2 2 2-2M30 58h-8M30 61l-7 2M62 58h8M62 61l7 2M68 78c8 0 14-4 14-12M8 80h78")
        "welcome" -> listOf("M22 70c-6-20 10-40 36-42 26-2 46 10 50 28 3 14-8 24-26 26H40c-10 0-16-4-18-12z", "M18 58c-6-6-6-16 0-22l4-14 9 9c6-2 12-2 18 0l9-9 3 15c5 7 4 16-3 22", "M26 46c2 2 5 2 7 0M42 46c2 2 5 2 7 0M36 52l1.5 1.5L39 52M14 50l-8-2M14 54l-8 2M60 50l8-2M60 54l8 2M106 60c8 6 8 16-2 20-10 4-30 4-48 2M70 40c6-4 14-4 20 0M86 18c2-3 6-3 7 0M94 10c2-3 6-3 7 0")
        "nap" -> listOf("M16 54c-2-13 8-22 25-22 9 0 15 3 19 7M58 54c-1-9 3-17 13-17s14 8 13 17M62 38l1-12 7 9M79 35l6-9 0 13M67 46q2.5 2 5 0M77 46q2.5 2 5 0M73 50q1.5 1 3 0M16 54c-8-1-10-8-4-11M86 18h5l-5 5h5M80 10h3.5l-3.5 3.5h3.5")
        "stretch" -> listOf("M22 60C10 50 8 30 22 24C30 21 34 30 28 34M26 62C42 50 76 48 104 54C122 58 136 52 148 44M56 68C82 74 122 70 146 58M40 64L32 78L40 78M62 68L60 78L68 78M146 58L176 72L190 72M142 62L168 76L180 76", "M174 36a14 14 0 1 1-28 0a14 14 0 1 1 28 0M150 27L151 13L159 23M163 23L172 14L172 29M154 38q3 3 6 0q3 3 6 0M152 33h1M167 33h1M144 46C150 52 160 52 166 48")
        "note" -> listOf("M6 34c2-10 8-16 16-16 3 0 5 1 7 3M14 20l-2-7 6 4M24 18l3-6 1 7M29 21c4-2 10-2 14 2 5 5 3 11-3 11H8M43 23c6-3 12 0 13 5M17 24h.01M22 24h.01")
        else -> listOf("M8 38c-4-8 2-18 14-19 3-6 0-11 0-11l5 4h6l5-4s-2 6 1 11c9 2 12 9 9 16M8 38h40M48 34c5-1 7-5 5-9M26 17.5c.6.5 1.3.5 2 0M33 17.5c.6.5 1.3.5 2 0M29.5 21l1 .8 1-.8M20 21h-5M20 23l-4 1.5M41 21h5M41 23l4 1.5")
    }
    val parsed = remember(variant) { paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier) {
        val s = minOf(size.width/dimensions.width,size.height/dimensions.height)
        translate((size.width-dimensions.width*s)/2,(size.height-dimensions.height*s)/2) { scale(s,s,Offset.Zero) {
            if(variant == "welcome") parsed.take(2).forEach { drawPath(it,c.surfaceElevated) }
            if(variant == "nap") drawOval(c.primary.copy(alpha = .4f),Offset(8f,51f),Size(80f,12f))
            parsed.forEach { drawPath(it,c.textPrimary,style = Stroke(1.6f,cap = StrokeCap.Round,join = StrokeJoin.Round)) }
            if(variant == "welcome") { drawCircle(c.accent.copy(alpha=.55f),2.6f,Offset(28f,51f));drawCircle(c.accent.copy(alpha=.55f),2.6f,Offset(47f,51f)) }
        } }
    }
}

@Composable fun KittyAvatar(modifier: Modifier, variant: Int = 0) {
    val c = AppTheme.colors
    Canvas(modifier) {
        val u = minOf(size.width,size.height)/48f
        scale(u,u,Offset.Zero) {
            fun path(s: String) = PathParser().parsePathString(s).toPath()
            val head = path("M9 25Q8 14 11 7L19 13Q24 12 29 13L37 7Q40 14 39 25Q39 38 24 39Q9 38 9 25Z")
            drawPath(head, when(variant%4) { 1 -> c.accent.copy(alpha=.45f);2 -> c.secondary.copy(alpha=.5f);3 -> c.surfaceElevated;else -> c.primary.copy(alpha=.55f) }.compositeOver(c.surface))
            drawPath(head,c.textPrimary,style=Stroke(1.6f,join=StrokeJoin.Round))
            drawPath(path("M12.5 11L13 16L16.8 13.4ZM35.5 11L35 16L31.2 13.4Z"),c.accent.copy(alpha=.6f))
            if(variant%4==1) { listOf(18.5f,29.5f).forEach { drawCircle(c.textPrimary,2.6f,Offset(it,25f));drawCircle(c.surface,.9f,Offset(it+.9f,24.1f)) } }
            else drawPath(path("M15.5 25q2.5-2.6 5 0M27.5 25q2.5-2.6 5 0"),c.textPrimary,style=Stroke(1.5f,cap=StrokeCap.Round))
            drawOval(c.accent.copy(alpha=.5f),Offset(11.7f,28.4f),Size(5.6f,3.2f));drawOval(c.accent.copy(alpha=.5f),Offset(30.7f,28.4f),Size(5.6f,3.2f))
            drawPath(path("M22.4 28.6h3.2l-1.6 1.8z"),c.accent)
            drawPath(path("M24 30.4q-1.4 2-3 1M24 30.4q1.4 2 3 1"),c.textPrimary,style=Stroke(1.5f,cap=StrokeCap.Round))
        }
    }
}

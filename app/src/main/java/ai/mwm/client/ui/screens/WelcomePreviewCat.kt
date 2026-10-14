package ai.mwm.client.ui.screens

import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The selected Welcome SVG, preserving its exact paths, colors and paint order.
 * The head is filled AFTER the body's outline so the back never cuts through the face. */
@Composable internal fun WelcomePreviewCat(modifier: Modifier) {
    val c = AppTheme.colors
    val paths = remember {
        listOf(
            "M22 70c-6-20 10-40 36-42 26-2 46 10 50 28 3 14-8 24-26 26H40c-10 0-16-4-18-12z",
            "M18 58c-6-6-6-16 0-22l4-14 9 9c6-2 12-2 18 0l9-9 3 15c5 7 4 16-3 22",
            "M26 46c2 2 5 2 7 0M42 46c2 2 5 2 7 0",
            "M36 52l1.5 1.5L39 52",
            "M14 50l-8-2M14 54l-8 2M60 50l8-2M60 54l8 2",
            "M24 26l2 4M51 26l-2 4",
            "M106 60c8 6 8 16-2 20-10 4-30 4-48 2",
            "M70 40c6-4 14-4 20 0",
            "M86 18c2-3 6-3 7 0M94 10c2-3 6-3 7 0"
        ).map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(modifier) {
        val s = minOf(size.width / 122f, size.height / 92f)
        val ink = Stroke(1.6.dp.toPx() / s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        translate((size.width - 122f * s) / 2, (size.height - 92f * s) / 2) {
            scale(s, s, Offset.Zero) {
                paths.forEachIndexed { index, path ->
                    if(index < 2) drawPath(path, c.surfaceElevated)
                    drawPath(path, when(index) {
                        5 -> c.accent
                        7, 8 -> c.textTertiary
                        else -> c.textPrimary
                    }, style = ink)
                }
                drawCircle(c.accent.copy(alpha = .55f), 2.6f, Offset(28f, 51f))
                drawCircle(c.accent.copy(alpha = .55f), 2.6f, Offset(47f, 51f))
            }
        }
    }
}

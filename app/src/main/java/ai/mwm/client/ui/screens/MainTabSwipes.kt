package ai.mwm.client.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Observe only unclaimed swipes. Horizontal rails and task swipe actions win. */
fun Modifier.mainTabSwipes(enabled: Boolean, tab: String, onTab: (String) -> Unit): Modifier =
    if(!enabled) this else pointerInput(tab) {
        val tabs = listOf("Today", "Upcoming", "Lists", "Progress", "Us")
        val threshold = 72.dp.toPx()
        val verticalLimit = 40.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val start = down.position
            var end = start
            var claimed = false
            var vertical = false
            var multiple = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.find { it.id == down.id } ?: break
                if(event.changes.count { it.pressed } > 1) multiple = true
                end = change.position
                if(abs(end.y - start.y) > verticalLimit) vertical = true
                if(change.position != change.previousPosition && change.isConsumed) claimed = true
            } while(change.pressed)
            val dx = end.x - start.x
            if(!claimed && !vertical && !multiple && abs(dx) > threshold) {
                val next = tabs.indexOf(tab) + if(dx < 0) 1 else -1
                if(next in tabs.indices) onTab(tabs[next])
            }
        }
    }

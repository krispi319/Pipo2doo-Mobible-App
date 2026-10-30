package ai.mwm.client.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled

/** Keep the submitted form still until its callback returns; never discard the draft on failure. */
internal fun Modifier.freezeWhileSaving(saving:Boolean):Modifier = if(!saving)this else
    clearAndSetSemantics {disabled()}.pointerInput(Unit) {
        awaitPointerEventScope {
            while(true)awaitPointerEvent(PointerEventPass.Initial).changes.forEach {it.consume()}
        }
    }

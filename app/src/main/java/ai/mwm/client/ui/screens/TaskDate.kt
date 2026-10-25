package ai.mwm.client.ui.screens

import ai.mwm.client.data.clockZone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Refresh date buckets across midnight even when shared records have not changed. */
@Composable internal fun currentTaskDay(selection: String): Long {
    val day by produceState(LocalDate.now(clockZone(selection)).toEpochDay(),selection) {
        while(true) {
            value=LocalDate.now(clockZone(selection)).toEpochDay()
            delay(30_000)
        }
    }
    return day
}

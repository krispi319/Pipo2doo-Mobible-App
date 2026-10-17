package ai.mwm.client.ui.screens

import ai.mwm.client.R
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

/** One vector mark for headers, launchers and notification artwork. */
@Composable internal fun PipoBrand(modifier:Modifier=Modifier) {
    Image(painterResource(R.drawable.pipo_logo),"Pipo2doo",modifier)
}

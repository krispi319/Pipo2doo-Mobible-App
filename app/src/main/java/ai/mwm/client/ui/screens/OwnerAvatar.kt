package ai.mwm.client.ui.screens

import ai.mwm.client.data.Owner
import ai.mwm.client.data.Preferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Decorative image inside an owner control; its parent announces the full name. */
@Composable internal fun OwnerAvatar(owner: Owner, preferences: Preferences, modifier: Modifier = Modifier) {
    ProfilePhoto(
        photo=if(owner==Owner.Me)preferences.myPhoto else preferences.partnerPhoto,
        me=owner==Owner.Me,
        modifier=modifier.clearAndSetSemantics {},
        preset=if(owner==Owner.Me)preferences.avatar else -1,
        allowRetry=false
    )
}

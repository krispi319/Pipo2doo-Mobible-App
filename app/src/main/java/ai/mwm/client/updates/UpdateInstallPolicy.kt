package ai.mwm.client.updates

/** A verified download is insufficient: installation also requires the user's request and foreground UI. */
fun shouldOpenUpdateInstaller(state:UpdateState,foreground:Boolean):Boolean =
    foreground && state.installPending && state.ready && !state.busy && !state.error

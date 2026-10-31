package ai.mwm.client.data

data class ChatPresence(val visible:Boolean=false,val partnerOnline:Boolean=false,val known:Boolean=false,val saving:Boolean=false,val error:String="")

fun partnerPresenceText(paired:Boolean,connected:Boolean,presence:ChatPresence):String = when {
    !paired -> "Notes on this phone"
    !connected -> "Reconnecting…"
    !presence.known -> "Status unavailable"
    presence.partnerOnline -> "Online"
    else -> "Offline"
}

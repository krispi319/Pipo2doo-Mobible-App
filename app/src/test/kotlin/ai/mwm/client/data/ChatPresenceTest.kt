package ai.mwm.client.data

import org.junit.Assert.*
import org.junit.Test

class ChatPresenceTest {
    @Test fun defaultIsInvisibleAndUnknown() {assertFalse(ChatPresence().visible);assertFalse(ChatPresence().known)}
    @Test fun networkConnectionAloneNeverMeansPartnerIsOnline() {
        assertEquals("Status unavailable",partnerPresenceText(true,true,ChatPresence()))
        assertEquals("Offline",partnerPresenceText(true,true,ChatPresence(known=true)))
    }
    @Test fun partnerOnlineRequiresConfirmedPresenceAndOurConnection() {
        val presence=ChatPresence(known=true,partnerOnline=true)
        assertEquals("Online",partnerPresenceText(true,true,presence))
        assertEquals("Reconnecting…",partnerPresenceText(true,false,presence))
        assertEquals("Notes on this phone",partnerPresenceText(false,true,presence))
    }
}

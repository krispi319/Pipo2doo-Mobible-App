package ai.mwm.client.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CareCodecRegressionTest {
    @Test fun sharedPayloadRoundTripsLogsWithoutLocalControls() {
        val log=DayLog(20743,true,false,"Light","Mild",2,setOf("Chocolate"),setOf("Fatigue"),"A little rest ♡")
        val own=CareState(logs=listOf(log),cycle=30,duration=6,checkIn=true,checkTime=123,consent=true,shareSpace="space",pendingShare=true)
        val payload=CareCodec.encode(own)
        assertEquals(setOf("cycle","duration","logs"),payload.keys().asSequence().toSet())
        val decoded=CareCodec.decode(JSONObject(payload.toString()))
        assertEquals(own.logs,decoded.logs)
        assertEquals(30,decoded.cycle)
        assertEquals(6,decoded.duration)
        assertFalse(decoded.checkIn)
        assertFalse(decoded.consent)
        assertFalse(decoded.pendingShare)
        assertEquals("",decoded.shareSpace)
    }
    @Test fun twoOwnersAndEmptySnapshotsStaySeparate() {
        val a=CareState(logs=listOf(DayLog(20743,note="Owner A")))
        val b=CareState(logs=listOf(DayLog(20744,note="Owner B")),cycle=26)
        assertEquals("Owner A",CareCodec.decode(CareCodec.encode(a)).logs.single().note)
        assertEquals("Owner B",CareCodec.decode(CareCodec.encode(b)).logs.single().note)
        assertTrue(CareCodec.decode(CareCodec.encode(CareState())).logs.isEmpty())
    }
}

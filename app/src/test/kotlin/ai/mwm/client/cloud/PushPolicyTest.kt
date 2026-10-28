package ai.mwm.client.cloud
import org.junit.Assert.*
import org.junit.Test
class PushPolicyTest {
 @Test fun acceptsKnownOpaqueReferencesOnly(){val id="12345678-1234-1234-1234-123456789abc";for(type in listOf("task_event","partner_poke"))assertTrue(validPushData(mapOf("type" to type,"event_id" to id)));for(type in listOf("partner_note","partner_locket"))assertTrue(validPushData(mapOf("type" to type,"record_id" to id)));assertFalse(validPushData(mapOf("type" to "unknown","event_id" to id)));assertFalse(validPushData(mapOf("type" to "task_event","event_id" to "private message")));assertFalse(validPushData(emptyMap()))}
 @Test fun retriesTransientFailuresOnly(){assertTrue(transientPushFailure(java.io.IOException()));assertTrue(transientPushFailure(SupabaseFailure(503,"","")));assertTrue(transientPushFailure(SupabaseFailure(429,"","")));assertFalse(transientPushFailure(SupabaseFailure(403,"","")));assertFalse(transientPushFailure(IllegalStateException()))}
}

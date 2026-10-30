package ai.mwm.client.data

import org.junit.Assert.*
import org.junit.Test

class CareSupportDraftTest {
    private val owner="account-a:space-a"
    @Test fun retryKeepsRecordAndOriginalSendTime() {
        val first=CareSupportDraft(owner).edit("Here for you ♡").prepare(owner,1000)
        val retry=first.prepare(owner,9000)
        assertEquals(first,retry)
        assertEquals(1000L,retry.sentAt)
    }
    @Test fun textTypedDuringSendIsNotClearedByOldSuccess() {
        val sent=CareSupportDraft(owner).edit("First note").prepare(owner,1000)
        val draft=sent.edit("Another thought")
        assertNotEquals(sent.id,draft.id)
        assertEquals(0L,draft.sentAt)
        assertEquals(draft,draft.acknowledge(owner,sent.id))
    }
    @Test fun confirmedMatchingNoteClearsOnlyThatDraft() {
        val sent=CareSupportDraft(owner).edit("Rest today").prepare(owner,1000)
        val next=sent.acknowledge(owner,sent.id)
        assertEquals("",next.message)
        assertEquals(0L,next.sentAt)
        assertNotEquals(sent.id,next.id)
    }
    @Test fun oldAccountOrPartnerResponseCannotClearNewDraft() {
        val current=CareSupportDraft("account-b:space-b",id="same-id",message="New account note")
        assertEquals(current,current.acknowledge(owner,"same-id"))
        assertTrue(runCatching {current.prepare(owner,1000)}.isFailure)
    }
    @Test fun whitespaceOnlyEditRetriesSameServerPayload() {
        val first=CareSupportDraft(owner).edit("Hello").prepare(owner,1000)
        val retry=first.edit(" Hello ").prepare(owner,9000)
        assertEquals(first.id,retry.id)
        assertEquals(first.sentAt,retry.sentAt)
        assertEquals(first.message.trim(),retry.message.trim())
    }
    @Test fun draftDateIsFirstSendRatherThanTypingTime() {
        val typed=CareSupportDraft(owner).edit("A note after midnight")
        assertEquals(0L,typed.sentAt)
        assertEquals(2000L,typed.prepare(owner,2000).sentAt)
    }
    @Test fun emptyNoteCannotBePrepared(){assertTrue(runCatching {CareSupportDraft(owner).edit("  ").prepare(owner,1)}.isFailure)}
    @Test fun textEntryRespectsExistingLimit(){assertEquals(500,CareSupportDraft(owner).edit("a".repeat(600)).message.length)}
}

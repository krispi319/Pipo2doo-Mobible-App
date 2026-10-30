package ai.mwm.client.data

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class ChatPersistenceTest {
    private val owner="account-a"
    private val space="space-a"
    private val sent=ChatComposerDraft("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Message","Hello ♡","photo/path","reply-id")
    private val cleared=ChatComposerDraft("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","Message","")
    private fun data(state:String="Sending",draft:ChatComposerDraft=cleared):ChatConversationData {
        val note=Appreciation(id=sent.id,kind=sent.kind,message=sent.text,picture=sent.picture,replyTo=sent.replyTo,at=1000,taskTitle="Task")
        return ChatConversationData(draft,mapOf(note.id to StoredChatSend(ChatPending(note,state,"Network unavailable"),sent,cleared.id)))
    }
    @Test fun diskCodecKeepsMessageIdPhotoReplyAndRetryOwnership() {
        val original=data("Failed",sent)
        assertEquals(original,ChatConversationCodec.decode(owner,space,ChatConversationCodec.encode(owner,space,original)))
    }
    @Test fun anotherAccountCannotLoadThisConversation() {
        val json=ChatConversationCodec.encode(owner,space,data())
        assertTrue(runCatching {ChatConversationCodec.decode("account-b",space,json)}.isFailure)
    }
    @Test fun anotherPartnerSpaceCannotLoadThisConversation() {
        val json=ChatConversationCodec.encode(owner,space,data())
        assertTrue(runCatching {ChatConversationCodec.decode(owner,"space-b",json)}.isFailure)
    }
    @Test fun interruptedSendRestoresOriginalDraftAndKeepsSameRetryId() {
        val restored=data().interrupted()
        assertEquals(sent,restored.draft)
        assertEquals("Failed",restored.pending.getValue(sent.id).state)
        assertEquals(sent.id,restored.pending.getValue(sent.id).note.id)
        assertEquals(restored,restored.interrupted())
    }
    @Test fun interruptedSendNeverOverwritesNewTyping() {
        val next=cleared.copy(id="cccccccc-cccc-cccc-cccc-cccccccccccc",text="Next thought")
        assertEquals(next,data(draft=next).interrupted().draft)
    }
    @Test fun sentAndFailedStatusesSurviveReopening() {
        for(status in listOf("Sent","Failed")) {
            val original=data(status)
            assertEquals(original,ChatConversationCodec.decode(owner,space,ChatConversationCodec.encode(owner,space,original)).interrupted())
        }
    }
    @Test fun malformedOrDuplicateOutboxCannotReplaceSavedDraft() {
        val json=ChatConversationCodec.encode(owner,space,data())
        val item=json.getJSONArray("outbox").getJSONObject(0)
        json.put("outbox",JSONArray().put(item).put(item))
        assertTrue(runCatching {ChatConversationCodec.decode(owner,space,json)}.isFailure)
        json.put("schema",99)
        assertTrue(runCatching {ChatConversationCodec.decode(owner,space,json)}.isFailure)
    }
    @Test fun serverConfirmationClearsOnlyRestoredSubmittedDraft() {
        val failed=data("Failed",sent)
        val note=failed.pending.getValue(sent.id).note.copy(picture="cloud/photo")
        val confirmed=failed.confirmed(listOf(note))
        assertTrue(confirmed.outbox.isEmpty())
        assertEquals("",confirmed.draft.text)
        assertNotEquals(sent.id,confirmed.draft.id)
    }
    @Test fun serverConfirmationPreservesNewDraftAndEndsInterruptedSending() {
        val next=cleared.copy(id="cccccccc-cccc-cccc-cccc-cccccccccccc",text="New message")
        val sending=data(draft=next)
        val note=sending.pending.getValue(sent.id).note
        val confirmed=sending.confirmed(listOf(note))
        assertEquals(next,confirmed.draft)
        assertTrue(confirmed.outbox.isEmpty())
        assertEquals(sending,sending.confirmed(listOf(note.copy(owner=Owner.You))))
    }
    @Test fun partitionNamesCannotCollideAcrossOwnerAndSpace() {
        assertNotEquals(ChatConversationStore.partition(owner,space),ChatConversationStore.partition(owner,"space-b"))
        assertNotEquals(ChatConversationStore.partition(owner,space),ChatConversationStore.partition("account-b",space))
    }
}

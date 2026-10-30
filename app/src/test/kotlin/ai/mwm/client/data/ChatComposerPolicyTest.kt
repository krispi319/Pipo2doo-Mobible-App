package ai.mwm.client.data

import org.junit.Assert.*
import org.junit.Test

class ChatComposerPolicyTest {
    private val draft=ChatComposerDraft("original","Message"," Hello ","photo","reply")
    private val note=Appreciation(id="original",kind="Message",message="Hello",picture="photo",replyTo="reply")
    private val cleared=ChatComposerDraft("replacement","Message","")
    @Test fun submissionMatchesOriginalContentIncludingTrimmedText(){assertTrue(draft.matches(note))}
    @Test fun retryOfOlderBubbleDoesNotOwnNewerDraft(){assertFalse(draft.copy(id="next",text="New message").matches(note))}
    @Test fun changedAttachmentReplyOrStyleIsNotTheSubmittedDraft(){assertFalse(draft.copy(picture="other").matches(note));assertFalse(draft.copy(replyTo="other").matches(note));assertFalse(draft.copy(kind="Thank you").matches(note))}
    @Test fun failedSendRestoresUntouchedReplacement(){assertTrue(shouldRestoreFailedChatDraft(cleared,"replacement"))}
    @Test fun newTypingSurvivesFailedEarlierSend(){val next=cleared.copy(id="new-text",text="Next draft");assertFalse(shouldRestoreFailedChatDraft(next,"replacement"))}
    @Test fun userClearingNewTypingStillCountsAsNewerDraft(){val next=cleared.copy(id="edited-then-erased");assertFalse(shouldRestoreFailedChatDraft(next,"replacement"))}
    @Test fun newPictureReplyOrKindSurvivesOlderFailure(){for(next in listOf(cleared.copy(id="picture",picture="next-photo"),cleared.copy(id="reply",replyTo="next-reply"),cleared.copy(id="style",kind="Thank you")))assertFalse(shouldRestoreFailedChatDraft(next,"replacement"))}
    @Test fun contentGuardProtectsDraftEvenIfAnEditorForgetsToChangeItsId(){assertFalse(shouldRestoreFailedChatDraft(cleared.copy(text="New text"),"replacement"));assertFalse(shouldRestoreFailedChatDraft(cleared.copy(picture="new-photo"),"replacement"));assertFalse(shouldRestoreFailedChatDraft(cleared.copy(replyTo="new-reply"),"replacement"))}
    @Test fun retryWithoutClearingDoesNotRestoreOverCurrentComposer(){assertFalse(shouldRestoreFailedChatDraft(cleared,null))}
}

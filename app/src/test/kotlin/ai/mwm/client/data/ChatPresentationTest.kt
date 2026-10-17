package ai.mwm.client.data
import org.junit.Assert.*
import org.junit.Test
class ChatPresentationTest {
 @Test fun locketReactionShowsHumanCaption() {
  val note=Appreciation(kind="Heart",message="Loved your picture",taskTitle="4f9f7a67-335c-4cef-850d-08df3a7c1842")
  assertEquals("Locket picture",chatContext(note,PipoState()))
 }
 @Test fun supportRecipientIdNeverBecomesTaskTitle() {
  assertEquals("Care & support",chatContext(Appreciation(kind="CareSupport",message="Hi",taskTitle=newId()),PipoState()))
 }
 @Test fun unknownRecordIdIsHidden() {
  assertNull(chatContext(Appreciation(kind="Thank you",message="",taskTitle=newId()),PipoState()))
 }
 @Test fun taskIdResolvesToTitleAndRealTitlesRemain() {
  val task=Task(title="Feed the cat")
  assertEquals("Feed the cat",chatContext(Appreciation(kind="Thank you",message="",taskTitle=task.id),PipoState(tasks=listOf(task))))
  assertEquals("Shopping",chatContext(Appreciation(kind="Thank you",message="",taskTitle="Shopping"),PipoState()))
  assertNull(chatContext(Appreciation(kind="Message",message="Hi"),PipoState()))
 }
}

package ai.mwm.client.data
import org.junit.Assert.*
import org.junit.Test
class WorkspaceIsolationTest {
 @Test fun newCoupleDoesNotCarryOldPhotoConsentOrDates() {
  val old=Preferences(myName="Kris",partnerName="Old partner",myPhoto="cloud:old/photo.jpg",partnerPhoto="cloud:old/partner.jpg",shareProfile=true,togetherSince=1,code="OLD",avatar=2,language="vi",pokeOptIn=true)
  val next=old.startNewCouple(20000)
  assertEquals("Kris",next.myName);assertEquals(2,next.avatar);assertEquals("vi",next.language);assertTrue(next.pokeOptIn)
  assertEquals(20000,next.togetherSince);assertEquals("",next.partnerPhoto);assertEquals("",next.myPhoto);assertTrue(next.shareProfile)
  assertEquals("/own/private/photo.jpg",old.copy(myPhoto="/own/private/photo.jpg").startNewCouple(20000).myPhoto)
 }
 @Test fun careStartsEmptyOnlyWhenSpaceChanges() {
  val old=CareState(logs=listOf(DayLog(20000,start=true,note="Old history")),consent=true,shareSpace="old",pendingShare=true,checkIn=true,headsUp=true,endReminder=true)
  assertSame(old,old.forCouple("old","old"));assertSame(old,old.forCouple("","first"))
  val next=old.forCouple("old","new")
  assertTrue(next.logs.isEmpty());assertFalse(next.consent);assertFalse(next.pendingShare);assertFalse(next.checkIn);assertEquals("",next.shareSpace)
  assertEquals(1,old.logs.size) // Original state is left available for the encrypted archive.
 }
 @Test fun ownUploadedProfileRestoresAfterRestartOrOnSecondPhone() {
  val local=Preferences(myName="Me",myPhoto="",shareProfile=false,avatar=-1)
  val own=mapOf("name" to "Kris","photo" to "cloud:current/photo.jpg","avatar" to 3)
  val restored=restoreSharedProfile(local,own,null,"Waiting for partner")
  assertEquals("cloud:current/photo.jpg",restored.myPhoto);assertEquals("Kris",restored.myName);assertTrue(restored.shareProfile);assertEquals(3,restored.avatar)
 }
 @Test fun missingPartnerProfileDoesNotReuseFormerPartner() {
  val restored=restoreSharedProfile(Preferences(partnerName="Old partner",partnerPhoto="cloud:old/photo.jpg"),null,null,"User 2")
  assertEquals("User 2",restored.partnerName);assertEquals("",restored.partnerPhoto);assertTrue(restored.shareProfile)
 }
 @Test fun latestServerProfileWinsOverStaleLocalPhotoPath() {
  val local=Preferences(myPhoto="/another-phone/missing.jpg",shareProfile=true)
  val result=restoreSharedProfile(local,mapOf("name" to "Me","photo" to "cloud:new/photo.jpg"),mapOf("name" to "Partner","photo" to "cloud:new/partner.jpg"),"User 2")
  assertEquals("cloud:new/photo.jpg",result.myPhoto);assertEquals("cloud:new/partner.jpg",result.partnerPhoto)
  assertEquals("",restoreSharedProfile(local,mapOf("photo" to ""),null,"User 2").myPhoto)
 }
}

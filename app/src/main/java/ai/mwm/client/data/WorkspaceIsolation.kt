package ai.mwm.client.data

/** Personal identity stays; couple dates, shared-photo references and consent start fresh. */
fun Preferences.startNewCouple(today: Long): Preferences = copy(
    togetherSince=today,partnerName="Partner",partnerPhoto="",code="------",codeExpiresAt=0,
    myPhoto=myPhoto.takeUnless {it.startsWith("cloud:")} ?: "",shareProfile=false
)
fun CareState.forCouple(previous: String,next: String): CareState =
    if(previous.isNotBlank() && previous!=next) CareState() else this

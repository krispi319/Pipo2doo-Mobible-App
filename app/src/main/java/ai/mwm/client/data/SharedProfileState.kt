package ai.mwm.client.data

/** The saved shared profile is authoritative across this owner's phones; never trust an old local path. */
fun restoreSharedProfile(local: Preferences,own: Map<String,Any?>?,other: Map<String,Any?>?,partnerLabel: String): Preferences = local.copy(
    myName=own?.get("name") as? String ?: local.myName,
    avatar=(own?.get("avatar") as? Number)?.toInt() ?: local.avatar,
    myPhoto=if(own!=null) own["photo"] as? String ?: "" else local.myPhoto,
    shareProfile=true,
    partnerName=other?.get("name") as? String ?: partnerLabel,
    partnerPhoto=other?.get("photo") as? String ?: "",
    partnerClockZone=other?.get("timezone") as? String ?: ""
)

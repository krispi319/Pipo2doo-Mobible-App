package ai.mwm.client.cloud

import org.json.JSONObject

/** Personal restores never turn sharing on or roll back currently shared history. */
internal fun privateCareRestore(saved:JSONObject,current:JSONObject):JSONObject {
    val data=JSONObject(saved.toString())
    val vault=data.getJSONObject("vault")
    val live=current.getJSONObject("vault")
    for(key in listOf("consent","shareSpace","pendingShare","history","includeNotes","categories"))vault.put(key,live.opt(key))
    if(live.optBoolean("consent") || live.optBoolean("pendingShare"))
        for(key in listOf("logs","cycle","duration"))vault.put(key,live.get(key))
    data.put("space",current.optString("space"))
    return data
}

internal fun <T> restoreWithRollback(apply:()->T,rollback:()->Unit):T {
    try {return apply()}catch(e:Exception) {
        try {rollback()}catch(recovery:Exception) {
            throw IllegalStateException("Restore interrupted. Local recovery could not finish; keep this installation.",e).also {it.addSuppressed(recovery)}
        }
        throw e
    }
}

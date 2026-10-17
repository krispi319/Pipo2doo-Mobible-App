package ai.mwm.client.data

import android.content.Context

/** Read markers are device-local IDs, partitioned by account and shared space. No message text. */
object ChatReadState {
    fun incoming(notes: List<Appreciation>) = notes.filter {it.owner==Owner.You && !it.sample && it.kind!="Locket"}
    private fun store(context: Context,account: String) = context.getSharedPreferences("chat_read_$account",Context.MODE_PRIVATE)
    fun read(context: Context,space: String,account: String=AccountScope.current(context)): Set<String> =
        store(context,account).getStringSet(space,emptySet())?.toSet() ?: emptySet()
    @Synchronized fun mark(context: Context,space: String,ids: Set<String>) {
        if(space.isBlank()) return
        val prefs=store(context,AccountScope.current(context))
        val all=(prefs.getStringSet(space,emptySet()) ?: emptySet())+ids
        prefs.edit().putStringSet(space,all).apply()
    }
    fun unread(notes: List<Appreciation>,read: Set<String>) = incoming(notes).count {it.id !in read}
}

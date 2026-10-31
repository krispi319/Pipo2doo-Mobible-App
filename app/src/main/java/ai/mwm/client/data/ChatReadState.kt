package ai.mwm.client.data

import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Read markers are device-local IDs, partitioned by account and shared space. No message text. */
object ChatReadState {
    fun incoming(notes: List<Appreciation>) = notes.filter {it.owner==Owner.You && !it.sample && it.kind!="Locket"}
    private fun store(context: Context,account: String) = context.getSharedPreferences("chat_read_$account",Context.MODE_PRIVATE)
    fun read(context: Context,space: String,account: String=AccountScope.current(context)): Set<String> =
        store(context,account).getStringSet(space,emptySet())?.toSet() ?: emptySet()
    fun observe(context: Context,space: String,account: String=AccountScope.current(context)): Flow<Set<String>> = callbackFlow {
        val prefs=store(context,account)
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->
            if(key==space)trySend(read(context,space,account))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read(context,space,account))
        awaitClose {prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }.distinctUntilChanged()
    @Synchronized fun mark(context: Context,space: String,ids: Set<String>,account: String=AccountScope.current(context)) {
        if(space.isBlank() || ids.isEmpty() || account!=AccountScope.current(context)) return
        val prefs=store(context,account)
        val all=(prefs.getStringSet(space,emptySet()) ?: emptySet())+ids
        if(all!=read(context,space,account))prefs.edit().putStringSet(space,all).apply()
    }
    fun unread(notes: List<Appreciation>,read: Set<String>) = incoming(notes).count {it.id !in read}
}

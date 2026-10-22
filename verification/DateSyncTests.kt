import ai.mwm.client.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
object AccountScope {var account="a";fun current(c:Any)=account}
data class Connection(val uid:String="a",val spaceId:String="s",val busy:Boolean=false,val connected:Boolean=true,val online:Boolean=true,val togetherDateKnown:Boolean=false,val togetherDateVersion:Long=0,val message:String="")
class Repo {val state=MutableStateFlow(PipoState());var isRemote=false
 fun updateOwnPreferences(f:(Preferences)->Preferences) {state.value=state.value.copy(preferences=f(state.value.preferences))}
 fun change(f:(PipoState)->PipoState) {state.value=f(state.value)}
}
class Api {var calls=0;var version=0L;var expected=-1L;var fail=false
 suspend fun saveTogetherDate(u:String,s:String,d:Long,v:Long):Map<String,Any?> {calls++;expected=v;if(fail)error("Conflict");version++;return mapOf("since_day" to d,"version" to version)}
}
class Sync {
 val lock=Mutex();val context=Any();val repository=Repo();val api=Api();val mutable=MutableStateFlow(Connection());var refreshes=0
 private fun n(m:Map<String,Any?>,k:String)=(m[k] as? Number)?.toLong() ?: 0L
 private suspend fun refresh() {refreshes++}
    suspend fun setTogetherDate(day:Long,expectedVersion:Long,expectedAccount:String)=lock.withLock {
        require(validTogetherDate(day)) {"Choose a date from 1900 through today."}
        check(AccountScope.current(context)==expectedAccount) {"Account changed. Reopen settings."}
        val c=mutable.value
        check(!c.busy) {"Please wait for the current update."}
        if(!c.connected) {
            check(!repository.isRemote) {"Shared space is reconnecting. Try again when online."}
            repository.updateOwnPreferences {it.copy(togetherSince=day)}
            return@withLock
        }
        check(c.online) {"Reconnect before setting your shared date."}
        mutable.value=c.copy(busy=true)
        try {
            val saved=api.saveTogetherDate(c.uid,c.spaceId,day,expectedVersion)
            check(AccountScope.current(context)==expectedAccount && mutable.value.spaceId==c.spaceId) {"Partner changed. Reopen settings."}
            repository.change {it.copy(preferences=it.preferences.copy(togetherSince=n(saved,"since_day")))}
            mutable.value=mutable.value.copy(togetherDateKnown=true,togetherDateVersion=n(saved,"version"),message="Together date saved · Shared with your partner")
        } catch(e:CancellationException) {throw e} catch(e:Exception) {
            runCatching {refresh()}
            throw e
        } finally {mutable.value=mutable.value.copy(busy=false)}
    }

}
fun main()=runBlocking {
 var n=0;fun test(v:Boolean) {check(v);n++}
 val sync=Sync();sync.setTogetherDate(10000,0,"a")
 test(sync.repository.state.value.preferences.togetherSince==10000L)
 test(sync.mutable.value.togetherDateKnown && sync.mutable.value.togetherDateVersion==1L && !sync.mutable.value.busy)
 test(sync.api.expected==0L)
 sync.api.fail=true;val old=sync.repository.state.value
 test(runCatching {sync.setTogetherDate(11000,0,"a")}.isFailure)
 test(sync.repository.state.value==old && sync.refreshes==1 && !sync.mutable.value.busy)
 val offline=Sync();offline.mutable.value=Connection(online=false)
 test(runCatching {offline.setTogetherDate(10000,0,"a")}.isFailure && offline.api.calls==0)
 val local=Sync();local.mutable.value=Connection(connected=false)
 local.setTogetherDate(9000,0,"a");test(local.repository.state.value.preferences.togetherSince==9000L && local.api.calls==0)
 local.repository.isRemote=true;test(runCatching {local.setTogetherDate(10000,0,"a")}.isFailure)
 test(runCatching {sync.setTogetherDate(10000,0,"other")}.isFailure)
 println("$n actual date-sync method checks passed with repository/transport doubles")
}

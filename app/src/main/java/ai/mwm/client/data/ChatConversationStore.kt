package ai.mwm.client.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class ChatStorageState(val data:ChatConversationData,val ready:Boolean=false,val staging:Boolean=false,val error:String?=null) {
    val sending get()=staging || data.outbox.values.any {it.pending.state=="Sending"}
}
/** Device-private drafts and idempotent retry records; never a replacement for server authorization. */
internal class ChatConversationStore private constructor(private val context:Context,private val owner:String,private val space:String,initialKind:String,private val persistent:Boolean=true) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val guard=Any()
    private val io=Mutex()
    private val writes=Channel<Unit>(Channel.CONFLATED)
    private val file=AtomicFile(File(context.noBackupFilesDir,"chat_drafts_${partition(owner,space)}.bin"))
    private val mutable=MutableStateFlow(ChatStorageState(ChatConversationData(ChatComposerDraft(newId(),initialKind,"")),ready=!persistent))
    val state=mutable.asStateFlow()
    init {
        if(persistent)scope.launch {
            try {
                val data=io.withLock {
                    if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists())mutable.value.data
                    else {
                        val bytes=file.openRead().use {it.readBytes()};require(bytes.size in 13..1_048_576)
                        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
                        ChatConversationCodec.decode(owner,space,JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))).interrupted()
                    }
                }
                synchronized(guard){mutable.value=ChatStorageState(data,ready=true)}
                writes.trySend(Unit)
            }catch(e:CancellationException){throw e}catch(_:Exception){synchronized(guard){mutable.value=mutable.value.copy(error="Saved chat drafts could not be opened. They have not been replaced.")}}
        }
        scope.launch {for(signal in writes) {
            try {if(persistent)io.withLock {val data=synchronized(guard){mutable.value.takeIf {it.ready}?.data};if(data!=null)write(data)}}
            catch(e:CancellationException){throw e}catch(_:Exception){synchronized(guard){mutable.value=mutable.value.copy(error="Draft is kept in memory. Device save failed; keep chat open and retry.")}}
        }}
    }
    private fun editable()=(!persistent || AccountScope.current(context)==owner && owner!="locked") && mutable.value.ready
    fun editDraft(transform:(ChatComposerDraft)->ChatComposerDraft) {
        synchronized(guard){if(!editable())return;val old=mutable.value;val draft=transform(old.data.draft);if(draft==old.data.draft)return;mutable.value=old.copy(data=old.data.copy(draft=draft),error=null)}
        writes.trySend(Unit)
    }
    fun reconcile(messages:List<Appreciation>) {
        synchronized(guard){
            if(!editable())return
            val old=mutable.value;val confirmed=old.data.confirmed(messages)
            if(confirmed==old.data)return
            mutable.value=old.copy(data=confirmed,error=null)
        }
        writes.trySend(Unit)
    }
    /** The queued message reaches encrypted disk before clearing the editor or invoking the network. */
    fun submit(note:Appreciation,send:(Appreciation,(String?)->Unit)->Unit) {
        synchronized(guard){if(!editable() || mutable.value.sending)return;mutable.value=mutable.value.copy(staging=true,error=null)}
        scope.launch {
            try {
                io.withLock {
                    val before=synchronized(guard){check(editable());mutable.value.data}
                    require(before.outbox.size<128 || note.id in before.outbox){"Retry an earlier failed message before sending more."}
                    val original=before.draft.takeIf {it.matches(note)}
                    val replacement=original?.let {ChatComposerDraft(newId(),it.kind,"")}
                    val staged=StoredChatSend(ChatPending(note),original,replacement?.id)
                    val proposed=before.copy(draft=replacement ?: before.draft,outbox=before.outbox+(note.id to staged))
                    write(proposed)
                    synchronized(guard){
                        val latest=mutable.value
                        val owns=original!=null && latest.data.draft.matches(note)
                        val actual=staged.copy(clearedId=if(owns)replacement?.id else null)
                        mutable.value=latest.copy(staging=false,data=latest.data.copy(draft=if(owns)replacement!! else latest.data.draft,outbox=latest.data.outbox+(note.id to actual)))
                    }
                    writes.trySend(Unit)
                }
                val reply=CompletableDeferred<String?>()
                try {
                    withContext(Dispatchers.Main.immediate){send(note){error->reply.complete(error)}}
                    finish(note.id,withTimeout(90_000){reply.await()})
                }catch(_:TimeoutCancellationException){finish(note.id,"Send was not confirmed. Retry the same message.")}
                catch(e:CancellationException){throw e}
                catch(e:Exception){finish(note.id,e.message ?: "Message could not be sent. Retry the same message.")}
            }catch(e:CancellationException){throw e}catch(e:Exception){synchronized(guard){mutable.value=mutable.value.copy(staging=false,error=e.message ?: "Message could not be saved on this phone. Your draft was kept.")}}
        }
    }
    private fun finish(id:String,error:String?) {
        synchronized(guard){
            val old=mutable.value;val staged=old.data.outbox[id] ?: return
            val draft=if(error!=null && staged.submittedDraft!=null && shouldRestoreFailedChatDraft(old.data.draft,staged.clearedId))staged.submittedDraft else old.data.draft
            val done=staged.copy(pending=staged.pending.copy(state=if(error==null)"Sent" else "Failed",error=error))
            mutable.value=old.copy(data=old.data.copy(draft=draft,outbox=old.data.outbox+(id to done)),error=error)
        }
        writes.trySend(Unit)
    }
    private fun write(data:ChatConversationData) {
        if(!persistent)return
        val plain=ChatConversationCodec.encode(owner,space,data).toString().toByteArray(Charsets.UTF_8);require(plain.size<=1_000_000)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());val stream=file.startWrite()
        try {stream.write(cipher.iv+cipher.doFinal(plain));file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}
    }
    companion object {
        private val stores=mutableMapOf<String,ChatConversationStore>()
        @Synchronized fun open(context:Context,owner:String,space:String,initialKind:String):ChatConversationStore {
            require(owner==AccountScope.current(context) && owner!="locked") {"Your account is still opening."}
            return stores.getOrPut(partition(owner,space)){ChatConversationStore(context.applicationContext,owner,space,initialKind)}
        }
        fun preview(context:Context,initialKind:String)=ChatConversationStore(context.applicationContext,"preview","preview",initialKind,persistent=false)
        internal fun partition(owner:String,space:String):String=MessageDigest.getInstance("SHA-256").digest((owner+"\n"+space).toByteArray()).joinToString(""){"%02x".format(it)}
        @Synchronized private fun key():SecretKey {
            val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
            (store.getKey("pipo_chat_drafts_v1",null) as? SecretKey)?.let{return it}
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {init(KeyGenParameterSpec.Builder("pipo_chat_drafts_v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
        }
    }
}

package ai.mwm.client.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SupabaseFailure(val status: Int, val reason: String, message: String): Exception(message)
class SignInRequired: Exception("Sign in to Supabase before creating or joining a shared space.")

/** User tokens only, encrypted with a device key and excluded from Android backup. Never stores passwords. */
class SupabaseClient(private val context: Context) {
    private val config by lazy { JSONObject(context.assets.open("supabase-client-config.json").bufferedReader().use { it.readText() }) }
    private val file = AtomicFile(File(context.noBackupFilesDir, "supabase_session.bin"))
    private val authLock = Mutex()
    @Volatile private var session: JSONObject? = null
    @Volatile private var epoch = 0L
    @Volatile private var loaded = false
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("pipo_supabase_session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("pipo_supabase_session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun load() {
        if(loaded) return
        session = runCatching {
            val bytes = file.openRead().use { it.readBytes() }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
            JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)), Charsets.UTF_8))
        }.getOrNull()
        loaded = true
    }
    private fun save(value: JSONObject) {
        require(value.optString("access_token").isNotBlank() && value.optString("refresh_token").isNotBlank()) { "Sign-in returned no session. Confirm your email, then sign in." }
        value.put("expires_at", System.currentTimeMillis()/1000 + value.optLong("expires_in",3600))
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key())
        val stream=file.startWrite()
        try { stream.write(cipher.iv+cipher.doFinal(value.toString().toByteArray())); file.finishWrite(stream) } catch(e: Exception) {file.failWrite(stream);throw e}
        session=value;loaded=true
    }
    suspend fun hasSession(): Boolean = withContext(Dispatchers.IO) { authLock.withLock { load(); session != null } }
    suspend fun uid(): String { token(); return requireNotNull(session).getJSONObject("user").getString("id") }
    private suspend fun token(): String = withContext(Dispatchers.IO) { authLock.withLock {
        load(); val s=session ?: throw SignInRequired()
        if(s.optLong("expires_at") <= System.currentTimeMillis()/1000+60) {
            try { save(raw("/auth/v1/token?grant_type=refresh_token","POST",JSONObject().put("refresh_token",s.getString("refresh_token")),null) as JSONObject) }
            catch(e: SupabaseFailure) { if(e.status in listOf(400,401,403)) {file.delete();session=null;throw SignInRequired()};throw e }
        }
        requireNotNull(session).getString("access_token")
    } }
    suspend fun authenticate(email: String, password: String, signup: Boolean): Boolean = withContext(Dispatchers.IO) { authLock.withLock {
        val body=JSONObject().put("email",email.trim()).put("password",password)
        val reply=raw(if(signup) "/auth/v1/signup" else "/auth/v1/token?grant_type=password","POST",body,null) as JSONObject
        if(reply.optString("access_token").isBlank()) false else {epoch++;save(reply);true}
    } }
    suspend fun signOut() = withContext(Dispatchers.IO) { authLock.withLock {load();epoch++;file.delete();session=null;loaded=true} }
    suspend fun request(path: String, method: String="GET", body: JSONObject?=null): Any? {
        val generation=epoch
        val jwt=token()
        check(generation==epoch) {"Account changed. Retry from your current account."}
        return try {
            val result=raw(path,method,body,jwt)
            check(generation==epoch) {"Account changed. A stale response was ignored."}
            result
        } catch(e: SupabaseFailure) {
            if(e.status==401 && generation==epoch) {signOut();throw SignInRequired()};throw e
        }
    }
    suspend fun rpc(name: String, body: JSONObject=JSONObject()): Any? = request("/rest/v1/rpc/$name","POST",body)

    /** Authenticated private Storage transport. No public image URLs or service-role secrets. */
    suspend fun photoObject(key: String, upload: ByteArray? = null): ByteArray {
        require(Regex("[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}\\.jpg").matches(key)) {"Invalid picture key."}
        require(upload==null || upload.size in 1..5_242_880) {"Choose a picture smaller than 5 MB."}
        val originalEpoch=epoch
        val jwt=token()
        check(originalEpoch==epoch) {"Account changed. Retry this picture."}
        return withContext(Dispatchers.IO) {
            val endpoint=config.getString("url").trimEnd('/')+"/storage/v1/object/pipo-shared-images/"+key
            val connection=URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod=if(upload==null) "GET" else "POST"
                connection.connectTimeout=15000
                connection.readTimeout=30000
                connection.setRequestProperty("apikey",config.getString("publishableKey"))
                connection.setRequestProperty("Authorization","Bearer $jwt")
                if(upload!=null) {
                    connection.setRequestProperty("Content-Type","image/jpeg")
                    connection.doOutput=true
                    connection.outputStream.use {it.write(upload)}
                }
                val status=connection.responseCode
                val stream=if(status in 200..299) connection.inputStream else connection.errorStream
                val out=java.io.ByteArrayOutputStream()
                stream?.use {input ->
                    val buffer=ByteArray(8192)
                    var count=0
                    while(true) {
                        val n=input.read(buffer)
                        if(n<0) break
                        count+=n
                        check(count<=6_291_456) {"Photo response is too large."}
                        out.write(buffer,0,n)
                    }
                }
                if(status !in 200..299) {
                    val details=runCatching {JSONObject(out.toString("UTF-8"))}.getOrDefault(JSONObject())
                    throw SupabaseFailure(status,details.optString("error",details.optString("statusCode")),
                        details.optString("message","Picture storage failed (HTTP $status)."))
                }
                check(originalEpoch==epoch) {"Account changed. Picture response ignored."}
                out.toByteArray()
            } finally {connection.disconnect()}
        }
    }
    private suspend fun raw(path: String, method: String, body: JSONObject?, jwt: String?): Any? = withContext(Dispatchers.IO) {
        val connection=URL(config.getString("url").trimEnd('/')+path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod=method;connection.connectTimeout=15000;connection.readTimeout=15000
            connection.setRequestProperty("apikey",config.getString("publishableKey"))
            connection.setRequestProperty("Content-Type","application/json")
            connection.setRequestProperty("Accept","application/json")
            if(jwt!=null) connection.setRequestProperty("Authorization","Bearer $jwt")
            if(body!=null) {connection.doOutput=true;connection.outputStream.use {it.write(body.toString().toByteArray())}}
            val status=connection.responseCode
            val text=(if(status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use {it.readText()} ?: ""
            if(status !in 200..299) {
                val error=runCatching {JSONObject(text)}.getOrDefault(JSONObject())
                val reason=error.optString("code",error.optString("error_code"))
                val detail=error.optString("msg",error.optString("message",error.optString("error_description","Supabase request failed (HTTP $status).")))
                throw SupabaseFailure(status,reason,detail)
            }
            if(text.isBlank() || text=="null") null else JSONTokener(text).nextValue()
        } finally {connection.disconnect()}
    }
}

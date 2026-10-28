package ai.mwm.client.updates

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val MAX_APK_BYTES=262_144_000L
private const val MAX_METADATA_BYTES=2_000_000

data class UpdateRelease(val tag:String,val notes:String,val download:String,val bytes:Long,val digest:String="")
data class UpdateState(val repository:String="",val installed:String="",val busy:Boolean=false,val progress:Int?=null,
    val release:UpdateRelease?=null,val ready:Boolean=false,val message:String="",val error:Boolean=false)
class AppUpdater(app:Application):AndroidViewModel(app) {
    private val context=app.applicationContext
    private val preferences=context.getSharedPreferences("pipo_app_updates",Context.MODE_PRIVATE)
    private val installed=context.packageManager.getPackageInfo(context.packageName,0)
    private val apk=File(context.cacheDir,"app_updates/pipo-update.apk")
    private val mutable=MutableStateFlow(UpdateState(repository=preferences.getString("repository","").orEmpty().ifBlank {
        runCatching {JSONObject(context.assets.open("pipo-update-config.json").bufferedReader().use {it.readText()}).optString("repository")}.getOrDefault("")
    },installed=installed.versionName ?: "1.4.0"))
    val state:StateFlow<UpdateState> = mutable
    private var checked=false
    fun configure(value:String) {
        if(mutable.value.busy)return
        try {
            val repo=publicRepository(value)
            check(preferences.edit().putString("repository",repo).commit()) {"Could not save the release link."}
            apk.delete();mutable.value=UpdateState(repository=repo,installed=mutable.value.installed);checked=false;check()
        }catch(e:Exception){mutable.value=mutable.value.copy(error=true,message=e.message ?: "Invalid release link.")}
    }
    fun checkOnce(){if(!checked && mutable.value.repository.isNotBlank())check()}
    fun check() {
        val before=mutable.value
        if(before.busy || before.repository.isBlank())return
        checked=true;mutable.value=before.copy(busy=true,progress=null,error=false,message="Checking for updates…")
        viewModelScope.launch {
            try {
                val repo=publicRepository(before.repository)
                val response=withContext(Dispatchers.IO){connection("https://api.github.com/repos/$repo/releases/latest").let {c->
                    try {val bytes=c.inputStream.use {it.readNBytesCompat(MAX_METADATA_BYTES)};JSONObject(String(bytes,Charsets.UTF_8))}finally{c.disconnect()}
                }}
                require(!response.optBoolean("draft") && !response.optBoolean("prerelease")){"No stable public release is available."}
                val tag=response.getString("tag_name");require(releaseVersion(tag)!=null){"Use a release tag such as v1.4.0."}
                if(!isNewerRelease(tag,before.installed)) {mutable.value=before.copy(release=null,ready=false,message="You’re up to date ✓");return@launch}
                val list=response.optJSONArray("assets")
                val assets=if(list==null)emptyList() else (0 until list.length()).map {list.getJSONObject(it)}.filter {it.optString("name").endsWith(".apk",true)}
                val chosen=when {assets.size==1->assets.single();else->assets.singleOrNull {it.optString("name").contains("universal",true)}}
                    ?: error("Attach one signed APK, or one clearly named universal APK, to this release.")
                val url=chosen.getString("browser_download_url");require(trustedDownload(url,repo)){"The release APK link does not match this repository."}
                val size=chosen.getLong("size");require(size in 1..MAX_APK_BYTES){"The update size is unsupported."}
                val hash=chosen.optString("digest").removePrefix("sha256:").takeIf {it.matches(Regex("[a-fA-F0-9]{64}"))}.orEmpty()
                val release=UpdateRelease(tag,response.optString("body").take(6000),url,size,hash)
                if(before.release!=release)apk.delete()
                mutable.value=before.copy(release=release,ready=before.ready && before.release==release,message="A little update is here")
            }catch(e:CancellationException){throw e}
            catch(e:Exception){mutable.value=before.copy(error=true,message=e.message ?: "Could not check updates. Please retry.")}
        }
    }
    fun download() {
        val before=mutable.value;val release=before.release ?: return
        if(before.busy)return
        mutable.value=before.copy(busy=true,progress=0,error=false,message="Downloading update…")
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    require(trustedDownload(release.download,before.repository)){"Invalid update link."}
                    apk.parentFile?.mkdirs();val partial=File(apk.path+".part");val c=connection(release.download)
                    try {
                        val hash=MessageDigest.getInstance("SHA-256");var count=0L
                        partial.outputStream().use {output->c.inputStream.use {input->
                            val buffer=ByteArray(32*1024)
                            while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;count+=n;require(count<=MAX_APK_BYTES && count<=release.bytes){"The update download is too large."};output.write(buffer,0,n);hash.update(buffer,0,n)
                                mutable.value=mutable.value.copy(progress=((count*100)/release.bytes).toInt().coerceIn(0,100))}
                        }}
                        require(count==release.bytes){"The update download was incomplete. Please retry."}
                        val actual=hash.digest().joinToString(""){"%02x".format(it)}
                        require(release.digest.isBlank() || release.digest.equals(actual,true)){"The update verification failed. Please retry."}
                        validate(partial)
                        if(apk.exists())check(apk.delete()){"Could not replace the old download."}
                        check(partial.renameTo(apk)){"Could not finish saving the update."}
                    } finally {c.disconnect();partial.delete()}
                }
                mutable.value=before.copy(ready=true,message="Update verified · Ready to install")
            }catch(e:CancellationException){mutable.value=before;throw e}
            catch(e:Exception){mutable.value=before.copy(error=true,message=e.message ?: "Download failed. Please retry.")}
        }
    }
    @Suppress("DEPRECATION") private fun code(info:PackageInfo):Long=if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION") private fun signers(info:PackageInfo):Set<String> {
        val values=if(Build.VERSION.SDK_INT>=28) info.signingInfo?.apkContentsSigners else info.signatures
        return values?.map {MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString(""){b->"%02x".format(b)}}?.toSet() ?: emptySet()
    }
    @Suppress("DEPRECATION") private fun validate(file:File) {
        val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val current=context.packageManager.getPackageInfo(context.packageName,flags)
        val next=context.packageManager.getPackageArchiveInfo(file.absolutePath,flags) ?: error("This download is not a valid APK.")
        val problem=updatePackageProblem(current.packageName,code(current),signers(current),next.packageName,code(next),signers(next))
        require(problem==null){problem ?: "Update verification failed."}
    }
    fun install() {
        if(!mutable.value.ready || mutable.value.busy)return
        try {
            validate(apk)
            if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                mutable.value=mutable.value.copy(message="Allow this app to install updates, return here, then tap Install update.")
                return
            }
            val uri=FileProvider.getUriForFile(context,context.packageName+".updates",apk)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }catch(e:Exception){mutable.value=mutable.value.copy(error=true,message=e.message ?: "Could not open the Android installer.")}
    }
    private fun connection(initial:String):HttpURLConnection {
        var url=initial
        repeat(6) {
            require(trustedUpdateHost(url)){"An unsafe update redirect was blocked."}
            val c=URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects=false;c.connectTimeout=15_000;c.readTimeout=20_000
            c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","Pipo2doo-updater")
            val status=c.responseCode
            if(status in listOf(301,302,303,307,308)){val location=c.getHeaderField("Location");c.disconnect();require(!location.isNullOrBlank()){ "The update link is unavailable."};url=URL(URL(url),location).toString()}
            else {if(status !in 200..299){c.disconnect();error(when(status){404->"No public release found. Check the repository link and publish a release with an APK.";403,429->"GitHub is limiting update checks. Please try again later.";else->"Update server returned HTTP $status. Please retry."})};return c}
        }
        error("Too many update redirects.")
    }
}
private fun java.io.InputStream.readNBytesCompat(limit:Int):ByteArray {
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true){val n=read(buffer);if(n<0)break;require(out.size()+n<=limit){"Release information is too large."};out.write(buffer,0,n)}
    return out.toByteArray()
}

package ai.mwm.client.updates

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.AtomicFile
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

data class UpdateState(val repository:String="",val installed:String="",val busy:Boolean=false,val progress:Int?=null,
    val release:UpdateRelease?=null,val ready:Boolean=false,val message:String="",val error:Boolean=false,val installPending:Boolean=false)

/** Public GitHub Releases only. An APK never replaces or exports account data. */
class AppUpdater(app:Application):AndroidViewModel(app) {
    private val context=app.applicationContext
    private val installed=context.packageManager.getPackageInfo(context.packageName,0)
    private val apk=File(context.cacheDir,"app_updates/pipo-update.apk")
    private val cache=AtomicFile(File(context.cacheDir,"app_updates/pipo-update.json"))
    private val repository=runCatching {
        publicRepository(JSONObject(context.assets.open("pipo-update-config.json").bufferedReader().use {it.readText()}).getString("repository"))
    }.getOrDefault(DEFAULT_UPDATE_REPOSITORY)
    private val mutable=MutableStateFlow(UpdateState(repository=repository,installed=installed.versionName ?: "1.4.1"))
    val state:StateFlow<UpdateState> = mutable
    private var checked=false
    private var downloadRequested=false
    private var installAfterDownload=false
    private var waitingForInstallPermission=false
    private var work:Job?=null
    @Volatile private var activeConnection:HttpURLConnection?=null

    /** Passive card composition only checks; an explicit tap also downloads the newer APK. */
    fun checkOnce(){if(!checked)check()}
    fun startUpdate(){installAfterDownload=true;check(autoDownload=true)}
    fun check(autoDownload:Boolean=false) {
        if(mutable.value.busy){if(autoDownload)downloadRequested=true;return}
        downloadRequested=autoDownload
        checked=true
        mutable.value=mutable.value.copy(busy=true,progress=null,error=false,message="Checking for updates…")
        work=viewModelScope.launch {
            try {
                val saved=withContext(Dispatchers.IO){readCached()}
                mutable.value=mutable.value.copy(release=saved,ready=saved!=null)
                val response=withContext(Dispatchers.IO) {
                    val c=connection("https://api.github.com/repos/$repository/releases/latest")
                    try {JSONObject(c.inputStream.use {it.readLimited(MAX_METADATA_BYTES)}.toString(Charsets.UTF_8))}
                    finally {disconnect(c)}
                }
                require(!response.optBoolean("draft") && !response.optBoolean("prerelease")){"No stable public release is available yet."}
                val tag=response.getString("tag_name")
                require(releaseVersion(tag)!=null){"The latest release needs a version tag such as v1.4.1."}
                if(!isNewerRelease(tag,mutable.value.installed)) {
                    withContext(Dispatchers.IO){clearCached()}
                    mutable.value=mutable.value.copy(release=null,ready=false,message="You’re up to date ✓")
                    return@launch
                }
                val release=parseUpdateRelease(response,repository)
                val ready=saved!=null && sameUpdateAsset(saved,release)
                if(!ready)withContext(Dispatchers.IO){clearCached()}
                mutable.value=mutable.value.copy(release=release,ready=ready,message=if(ready)"Ready to install · Your data stays" else "A little update is here")
                if(downloadRequested && !ready)downloadRelease(release)
            }catch(e:CancellationException){mutable.value=mutable.value.copy(progress=null,error=false,message="Update check or download cancelled");throw e}
            catch(e:Exception){mutable.value=mutable.value.copy(progress=null,error=true,message=e.message ?: "Could not check updates. Please retry.")}
            finally {
                mutable.value=mutable.value.copy(busy=false,installPending=installAfterDownload && mutable.value.ready && !mutable.value.error)
                work=null;downloadRequested=false;installAfterDownload=false
            }
        }
    }
    fun download() {
        val release=mutable.value.release ?: return
        if(mutable.value.busy || mutable.value.ready)return
        installAfterDownload=true
        mutable.value=mutable.value.copy(busy=true,error=false)
        work=viewModelScope.launch {
            try {downloadRelease(release)}
            catch(e:CancellationException){mutable.value=mutable.value.copy(progress=null,error=false,message="Download cancelled · Tap Retry when you’re ready");throw e}
            catch(e:Exception){mutable.value=mutable.value.copy(progress=null,error=true,message=e.message ?: "Download failed. Please retry.")}
            finally {mutable.value=mutable.value.copy(busy=false,installPending=installAfterDownload && mutable.value.ready && !mutable.value.error);work=null;downloadRequested=false;installAfterDownload=false}
        }
    }
    fun cancel(){installAfterDownload=false;waitingForInstallPermission=false;mutable.value=mutable.value.copy(installPending=false);if(mutable.value.busy){work?.cancel();runCatching {activeConnection?.disconnect()}}}
    fun deferInstallation(){installAfterDownload=false;waitingForInstallPermission=false;mutable.value=mutable.value.copy(installPending=false)}
    private suspend fun downloadRelease(release:UpdateRelease) {
        mutable.value=mutable.value.copy(progress=0,ready=false,error=false,message="Downloading update…")
        withContext(Dispatchers.IO) {
            require(trustedDownload(release.download,repository)){"Invalid update link."}
            check(apk.parentFile?.mkdirs()==true || apk.parentFile?.isDirectory==true){"Could not create the update folder."}
            require(apk.parentFile!!.usableSpace>release.bytes+5_242_880L){"Not enough free space. Free some storage and tap Retry."}
            val partial=File(apk.path+".part.apk")
            val c=connection(release.download)
            try {
                val hash=MessageDigest.getInstance("SHA-256");var count=0L
                partial.outputStream().use {output->c.inputStream.use {input->
                    val buffer=ByteArray(32*1024)
                    while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;count+=n
                        require(count<=MAX_APK_BYTES && count<=release.bytes){"The update download is larger than expected."}
                        output.write(buffer,0,n);hash.update(buffer,0,n)
                        mutable.value=mutable.value.copy(progress=((count*100)/release.bytes).toInt().coerceIn(0,100))
                    }
                }}
                require(count==release.bytes){"The download was interrupted. Tap Retry to download it again."}
                val actual=hash.digest().toHex()
                require(release.digest.isBlank() || release.digest.equals(actual,true)){"The update verification failed. Please retry."}
                validate(partial,release.tag)
                currentCoroutineContext().ensureActive()
                if(apk.exists())check(apk.delete()){"Could not replace the old update download."}
                check(partial.renameTo(apk)){"Could not finish saving the update."}
                saveCached(release,actual)
            }finally {disconnect(c);partial.delete()}
        }
        mutable.value=mutable.value.copy(ready=true,progress=null,message="Update verified · Ready to install")
    }
    private suspend fun readCached():UpdateRelease? {
        if(!apk.exists())return null
        return try {
            val root=JSONObject(cache.openRead().bufferedReader().use {it.readText()})
            require(root.getString("repository")==repository)
            val release=UpdateRelease(root.getString("tag"),root.optString("notes"),root.getString("download"),root.getLong("bytes"),root.optString("digest"),root.optLong("assetId"),root.optString("updatedAt"))
            require(trustedDownload(release.download,repository) && release.bytes in 1..MAX_APK_BYTES && apk.length()==release.bytes)
            val expected=root.getString("sha256")
            require(expected.matches(Regex("[a-f0-9]{64}")) && fileDigest(apk)==expected)
            validate(apk,release.tag)
            release
        }catch(e:CancellationException){throw e}catch(_:Exception){clearCached();null}
    }
    private fun saveCached(release:UpdateRelease,sha256:String) {
        val root=JSONObject().put("repository",repository).put("tag",release.tag).put("notes",release.notes).put("download",release.download)
            .put("bytes",release.bytes).put("digest",release.digest).put("assetId",release.assetId).put("updatedAt",release.updatedAt).put("sha256",sha256)
        val out=cache.startWrite()
        try {out.write(root.toString().toByteArray(Charsets.UTF_8));cache.finishWrite(out)}catch(e:Exception){cache.failWrite(out);throw e}
    }
    private fun clearCached(){apk.delete();cache.delete()}
    private suspend fun fileDigest(file:File):String {val digest=MessageDigest.getInstance("SHA-256");file.inputStream().use {input->val buffer=ByteArray(32*1024);while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}};return digest.digest().toHex()}
    @Suppress("DEPRECATION") private fun code(info:PackageInfo):Long=if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION") private fun signers(info:PackageInfo):Set<String> {
        val values=if(Build.VERSION.SDK_INT>=28)info.signingInfo?.apkContentsSigners else info.signatures
        return values?.map {MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex()}?.toSet() ?: emptySet()
    }
    @Suppress("DEPRECATION") private fun validate(file:File,releaseTag:String) {
        val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val current=context.packageManager.getPackageInfo(context.packageName,flags)
        val next=context.packageManager.getPackageArchiveInfo(file.absolutePath,flags) ?: error("This download is not a valid APK.")
        val problem=updatePackageProblem(current.packageName,code(current),signers(current),next.packageName,code(next),signers(next),releaseTag,next.versionName.orEmpty())
        require(problem==null){problem ?: "Update verification failed."}
    }
    /** Continue an explicit update after permission; Android still asks for confirmation. */
    fun resumeInstaller() {
        if(mutable.value.ready && !mutable.value.busy) {
            val allowed=Build.VERSION.SDK_INT<26 || context.packageManager.canRequestPackageInstalls()
            val resume=waitingForInstallPermission && allowed
            if(resume)waitingForInstallPermission=false
            mutable.value=mutable.value.copy(error=false,installPending=mutable.value.installPending || resume,
                message=if(allowed)"Ready to install · Android will ask for confirmation" else "Allow Pipo2doo to install updates, then return here")
        }
    }
    fun install() {
        val release=mutable.value.release ?: return
        if(!mutable.value.ready || mutable.value.busy)return
        mutable.value=mutable.value.copy(busy=true,progress=null,error=false,installPending=false,message="Checking the downloaded update…")
        work=viewModelScope.launch {
            try {
                withContext(Dispatchers.IO){
                    val saved=readCached() ?: error("The downloaded update is no longer available. Tap Retry to download it again.")
                    require(sameUpdateAsset(saved,release)){"The downloaded update changed. Tap Retry to check again."}
                }
                if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()) {
                    waitingForInstallPermission=true
                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    mutable.value=mutable.value.copy(message="Allow Pipo2doo to install updates, then return here to continue.")
                    return@launch
                }
                val uri=FileProvider.getUriForFile(context,context.packageName+".updates",apk)
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                mutable.value=mutable.value.copy(error=false,message="Finish in Android’s installer. Your account and data stay in the app.")
            }catch(e:CancellationException){mutable.value=mutable.value.copy(error=false,message="Installation check cancelled · Your download was kept");throw e}
            catch(e:Exception){mutable.value=mutable.value.copy(ready=false,error=true,message=e.message ?: "Could not open the Android installer. Please retry.")}
            finally {mutable.value=mutable.value.copy(busy=false);work=null}
        }
    }
    private fun connection(initial:String):HttpURLConnection {
        var url=initial
        repeat(6) {
            require(trustedUpdateHost(url)){"An unsafe update redirect was blocked."}
            val c=URL(url).openConnection() as HttpURLConnection;activeConnection=c
            try {
                c.instanceFollowRedirects=false;c.connectTimeout=15_000;c.readTimeout=20_000
                c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","Pipo2doo-updater")
                val status=c.responseCode
                if(status in listOf(301,302,303,307,308)) {val location=c.getHeaderField("Location");disconnect(c);require(!location.isNullOrBlank()){"The update link is unavailable."};url=URL(URL(url),location).toString()}
                else {if(status !in 200..299)error(when(status){404->"No public APK release is available yet. A commit or source ZIP is not an installable update.";403,429->"GitHub is limiting update checks. Please try again later.";else->"Update server returned HTTP $status. Please retry."});return c}
            }catch(e:Exception){disconnect(c);throw e}
        }
        error("Too many update redirects.")
    }
    private fun disconnect(c:HttpURLConnection){c.disconnect();if(activeConnection===c)activeConnection=null}
}
private fun ByteArray.toHex()=joinToString(""){"%02x".format(it)}
private fun java.io.InputStream.readLimited(limit:Int):ByteArray {
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true){val n=read(buffer);if(n<0)break;require(out.size()+n<=limit){"Release information is too large."};out.write(buffer,0,n)}
    return out.toByteArray()
}

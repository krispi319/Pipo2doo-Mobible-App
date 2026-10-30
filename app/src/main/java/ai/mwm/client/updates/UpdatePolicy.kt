package ai.mwm.client.updates

import java.net.URI
import org.json.JSONObject

const val DEFAULT_UPDATE_REPOSITORY = "krispi319/Pipo2doo-Mobible-App"

data class UpdateRelease(val tag:String,val notes:String,val download:String,val bytes:Long,val digest:String="",val assetId:Long=0,val updatedAt:String="")


fun publicRepository(input:String):String {
    val value=input.trim().trimEnd('/').removeSuffix(".git")
    val repo=if(value.startsWith("https://github.com/",true)) {
        val uri=URI(value);require(uri.userInfo==null && uri.query==null && uri.fragment==null){"Use the repository or Releases page link."}
        uri.path.trim('/').split('/').take(2).joinToString("/")
    } else value
    require(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}").matches(repo) && !repo.endsWith("/.") && !repo.endsWith("/..")) {"Paste a public GitHub link, such as https://github.com/owner/app/releases."}
    return repo
}
fun releaseVersion(tag:String):List<Int>? = tag.trim().removePrefix("v").removePrefix("V").takeIf {Regex("[0-9]+(\\.[0-9]+){1,3}").matches(it)}?.split('.')?.map {it.toIntOrNull() ?: return null}
fun isNewerRelease(tag:String,installed:String):Boolean {
    val a=releaseVersion(tag) ?: return false;val b=releaseVersion(installed) ?: return false
    for(i in 0 until maxOf(a.size,b.size)){val d=(a.getOrNull(i) ?: 0).compareTo(b.getOrNull(i) ?: 0);if(d!=0)return d>0}
    return false
}
fun trustedDownload(url:String,repository:String):Boolean = runCatching {
    val u=URI(url);val expected=publicRepository(repository).split('/')
    val parts=u.path.trim('/').split('/')
    u.scheme=="https" && u.host.equals("github.com",true) && u.userInfo==null && u.port==-1 && parts.size>=6 &&
        parts[0].equals(expected[0],true) && parts[1].equals(expected[1],true) && parts[2]=="releases" && parts[3]=="download"
}.getOrDefault(false)
fun trustedUpdateHost(url:String):Boolean = runCatching {val u=URI(url);u.scheme=="https" && u.userInfo==null && u.port==-1 && u.host?.lowercase() in setOf("github.com","api.github.com","release-assets.githubusercontent.com","objects.githubusercontent.com","github-releases.githubusercontent.com")}.getOrDefault(false)
fun sameUpdateSigner(installed:Set<String>,downloaded:Set<String>):Boolean = installed.isNotEmpty() && installed==downloaded
fun updatePackageProblem(installedPackage:String,installedCode:Long,installedSigners:Set<String>,downloadedPackage:String,downloadedCode:Long,downloadedSigners:Set<String>,releaseTag:String="",downloadedName:String=""):String? = when {
    installedPackage!=downloadedPackage->"This APK belongs to a different app."
    downloadedCode<=installedCode->"This APK is not newer than the installed app."
    !sameUpdateSigner(installedSigners,downloadedSigners)->"This update uses a different signing key. Keep the installed app; ask for an APK signed with the original key."
    releaseTag.isNotBlank() && !sameReleaseVersion(releaseTag,downloadedName)->"The APK version does not match this release. Please wait for the corrected APK."
    else->null
}

fun sameReleaseVersion(a:String,b:String):Boolean {
    val left=releaseVersion(a) ?: return false;val right=releaseVersion(b) ?: return false
    return (0 until maxOf(left.size,right.size)).all {(left.getOrNull(it) ?: 0)==(right.getOrNull(it) ?: 0)}
}
/** Release notes can change without invalidating the verified APK asset. */
fun sameUpdateAsset(a:UpdateRelease,b:UpdateRelease):Boolean =
    sameReleaseVersion(a.tag,b.tag) && a.download==b.download && a.bytes==b.bytes &&
    a.digest.equals(b.digest,true) && a.assetId==b.assetId && a.updatedAt==b.updatedAt

/** Parse only stable published APK assets; source ZIPs and app bundles are not installers. */
fun parseUpdateRelease(response:JSONObject,repository:String):UpdateRelease {
    require(!response.optBoolean("draft") && !response.optBoolean("prerelease")){"No stable public release is available yet."}
    val tag=response.getString("tag_name")
    require(releaseVersion(tag)!=null){"The latest release needs a version tag such as v1.4.1."}
    val assets=response.optJSONArray("assets")
    val apks=if(assets==null)emptyList() else (0 until assets.length()).map {assets.getJSONObject(it)}.filter {it.optString("name").endsWith(".apk",true)}
    val chosen=if(apks.size==1)apks.single() else apks.singleOrNull {it.optString("name").contains("universal",true)}
        ?: error("The new release does not have one installable APK yet. Please try again later.")
    val url=chosen.getString("browser_download_url")
    require(trustedDownload(url,repository)){"The release APK link does not match this app’s repository."}
    val size=chosen.getLong("size")
    require(size in 1..262_144_000L){"The update size is unsupported."}
    val rawDigest=chosen.optString("digest").takeUnless {it=="null"}.orEmpty()
    val digest=if(rawDigest.isBlank())"" else {
        require(rawDigest.startsWith("sha256:") && rawDigest.removePrefix("sha256:").matches(Regex("[a-fA-F0-9]{64}"))){"The release APK checksum is invalid. Please wait for a corrected release."}
        rawDigest.removePrefix("sha256:")
    }
    return UpdateRelease(tag,response.optString("body").take(6000),url,size,digest,chosen.optLong("id"),chosen.optString("updated_at"))
}

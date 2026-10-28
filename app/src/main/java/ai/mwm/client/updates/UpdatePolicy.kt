package ai.mwm.client.updates

import java.net.URI

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
fun updatePackageProblem(installedPackage:String,installedCode:Long,installedSigners:Set<String>,downloadedPackage:String,downloadedCode:Long,downloadedSigners:Set<String>):String? = when {
    installedPackage!=downloadedPackage->"This APK belongs to a different app."
    downloadedCode<=installedCode->"This APK is not newer than the installed app."
    !sameUpdateSigner(installedSigners,downloadedSigners)->"This update uses a different signing key. Keep the installed app; ask for an APK signed with the original key."
    else->null
}

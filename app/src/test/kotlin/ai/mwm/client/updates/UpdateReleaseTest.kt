package ai.mwm.client.updates

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateReleaseTest {
    private val url="https://github.com/$DEFAULT_UPDATE_REPOSITORY/releases/download/v1.4.2/Pipo2doo.apk"
    private fun asset(name:String="Pipo2doo.apk",link:String=url)=JSONObject().put("name",name).put("browser_download_url",link).put("size",42_000_000).put("id",42).put("updated_at","2026-10-30T12:00:00Z").put("digest","sha256:"+"a".repeat(64))
    private fun release(vararg assets:JSONObject)=JSONObject().put("tag_name","v1.4.2").put("draft",false).put("prerelease",false).put("body","Small fixes").put("assets",JSONArray(assets.toList()))
    @Test fun actualPublicV140AssetIsInstallableMetadata() {
        val stream=javaClass.classLoader!!.getResourceAsStream("github_release_v140.json")!!
        val found=parseUpdateRelease(JSONObject(stream.bufferedReader().use {it.readText()}),DEFAULT_UPDATE_REPOSITORY)
        assertEquals("v1.4.0",found.tag);assertEquals(44_346_733L,found.bytes);assertEquals(685858539L,found.assetId)
        assertEquals("734f9127bef81ab3264dc427e669b7697ad56a61ae6b93340a5881304f41f43c",found.digest)
        assertFalse(isNewerRelease(found.tag,"1.4.1"))
    }
    @Test fun selectsUniversalAmongSeveralApks(){val found=parseUpdateRelease(release(asset("arm64.apk"),asset("pipo-universal.apk")),DEFAULT_UPDATE_REPOSITORY);assertEquals(url,found.download)}
    @Test fun rejectsAmbiguousOrMissingApk(){for(value in listOf(release(),release(asset("source.zip")),release(asset("app.aab")),release(asset("a.apk"),asset("b.apk"))))assertTrue(runCatching {parseUpdateRelease(value,DEFAULT_UPDATE_REPOSITORY)}.isFailure)}
    @Test fun rejectsDraftPrereleaseForeignRepoAndBadDigest(){for(value in listOf(release(asset()).put("draft",true),release(asset()).put("prerelease",true),release(asset(link="https://github.com/other/app/releases/download/v1.4.2/app.apk")),release(asset().put("digest","sha256:bad")),release(asset().put("size",0)),release(asset().put("size",262_144_001))))assertTrue(runCatching {parseUpdateRelease(value,DEFAULT_UPDATE_REPOSITORY)}.isFailure)}
    @Test fun notesAloneNeverInvalidateVerifiedAsset(){val a=parseUpdateRelease(release(asset()),DEFAULT_UPDATE_REPOSITORY);assertTrue(sameUpdateAsset(a,a.copy(notes="New release notes")));assertFalse(sameUpdateAsset(a,a.copy(assetId=43)));assertFalse(sameUpdateAsset(a,a.copy(updatedAt="changed")));assertFalse(sameUpdateAsset(a,a.copy(bytes=a.bytes+1)));assertFalse(sameUpdateAsset(a,a.copy(digest="b".repeat(64))));assertFalse(sameUpdateAsset(a,a.copy(tag="v1.4.3")))}
    @Test fun apkNameMustMatchDisplayedReleaseTag(){assertNull(updatePackageProblem("app",150,setOf("key"),"app",151,setOf("key"),"v1.4.1","1.4.1"));assertNotNull(updatePackageProblem("app",150,setOf("key"),"app",151,setOf("key"),"v1.4.2","1.4.1"));assertNotNull(updatePackageProblem("app",150,setOf("key"),"app",151,setOf("key"),"v1.4.1",""));assertTrue(sameReleaseVersion("v1.4.1","1.4.1.0"));assertFalse(sameReleaseVersion("v1.4.1-beta","1.4.1"))}
}

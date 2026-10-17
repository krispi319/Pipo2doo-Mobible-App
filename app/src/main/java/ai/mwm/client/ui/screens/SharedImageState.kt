package ai.mwm.client.ui.screens

import ai.mwm.client.cloud.LocalPartnerConnection
import ai.mwm.client.cloud.PipoFirebase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*

internal data class SharedImageResult(val bitmap: Bitmap?=null,val loading: Boolean=false,val failed: Boolean=false)
internal data class SharedImageHandle(val result: SharedImageResult,val retry: ()->Unit)

/** No persistent shared-photo cache. Retry transient download failures and discard old-couple images. */
@Composable internal fun rememberSharedImage(photo: String,maxEdge: Int): SharedImageHandle {
    val context=LocalContext.current
    val connection=LocalPartnerConnection.current
    return key(photo,connection.uid,connection.spaceId,connection.generation,connection.online) {
        var retry by remember {mutableIntStateOf(0)}
        val result by produceState(SharedImageResult(loading=photo.isNotBlank()),retry) {
            value=SharedImageResult(loading=photo.isNotBlank())
            if(photo.isBlank() || photo.startsWith("asset:")) {value=SharedImageResult();return@produceState}
            if(photo.startsWith("cloud:") && (!connection.connected || !connection.online)) {value=SharedImageResult(failed=true);return@produceState}
            repeat(if(photo.startsWith("cloud:")) 3 else 1) {attempt->
                try {
                    val bitmap=withContext(Dispatchers.IO) {
                        val bytes=if(photo.startsWith("cloud:")) PipoFirebase.get(context).readPhoto(photo) else java.io.File(photo).readBytes()
                        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                        require(bounds.outWidth>0 && bounds.outHeight>0) {"Picture cannot be decoded"}
                        val options=BitmapFactory.Options().apply {inSampleSize=(maxOf(bounds.outWidth,bounds.outHeight)/maxEdge).coerceAtLeast(1)}
                        BitmapFactory.decodeByteArray(bytes,0,bytes.size,options) ?: error("Picture unavailable")
                    }
                    value=SharedImageResult(bitmap=bitmap);return@produceState
                } catch(e: CancellationException) {throw e} catch(_: Exception) {if(attempt<2 && photo.startsWith("cloud:")) delay(1200L*(attempt+1))}
            }
            value=SharedImageResult(failed=true)
        }
        SharedImageHandle(result) {retry++}
    }
}

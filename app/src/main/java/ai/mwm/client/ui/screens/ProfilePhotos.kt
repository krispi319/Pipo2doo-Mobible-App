package ai.mwm.client.ui.screens

import ai.mwm.client.R
import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable fun ProfilePhoto(photo: String, me: Boolean, modifier: Modifier = Modifier, preset: Int = -1) {
    val context = LocalContext.current
    val connection=ai.mwm.client.cloud.LocalPartnerConnection.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo, connection.uid, connection.generation, connection.online, connection.connected) {
        value = withContext(Dispatchers.IO) { runCatching {
            if(photo.startsWith("cloud:")) {if(!connection.connected || !connection.online)return@runCatching null;val bytes=ai.mwm.client.cloud.PipoFirebase.get(context).readPhoto(photo);return@runCatching BitmapFactory.decodeByteArray(bytes,0,bytes.size)}
            if(photo.isBlank() || photo.startsWith("asset:")) null else {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(photo, options)
                options.inSampleSize = (maxOf(options.outWidth, options.outHeight) / 512).coerceAtLeast(1)
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFile(photo, options)
            }
        }.getOrNull() }
    }
    Box(modifier.clip(CircleShape).background(if(me) AppTheme.colors.primary else AppTheme.colors.accent)) {
        if(bundledPicture(photo) != null) Image(painterResource(bundledPicture(photo)!!), "Sample profile photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if(bitmap != null) Image(bitmap!!.asImageBitmap(), "Profile photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if(preset >= 0) KittyAvatar(Modifier.fillMaxSize().padding(4.dp), variant = preset)
        else Icon(Icons.Outlined.PersonOutline, localized("Empty avatar", LocalAppLanguage.current), Modifier.fillMaxSize().padding(12.dp), tint = AppTheme.colors.textSecondary)
    }
}

@Composable fun PhotoDuo(p: Preferences, diameter: androidx.compose.ui.unit.Dp = 72.dp, middle: String = "", modifier: Modifier = Modifier, onMe: (() -> Unit)? = null, cats: Boolean = false, onPartner: (() -> Unit)? = null) {
    val colors = AppTheme.colors
    Box(modifier.size(diameter * if(cats) 1.46f else 1.62f, diameter)) {
        Box(Modifier.align(Alignment.CenterStart).size(diameter).background(colors.primary, CircleShape))
        Box(Modifier.align(Alignment.CenterEnd).size(diameter).background(colors.accent, CircleShape))
        if(cats) {
            Box(Modifier.align(Alignment.CenterStart).padding(start=8.dp).width(diameter*.46f),contentAlignment=Alignment.Center) { ProfilePhoto(p.myPhoto,true,Modifier.size(52.dp).then(if(onMe!=null) Modifier.clickable(onClick=onMe) else Modifier),p.avatar) }
            Box(Modifier.align(Alignment.CenterEnd).padding(end=8.dp).width(diameter*.46f),contentAlignment=Alignment.Center) { ProfilePhoto(p.partnerPhoto,false,Modifier.size(52.dp),-1) }
        } else {
            ProfilePhoto(p.myPhoto, true, Modifier.align(Alignment.CenterStart).size(diameter).padding(4.dp).then(if(onMe != null) Modifier.clickable(onClick = onMe) else Modifier), p.avatar)
            ProfilePhoto(p.partnerPhoto, false, Modifier.align(Alignment.CenterEnd).size(diameter).padding(4.dp).then(if(onPartner != null) Modifier.clickable(onClick = onPartner) else Modifier))
        }
        Canvas(Modifier.fillMaxSize()) {
            val a = Path().apply { addOval(Rect(0f, 0f, size.height, size.height)) }
            val b = Path().apply { addOval(Rect(size.width-size.height, 0f, size.width, size.height)) }
            drawPath(Path.combine(PathOperation.Intersect, a, b), colors.secondary)
        }
        Box(Modifier.align(Alignment.Center).width(diameter * if(cats) .5f else .34f)) { PText(middle, Modifier.fillMaxWidth(), style = if(cats) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelSmall, align = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}

@Composable fun rememberPhotoPicker(onPhoto: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf(false) }
    var pickerOwner by remember {mutableStateOf(AccountScope.current(context))}
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri != null && pickerOwner==AccountScope.current(context)) scope.launchPhoto(context, uri, pickerOwner, onPhoto) { failure = true }
    }
    if(failure) AlertDialog(onDismissRequest = { failure = false }, title = { PText("Photo unavailable") }, text = { PText("Choose another photo from your gallery.") }, confirmButton = { TextButton({ failure = false }) { PText("OK") } })
    return { pickerOwner=AccountScope.current(context);launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

private fun kotlinx.coroutines.CoroutineScope.launchPhoto(context: Context, uri: Uri, owner: String, onPhoto: (String) -> Unit, onError: () -> Unit) = launch {
    val file = withContext(Dispatchers.IO) { runCatching {
        val dir = File(context.noBackupFilesDir, "pictures_$owner").apply { mkdirs() }
        val file = File(dir, "${newId()}.jpg")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1024).coerceAtLeast(1) }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            val bitmap = BitmapFactory.decodeStream(input, null, options) ?: error("Invalid image")
            val scale = minOf(1f, 768f / maxOf(bitmap.width, bitmap.height))
            val resized = android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
            file.outputStream().use { resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        }
        file.absolutePath
    }.getOrNull() }
    if(owner!=AccountScope.current(context))return@launch
    if(file != null) onPhoto(file) else onError()
}

package ai.mwm.client.ui.screens

import ai.mwm.client.R
import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun bundledPicture(photo: String): Int? = when(photo) {
    "asset:profile_mina" -> R.drawable.profile_mina
    "asset:profile_partner" -> R.drawable.profile_partner
    "asset:demo_travel" -> R.drawable.demo_travel
    "asset:demo_tap" -> R.drawable.demo_tap
    "asset:demo_scarf" -> R.drawable.demo_scarf
    "asset:demo_movie" -> R.drawable.demo_movie
    "asset:demo_locket" -> R.drawable.demo_locket
    "asset:demo_balcony" -> R.drawable.demo_balcony
    else -> null
}

@Composable fun LocalPicture(photo: String, modifier: Modifier = Modifier) {
    val context=LocalContext.current
    val connection=ai.mwm.client.cloud.LocalPartnerConnection.current
    val resource = bundledPicture(photo)
    if(resource != null) Image(painterResource(resource), "Sample picture", modifier, contentScale = ContentScale.Crop)
    else {
        val image=rememberSharedImage(photo,1024)
        val bitmap=image.result.bitmap
        if(bitmap != null) Image(bitmap.asImageBitmap(), "Saved picture", modifier, contentScale = ContentScale.Crop)
        else Box(modifier.background(AppTheme.colors.surfaceSunken), contentAlignment = Alignment.Center) {
            if(image.result.loading) CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
            else if(image.result.failed) IconButton(image.retry) {Icon(Icons.Outlined.Refresh,"Retry picture")}
            else Icon(Icons.Outlined.Image,"No picture yet")
        }
    }
}

@Composable fun rememberCameraPicture(onPhoto: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf(false) }
    var cameraOwner by remember {mutableStateOf(AccountScope.current(context))}
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        val owner=cameraOwner
        if(bitmap != null && owner==AccountScope.current(context)) scope.launch {
            val path = withContext(Dispatchers.IO) { runCatching {
                val folder = File(context.noBackupFilesDir, "pictures_$owner").apply { mkdirs() }
                val file = File(folder, "${newId()}.jpg")
                file.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)) }
                file.absolutePath
            }.getOrNull() }
            if(owner!=AccountScope.current(context))return@launch
            if(path != null) onPhoto(path) else failure = true
        }
    }
    if(failure) AlertDialog(onDismissRequest = {failure=false}, title={PText("Camera unavailable")}, text={PText("Choose a picture from your gallery instead.")}, confirmButton={OutlinedButton({failure=false}) {PText("OK")}})
    return { cameraOwner=AccountScope.current(context);runCatching {camera.launch(null)}.onFailure {failure=true} }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun PictureActions(pictures: List<String>, onChange: (List<String>) -> Unit) {
    val gallery = rememberPhotoPicker { onChange(pictures + it) }
    val camera = rememberCameraPicture { onChange(pictures + it) }
    val c=AppTheme.colors
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {PText("Cover picture",Modifier.weight(1f),MaterialTheme.typography.labelSmall,true);Badge("Optional",c.surfaceSunken)}
        Column(Modifier.fillMaxWidth().background(c.primary.copy(alpha=.1f).compositeOver(c.surface),RoundedCornerShape(14.dp)).drawBehind {drawRoundRect(c.primary.copy(alpha=.55f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),style=Stroke(2.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),5.dp.toPx()))))}.padding(16.dp,18.dp,16.dp,14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(pictures.isNotEmpty()) {
                LocalPicture(pictures.first(),Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)))
                TextButton({onChange(pictures.drop(1))}) {PText("Remove cover picture")}
            } else {
                Surface(Modifier.size(64.dp,52.dp).rotate(-4f),shape=RoundedCornerShape(8.dp),color=c.surface,shadowElevation=1.dp) {Box(contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Image,null,Modifier.size(44.dp,36.dp),tint=c.textSecondary)}}
                PText("Add a picture to this list",style=MaterialTheme.typography.bodyMedium.copy(fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold))
                PText(if(ai.mwm.client.cloud.LocalPartnerConnection.current.connected) "Shared pictures upload when you save the list" else "Shows on your list card · Saved on this phone, not uploaded",style=MaterialTheme.typography.bodySmall,muted=true,align=TextAlign.Center)
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(gallery,contentPadding=PaddingValues(12.dp,0.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=c.surface),border=BorderStroke(1.dp,c.separator)) {Icon(Icons.Outlined.Image,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));PText("Choose from gallery",style=MaterialTheme.typography.labelLarge)}
                OutlinedButton(camera,contentPadding=PaddingValues(12.dp,0.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=c.surface),border=BorderStroke(1.dp,c.separator)) {Icon(Icons.Outlined.PhotoCamera,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));PText("Take photo",style=MaterialTheme.typography.labelLarge)}
            }
        }
    }
}

@Composable fun ListPictureRail(list: SharedList, onSave: (SharedList) -> Unit, editable: Boolean = true) {
    var viewer by remember {mutableStateOf<String?>(null)}
    var manage by remember {mutableStateOf(false)}
    var remove by remember {mutableStateOf<String?>(null)}
    var choose by remember {mutableStateOf(false)}
    val gallery = rememberPhotoPicker {onSave(list.copy(pictures=list.pictures+it))}
    val camera = rememberCameraPicture {onSave(list.copy(pictures=list.pictures+it))}
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {PText("Pictures · ${list.pictures.size}",Modifier.weight(1f),MaterialTheme.typography.titleMedium);OutlinedButton({manage=true},enabled=editable && list.pictures.isNotEmpty()) {PText("Manage")}}
    LazyRow(contentPadding=PaddingValues(20.dp,6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        items(list.pictures) {photo -> val first=photo==list.pictures.first();Surface(onClick={viewer=photo},modifier=Modifier.rotate(if(first) -1.4f else 1.2f),shape=RoundedCornerShape(8.dp),color=AppTheme.colors.surfaceElevated,shadowElevation=3.dp,border=BorderStroke(1.dp,AppTheme.colors.separator)) {Column(Modifier.padding(6.dp)) {Box {LocalPicture(photo,Modifier.size(if(first) 196.dp else 110.dp,110.dp).clip(RoundedCornerShape(6.dp)));if(first) Box(Modifier.padding(6.dp)) {Badge("Cover",AppTheme.colors.surfaceElevated)}};PText(if(first) "Cover picture" else "Picture",Modifier.padding(top=6.dp),MaterialTheme.typography.labelSmall,true)}}}
        item {OutlinedButton({choose=true},enabled=editable,modifier=Modifier.size(116.dp,142.dp),shape=RoundedCornerShape(14.dp)) {Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {Icon(Icons.Outlined.AddPhotoAlternate,null);PText("Add picture",style=MaterialTheme.typography.bodySmall);PText("Camera or gallery",style=MaterialTheme.typography.labelSmall,muted=true)}}}
    }
    if(choose) AlertDialog(onDismissRequest={choose=false},title={PText("Add picture")},confirmButton={OutlinedButton({choose=false;gallery()}) {PText("Gallery")}},dismissButton={OutlinedButton({choose=false;camera()}) {PText("Camera")}})
    viewer?.let {photo -> AlertDialog(onDismissRequest={viewer=null},text={LocalPicture(photo,Modifier.fillMaxWidth().height(260.dp))},confirmButton={OutlinedButton({viewer=null}) {PText("Close")}})}
    if(manage) AlertDialog(onDismissRequest={manage=false},title={PText("Manage pictures")},text={LazyColumn {items(list.pictures) {photo -> Column {LocalPicture(photo,Modifier.fillMaxWidth().height(100.dp));Row {OutlinedButton({onSave(list.copy(pictures=listOf(photo)+list.pictures.filterNot {it==photo}))}) {PText("Make cover")};OutlinedButton({remove=photo}) {PText("Remove")}}}}}},confirmButton={OutlinedButton({manage=false}) {PText("Done")}})
    remove?.let {photo -> AlertDialog(onDismissRequest={remove=null},title={PText("Remove this picture?")},text={PText("The attachment will be removed from this list.")},confirmButton={OutlinedButton({onSave(list.copy(pictures=list.pictures-photo));remove=null}) {PText("Remove")}},dismissButton={OutlinedButton({remove=null}) {PText("Cancel")}})}
}

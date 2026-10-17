package ai.mwm.client.ui.screens

import ai.mwm.client.cloud.LocalPartnerConnection
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable internal fun ChatPhotoViewer(photo: String,onClose: ()->Unit) {
    val connection=LocalPartnerConnection.current
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        key(photo,connection.uid,connection.spaceId) {
            var scale by remember {mutableFloatStateOf(1f)}
            var offset by remember {mutableStateOf(Offset.Zero)}
            val gesture=rememberTransformableState {zoom,pan,_->
                scale=(scale*zoom).coerceIn(1f,4f)
                offset=if(scale==1f) Offset.Zero else Offset((offset.x+pan.x).coerceIn(-1600f,1600f),(offset.y+pan.y).coerceIn(-1600f,1600f))
            }
            Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
                val imageModifier=Modifier.fillMaxSize().transformable(gesture).graphicsLayer {scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y}
                val bundled=bundledPicture(photo)
                if(bundled!=null) Image(painterResource(bundled),"Chat picture",imageModifier,contentScale=ContentScale.Fit)
                else {
                    val handle=rememberSharedImage(photo,2048)
                    val bitmap=handle.result.bitmap
                    if(bitmap!=null) Image(bitmap.asImageBitmap(),"Chat picture · Pinch to zoom",imageModifier,contentScale=ContentScale.Fit)
                    else if(handle.result.loading) CircularProgressIndicator(Modifier.align(Alignment.Center),color=Color.White)
                    else Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("Picture could not be loaded",color=Color.White)
                        IconButton(handle.retry) {Icon(Icons.Outlined.Refresh,"Retry picture",tint=Color.White)}
                    }
                }
                IconButton(onClose,Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color.Black.copy(alpha=.5f))) {Icon(Icons.Outlined.Close,"Close picture",tint=Color.White)}
                Text("Pinch to zoom · Drag to move",Modifier.align(Alignment.BottomCenter).padding(16.dp),color=Color.White,style=MaterialTheme.typography.labelMedium)
            }
        }
    }
}

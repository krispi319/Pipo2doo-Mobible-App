package ai.mwm.client.ui.screens

import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.time.*
import kotlin.math.*

@Composable internal fun ClockFace(time:ZonedDateTime?,modifier:Modifier=Modifier) {
    val c=AppTheme.colors
    Canvas(modifier) {
        val center=Offset(size.width/2,size.height/2);val radius=minOf(size.width,size.height)*.44f
        drawCircle(c.surfaceElevated,radius,center)
        drawCircle(c.separator,radius,center,style=Stroke(1.2.dp.toPx()))
        repeat(4) {i->val a=i*PI/2;drawCircle(c.textSecondary,1.dp.toPx(),center+Offset((sin(a)*radius*.79).toFloat(),(-cos(a)*radius*.79).toFloat()))}
        if(time!=null) {
            fun hand(angle:Double,length:Float,width:Float) {drawLine(c.textPrimary,center,center+Offset((sin(angle)*radius*length).toFloat(),(-cos(angle)*radius*length).toFloat()),width,StrokeCap.Round)}
            hand((time.hour%12+time.minute/60.0)*PI/6,.46f,1.7.dp.toPx())
            hand(time.minute*PI/30,.65f,1.3.dp.toPx())
            drawCircle(c.primary,2.dp.toPx(),center)
        }
    }
}
@Composable internal fun TimezoneChoiceDialog(mine:Boolean,onClose:()->Unit,onSelect:(String)->Unit) {
    var query by remember {mutableStateOf("")}
    val common=listOf("Europe/Berlin","Asia/Ho_Chi_Minh","America/New_York","America/Los_Angeles","Asia/Tokyo","UTC")
    val options=remember(query) {if(query.isBlank()) common else ZoneId.getAvailableZoneIds().sorted().filter {it.replace('_',' ').contains(query,true)}}
    AlertDialog(onDismissRequest=onClose,title={PText(if(mine) "My clock timezone" else "Partner clock on this phone")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,label={PText("Search city or region")})
        OutlinedButton({onSelect("")},Modifier.fillMaxWidth()) {PText(if(mine) "Automatic · Phone timezone" else "Use partner's shared timezone")}
        LazyColumn(Modifier.heightIn(max=280.dp)) {items(options,key={it}) {zone->OutlinedButton({onSelect(zone)},Modifier.fillMaxWidth()) {PText(zone.replace('_',' '),Modifier.fillMaxWidth())}}}
    }},confirmButton={TextButton(onClose) {PText("Close")}})
}

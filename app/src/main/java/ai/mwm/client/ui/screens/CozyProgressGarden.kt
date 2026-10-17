package ai.mwm.client.ui.screens

import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlin.math.*

/** Original vector artwork, crisp at every screen size and using the selected app theme. */
@Composable fun CozyProgressGarden(completed: Int,streak: Int,month: Boolean) {
    val c=AppTheme.colors
    Surface(Modifier.padding(20.dp,10.dp).fillMaxWidth(),shape=RoundedCornerShape(28.dp),color=c.secondary.copy(alpha=.13f).compositeOver(c.surface)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    PText("OUR LITTLE GARDEN",style=MaterialTheme.typography.labelSmall,muted=true)
                    PText(if(completed==0) "Good things take time" else "Growing together",style=MaterialTheme.typography.headlineSmall)
                }
                Surface(shape=RoundedCornerShape(14.dp),color=c.surface) {Column(Modifier.padding(10.dp,8.dp),horizontalAlignment=Alignment.CenterHorizontally) {PText("$streak",style=MaterialTheme.typography.titleLarge);PText("day streak",style=MaterialTheme.typography.labelSmall,muted=true)}}
            }
            GardenCats(completed,Modifier.fillMaxWidth().height(156.dp))
            PText("$completed little wins this ${if(month) "month" else "week"}",style=MaterialTheme.typography.titleSmall)
            PText(if(completed==0) "A fresh patch, ready for your first little win ♡" else "Every little thing you do helps us bloom ♡",style=MaterialTheme.typography.bodySmall,muted=true)
        }
    }
}

@Composable private fun GardenCats(completed: Int,modifier: Modifier) {
    val c=AppTheme.colors
    val ink=c.textPrimary.copy(alpha=.8f)
    val peach=c.primary.copy(alpha=.55f).compositeOver(c.surface)
    val cream=c.accent.copy(alpha=.3f).compositeOver(c.surface)
    val leaf=c.secondary
    Canvas(modifier.semantics {contentDescription="Two cuddly cats tending a little flower garden"}) {
        val factor=minOf(size.width/320f,size.height/156f)
        translate((size.width-320*factor)/2,(size.height-156*factor)/2) {scale(factor,factor,Offset.Zero) {
            drawOval(leaf.copy(alpha=.16f),Offset(10f,106f),Size(300f,43f))
            // A soft sun and little floating hearts frame the two cats.
            drawCircle(c.primary.copy(alpha=.24f),24f,Offset(265f,31f))
            repeat(8) {i->val a=i*PI/4;val p=Offset(265f+cos(a).toFloat()*31,31f+sin(a).toFloat()*31);drawLine(c.primary.copy(alpha=.5f),p,p+Offset(cos(a).toFloat()*5,sin(a).toFloat()*5),2f,StrokeCap.Round)}
            fun heart(x: Float,y: Float,s: Float,color: Color) {drawPath(Path().apply {moveTo(x,y+s);cubicTo(x-s*2,y-s*.2f,x-s*.8f,y-s*1.7f,x,y-s*.6f);cubicTo(x+s*.8f,y-s*1.7f,x+s*2,y-s*.2f,x,y+s);close()},color)}
            heart(157f,23f,7f,c.accent);heart(38f,39f,4f,c.primary);heart(205f,14f,3f,c.secondary)
            fun cat(x: Float,y: Float,fur: Color,mirror: Boolean) {
                translate(x,y) {scale(if(mirror) -1f else 1f,1f,Offset.Zero) {
                    drawPath(Path().apply {moveTo(-30f,53f);cubicTo(-65f,62f,-63f,20f,-46f,30f)},fur,style=Stroke(12f,cap=StrokeCap.Round))
                    drawRoundRect(fur,Offset(-28f,17f),Size(58f,56f),CornerRadius(27f))
                    val head=Path().apply {moveTo(-31f,0f);lineTo(-30f,-25f);lineTo(-13f,-14f);quadraticTo(0f,-19f,13f,-14f);lineTo(30f,-25f);lineTo(31f,0f);cubicTo(40f,37f,-40f,37f,-31f,0f);close()}
                    drawPath(head,fur);drawPath(head,ink,style=Stroke(1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                    drawPath(Path().apply {moveTo(-26f,-16f);lineTo(-24f,-5f);lineTo(-16f,-11f);close();moveTo(26f,-16f);lineTo(24f,-5f);lineTo(16f,-11f);close()},c.accent.copy(alpha=.45f))
                    listOf(-12f,12f).forEach {eye->drawArc(ink,5f,170f,false,Offset(eye-4,3f),Size(8f,5f),style=Stroke(2f,cap=StrokeCap.Round))}
                    drawOval(c.accent.copy(alpha=.5f),Offset(-26f,12f),Size(12f,6f));drawOval(c.accent.copy(alpha=.5f),Offset(14f,12f),Size(12f,6f))
                    drawPath(Path().apply {moveTo(-3f,13f);lineTo(3f,13f);lineTo(0f,16f);close()},ink)
                    drawPath(Path().apply {moveTo(-5f,18f);quadraticTo(0f,24f,5f,18f)},ink,style=Stroke(1.5f,cap=StrokeCap.Round))
                    drawOval(fur,Offset(-24f,61f),Size(22f,13f));drawOval(fur,Offset(4f,61f),Size(22f,13f))
                    drawLine(ink,Offset(-21f,70f),Offset(-13f,70f),1.2f,StrokeCap.Round);drawLine(ink,Offset(13f,70f),Offset(21f,70f),1.2f,StrokeCap.Round)
                }}
            }
            cat(113f,48f,peach,false);cat(193f,48f,cream,true)
            // A handful of blooms responds to completed work; empty state shows tiny seedlings.
            repeat(5) {i->val x=38f+i*61f;val y=141f-(i%2)*7
                drawLine(leaf,Offset(x,149f),Offset(x,y-12),2.5f,StrokeCap.Round)
                drawOval(leaf.copy(alpha=.75f),Offset(x-10,y-4),Size(10f,5f))
                if(completed>i) {
                    repeat(5) {petal->val a=petal*2*PI/5;drawCircle(if(i%2==0) c.accent else c.primary,5f,Offset(x+cos(a).toFloat()*6,y-15+sin(a).toFloat()*6))}
                    drawCircle(c.surface,3f,Offset(x,y-15))
                }
            }
        }}
    }
}

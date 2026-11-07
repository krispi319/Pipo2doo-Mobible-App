package ai.mwm.client.ui.screens

import ai.mwm.client.R
import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*

/** Us account card only; shared SettingsOrbit retains its existing layouts. */
@Composable internal fun CompactSettingsProfiles(p:Preferences,days:Long?,modifier:Modifier=Modifier,onEditPhoto:(()->Unit)?=null,onMilestones:(()->Unit)?=null) {
    val c=AppTheme.colors
    val connection=ai.mwm.client.cloud.LocalPartnerConnection.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val diameter=minOf(108.dp,((maxWidth-72.dp)/2).coerceAtLeast(64.dp))
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(true,false).forEachIndexed {index,me->
            if(index==1) Column(Modifier.width(56.dp).then(if(onMilestones!=null)Modifier.clickable(onClick=onMilestones).semantics {contentDescription="Open our milestones"} else Modifier),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.8.dp)) {
                Icon(Icons.Outlined.Favorite,null,Modifier.size(18.dp),tint=c.accent)
                BasicText(days?.toString() ?: "♡",Modifier.fillMaxWidth(),style=MaterialTheme.typography.headlineSmall.copy(color=c.textPrimary,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center),maxLines=1,softWrap=false,autoSize=TextAutoSize.StepBased(minFontSize=10.sp,maxFontSize=24.sp,stepSize=1.sp))
                PText(if(days==null) "Set our date" else "Days\ntogether",style=MaterialTheme.typography.labelSmall.copy(fontSize=10.sp,lineHeight=12.sp),muted=true,align=TextAlign.Center)
            }
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.2.dp)) {
                Box(Modifier.size(diameter).semantics {contentDescription=if(me)"My settings avatar" else "Partner settings avatar"}) {
                    Box(Modifier.fillMaxSize().border(2.dp,if(me)c.primary else c.accent,CircleShape).padding(4.dp).clip(CircleShape)) {
                        val photo=if(me)p.myPhoto else p.partnerPhoto
                        if(photo==if(me)"asset:profile_mina" else "asset:profile_partner") Image(painterResource(if(me)R.drawable.settings_demo_me else R.drawable.settings_demo_partner),"Sample portrait",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        else ProfilePhoto(photo,me,Modifier.fillMaxSize(),if(me)p.avatar else -1)
                    }
                    if(me && onEditPhoto!=null) OutlinedIconButton(onEditPhoto,modifier=Modifier.align(Alignment.BottomEnd).size(32.dp),colors=IconButtonDefaults.outlinedIconButtonColors(containerColor=c.surface,contentColor=c.textPrimary),border=BorderStroke(1.dp,c.separator)) {Icon(Icons.Outlined.Edit,"Change my photo",Modifier.size(15.dp))}
                    else if(!me) Box(Modifier.align(Alignment.BottomEnd).size(24.dp).background(c.surface,CircleShape).border(1.dp,c.separator,CircleShape),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Lock,"Partner photo is read-only",Modifier.size(12.dp),tint=c.textSecondary)}
                }
                Text(if(me)p.myName else p.partnerName,Modifier.fillMaxWidth(),style=MaterialTheme.typography.titleSmall,color=c.textPrimary,textAlign=TextAlign.Center,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                val role=if(me)connection.role else connection.partnerRole
                if(role.isNotBlank()) PText(ai.mwm.client.cloud.roleLabel(role),style=MaterialTheme.typography.labelSmall,muted=true,align=TextAlign.Center)
            }
        }
    }
}
}

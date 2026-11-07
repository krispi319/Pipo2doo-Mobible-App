package ai.mwm.client.ui.screens

import ai.mwm.client.R
import ai.mwm.client.cloud.LocalPartnerConnection
import ai.mwm.client.cloud.roleLabel
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*

/** The milestones page's larger portraits; shared SettingsOrbit layouts stay unchanged. */
@Composable internal fun MilestonesCoupleCard(p:Preferences,days:Long,onEditPhoto:()->Unit) {
    val c=AppTheme.colors
    val connection=LocalPartnerConnection.current
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=c.surface,border=BorderStroke(1.dp,c.separator)) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val diameter=minOf(108.dp,((maxWidth-72.dp)/2).coerceAtLeast(64.dp))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    @Composable fun Portrait(me:Boolean,modifier:Modifier) {
                        Column(modifier,horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(diameter).semantics {contentDescription=if(me)"My milestones avatar" else "Partner milestones avatar"}) {
                                Box(Modifier.fillMaxSize().border(2.dp,if(me)c.primary else c.accent,CircleShape).padding(4.dp).clip(CircleShape)) {
                                    val photo=if(me)p.myPhoto else p.partnerPhoto
                                    if(photo==if(me)"asset:profile_mina" else "asset:profile_partner")
                                        Image(painterResource(if(me)R.drawable.settings_demo_me else R.drawable.settings_demo_partner),"Sample portrait",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                                    else ProfilePhoto(photo,me,Modifier.fillMaxSize(),if(me)p.avatar else -1)
                                }
                                if(me)OutlinedIconButton(onEditPhoto,modifier=Modifier.align(Alignment.BottomEnd).size(32.dp),shape=CircleShape,
                                    border=BorderStroke(1.dp,c.separator),colors=IconButtonDefaults.outlinedIconButtonColors(containerColor=c.surface,contentColor=c.textPrimary)) {
                                    Icon(Icons.Outlined.Edit,"Change my photo",Modifier.size(15.dp))
                                } else Box(Modifier.align(Alignment.BottomEnd).size(24.dp).background(c.surface,CircleShape).border(1.dp,c.separator,CircleShape),contentAlignment=Alignment.Center) {
                                    Icon(Icons.Outlined.Lock,"Partner photo is read-only",Modifier.size(12.dp),tint=c.textSecondary)
                                }
                            }
                            Text(if(me)p.myName else p.partnerName,Modifier.fillMaxWidth(),style=MaterialTheme.typography.titleSmall,color=c.textPrimary,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
                            val role=if(me)connection.role else connection.partnerRole
                            if(role.isNotBlank())PText(roleLabel(role),style=MaterialTheme.typography.labelSmall,muted=true,align=TextAlign.Center)
                        }
                    }
                    Portrait(true,Modifier.weight(1f))
                    Column(Modifier.width(56.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Outlined.Favorite,null,Modifier.size(15.dp),tint=c.accent)
                        BasicText(days.toString(),Modifier.fillMaxWidth(),style=MaterialTheme.typography.headlineSmall.copy(color=c.textPrimary,textAlign=TextAlign.Center),maxLines=1,softWrap=false,
                            autoSize=TextAutoSize.StepBased(minFontSize=10.sp,maxFontSize=24.sp,stepSize=1.sp))
                        PText("Days\ntogether",style=MaterialTheme.typography.labelSmall.copy(fontSize=10.sp,lineHeight=12.sp),muted=true,align=TextAlign.Center)
                    }
                    Portrait(false,Modifier.weight(1f))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.separator.copy(alpha=.3f)))
            PText("Together since ${dayText(p.togetherSince)}",Modifier.fillMaxWidth(),MaterialTheme.typography.labelSmall,muted=true,align=TextAlign.Center)
        }
    }
}

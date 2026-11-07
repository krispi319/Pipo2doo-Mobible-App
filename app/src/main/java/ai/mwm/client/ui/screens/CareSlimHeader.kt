package ai.mwm.client.ui.screens

import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared presentation for local, unpaired and paired care views. Ownership stays in CareScreen. */
@Composable internal fun CareSlimHeader(name:String,partner:String,partnerView:Boolean,onPartnerView:(Boolean)->Unit,
    onRefresh:()->Unit,cycleTitle:String?,cycleSubtitle:String,notice:@Composable ()->Unit={}) {
    val c=AppTheme.colors
    var choosing by remember {mutableStateOf(false)}
    val selectedName=if(partnerView)partner else name
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                PText("Period & Care",Modifier.weight(1f),MaterialTheme.typography.titleLarge.copy(fontSize=19.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold))
                Box(Modifier.widthIn(max=112.dp)) {
                    Surface(onClick={choosing=true},shape=CircleShape,color=c.secondary.copy(alpha=.38f),
                        border=BorderStroke(1.dp,c.separator),modifier=Modifier.semantics {
                            contentDescription="Choose care profile";stateDescription=selectedName
                        }) {
                        Row(Modifier.padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(selectedName,Modifier.weight(1f,fill=false),style=MaterialTheme.typography.bodySmall,color=c.textPrimary,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Icon(Icons.Outlined.ArrowDropDown,null,Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(choosing,{choosing=false}) {
                        DropdownMenuItem(text={PText(name,preserveContent=true)},onClick={choosing=false;onPartnerView(false)},modifier=Modifier.semantics {contentDescription="View my care records"})
                        DropdownMenuItem(text={PText(partner,preserveContent=true)},onClick={choosing=false;onPartnerView(true)},modifier=Modifier.semantics {contentDescription="View partner care records"})
                    }
                }
                IconButton(onRefresh,Modifier.size(34.dp)) {Icon(Icons.Outlined.Refresh,"Refresh care records",Modifier.size(19.dp))}
            }
            PText("A little understanding, together",style=MaterialTheme.typography.bodySmall.copy(fontSize=11.sp,lineHeight=14.sp),muted=true)
        }
        notice()
        if(cycleTitle!=null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.separator))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    PText(cycleTitle,style=MaterialTheme.typography.titleLarge.copy(fontSize=19.sp,lineHeight=24.sp))
                    PText(cycleSubtitle,style=MaterialTheme.typography.bodySmall.copy(fontSize=11.sp,lineHeight=14.sp),muted=true)
                }
                ApprovedCat(Modifier.size(36.dp,32.dp),"care")
            }
        }
    }
}

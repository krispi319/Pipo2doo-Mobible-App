package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable internal fun CompactOwnerPicker(owner:Owner,p:Preferences,onChange:(Owner)->Unit) {
    val c=AppTheme.colors
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(Owner.Me,Owner.Together,Owner.You).forEach {value->
            val chosen=owner==value;val color=ownerColor(value)
            Column(Modifier.weight(1f).selectable(chosen,role=Role.RadioButton,onClick={onChange(value)}).padding(vertical=4.dp).semantics {contentDescription="List for ${ownerName(value,p)}"},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(48.dp).border(if(chosen)2.dp else 1.dp,if(chosen)color else c.separator,CircleShape).padding(4.dp),contentAlignment=Alignment.Center) {
                    if(value==Owner.Together) Box(Modifier.fillMaxSize().background(c.secondary.copy(alpha=.24f),CircleShape),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(24.dp),tint=c.textPrimary)}
                    else ProfilePhoto(if(value==Owner.Me)p.myPhoto else p.partnerPhoto,value==Owner.Me,Modifier.fillMaxSize(),if(value==Owner.Me)p.avatar else -1)
                    if(chosen) Icon(Icons.Outlined.CheckCircle,null,Modifier.align(Alignment.BottomEnd).size(16.dp).background(c.surface,CircleShape),tint=color)
                }
                Text(ownerName(value,p),style=MaterialTheme.typography.labelSmall,color=c.textPrimary,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

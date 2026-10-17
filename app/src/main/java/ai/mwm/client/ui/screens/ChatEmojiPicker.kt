package ai.mwm.client.ui.screens

import ai.mwm.client.data.CHAT_EMOJIS
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun ChatEmojiPicker(title:String,selected:String?=null,onDismiss:()->Unit,onSelect:(String)->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(selected!=null) Text("One reaction each · tap yours again to remove",style=MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=320.dp)) {
                CHAT_EMOJIS.chunked(4).forEach {row->item {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                        row.forEach {emoji->
                            if(emoji==selected) FilledTonalIconButton({onSelect(emoji)},Modifier.size(48.dp)) {Text(emoji,fontSize=24.sp)}
                            else IconButton({onSelect(emoji)},Modifier.size(48.dp)) {Text(emoji,fontSize=24.sp)}
                        }
                        repeat(4-row.size) {Spacer(Modifier.size(48.dp))}
                    }
                }}
            }
        }
    },confirmButton={TextButton(onDismiss) {Text("Done")}})
}

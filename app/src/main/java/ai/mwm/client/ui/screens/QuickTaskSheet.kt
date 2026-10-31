package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable internal fun QuickTaskSheet(draft:Task,state:PipoState,saving:Boolean,error:String?,onChange:(Task)->Unit,
    onSave:()->Unit,onMore:()->Unit,onClose:()->Unit,onNewList:()->Unit) {
    val focus=remember {FocusRequester()}
    var listsOpen by remember {mutableStateOf(false)}
    val canSave=!saving && draft.title.isNotBlank() && state.lists.any {it.id==draft.listId}
    LaunchedEffect(Unit) {withFrameNanos {};focus.requestFocus()}
    ModalBottomSheet(onDismissRequest={if(!saving)onClose()},containerColor=AppTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            PText("Add a little task",style=MaterialTheme.typography.titleLarge)
            PText("Today · Anytime",style=MaterialTheme.typography.bodySmall,muted=true)
            OutlinedTextField(draft.title,{onChange(draft.copy(title=it.take(80)))},Modifier.fillMaxWidth().focusRequester(focus),
                enabled=!saving,label={PText("What needs doing?")},singleLine=true,
                keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Sentences,imeAction=ImeAction.Done),
                keyboardActions=KeyboardActions(onDone={if(canSave)onSave()}))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(Owner.Me,Owner.Together,Owner.You).forEach {owner->
                    FilterChip(selected=draft.owner==owner,onClick={onChange(draft.copy(owner=owner))},enabled=!saving,label={PText(ownerName(owner,state.preferences))})
                }
            }
            Box {
                OutlinedButton({listsOpen=true},enabled=!saving) {PText(state.lists.find {it.id==draft.listId}?.name ?: "Choose a list")}
                DropdownMenu(listsOpen,{listsOpen=false}) {
                    state.lists.forEach {list->DropdownMenuItem(text={PText(list.name)},onClick={onChange(draft.copy(listId=list.id));listsOpen=false})}
                    DropdownMenuItem(text={PText("New list")},onClick={listsOpen=false;onNewList()})
                }
            }
            error?.let {PText(it,style=MaterialTheme.typography.bodySmall)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onMore,Modifier.weight(1f).heightIn(min=48.dp),enabled=!saving) {PText("More options")}
                Button(onSave,Modifier.weight(1f).heightIn(min=48.dp),enabled=canSave) {
                    if(saving)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp) else PText("Save task")
                }
            }
            TextButton(onClose,enabled=!saving) {PText("Cancel")}
        }
    }
}

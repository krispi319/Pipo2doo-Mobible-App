package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ChatEmojiPicker(title:String,selected:String?=null,onDismiss:()->Unit,onSelect:(String)->Unit) {
    val c=AppTheme.colors
    val choices=remember(selected!=null) {emojiPickerChoices(selected!=null)}
    val categories=remember(choices) {choices.map {it.category}.distinct()}
    var category by rememberSaveable {mutableStateOf("People")}
    var menu by remember {mutableStateOf(false)}
    var credits by remember {mutableStateOf(false)}
    val shown=choices.filter {it.category==category}
    val grid=rememberLazyGridState()
    val uri=LocalUriHandler.current
    LaunchedEffect(category) {grid.scrollToItem(0)}
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),sheetMaxWidth=480.dp,sheetGesturesEnabled=false,dragHandle=null,containerColor=c.surfaceElevated,shape=RoundedCornerShape(22.dp,22.dp,0.dp,0.dp)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.76f).padding(top=6.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton({menu=true},shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=8.dp)) {
                        Icon(when(category){"People"->Icons.Outlined.SentimentSatisfiedAlt;"Love"->Icons.Outlined.FavoriteBorder;"Nature"->Icons.Outlined.LocalFlorist;"Food"->Icons.Outlined.Restaurant;else->Icons.Outlined.Celebration},null,Modifier.size(21.dp))
                        Spacer(Modifier.width(7.dp));PText(category,style=MaterialTheme.typography.titleSmall);Spacer(Modifier.width(7.dp));Icon(Icons.Outlined.ExpandMore,null,Modifier.size(18.dp))
                    }
                    DropdownMenu(menu,{menu=false}) {categories.forEach {value->DropdownMenuItem(text={PText(value)},onClick={category=value;menu=false},trailingIcon={if(value==category)Icon(Icons.Outlined.Check,null,Modifier.size(18.dp))})}}
                }
                IconButton({credits=true}) {Icon(Icons.Outlined.Info,"Emoji artwork credits",Modifier.size(20.dp),tint=c.textSecondary)}
                OutlinedIconButton(onDismiss) {Icon(Icons.Outlined.Close,"Close emoji picker",Modifier.size(20.dp))}
            }
            PText(if(selected!=null) "One reaction each · tap yours again to remove" else title,Modifier.padding(horizontal=16.dp,vertical=4.dp),MaterialTheme.typography.labelSmall,true)
            HorizontalDivider(color=c.separator.copy(alpha=.5f))
            LazyVerticalGrid(columns=GridCells.Adaptive(48.dp),modifier=Modifier.weight(1f).fillMaxWidth(),state=grid,contentPadding=PaddingValues(8.dp,10.dp,8.dp,20.dp),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                items(shown,key={it.glyph}) {choice->val chosen=choice.glyph==selected
                    Surface(onClick={onSelect(choice.glyph)},modifier=Modifier.fillMaxWidth().height(50.dp).semantics {contentDescription=choice.name;this.selected=chosen},shape=RoundedCornerShape(9.dp),color=if(chosen)c.secondary.copy(alpha=.2f).compositeOver(c.surface) else Color.Transparent,border=if(chosen)BorderStroke(1.5.dp,c.secondary) else null) {
                        Box(Modifier.fillMaxSize().padding(5.dp),contentAlignment=Alignment.Center) {FlatChatEmoji(choice.glyph,Modifier.size(38.dp))}
                    }
                }
            }
        }
    }
    if(credits) AlertDialog(onDismissRequest={credits=false},title={PText("Emoji artwork")},text={PText("Twemoji graphics by Twitter, Inc. and contributors. Licensed under CC BY 4.0. SVG artwork converted to Android vector resources.",preserveContent=true)},confirmButton={TextButton({credits=false}) {PText("Done")}},dismissButton={Row {TextButton({uri.openUri("https://github.com/twitter/twemoji")}) {PText("Source")};TextButton({uri.openUri("https://creativecommons.org/licenses/by/4.0/")}) {PText("License")}}})
}

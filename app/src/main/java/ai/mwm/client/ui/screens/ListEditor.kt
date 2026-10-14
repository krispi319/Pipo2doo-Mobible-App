package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp

private data class EmojiChoice(val icon: String,val name: String,val category: String)
private val emojiLibrary=listOf(
    "Travel" to listOf("✈️:Plane flight","🏖️:Beach seaside","🗺️:Map","🚆:Train","⛺:Camping tent","🏔️:Mountain","🚲:Bicycle","🚗:Car","🏝️:Island","🎡:Ferris wheel","🗼:Tower","🚢:Ship","🛶:Kayak canoe","🧭:Compass","🧳:Suitcase packing"),
    "Food" to listOf("🛒:Groceries shopping","🥕:Carrot","🍜:Noodles","🍕:Pizza","🍫:Chocolate","🍓:Strawberry","🥑:Avocado","🍋:Lemon","☕:Coffee","🍵:Tea","🍰:Cake","🥛:Milk","🍞:Bread","🍎:Apple"),
    "Home" to listOf("🏡:House","🧺:Laundry basket","🔧:Repairs wrench","🧹:Cleaning broom","🛏️:Bed","🛋️:Sofa","💡:Light bulb","🔑:Key","🪣:Bucket","🧻:Paper","🪥:Toothbrush","🧽:Sponge","🪴:Plant","🧾:Bills receipt"),
    "Nature" to listOf("🐈:Cat","🐕:Dog","🌿:Herbs leaves","🌷:Flower","🌳:Tree","🌻:Sunflower","🌊:Ocean","🌞:Sun","🌙:Moon","🌈:Rainbow","🦋:Butterfly","🐠:Fish","🍄:Mushroom","🐾:Paw"),
    "Fun" to listOf("🎬:Movie cinema","🎁:Gift present","🎨:Art paint","🎮:Game","🎸:Music guitar","📚:Books","⚽:Football","🎂:Birthday","🎯:Darts","🧩:Puzzle","🎒:Backpack","🪁:Kite","🎟️:Tickets","🎵:Music"),
    "Symbols" to listOf("🧡:Orange heart","💚:Green heart","💗:Pink heart","⭐:Star","✅:Check","📌:Pin","💬:Message","✨:Sparkles","♾️:Infinity","☮️:Peace","🎉:Celebration","🔔:Bell","📅:Calendar","📝:Notes"),
    "Pipo themes" to listOf("🐈:Sleeping cat","🐾:Paw","🧶:Yarn ball","🐟:Fish","🏡:Shared cushion home","♡:Heart","🪴:Windowsill")
).flatMap {(category,entries)->entries.map {val parts=it.split(':',limit=2);EmojiChoice(parts[0],parts[1],category)}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ListEditor(list: SharedList?, onSave: (SharedList)->Unit, onClose: ()->Unit,preferences: Preferences=Preferences()) {
    val context=LocalContext.current
    val recentsStore=remember {context.getSharedPreferences("pipo_emoji_recents",0)}
    val recents=remember {recentsStore.getString("icons", "🛒|🧳|🎁|🧺|🍜|🪴|🎬")!!.split('|')}
    var name by rememberSaveable(list?.id) {mutableStateOf(list?.name ?: "")}
    var emoji by rememberSaveable(list?.id) {mutableStateOf(list?.emoji ?: "🧺")}
    var pictures by remember(list?.id) {mutableStateOf(list?.pictures ?: emptyList<String>())}
    var owner by rememberSaveable(list?.id) {mutableStateOf(list?.owner ?: Owner.Together)}
    var query by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf("Travel")}
    val language=LocalAppLanguage.current
    val choices=if(query.isNotBlank()) emojiLibrary.filter {it.name.contains(query,true)||localized(it.name,language).contains(query,true)||it.category.contains(query,true)||localized(it.category,language).contains(query,true)||it.icon.contains(query)} else if(category=="Recent") recents.map {EmojiChoice(it,"Recently used","Recent")} else emojiLibrary.filter {it.category==category}
    val c = AppTheme.colors
    ModalBottomSheet(onDismissRequest=onClose,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=c.surfaceElevated,shape=RoundedCornerShape(28.dp,28.dp,0.dp,0.dp),scrimColor=c.textPrimary.copy(alpha=.34f),dragHandle={Box(Modifier.padding(top=10.dp,bottom=2.dp).size(40.dp,4.dp).background(c.separator,CircleShape))}) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.90f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start=20.dp,end=12.dp),verticalAlignment=Alignment.CenterVertically) {PText(if(list==null) "New list" else "Edit list",Modifier.weight(1f),MaterialTheme.typography.titleLarge);TextButton(onClose) {PText("Cancel",decoration=TextDecoration.Underline)}}
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp,6.dp,20.dp,16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item {
                    Surface(color=c.surface,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,c.separator)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp,10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Surface(Modifier.size(56.dp),color=ownerColor(owner).copy(alpha=.3f).compositeOver(c.surface),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.5.dp,c.secondary.copy(alpha=.7f))) {Box(contentAlignment=Alignment.Center) {Text(emoji,fontSize=30.sp)}}
                            Column(Modifier.weight(1f)) {PText("List name",style=MaterialTheme.typography.labelSmall,muted=true);SheetInput(name,{name=it.take(80)},"List name",Modifier.fillMaxWidth(),18.sp)}
                        }
                    }
                }
                item {PictureActions(pictures) {pictures=it}}
                item {ListSheetGroup {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {PText("Whose list",Modifier.weight(1f),MaterialTheme.typography.labelSmall,true);Badge(if(ai.mwm.client.cloud.LocalPartnerConnection.current.connected) "Shared space" else "Local mode",c.surface)}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(Owner.Me,Owner.Together,Owner.You).forEach {value ->Surface(onClick={owner=value},modifier=Modifier.weight(1f),shape=CircleShape,color=if(owner==value) ownerColor(value) else c.surface,border=if(owner==value) null else BorderStroke(1.5.dp,c.separator)) {Box(Modifier.padding(6.dp,10.dp),contentAlignment=Alignment.Center) {PText(if(value==Owner.Me) "Me" else ownerName(value,preferences),style=MaterialTheme.typography.labelLarge)}}}}
                }}
                item {Surface(color=c.surface,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.5.dp,c.secondary.copy(alpha=.6f))) {Row(Modifier.fillMaxWidth().padding(14.dp,10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Surface(Modifier.size(44.dp),shape=CircleShape,color=c.secondary.copy(alpha=.22f).compositeOver(c.surface),border=BorderStroke(2.dp,c.secondary)) {Box(contentAlignment=Alignment.Center) {Text(emoji,fontSize=24.sp)}};Column(Modifier.weight(1f)) {PText("Type your own emoji",style=MaterialTheme.typography.labelSmall,muted=true);SheetInput(emoji,{emoji=it.take(16)},"Custom emoji from keyboard",Modifier.fillMaxWidth(),16.sp,underline=false)};Badge("In use",c.secondary)}}}
                item {ListSheetGroup {
                    Row {PText("Icon library",Modifier.weight(1f),MaterialTheme.typography.labelSmall,true);Badge("Search or type",c.surface)}
                    Surface(shape=CircleShape,color=c.surface,border=BorderStroke(1.5.dp,c.separator)) {Row(Modifier.padding(14.dp,2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {Icon(Icons.Outlined.Search,null,Modifier.size(18.dp));SheetInput(query,{query=it},"Search emoji — try Beach",Modifier.weight(1f),16.sp,underline=false)}}
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {items(listOf("Recent","Pipo themes","Travel","Food","Home","Nature","Fun","Symbols")) {value->Pill(value,category==value,c.secondary) {category=value;query=""}}}
                }}
                if(query.isBlank() && category!="Recent") item {EmojiGroup("Recently used",recents.map {EmojiChoice(it,"Recently used","Recent")},emoji) {emoji=it}}
                if(query.isBlank() && category!="Pipo themes") item {EmojiGroup("Pipo themes",emojiLibrary.filter {it.category=="Pipo themes"},emoji) {emoji=it}}
                item {EmojiGroup(if(query.isBlank()) if(category=="Travel") "Travel & places" else category else "Search results",choices,emoji) {emoji=it}}
            }
            DashedRule()
            Button({recentsStore.edit().putString("icons",(listOf(emoji)+recents.filterNot {it==emoji}).take(14).joinToString("|")).apply();onSave((list ?: SharedList(name=name)).copy(name=name.trim(),emoji=emoji.trim(),owner=owner,pictures=pictures));onClose()},enabled=name.isNotBlank()&&emoji.isNotBlank(),modifier=Modifier.fillMaxWidth().padding(20.dp,10.dp).heightIn(min=52.dp)) {PText(if(list==null) "Create list" else "Save list")}
        }
    }
}

@Composable internal fun SheetInput(value: String,onChange: (String)->Unit,label: String,modifier: Modifier=Modifier,fontSize: androidx.compose.ui.unit.TextUnit=18.sp,underline: Boolean=true) {
    val c=AppTheme.colors
    BasicTextField(value,onChange,modifier.heightIn(min=36.dp).semantics {contentDescription=label}.then(if(underline) Modifier.drawBehind {drawLine(c.separator,Offset(0f,size.height),Offset(size.width,size.height),1.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),4.dp.toPx())))} else Modifier).padding(vertical=6.dp),singleLine=true,textStyle=MaterialTheme.typography.bodyLarge.copy(fontSize=fontSize,fontWeight=FontWeight.SemiBold,color=c.textPrimary),cursorBrush=SolidColor(c.primary),keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Sentences),decorationBox={inner->if(value.isBlank()) PText(label,style=MaterialTheme.typography.bodyMedium,muted=true);inner()})
}

@Composable private fun ListSheetGroup(content: @Composable ColumnScope.()->Unit) {Surface(color=AppTheme.colors.surfaceSunken,shape=RoundedCornerShape(14.dp)) {Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}

@Composable private fun EmojiGroup(title: String,choices: List<EmojiChoice>,selectedIcon: String,onSelect: (String)->Unit) {
    val c=AppTheme.colors
    ListSheetGroup {
        Row {PText(title,Modifier.weight(1f),MaterialTheme.typography.labelSmall,true);Badge(choices.size.toString(),c.surface)}
        choices.chunked(7).forEach {row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {row.forEach {choice->Surface(onClick={onSelect(choice.icon)},modifier=Modifier.weight(1f).heightIn(min=42.dp).semantics {contentDescription=choice.name;selected=selectedIcon==choice.icon},color=if(selectedIcon==choice.icon) c.secondary.copy(alpha=.34f).compositeOver(c.surface) else c.surface,shape=RoundedCornerShape(8.dp),border=BorderStroke(if(selectedIcon==choice.icon) 2.dp else 1.dp,if(selectedIcon==choice.icon) c.secondary else c.separator)) {Box(contentAlignment=Alignment.Center) {Text(choice.icon,fontSize=22.sp,modifier=Modifier.padding(4.dp))}}};repeat(7-row.size) {Spacer(Modifier.weight(1f))}}}
        if(choices.isEmpty()) PText("No matching icons. Try another word or enter your own emoji.",muted=true)
    }
}

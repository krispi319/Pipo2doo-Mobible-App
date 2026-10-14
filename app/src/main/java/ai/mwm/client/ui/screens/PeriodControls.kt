package ai.mwm.client.ui.screens

import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.AppTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable fun PeriodControls(state: CareState,onUpdate: ((CareState)->CareState)->Unit) {
    val context=LocalContext.current
    val today=LocalDate.now().toEpochDay()
    val active=state.starts.lastOrNull()?.takeIf {start ->state.logs.none {it.end&&it.day>=start}}
    var failure by remember {mutableStateOf<String?>(null)}
    fun record(day: Long,start: Boolean) {
        if(day>today || (start && state.logs.any {it.end&&it.day>=day}) || (!start && (active==null || day<active))) {failure="Choose an actual date on or before today, with end on or after start.";return}
        onUpdate {current ->
            val log=current.logs.find {it.day==day} ?: DayLog(day)
            current.copy(logs=current.logs.filterNot {it.day==day}+if(start) log.copy(start=true,end=false) else log.copy(end=true))
        }
    }
    Paper {
        if(active!=null) {
            Row {PText("Started ${dayText(active)}",Modifier.weight(1f),MaterialTheme.typography.titleMedium);OutlinedButton({pickDay(context,active) {day ->
                if(day>today || day <= (state.starts.dropLast(1).lastOrNull() ?: Long.MIN_VALUE)) failure="Choose a start after the previous period and on or before today."
                else if(day!=active) onUpdate {current ->val old=current.logs.find {it.day==active};val target=current.logs.find {it.day==day} ?: DayLog(day);current.copy(logs=current.logs.filterNot {it.day==active||it.day==day}+listOfNotNull(old?.copy(start=false))+target.copy(start=true,end=false))}
            }}) {PText("Correct")}}
            PText("Day ${today-active+1} · Never automatically ended",style=MaterialTheme.typography.bodySmall,muted=true)
        } else ConfirmPeriodSlider("Slide to start today") {record(today,true)}
        if(active==null) OutlinedButton({pickDay(context,today) {record(it,true)}},Modifier.fillMaxWidth()) {PText("Tap · Pick start date")}
        if(active!=null) {
            ConfirmPeriodSlider("Slide to end today") {record(today,false)}
            OutlinedButton({pickDay(context,today) {record(it,false)}},Modifier.fillMaxWidth()) {PText("Tap · Pick end date")}
        } else PText("End becomes available after you log a start.",style=MaterialTheme.typography.bodySmall,muted=true)
    }
    failure?.let {message -> AlertDialog(onDismissRequest={failure=null},title={PText("Check period date")},text={PText(message)},confirmButton={OutlinedButton({failure=null}) {PText("OK")}})}
}

@Composable private fun ConfirmPeriodSlider(label: String,onConfirm: ()->Unit) {
    var value by remember(label) {mutableFloatStateOf(0f)}
    Column {
        PText(label,style=MaterialTheme.typography.titleMedium)
        Slider(value,{value=it},onValueChangeFinished={if(value>=.9f) onConfirm();value=0f},modifier=Modifier.fillMaxWidth().semantics {contentDescription=label},colors=SliderDefaults.colors(thumbColor=AppTheme.colors.accent,activeTrackColor=AppTheme.colors.accent))
    }
}

@Composable fun PrivatePublicControl(partner: String) {
    var open by remember {mutableStateOf(false)}
    Paper(color=AppTheme.colors.secondary.copy(alpha=.16f)) {
        Row {PText("Care together",Modifier.weight(1f),MaterialTheme.typography.titleLarge);OutlinedButton({open=true}) {PText("Private / Public")}}
        PText("Private · Only you",style=MaterialTheme.typography.titleMedium)
        PText("Public means sharing only with your current authenticated partner after explicit consent. It never means open health access.",style=MaterialTheme.typography.bodySmall,muted=true)
        PText("No care messages sent or received. Partner support is separate from your health records.",style=MaterialTheme.typography.bodySmall,muted=true)
        OutlinedButton({},enabled=false,modifier=Modifier.fillMaxWidth()) {PText("Partner support · Unavailable")}
    }
    if(open) AlertDialog(onDismissRequest={open=false},title={PText("Private / Public")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {PText("Your records are private.",style=MaterialTheme.typography.titleMedium);PText("No authenticated partner is connected. $partner is a local sample name. Public sharing cannot be enabled until secure partner access is available and verified.");PText("Sharing would require your explicit category and date-scope consent, with notes separately opt-in. Pairing alone never grants health-record access.",style=MaterialTheme.typography.bodySmall)}},confirmButton={OutlinedButton({open=false}) {PText("Keep private")}},dismissButton={OutlinedButton({},enabled=false) {PText("Review & share")}})
}

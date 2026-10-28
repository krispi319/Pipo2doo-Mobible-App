package ai.mwm.client.data

import java.time.Instant
import java.time.ZoneId

data class SupportDay(val day:Long?,val entries:List<Appreciation>)
fun supportDay(at:Long,zone:ZoneId):Long?=if(at<=0)null else Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toEpochDay()
fun supportTimeline(notes:List<Appreciation>,zone:ZoneId):List<SupportDay> = notes.filter {it.kind=="CareSupport"}
    .distinctBy {it.id}.groupBy {supportDay(it.at,zone)}.map {(day,entries)->SupportDay(day,entries.sortedWith(compareBy<Appreciation> {it.at}.thenBy {it.id}))}
    .sortedWith(compareByDescending<SupportDay> {it.day ?: Long.MIN_VALUE})
fun supportForDay(notes:List<Appreciation>,day:Long,zone:ZoneId):List<Appreciation> = supportTimeline(notes,zone).firstOrNull {it.day==day}?.entries ?: emptyList()

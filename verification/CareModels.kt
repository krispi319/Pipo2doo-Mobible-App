package ai.mwm.client.data
import java.time.LocalDate
data class DayLog(val day: Long, val start: Boolean = false, val end: Boolean = false, val flow: String = "None", val cramps: String = "None", val mood: Int? = null, val cravings: Set<String> = emptySet(), val symptoms: Set<String> = emptySet(), val note: String = "")
data class CareState(val logs: List<DayLog> = emptyList(), val cycle: Int = 28, val duration: Int = 5, val checkIn: Boolean = false, val headsUp: Boolean = false, val checkTime: Int = 1200, val headsTime: Int = 540, val consent: Boolean = false, val categories: Set<String> = setOf("Mood", "Cravings", "Symptoms"), val history: Boolean = false, val includeNotes: Boolean = false, val endReminder: Boolean = false, val endTime: Int = 540, val shareSpace: String = "", val pendingShare: Boolean = false) {
    val starts get() = logs.filter { it.start }.map { it.day }.sorted()
    val intervals get() = starts.zipWithNext().filter { (a,b) -> logs.any { it.end && it.day in a until b } }.map { (a,b) -> (b-a).toInt() }.filter { it in 10..120 }.takeLast(6)
    val estimateCycle: Int get() { val values = intervals.sorted(); return if(values.size < 3) cycle else if(values.size % 2 == 1) values[values.size/2] else (values[values.size/2-1]+values[values.size/2])/2 }
    fun nextStart(today: Long = LocalDate.now().toEpochDay()): Long? { var next = starts.lastOrNull()?.plus(estimateCycle) ?: return null; while(next < today) next += estimateCycle; return next }
    fun predicted(day: Long): Boolean { val last = starts.lastOrNull() ?: return false; val delta = day-last; val occurrence=last+(delta/estimateCycle)*estimateCycle; val ended=logs.any { it.end && it.day in occurrence..day }; return delta >= 0 && !ended && delta % estimateCycle < duration && logs.none { it.day == day && (it.flow != "None" || it.start) } }
}


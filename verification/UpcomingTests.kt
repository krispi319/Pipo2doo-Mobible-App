import ai.mwm.client.data.*
import java.time.LocalDate
fun main() {
    var checks=0
    // Every weekday, all dates before and after the weekend boundary.
    for(offset in 0L..6L) {
        val today=LocalDate.of(2026,10,12).toEpochDay()+offset
        val tasks=(-4L..25L).map {Task(id="t$it",title="Task $it",day=today+it)} + Task(id="done",title="Done",day=today,status=TaskStatus.Done)
        val groups=upcomingSections(tasks,today)
        val ids=groups.flatMap {it.tasks}.map {it.id}
        check(ids.size==30 && ids.toSet().size==30 && "done" !in ids);checks++
        check(groups.first {it.key=="overdue"}.tasks.all {it.day<today});checks++
        check(groups.first {it.key=="tomorrow"}.tasks.single().day==today+1);checks++
        for(day in today-4..today+25) {check(upcomingSections(tasks,today,day).single().tasks.single().day==day);checks++}
    }
    check(upcomingSections(emptyList(),0).all {it.tasks.isEmpty()});checks++
    println("$checks upcoming grouping checks passed")
}

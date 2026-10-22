import ai.mwm.client.data.*
import java.time.LocalDate
fun main() {
 var n=0
 fun test(ok:Boolean) {check(ok);n++}
 val today=LocalDate.of(2026,10,18).toEpochDay()
 test(validTogetherDate(today,today))
 test(validTogetherDate(LocalDate.of(1900,1,1).toEpochDay(),today))
 test(!validTogetherDate(today+1,today))
 test(!validTogetherDate(LocalDate.of(1899,12,31).toEpochDay(),today))
 test(legacyDemoState().tasks.none {it.reminder})
 test(Task().reminder)
 test(Task(reminder=false).copy(title="Existing reminder stays off").reminder==false)
 test(Task(owner=Owner.You).isOwnReminder()==false)
 test(SIMPLE_TASK_STATUSES==listOf(TaskStatus.Todo,TaskStatus.Done))
 test(Task(status=TaskStatus.Doing).boardStatus()==TaskStatus.Todo)
 test(Task(status=TaskStatus.Todo).boardStatus()==TaskStatus.Todo)
 test(Task(status=TaskStatus.Done).boardStatus()==TaskStatus.Done)
 val old=Task(status=TaskStatus.Doing,title="Legacy")
 val tasks=listOf(old,Task(status=TaskStatus.Todo),Task(status=TaskStatus.Done))
 test(SIMPLE_TASK_STATUSES.sumOf {s->tasks.count {it.boardStatus()==s}}==tasks.size)
 test(old.status==TaskStatus.Doing)
 println("$n date validation, nudge default, reminder preservation and two-column checks passed")
}

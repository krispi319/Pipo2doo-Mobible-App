import ai.mwm.client.data.*
fun main() {
 var checks=0
 fun test(value:Boolean) {check(value);checks++}
 val tasks=listOf(Task(id="old",title="old",day=99),Task(id="today",day=100,minute=600),Task(id="early",day=100,minute=500),Task(id="tomorrow",day=101),Task(id="later",day=150),Task(id="done",day=99,status=TaskStatus.Done))
 val groups=planningGroups(tasks,100)
 test(groups.map {it.key}==listOf("overdue","today","tomorrow","later"))
 test(groups.flatMap {it.tasks}.map {it.id}.toSet()==tasks.filterNot {it.done}.map {it.id}.toSet())
 test(groups.sumOf {it.tasks.size}==5)
 test(groups[0].tasks.single().id=="old")
 test(groups[1].tasks.map {it.id}==listOf("early","today"))
 test(groups[2].tasks.single().id=="tomorrow")
 test(groups[3].tasks.single().id=="later")
 test(planningGroups(tasks,100,99).single().tasks.single().id=="old")
 test(planningGroups(tasks,100,300).single().tasks.isEmpty())
 test(planningGroups(emptyList(),100).size==4)
 test(compactBoardWidth(320f)<272f)
 test(compactBoardWidth(360f)>compactBoardWidth(320f))
 test(compactBoardWidth(320f,1.5f)>compactBoardWidth(320f,1f))
 for(width in listOf(240f,280f,320f,360f,430f,600f)) {test(compactBoardWidth(width)<=width-24);test(compactBoardWidth(width)>0)}
 println("$checks planning groups, completion filtering, selection and responsive width checks passed")
}

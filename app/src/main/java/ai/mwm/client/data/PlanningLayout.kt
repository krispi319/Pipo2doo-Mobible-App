package ai.mwm.client.data

data class PlanningGroup(val key:String,val title:String,val tasks:List<Task>)
fun planningGroups(tasks:List<Task>,today:Long,selected:Long?=null):List<PlanningGroup> {
    val open=tasks.filterNot {it.done}.sortedWith(compareBy<Task> {it.day}.thenBy {it.minute ?: 1440}.thenBy {it.id})
    if(selected!=null) return listOf(PlanningGroup("day_$selected","Selected day",open.filter {it.day==selected}))
    return listOf(
        PlanningGroup("overdue","Overdue",open.filter {it.day<today}),
        PlanningGroup("today","Today",open.filter {it.day==today}),
        PlanningGroup("tomorrow","Tomorrow",open.filter {it.day==today+1}),
        PlanningGroup("later","Later",open.filter {it.day>today+1})
    )
}
/** Leave a glimpse of the next column; avoid narrow, unreadable mini cards. */
fun compactBoardWidth(viewport:Float,fontScale:Float=1f):Float =
    ((viewport-32f)*.72f*(fontScale.coerceIn(1f,1.4f))).coerceIn(196f,300f).coerceAtMost((viewport-24f).coerceAtLeast(0f))

/** Disjoint, chronological sections for the vertical Upcoming page. */
fun upcomingSections(tasks:List<Task>,today:Long,selected:Long?=null):List<PlanningGroup> {
    val open=tasks.filterNot {it.done}.sortedWith(compareBy<Task> {it.day}.thenBy {it.minute ?: 1440}.thenBy {it.id})
    if(selected!=null) return listOf(PlanningGroup("selected","Selected day",open.filter {it.day==selected}))
    val weekEnd=java.time.LocalDate.ofEpochDay(today).with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)).toEpochDay()
    return listOf(
        PlanningGroup("overdue","Overdue",open.filter {it.day<today}),
        PlanningGroup("today","Today",open.filter {it.day==today}),
        PlanningGroup("tomorrow","Tomorrow",open.filter {it.day==today+1}),
        PlanningGroup("week","This week",open.filter {it.day>today+1 && it.day<=weekEnd}),
        PlanningGroup("later","Later",open.filter {it.day>maxOf(today+1,weekEnd)})
    )
}

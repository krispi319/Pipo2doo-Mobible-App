package ai.mwm.client.data

import java.time.LocalDate
fun validTogetherDate(day:Long,today:Long=LocalDate.now().toEpochDay()):Boolean = day in LocalDate.of(1900,1,1).toEpochDay()..today
/** Keep legacy Doing data readable without exposing a third workflow column. */
val SIMPLE_TASK_STATUSES=listOf(TaskStatus.Todo,TaskStatus.Done)
fun Task.boardStatus():TaskStatus = if(done)TaskStatus.Done else TaskStatus.Todo

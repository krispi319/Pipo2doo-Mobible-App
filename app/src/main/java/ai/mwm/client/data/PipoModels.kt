package ai.mwm.client.data

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
fun String.capFirst(): String {
    val index = indexOfFirst { it.isLetter() }
    return if (index < 0) this else substring(0, index) + substring(index, index + 1).uppercase(Locale.ENGLISH) + substring(index + 1)
}
val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
fun dayText(day: Long): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))
fun timeText(minute: Int?): String = minute?.let { LocalTime.of(it / 60, it % 60).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)) } ?: "Anytime"
enum class Owner(val label: String) { Me("Me"), You("You"), Together("Together") }
enum class Repeat(val label: String, val days: Long) { Once("Once", 0), Daily("Daily", 1), Weekly("Weekly", 7) }
enum class TaskStatus(val label: String) { Todo("To do"), Doing("Doing"), Done("Done") }
data class TaskEvent(val text: String, val at: Long = System.currentTimeMillis(), val owner: Owner = Owner.Me)
data class Task(
    val id: String = newId(), val title: String = "", val owner: Owner = Owner.Together,
    val day: Long = LocalDate.now().toEpochDay(), val minute: Int? = null,
    val listId: String = "home", val note: String = "", val tags: List<String> = emptyList(),
    val repeat: Repeat = Repeat.Once, val reminder: Boolean = true, val offset: Int = 0,
    val status: TaskStatus = TaskStatus.Todo, val completedAt: Long = 0,
    val parentId: String = "", val events: List<TaskEvent> = emptyList(), val assigneeUid: String = "",
    val scheduledAt: Long = 0, val scheduleZone: String = "", val displayZone: String = "", val seriesId: String = ""
) {
    val done: Boolean get() = status == TaskStatus.Done
    fun dueAt(zone: ZoneId = ZoneId.systemDefault()): Long = if(scheduledAt > 0 && minute != null) scheduledAt else LocalDate.ofEpochDay(day)
        .atTime(LocalTime.of((minute ?: 540) / 60, (minute ?: 540) % 60)).atZone(zone).toInstant().toEpochMilli()
    fun reminderAt(defaultMinute: Int = 540, zone: ZoneId = ZoneId.systemDefault()): Long {
        if(scheduledAt > 0 && minute != null && offset != -1) return scheduledAt - offset * 60_000L
        val dueMinute = minute ?: defaultMinute.coerceIn(0, 1439)
        val time = if(offset == -1) 540 else dueMinute
        val due = LocalDate.ofEpochDay(day).atTime(time / 60, time % 60).atZone(zone).toInstant().toEpochMilli()
        return if(offset == -1) due else due - offset * 60_000L
    }
    val reminderKey: String get() = if(scheduledAt > 0 && minute != null) "$scheduledAt:$offset" else "$day:$minute:$offset"
}
data class SharedList(val id: String = newId(), val name: String, val emoji: String = "🧺", val pinned: Boolean = false, val owner: Owner = Owner.Together, val pictures: List<String> = emptyList(), val assigneeUid: String = "")
data class Tag(val id: String = newId(), val name: String)
data class Appreciation(val id: String = newId(), val kind: String, val message: String, val owner: Owner = Owner.Me, val at: Long = System.currentTimeMillis(), val taskTitle: String = "", val sample: Boolean = false, val picture: String = "", val replyTo: String = "")
data class Preferences(
    val myName: String = "User 1", val partnerName: String = "User 2", val code: String = "K4T9PB",
    val started: Boolean = false, val avatar: Int = -1, val quietEnabled: Boolean = true,
    val quietStart: Int = 1320, val quietEnd: Int = 450, val style: String = "Soft purr",
    val sound: Boolean = true, val haptics: Boolean = true,
    val myPhoto: String = "", val partnerPhoto: String = "", val pokeOptIn: Boolean = true,
    val themeMode: String = "System", val language: String = "en", val codeExpiresAt: Long = 0L,
    val togetherSince: Long = LocalDate.of(2023, 2, 14).toEpochDay(),
    val defaultReminderTime: Int = 750, val keepResetHistory: Boolean = true, val shareProfile: Boolean = true,
    val clockZone: String = "", val partnerClockZone: String = "", val partnerClockOverride: String = "", val listLayout: String = "Wide"
)
/** Enforced at the persistence boundary, not just by hiding partner controls.
 * This offline installation has one current individual; Owner.You is never its identity. */
internal fun requireOwnPreferencesUpdate(current: Preferences, next: Preferences) {
    // In local mode this is an editable sample label, not an authenticated partner profile.
    require(next.partnerName == current.partnerName && next.partnerPhoto == current.partnerPhoto) {
        "Only your partner can change their own profile"
    }
}

/** Only the assignee receives reminders; Together includes both members. Editing stays shared. */
fun Task.isOwnReminder(): Boolean = reminder && !done && owner!=Owner.You

data class PipoState(val tasks: List<Task> = emptyList(), val lists: List<SharedList> = emptyList(), val tags: List<Tag> = emptyList(), val notes: List<Appreciation> = emptyList(), val preferences: Preferences = Preferences(), val skippedDaily: Set<String> = emptySet())

/** One next occurrence only. An overdue task stays open until completed; missed days aren't duplicated. */
fun nextOccurrence(task: Task, now: Long = System.currentTimeMillis()): Task? {
    if (task.repeat == Repeat.Once) return null
    if(task.scheduledAt > 0 && task.minute != null) {
        val anchor = clockZone(task.scheduleZone)
        val view = clockZone(task.displayZone)
        var nextTime = Instant.ofEpochMilli(task.scheduledAt).atZone(anchor).plusDays(task.repeat.days)
        val today = Instant.ofEpochMilli(now).atZone(anchor).toLocalDate()
        val behind = java.time.temporal.ChronoUnit.DAYS.between(nextTime.toLocalDate(), today)
        if(behind > 0) nextTime = nextTime.plusDays(((behind + task.repeat.days - 1) / task.repeat.days) * task.repeat.days)
        if(nextTime.toInstant().toEpochMilli() <= now) nextTime = nextTime.plusDays(task.repeat.days)
        return task.copy(id = newId(), scheduledAt = nextTime.toInstant().toEpochMilli(), status = TaskStatus.Todo,
            completedAt = 0, parentId = task.id, events = listOf(TaskEvent("Next ${task.repeat.label.lowercase(Locale.ENGLISH)} occurrence", now))).inTaskZone(view)
    }
    var day = task.day + task.repeat.days
    val today = Instant.ofEpochMilli(now).atZone(clockZone(task.displayZone)).toLocalDate().toEpochDay()
    if (day < today) day += ((today - day + task.repeat.days - 1) / task.repeat.days) * task.repeat.days
    var next = task.copy(id = newId(), day = day, status = TaskStatus.Todo, completedAt = 0, parentId = task.id,
        events = listOf(TaskEvent("Next ${task.repeat.label.lowercase(Locale.ENGLISH)} occurrence", now)))
    if (next.dueAt(clockZone(task.displayZone)) <= now) next = next.copy(day = next.day + task.repeat.days)
    return next
}

fun seedState(): PipoState {
    val demo = legacyDemoState()
    return demo.copy(
        lists = demo.lists.map { it.copy(name = it.name.replace("Theo", "User 2"), pictures = when(it.id) { "trip" -> listOf("asset:demo_travel"); "home" -> listOf("asset:demo_tap"); "gifts" -> listOf("asset:demo_scarf"); "dates" -> listOf("asset:demo_movie"); "garden" -> listOf("asset:demo_balcony"); else -> emptyList() }) },
        tasks = demo.tasks.map { it.copy(note = it.note.replace("Theo", "User 2"), reminder = true) },
        preferences = Preferences(myPhoto = "asset:profile_mina", partnerPhoto = "asset:profile_partner")
    )
}

/** Only used to recognize the original bundled examples during migration. */
internal fun legacyDemoState(): PipoState {
    val today = LocalDate.now().toEpochDay()
    val lists = listOf(SharedList("groceries", "Weekly groceries & Pip's food", "🛒", true), SharedList("trip", "Porto weekend packing", "🧳", true, Owner.You), SharedList("home", "Apartment fixes before the landlord visit", "🔧", owner=Owner.Me), SharedList("dates", "Movie night queue", "🎬"), SharedList("cat", "Pip's vet & vaccine stuff", "🐈", owner=Owner.Me), SharedList("garden", "Balcony plants", "🪴"), SharedList("gifts", "Gift ideas for Theo's mum", "🎁", owner=Owner.You), SharedList("recipes", "Cozy recipes to try", "🍜"), SharedList("travel", "Lisbon kayak day", "🛶"))
    val tags = listOf(Tag("home_tag", "Home"), Tag("errand", "Errands"), Tag("pet", "Pet care"), Tag("us", "Together time"))
    fun task(title: String, owner: Owner, list: String, days: Long = 0, minute: Int? = null, done: Boolean = false, note: String = "", tag: String = "", repeat: Repeat = Repeat.Once, status: TaskStatus = TaskStatus.Todo) = Task(title = title, owner = owner, listId = list, day = today + days, minute = minute, status = if(done) TaskStatus.Done else status, completedAt = if(done) LocalDate.now().atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else 0, note = note, tags = if(tag.isEmpty()) emptyList() else listOf(tag), repeat = repeat, reminder = false, events = listOf(TaskEvent("Created this task")))
    val tasks = listOf(
        task("20-minute stretch before dinner", Owner.Me, "home", minute = 1080, repeat = Repeat.Daily),
        task("Pick up cat food for Pipo & Mochi", Owner.Together, "cat", minute = 570, tag = "pet"),
        task("Book dentist for Thursday afternoon", Owner.You, "home", minute = 750),
        task("Call the vet about Pipo's booster", Owner.Me, "cat", minute = 660, note = "Ask about the afternoon slot and bring his vaccination record.", tag = "pet"),
        task("Plan Saturday picnic menu", Owner.Together, "dates", minute = 1230, repeat = Repeat.Weekly, note = "Theo wants the lemon cake again. Let's check the weather!", tag = "us"),
        task("Water the windowsill basil", Owner.Me, "garden", done = true, tag = "home_tag"),
        task("Pick up oat milk & coffee beans", Owner.You, "groceries", done = true, note = "The Ethiopian roast, please.", tag = "errand"),
        task("Fix the wobbly bathroom shelf", Owner.You, "home", done = true),
        task("Return library books", Owner.Me, "home", -2, tag = "errand"),
        task("Book a vet check for Pipo", Owner.Together, "cat", -1, tag = "pet"),
        task("Pick up dry cleaning", Owner.Me, "home", 1, 540, tag = "errand"),
        task("Call landlord about the radiator", Owner.You, "home", 1, 750),
        task("Grocery run — oat milk, lemons, tofu", Owner.Together, "groceries", 1, 1080),
        task("Get passport photos taken", Owner.You, "trip", 2, 1080),
        task("Dinner at Marisol's", Owner.Together, "dates", 3, 1170, tag = "us"),
        task("Repot the balcony basil", Owner.Me, "garden", 4, 600),
        task("Deep clean the bathroom", Owner.Together, "home", 4, 840, repeat = Repeat.Weekly),
        task("Meal prep lunches for the week", Owner.You, "groceries", 5, 960),
        task("Dentist check-up", Owner.Me, "home", 8, 910),
        task("Split rent & utilities", Owner.Together, "home", 9),
        task("Descale the kettle", Owner.Me, "home", 2, note = "Vinegar under the sink."),
        task("Wash the duvet covers", Owner.Me, "home", 1, status = TaskStatus.Doing),
        task("Pack chargers & sunscreen", Owner.You, "trip", 3),
        task("Find a ceramic tea set", Owner.You, "gifts", 6),
        task("Try miso noodle soup", Owner.Together, "recipes", 3),
        task("Reserve the morning kayak session", Owner.Together, "travel", 8),
        task("Deep clean the fridge", Owner.Together, "home", 2, status = TaskStatus.Doing)
    )
    return PipoState(tasks, lists, tags, listOf(Appreciation(kind = "You're the best", message = "You even got the oat milk I like.", owner = Owner.You, sample = true), Appreciation(kind = "Purr-fect", message = "Pipo's litter box has never smelled so innocent.", taskTitle = "Clean litter box", sample = true)))
}

package com.swish.campustime.data

import android.content.Context
import androidx.room.withTransaction
import com.swish.campustime.reminder.ReminderScheduler
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

data class AppData(
    val semester: SemesterConfig?,
    val courses: List<Course>,
    val events: List<ScheduleEvent>,
    val tasks: List<Task>,
    val steps: List<TaskStep> = emptyList(),
    val blocks: List<TaskBlock> = emptyList()
)

class ScheduleRepository(private val context: Context, private val db: AppDatabase) {
    private val dao = db.scheduleDao()
    private val baseData = combine(dao.observeSemester(), dao.observeCourses(), dao.observeEvents(), dao.observeTasks()) { semester, courses, events, tasks ->
        AppData(semester, courses, events, tasks)
    }
    val data = combine(baseData, dao.observeSteps(), dao.observeBlocks()) { base, steps, blocks -> base.copy(steps = steps, blocks = blocks) }

    suspend fun ensureDefaults() {
        if (dao.semester() == null) {
            val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            dao.saveSemester(SemesterConfig(name = "当前学期", startEpochDay = monday.toEpochDay(), totalWeeks = 20, periodTimes = DEFAULT_PERIODS))
        }
    }

    suspend fun saveCourse(value: Course): Long {
        val id = if (value.id == 0L) dao.insertCourse(value) else { dao.updateCourse(value); value.id }
        reschedule()
        return id
    }
    suspend fun saveEvent(value: ScheduleEvent): Long {
        require(value.endEpochMillis > value.startEpochMillis) { "结束时间必须晚于开始时间" }
        val id = if (value.id == 0L) dao.insertEvent(value) else { dao.updateEvent(value); value.id }
        reschedule()
        return id
    }
    suspend fun saveTask(value: Task): Long {
        val old = if (value.id != 0L) dao.tasks().firstOrNull { it.id == value.id } else null
        val id = if (value.id == 0L) dao.insertTask(value) else { dao.updateTask(value); value.id }
        if (old != null && (old.dueEpochMillis != value.dueEpochMillis || old.hasDueDate != value.hasDueDate)) {
            val offset = value.dueEpochMillis - old.dueEpochMillis
            dao.blocks().filter { it.taskId == id }.forEach { block ->
                val due = if (!value.hasDueDate) null else if (old.recurrence == "NONE") value.dueEpochMillis
                    else block.occurrenceDueEpochMillis?.plus(offset)
                dao.updateBlock(block.copy(occurrenceDueEpochMillis = due))
            }
        }
        reschedule()
        return id
    }
    suspend fun saveEventOccurrence(series: ScheduleEvent, occurrenceStart: Long, replacement: ScheduleEvent) {
        val day = Instant.ofEpochMilli(occurrenceStart).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        db.withTransaction {
            dao.updateEvent(series.copy(excludedEpochDaysCsv = addExcludedDay(series.excludedEpochDaysCsv, day)))
            dao.insertEvent(replacement.copy(id = 0, recurrence = "NONE", weekdaysCsv = "", recurrenceEndEpochDay = null, excludedEpochDaysCsv = ""))
        }
        reschedule()
    }
    suspend fun saveTaskOccurrence(series: Task, occurrenceDue: Long, replacement: Task) {
        val day = Instant.ofEpochMilli(occurrenceDue).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        db.withTransaction {
            dao.updateTask(series.copy(excludedEpochDaysCsv = addExcludedDay(series.excludedEpochDaysCsv, day)))
            val wasCompleted = day.toString() in series.completedEpochDaysCsv.split(',')
            val newId = dao.insertTask(replacement.copy(id = 0, completed = wasCompleted, recurrence = "NONE", weekdaysCsv = "", recurrenceEndEpochDay = null, excludedEpochDaysCsv = "", completedEpochDaysCsv = ""))
            dao.blocksForOccurrence(series.id, occurrenceDue).forEach { dao.updateBlock(it.copy(taskId = newId, occurrenceDueEpochMillis = if (replacement.hasDueDate) replacement.dueEpochMillis else null)) }
        }
        reschedule()
    }
    suspend fun deleteEventOccurrence(series: ScheduleEvent, occurrenceStart: Long) {
        val day = Instant.ofEpochMilli(occurrenceStart).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        dao.updateEvent(series.copy(excludedEpochDaysCsv = addExcludedDay(series.excludedEpochDaysCsv, day)))
        reschedule()
    }
    suspend fun deleteTaskOccurrence(series: Task, occurrenceDue: Long) {
        val day = Instant.ofEpochMilli(occurrenceDue).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        db.withTransaction {
            dao.updateTask(series.copy(excludedEpochDaysCsv = addExcludedDay(series.excludedEpochDaysCsv, day)))
            dao.clearBlocksForOccurrence(series.id, occurrenceDue)
        }
        reschedule()
    }
    suspend fun saveSemester(value: SemesterConfig) { dao.saveSemester(value); reschedule() }
    suspend fun deleteCourse(value: Course) { dao.deleteCourse(value); reschedule() }
    suspend fun deleteEvent(value: ScheduleEvent) { dao.deleteEvent(value); reschedule() }
    suspend fun deleteTask(value: Task) {
        db.withTransaction { dao.clearStepsForTask(value.id); dao.clearBlocksForTask(value.id); dao.deleteTask(value) }
        reschedule()
    }

    suspend fun toggleTask(value: Task, occurrenceDue: Long? = null) {
        if (value.recurrence == "NONE" || occurrenceDue == null || value.completed) dao.updateTask(value.copy(completed = !value.completed))
        else {
            val day = Instant.ofEpochMilli(occurrenceDue).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            val days = value.completedEpochDaysCsv.split(',').mapNotNull { it.toLongOrNull() }.toMutableSet()
            if (!days.add(day)) days.remove(day)
            dao.updateTask(value.copy(completedEpochDaysCsv = days.sorted().joinToString(",")))
        }
        reschedule()
    }

    suspend fun addStep(taskId: Long, title: String) { require(title.isNotBlank()); dao.insertStep(TaskStep(taskId = taskId, title = title.trim())) }
    suspend fun toggleStep(value: TaskStep) = dao.updateStep(value.copy(completed = !value.completed))
    suspend fun deleteStep(value: TaskStep) = dao.deleteStep(value)
    suspend fun saveBlock(value: TaskBlock) {
        require(value.endEpochMillis > value.startEpochMillis) { "结束时间必须晚于开始时间" }
        if (value.id == 0L) dao.insertBlock(value) else dao.updateBlock(value)
        reschedule()
    }
    suspend fun deleteBlock(value: TaskBlock) { dao.deleteBlock(value); reschedule() }
    suspend fun restoreCourse(value: Course) { dao.insertCourse(value); reschedule() }
    suspend fun restoreEvent(value: ScheduleEvent) { dao.insertEvent(value); reschedule() }
    suspend fun restoreTask(value: Task, steps: List<TaskStep>, blocks: List<TaskBlock>) {
        db.withTransaction {
            dao.insertTask(value)
            steps.forEach { dao.insertStep(it) }
            blocks.forEach { dao.insertBlock(it) }
        }
        reschedule()
    }
    suspend fun restoreStep(value: TaskStep) { dao.insertStep(value) }
    suspend fun restoreBlock(value: TaskBlock) { dao.insertBlock(value); reschedule() }

    suspend fun snapshot() = AppData(dao.semester(), dao.courses(), dao.events(), dao.tasks(), dao.steps(), dao.blocks())

    suspend fun replaceAll(data: AppData) = db.withTransaction {
        requireNotNull(data.semester) { "备份中缺少学期设置" }
        dao.clearCourses(); dao.clearEvents(); dao.clearTasks(); dao.clearSteps(); dao.clearBlocks()
        dao.saveSemester(data.semester)
        data.courses.forEach { dao.insertCourse(it.copy(id = 0)) }
        data.events.forEach { dao.insertEvent(it.copy(id = 0)) }
        val taskIds = mutableMapOf<Long, Long>()
        data.tasks.forEach { taskIds[it.id] = dao.insertTask(it.copy(id = 0)) }
        data.steps.forEach { step -> taskIds[step.taskId]?.let { dao.insertStep(step.copy(id = 0, taskId = it)) } }
        data.blocks.forEach { block -> taskIds[block.taskId]?.let { dao.insertBlock(block.copy(id = 0, taskId = it)) } }
    }.also { reschedule() }

    suspend fun reschedule() = ReminderScheduler(context).scheduleAll(snapshot())

    companion object {
        const val LEGACY_DEFAULT_PERIODS = "08:00-08:45,08:55-09:40,10:00-10:45,10:55-11:40,14:00-14:45,14:55-15:40,16:00-16:45,16:55-17:40,19:00-19:45,19:55-20:40"
        const val DEFAULT_PERIODS = "08:00-08:40,08:45-09:25,09:45-10:25,10:35-11:15,11:20-12:00,13:30-14:10,14:15-14:55,15:15-15:55,16:00-16:40,16:45-17:25,19:20-20:00,20:05-20:45"
    }

    private fun addExcludedDay(csv: String, day: Long): String = (csv.split(',').mapNotNull { it.toLongOrNull() } + day).distinct().sorted().joinToString(",")
}

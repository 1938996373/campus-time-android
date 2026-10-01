package com.swish.campustime.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "semesters")
data class SemesterConfig(
    @PrimaryKey val id: Long = 1,
    val name: String,
    val startEpochDay: Long,
    val totalWeeks: Int,
    val periodTimes: String,
    val dayStartMinute: Int = 480,
    val dayEndMinute: Int = 1320,
    val breakStartMinute: Int = 720,
    val breakEndMinute: Int = 780,
    val bufferMinutes: Int = 10
)

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val teacher: String = "",
    val room: String = "",
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val startWeek: Int = 1,
    val endWeek: Int = 20,
    val weekParity: String = "ALL",
    val color: Long = 0xFF6750A4,
    val reminderMinutes: Int? = null,
    val note: String = "",
    val startPeriod: Int? = null,
    val endPeriod: Int? = null
)

@Entity(tableName = "schedule_events")
data class ScheduleEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val recurrence: String = "NONE",
    val weekdaysCsv: String = "",
    val recurrenceEndEpochDay: Long? = null,
    val color: Long = 0xFF006A6A,
    val reminderMinutes: Int? = null,
    val excludedEpochDaysCsv: String = ""
)

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val dueEpochMillis: Long,
    val completed: Boolean = false,
    val recurrence: String = "NONE",
    val weekdaysCsv: String = "",
    val recurrenceEndEpochDay: Long? = null,
    val reminderMinutes: Int? = null,
    val excludedEpochDaysCsv: String = "",
    val hasDueDate: Boolean = true,
    val plannedEpochDay: Long? = null,
    val estimateMinutes: Int? = null,
    val priority: Int = 0,
    val category: String = "",
    val completedEpochDaysCsv: String = ""
)

@Entity(tableName = "task_steps")
data class TaskStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val completed: Boolean = false
)

@Entity(tableName = "task_blocks")
data class TaskBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val occurrenceDueEpochMillis: Long? = null
)

data class ReminderConfig(val enabled: Boolean, val minutesBefore: Int)

enum class ScheduleKind { COURSE, EVENT, TASK, BLOCK }

data class ScheduleItem(
    val stableKey: String,
    val sourceId: Long,
    val kind: ScheduleKind,
    val title: String,
    val subtitle: String,
    val startMillis: Long,
    val endMillis: Long?,
    val color: Long,
    val completed: Boolean = false,
    val conflict: Boolean = false,
    val taskId: Long? = null
)

interface ScheduleImportAdapter {
    val sourceName: String
    suspend fun import(payload: ByteArray): Result<List<Course>>
}

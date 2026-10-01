package com.swish.campustime.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object ScheduleEngine {
    fun startOfWeek(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun buildItems(
        rangeStart: LocalDate,
        rangeEndInclusive: LocalDate,
        semester: SemesterConfig?,
        courses: List<Course>,
        events: List<ScheduleEvent>,
        tasks: List<Task>,
        zone: ZoneId = ZoneId.systemDefault(),
        blocks: List<TaskBlock> = emptyList()
    ): List<ScheduleItem> {
        val out = mutableListOf<ScheduleItem>()
        semester?.let { term ->
            val termMonday = startOfWeek(LocalDate.ofEpochDay(term.startEpochDay))
            courses.forEach { course ->
                var date = rangeStart
                while (!date.isAfter(rangeEndInclusive)) {
                    val week = Math.floorDiv(date.toEpochDay() - termMonday.toEpochDay(), 7L).toInt() + 1
                    val parityOk = course.weekParity == "ALL" || course.weekParity == "ODD" && week % 2 == 1 || course.weekParity == "EVEN" && week % 2 == 0
                    if (date.dayOfWeek.value == course.dayOfWeek && week in 1..term.totalWeeks && week in course.startWeek..course.endWeek && parityOk) {
                        val periods = periodRanges(term.periodTimes)
                        val startMinute = course.startPeriod?.let { periods.getOrNull(it - 1)?.first } ?: course.startMinute
                        val endMinute = course.endPeriod?.let { periods.getOrNull(it - 1)?.second } ?: course.endMinute
                        if (endMinute <= startMinute) { date = date.plusDays(1); continue }
                        val start = date.atStartOfDay().plusMinutes(startMinute.toLong()).atZone(zone).toInstant().toEpochMilli()
                        val end = date.atStartOfDay().plusMinutes(endMinute.toLong()).atZone(zone).toInstant().toEpochMilli()
                        val details = buildList {
                            if (course.teacher.isNotBlank()) add("老师：${course.teacher}")
                            if (course.room.isNotBlank()) add("教室：${course.room}")
                            if (course.note.isNotBlank()) add("备注：${course.note}")
                        }.joinToString("\n")
                        out += ScheduleItem("course-${course.id}-${date.toEpochDay()}", course.id, ScheduleKind.COURSE, course.title, details, start, end, course.color)
                    }
                    date = date.plusDays(1)
                }
            }
        }
        events.forEach { event ->
            occurrences(event.startEpochMillis, event.endEpochMillis, event.recurrence, event.weekdaysCsv, event.recurrenceEndEpochDay, event.excludedEpochDaysCsv, rangeStart, rangeEndInclusive, zone).forEach { (start, end) ->
                out += ScheduleItem("event-${event.id}-$start", event.id, ScheduleKind.EVENT, event.title, event.note.takeIf { it.isBlank() } ?: "备注：${event.note}", start, end, event.color)
            }
        }
        tasks.filter { it.hasDueDate }.forEach { task ->
            taskDueOccurrences(task, rangeStart, rangeEndInclusive, zone).forEach { due ->
                val day = Instant.ofEpochMilli(due).atZone(zone).toLocalDate().toEpochDay()
                val completed = task.completed || day.toString() in task.completedEpochDaysCsv.split(',')
                out += ScheduleItem("task-${task.id}-$due", task.id, ScheduleKind.TASK, task.title, task.note.takeIf { it.isBlank() } ?: "备注：${task.note}", due, null, 0xFFB3261E, completed, taskId = task.id)
            }
        }
        blocks.forEach { block ->
            val task = tasks.firstOrNull { it.id == block.taskId } ?: return@forEach
            if (block.startEpochMillis < rangeEndInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() &&
                block.endEpochMillis > rangeStart.atStartOfDay(zone).toInstant().toEpochMilli()) {
                val blockCompleted = if (task.recurrence == "NONE" || task.completed) task.completed else block.occurrenceDueEpochMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay().toString() in task.completedEpochDaysCsv.split(',')
                } ?: false
                out += ScheduleItem("block-${block.id}", block.id, ScheduleKind.BLOCK, "执行：${task.title}", "任务时间块", block.startEpochMillis, block.endEpochMillis, 0xFF3A6B35, blockCompleted, taskId = task.id)
            }
        }
        val timed = out.filter { it.endMillis != null }
        val conflicts = timed.filter { item ->
            timed.any { other -> item.stableKey != other.stableKey && item.startMillis < other.endMillis!! && other.startMillis < item.endMillis!! }
        }.map { it.stableKey }.toSet()
        return out.map { it.copy(conflict = it.stableKey in conflicts) }.sortedBy { it.startMillis }
    }

    fun taskDueOccurrences(task: Task, rangeStart: LocalDate, rangeEndInclusive: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Long> =
        if (!task.hasDueDate) emptyList() else occurrences(task.dueEpochMillis, task.dueEpochMillis, task.recurrence, task.weekdaysCsv,
            task.recurrenceEndEpochDay, task.excludedEpochDaysCsv, rangeStart, rangeEndInclusive, zone).map { it.first }

    fun periodRanges(value: String): List<Pair<Int, Int>> = value.split(',').mapNotNull { range ->
        val parts = range.trim().split('-')
        if (parts.size != 2) return@mapNotNull null
        runCatching {
            val start = java.time.LocalTime.parse(parts[0]); val end = java.time.LocalTime.parse(parts[1])
            (start.hour * 60 + start.minute) to (end.hour * 60 + end.minute)
        }.getOrNull()?.takeIf { it.second > it.first }
    }

    private fun occurrences(
        originalStart: Long,
        originalEnd: Long,
        recurrence: String,
        weekdaysCsv: String,
        recurrenceEndEpochDay: Long?,
        excludedEpochDaysCsv: String,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        zone: ZoneId
    ): List<Pair<Long, Long>> {
        val original = Instant.ofEpochMilli(originalStart).atZone(zone).toLocalDateTime()
        val duration = originalEnd - originalStart
        val rangeStartMillis = rangeStart.atStartOfDay(zone).toInstant().toEpochMilli()
        if (recurrence == "NONE") {
            return if (original.toLocalDate() in rangeStart..rangeEnd || originalEnd > rangeStartMillis && original.toLocalDate() <= rangeEnd) listOf(originalStart to originalEnd) else emptyList()
        }
        val allowedDays = weekdaysCsv.split(',').mapNotNull { it.toIntOrNull() }.toSet()
        val excludedDays = excludedEpochDaysCsv.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        val finalDate = recurrenceEndEpochDay?.let(LocalDate::ofEpochDay)
        val result = mutableListOf<Pair<Long, Long>>()
        val overlapDays = if (duration > 0) (duration / 86_400_000L + 1).coerceAtMost(365) else 0
        var date = maxOf(rangeStart.minusDays(overlapDays), original.toLocalDate())
        while (!date.isAfter(rangeEnd) && (finalDate == null || !date.isAfter(finalDate))) {
            val matches = when (recurrence) {
                "DAILY" -> true
                "WEEKLY" -> date.dayOfWeek == original.dayOfWeek
                "CUSTOM" -> date.dayOfWeek.value in allowedDays
                else -> false
            }
            if (matches && date.toEpochDay() !in excludedDays) {
                val start = LocalDateTime.of(date, original.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                if (start + duration > rangeStartMillis || duration == 0L && date >= rangeStart) result += start to start + duration
            }
            date = date.plusDays(1)
        }
        return result
    }
}

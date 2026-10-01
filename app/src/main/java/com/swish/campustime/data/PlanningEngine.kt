package com.swish.campustime.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class FreeSlot(val startMinute: Int, val endMinute: Int) {
    val durationMinutes: Int get() = endMinute - startMinute
}

object PlanningEngine {
    fun freeSlots(date: LocalDate, items: List<ScheduleItem>, config: SemesterConfig, zone: ZoneId = ZoneId.systemDefault()): List<FreeSlot> {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val nextDay = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val occupied = buildList {
            if (config.breakEndMinute > config.breakStartMinute) add(FreeSlot(config.breakStartMinute, config.breakEndMinute))
            items.filter { it.endMillis != null && it.startMillis < nextDay && it.endMillis > dayStart }.forEach { item ->
                val start = maxOf(dayStart, item.startMillis)
                val end = minOf(nextDay, item.endMillis!!)
                val startTime = Instant.ofEpochMilli(start).atZone(zone)
                val endTime = Instant.ofEpochMilli(end).atZone(zone)
                val from = if (start == dayStart) 0 else startTime.hour * 60 + startTime.minute
                val to = if (end == nextDay) 1440 else endTime.hour * 60 + endTime.minute
                add(FreeSlot((from - config.bufferMinutes).coerceAtLeast(0), (to + config.bufferMinutes).coerceAtMost(1440)))
            }
        }.sortedBy { it.startMinute }
        var cursor = config.dayStartMinute
        val result = mutableListOf<FreeSlot>()
        occupied.forEach { range ->
            if (range.startMinute > cursor) result += FreeSlot(cursor, minOf(range.startMinute, config.dayEndMinute))
            cursor = maxOf(cursor, range.endMinute)
        }
        if (cursor < config.dayEndMinute) result += FreeSlot(cursor, config.dayEndMinute)
        return result.filter { it.durationMinutes > 0 && it.startMinute < config.dayEndMinute }
    }

    fun minutesScheduledForTask(taskId: Long, occurrenceDue: Long?, blocks: List<TaskBlock>): Int = blocks.filter { it.taskId == taskId && it.occurrenceDueEpochMillis == occurrenceDue }
        .sumOf { ((it.endEpochMillis - it.startEpochMillis) / 60_000L).toInt() }
}

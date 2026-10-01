package com.swish.campustime.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PlanningEngineTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 9, 28)
    private val config = SemesterConfig(name = "测试", startEpochDay = date.toEpochDay(), totalWeeks = 20, periodTimes = "",
        dayStartMinute = 480, dayEndMinute = 1080, breakStartMinute = 720, breakEndMinute = 780, bufferMinutes = 10)

    @Test fun lunchAndBusyIntervalsAreExcludedWithBuffer() {
        val start = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val busy = ScheduleItem("course", 1, ScheduleKind.COURSE, "课", "", start, start + 60 * 60_000L, 0)
        val slots = PlanningEngine.freeSlots(date, listOf(busy), config, zone)
        assertEquals(listOf(FreeSlot(480, 530), FreeSlot(610, 720), FreeSlot(780, 1080)), slots)
    }

    @Test fun deadlinePointDoesNotConsumeWorkTime() {
        val start = date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val deadline = ScheduleItem("task", 1, ScheduleKind.TASK, "交作业", "", start, null, 0)
        val slots = PlanningEngine.freeSlots(date, listOf(deadline), config, zone)
        assertEquals(listOf(FreeSlot(480, 720), FreeSlot(780, 1080)), slots)
    }
}

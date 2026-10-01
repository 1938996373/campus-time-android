package com.swish.campustime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleEngineTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday = LocalDate.of(2026, 9, 7)
    private val semester = SemesterConfig(name = "测试学期", startEpochDay = monday.toEpochDay(), totalWeeks = 20, periodTimes = "")

    @Test fun oddWeekCourseOnlyAppearsOnOddWeek() {
        val course = Course(id = 1, title = "高等数学", dayOfWeek = 1, startMinute = 480, endMinute = 570, startWeek = 1, endWeek = 20, weekParity = "ODD")
        val week1 = ScheduleEngine.buildItems(monday, monday.plusDays(6), semester, listOf(course), emptyList(), emptyList(), zone)
        val week2 = ScheduleEngine.buildItems(monday.plusWeeks(1), monday.plusWeeks(1).plusDays(6), semester, listOf(course), emptyList(), emptyList(), zone)
        assertEquals(1, week1.size)
        assertTrue(week2.isEmpty())
    }

    @Test fun taskIsPointAndEventIsDuration() {
        val due = at(monday.plusDays(1), 12, 0)
        val eventStart = at(monday.plusDays(1), 9, 0)
        val items = ScheduleEngine.buildItems(monday, monday.plusDays(6), semester, emptyList(), listOf(ScheduleEvent(id = 2, title = "自习", startEpochMillis = eventStart, endEpochMillis = eventStart + 3_600_000)), listOf(Task(id = 3, title = "交作业", dueEpochMillis = due)), zone)
        assertEquals(2, items.size)
        assertTrue(items.first { it.kind == ScheduleKind.TASK }.endMillis == null)
        assertTrue(items.first { it.kind == ScheduleKind.EVENT }.endMillis != null)
    }

    @Test fun overlapsAreMarkedAsConflict() {
        val start = at(monday, 10, 0)
        val events = listOf(
            ScheduleEvent(id = 1, title = "A", startEpochMillis = start, endEpochMillis = start + 3_600_000),
            ScheduleEvent(id = 2, title = "B", startEpochMillis = start + 1_800_000, endEpochMillis = start + 5_400_000)
        )
        val items = ScheduleEngine.buildItems(monday, monday, semester, emptyList(), events, emptyList(), zone)
        assertEquals(2, items.size)
        assertFalse(items.any { !it.conflict })
    }

    @Test fun customWeekdayRecurrenceIsExpanded() {
        val original = at(monday, 8, 0)
        val event = ScheduleEvent(id = 4, title = "跑步", startEpochMillis = original, endEpochMillis = original + 1_800_000, recurrence = "CUSTOM", weekdaysCsv = "1,3,5")
        val items = ScheduleEngine.buildItems(monday, monday.plusDays(6), semester, emptyList(), listOf(event), emptyList(), zone)
        assertEquals(3, items.size)
    }

    @Test fun excludedOccurrenceIsNotExpanded() {
        val original = at(monday, 8, 0)
        val event = ScheduleEvent(id = 5, title = "背单词", startEpochMillis = original, endEpochMillis = original + 1_800_000, recurrence = "DAILY", excludedEpochDaysCsv = monday.plusDays(1).toEpochDay().toString())
        val items = ScheduleEngine.buildItems(monday, monday.plusDays(2), semester, emptyList(), listOf(event), emptyList(), zone)
        assertEquals(2, items.size)
        assertTrue(items.none { java.time.Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate() == monday.plusDays(1) })
    }

    @Test fun recurringCompletionBelongsToOneDate() {
        val task = Task(id = 9, title = "复习", dueEpochMillis = at(monday, 20, 0), recurrence = "DAILY",
            completedEpochDaysCsv = monday.toEpochDay().toString())
        val items = ScheduleEngine.buildItems(monday, monday.plusDays(2), semester, emptyList(), emptyList(), listOf(task), zone)
        assertEquals(listOf(true, false, false), items.map { it.completed })
    }

    @Test fun legacySeriesCompletionStillSuppressesFutureOccurrences() {
        val task = Task(id = 10, title = "旧版重复任务", dueEpochMillis = at(monday, 20, 0), recurrence = "DAILY", completed = true)
        val items = ScheduleEngine.buildItems(monday, monday.plusDays(2), semester, emptyList(), emptyList(), listOf(task), zone)
        assertEquals(3, items.size)
        assertTrue(items.all { it.completed })
    }

    @Test fun linkedCourseUsesUpdatedPeriodTimesAndSemesterBoundary() {
        val term = semester.copy(totalWeeks = 1, periodTimes = "08:00-08:40,08:50-09:30")
        val course = Course(id = 1, title = "实验", dayOfWeek = 1, startMinute = 480, endMinute = 520,
            startPeriod = 2, endPeriod = 2)
        val first = ScheduleEngine.buildItems(monday, monday, term, listOf(course), emptyList(), emptyList(), zone).single()
        assertEquals(at(monday, 8, 50), first.startMillis)
        assertTrue(ScheduleEngine.buildItems(monday.plusWeeks(1), monday.plusWeeks(1), term, listOf(course), emptyList(), emptyList(), zone).isEmpty())
    }

    @Test fun overnightPlanIsVisibleOnFollowingDay() {
        val start = at(monday, 23, 0)
        val event = ScheduleEvent(id = 2, title = "值班", startEpochMillis = start, endEpochMillis = start + 2 * 3_600_000L)
        val items = ScheduleEngine.buildItems(monday.plusDays(1), monday.plusDays(1), semester, emptyList(), listOf(event), emptyList(), zone)
        assertEquals(1, items.size)
    }

    private fun at(date: LocalDate, hour: Int, minute: Int) = LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}

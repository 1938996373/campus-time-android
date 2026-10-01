package com.swish.campustime.ui

import com.swish.campustime.data.ScheduleItem
import com.swish.campustime.data.ScheduleKind
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WeekTimelineTest {
    private val monday = LocalDate.of(2026, 9, 21)
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun item(id: Int, start: Int, end: Int?, day: Int = 0): ScheduleItem {
        val base = monday.plusDays(day.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        return ScheduleItem("$id", id.toLong(), if (end == null) ScheduleKind.TASK else ScheduleKind.EVENT,
            "安排$id", "备注", base + start * 60000L, end?.let { base + it * 60000L }, 0xFF006A6A)
    }

    @Test fun emptyWeekHasOnlyHoursAndClosingTime() {
        val axis = weekAxis(emptyList(), emptyList(), 20f, 1f)
        assertEquals((8..20).map { it * 60 } + 1245, axis.points)
    }

    @Test fun overlapChainsCombineButTouchingEventsAndDifferentDaysDoNot() {
        val groups = weekGroups(monday, listOf(item(1, 480, 540), item(2, 510, 570),
            item(3, 560, 600), item(4, 600, 660), item(5, 480, 540, 1)), zone)
        assertEquals(listOf(3, 1, 1), groups.map { it.entries.size })
        assertEquals(480, groups.first().start)
        assertEquals(600, groups.first().end)
    }

    @Test fun measuredLongContentFitsWithoutLosingSharedEndpoints() {
        val groups = weekGroups(monday, listOf(item(1, 480, 485), item(2, 485, 490)), zone)
        val axis = weekAxis(groups, listOf(1800f, 500f), 20f, 1f)
        assertTrue(axis.y(485) - axis.y(480) >= 1800f)
        assertTrue(axis.y(490) - axis.y(485) >= 500f)
        axis.points.forEach { assertEquals(it, axis.minute(axis.y(it))) }
        assertFalse(axis.points.contains(520))
    }

    @Test fun deadlineReservesSpaceBeforeNextCardAndKeepsNullEnd() {
        val groups = weekGroups(monday, listOf(item(1, 480, null), item(2, 481, 500)), zone)
        val axis = weekAxis(groups, listOf(400f, 200f), 20f, 1f)
        assertTrue(axis.y(481) - axis.y(480) >= 400f)
        assertNull(groups.first().entries.first().end)
    }

    @Test fun concurrentDeadlinesAndTimedEventsShareOneGroup() {
        val groups = weekGroups(monday, listOf(item(1, 480, null), item(2, 480, null), item(3, 480, 540)), zone)
        assertEquals(1, groups.size)
        assertEquals(3, groups.single().entries.size)
    }

    @Test fun overnightEventSplitsAtMidnightAndExpandsVisibleRange() {
        val groups = weekGroups(monday, listOf(item(1, 1380, 1500)), zone)
        assertEquals(listOf(1380, 0), groups.map { it.start })
        assertEquals(listOf(1440, 60), groups.map { it.end })
        val axis = weekAxis(groups, listOf(100f, 100f), 20f, 1f)
        assertEquals(0, axis.points.first())
        assertEquals(1440, axis.points.last())
    }
}

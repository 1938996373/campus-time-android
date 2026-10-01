package com.swish.campustime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swish.campustime.data.ScheduleItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

internal data class WeekEntry(val item: ScheduleItem, val day: Int, val start: Int, val end: Int?)
internal data class WeekGroup(val entries: List<WeekEntry>) {
    val day get() = entries.first().day
    val start get() = entries.minOf { it.start }
    val end get() = entries.maxOf { it.end ?: it.start }
}

internal fun weekGroups(weekStart: LocalDate, items: List<ScheduleItem>, zone: ZoneId = ZoneId.systemDefault()): List<WeekGroup> {
    val entries = buildList {
        repeat(7) { day ->
            val date = weekStart.plusDays(day.toLong())
            val startOfDay = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val nextDay = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            items.forEach { item ->
                val end = item.endMillis
                if (end == null) {
                    if (item.startMillis in startOfDay until nextDay) {
                        val time = Instant.ofEpochMilli(item.startMillis).atZone(zone)
                        add(WeekEntry(item, day, time.hour * 60 + time.minute, null))
                    }
                } else if (item.startMillis < nextDay && end > startOfDay) {
                    fun minute(value: Long) = Instant.ofEpochMilli(value).atZone(zone).let { it.hour * 60 + it.minute }
                    add(WeekEntry(item, day, if (item.startMillis <= startOfDay) 0 else minute(item.startMillis),
                        if (end >= nextDay) 1440 else minute(end)))
                }
            }
        }
    }
    return buildList {
        entries.groupBy { it.day }.values.forEach { dayEntries ->
            var current = mutableListOf<WeekEntry>()
            var end = -1
            dayEntries.sortedWith(compareBy<WeekEntry> { it.start }.thenBy { it.item.stableKey }).forEach { entry ->
                if (current.isNotEmpty() && entry.start >= end && entry.start != current.last().start) {
                    add(WeekGroup(current.toList()))
                    current = mutableListOf()
                    end = -1
                }
                current += entry
                end = maxOf(end, entry.end ?: entry.start)
            }
            if (current.isNotEmpty()) add(WeekGroup(current.toList()))
        }
    }
}

internal class WeekAxis(val points: List<Int>, val positions: FloatArray) {
    fun y(minute: Int): Float {
        val exact = points.binarySearch(minute)
        if (exact >= 0) return positions[exact]
        val i = (-exact - 2).coerceIn(0, points.size - 2)
        return positions[i] + (positions[i + 1] - positions[i]) *
            ((minute - points[i]).toFloat() / (points[i + 1] - points[i])).coerceIn(0f, 1f)
    }
    fun minute(y: Float): Int {
        if (y <= 0) return points.first()
        if (y >= positions.last()) return points.last()
        val i = positions.indexOfFirst { it > y } - 1
        return (points[i] + (points[i + 1] - points[i]) * (y - positions[i]) / (positions[i + 1] - positions[i])).roundToInt()
    }
}

internal fun weekAxis(groups: List<WeekGroup>, heights: List<Float>, minGap: Float, pixelsPerMinute: Float): WeekAxis {
    val first = minOf(480, groups.minOfOrNull { it.start } ?: 480)
    val last = maxOf(1245, groups.maxOfOrNull { it.end } ?: 1245)
    val ticks = sortedSetOf(first, last)
    ((first + 59) / 60..last / 60).forEach { ticks += it * 60 }
    groups.flatMap { it.entries }.forEach { entry ->
        ticks += entry.start
        entry.end?.let { ticks += it }
    }
    val points = ticks.toList()
    val gaps = points.zipWithNext { a, b -> maxOf(minGap, (b - a) * pixelsPerMinute) }.toMutableList()
    // Stretch the entire interval proportionally, preserving earlier size constraints.
    // Deadline-only blocks reserve room before the next boundary without inventing an end time.
    groups.forEachIndexed { index, group ->
        val start = points.indexOf(group.start)
        val end = if (group.end > group.start) points.indexOf(group.end) else start + 1
        if (end < points.size) {
            val available = (start until end).sumOf { gaps[it].toDouble() }.toFloat()
            val extra = (heights[index] - available).coerceAtLeast(0f)
            for (i in start until end) {
                gaps[i] += extra * (points[i + 1] - points[i]) / (points[end] - points[start])
            }
        }
    }
    val positions = FloatArray(points.size)
    gaps.forEachIndexed { i, gap -> positions[i + 1] = positions[i] + gap }
    return WeekAxis(points, positions)
}

@Composable
internal fun WeeklyGrid(weekStart: LocalDate, items: List<ScheduleItem>, modifier: Modifier,
    onBlank: (LocalDate, Int) -> Unit, onItem: (ScheduleItem) -> Unit) {
    val groups = remember(weekStart, items) { weekGroups(weekStart, items) }
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)
    val accent = MaterialTheme.colorScheme.primary
    SubcomposeLayout(modifier) { constraints ->
        val gutter = 52.dp.roundToPx()
        val width = constraints.maxWidth
        val top = 16.dp.roundToPx()
        val columns = (0..7).map { gutter + (width - gutter) * it / 7 }
        val labels = subcompose("labels") {
            val first = minOf(480, groups.minOfOrNull { it.start } ?: 480)
            val last = maxOf(1245, groups.maxOfOrNull { it.end } ?: 1245)
            val points = sortedSetOf(first, last)
            ((first + 59) / 60..last / 60).forEach { points += it * 60 }
            groups.flatMap { it.entries }.forEach { points += it.start; it.end?.let(points::add) }
            points.forEach { Text(weekTime(it), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = if (it % 60 == 0) FontWeight.SemiBold else FontWeight.Normal) }
        }.map { it.measure(Constraints(maxWidth = gutter - 12.dp.roundToPx())) }
        val natural = groups.mapIndexed { index, group ->
            val w = columns[group.day + 1] - columns[group.day]
            subcompose("measure-$index") { WeekGroupCard(group, Modifier.fillMaxWidth().clearAndSetSemantics {}, {}) }
                .single().measure(Constraints.fixedWidth(w))
        }
        val axis = weekAxis(groups, natural.map { it.height.toFloat() },
            maxOf(20.dp.toPx(), (labels.maxOfOrNull { it.height } ?: 0) + 4.dp.toPx()), .9.dp.toPx())
        val bottom = maxOf(axis.positions.last(), groups.indices.maxOfOrNull { axis.y(groups[it].start) + natural[it].height } ?: 0f)
        val height = top + bottom.roundToInt() + 96.dp.roundToPx()
        val background = subcompose("background") {
            Box(Modifier.fillMaxSize().pointerInput(axis, weekStart, columns) {
                detectTapGestures { offset ->
                    val day = (0..6).firstOrNull { offset.x >= columns[it] && offset.x < columns[it + 1] }
                    if (day != null && offset.y >= top && offset.y <= top + axis.positions.last()) {
                        onBlank(weekStart.plusDays(day.toLong()), axis.minute(offset.y - top).coerceIn(0, 1439))
                    }
                }
            })
        }.single().measure(Constraints.fixed(width, height))
        val lines = subcompose("lines") {
            axis.points.forEach { minute ->
                Box(Modifier.width(if (minute % 60 == 0) ((width - gutter) / density).dp else 7.dp)
                    .height(if (minute % 60 == 0) 1.dp else 2.dp)
                    .background(if (minute % 60 == 0) lineColor else accent))
            }
        }.map { it.measure(Constraints()) }
        val cards = groups.mapIndexed { index, group ->
            val w = columns[group.day + 1] - columns[group.day]
            val h = if (group.end > group.start) (axis.y(group.end) - axis.y(group.start)).roundToInt() else natural[index].height
            subcompose("card-$index") { WeekGroupCard(group, Modifier.fillMaxSize(), onItem) }
                .single().measure(Constraints.fixed(w, maxOf(h, natural[index].height)))
        }
        layout(width, height) {
            background.placeRelative(0, 0)
            axis.points.forEachIndexed { i, minute ->
                val y = top + axis.y(minute).roundToInt()
                labels[i].placeRelative(2.dp.roundToPx(), y - labels[i].height / 2)
                lines[i].placeRelative(if (minute % 60 == 0) gutter else gutter - 10.dp.roundToPx(), y)
            }
            cards.forEachIndexed { i, card -> card.placeRelative(columns[groups[i].day], top + axis.y(groups[i].start).roundToInt()) }
        }
    }
}

private fun weekTime(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

@Composable
private fun WeekGroupCard(group: WeekGroup, modifier: Modifier, onItem: (ScheduleItem) -> Unit) {
    val combined = group.entries.size > 1
    Surface(modifier.padding(horizontal = 1.dp), shape = RoundedCornerShape(6.dp),
        color = if (combined) MaterialTheme.colorScheme.secondaryContainer else Color(group.entries.first().item.color).copy(alpha = .16f)) {
        Column(Modifier.padding(horizontal = 3.dp, vertical = 5.dp)) {
            if (combined) {
                Text("重叠 · ${group.entries.size}项", fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
            }
            group.entries.forEachIndexed { index, entry ->
                if (index > 0) { HorizontalDivider(Modifier.padding(vertical = 5.dp)); }
                Column(Modifier.fillMaxWidth().alpha(if (entry.item.completed) .5f else 1f).clickable { onItem(entry.item) }) {
                    Text(entry.item.title, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
                    if (combined || entry.end == null) {
                        Text(if (entry.end == null) "截止\n${weekTime(entry.start)}" else "${weekTime(entry.start)}\n–${weekTime(entry.end)}",
                            fontSize = 9.sp, lineHeight = 12.sp)
                    }
                    if (entry.item.subtitle.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(entry.item.subtitle, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                }
            }
        }
    }
}

package com.swish.campustime.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swish.campustime.data.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private enum class Page(val label: String) { TODAY("今日"), WEEK("本周"), TASKS("任务"), SETTINGS("设置") }
private enum class AddKind { COURSE, EVENT, TASK }
private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val weekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusTimeApp(
    state: MainUiState,
    viewModel: MainViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit,
    ensureNotificationPermission: () -> Unit
) {
    var page by remember { mutableStateOf(Page.TODAY) }
    var addMenu by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<EditorTarget?>(null) }
    var blockEditor by remember { mutableStateOf<Triple<Task, TaskBlock?, Long?>?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            val result = snackbar.showSnackbar(it, actionLabel = if (state.canUndo) "撤销" else null)
            viewModel.consumeMessage()
            if (state.canUndo && result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.discardUndo()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Page.entries.forEach { item ->
                    NavigationBarItem(
                        selected = page == item,
                        onClick = { page = item },
                        icon = { Icon(when (item) { Page.TODAY -> Icons.Default.Today; Page.WEEK -> Icons.Default.DateRange; Page.TASKS -> Icons.Default.CheckCircle; Page.SETTINGS -> Icons.Default.Settings }, null) },
                        label = { Text(item.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (page != Page.SETTINGS) Box {
                FloatingActionButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, "新增") }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text("新增课程") }, leadingIcon = { Icon(Icons.Default.School, null) }, onClick = { addMenu = false; editor = EditorTarget.CourseTarget() })
                    DropdownMenuItem(text = { Text("新增计划") }, leadingIcon = { Icon(Icons.Default.Event, null) }, onClick = { addMenu = false; editor = EditorTarget.EventTarget() })
                    DropdownMenuItem(text = { Text("新增任务") }, leadingIcon = { Icon(Icons.Default.TaskAlt, null) }, onClick = { addMenu = false; editor = EditorTarget.TaskTarget() })
                }
            }
        }
    ) { padding ->
        if (state.loading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else when (page) {
            Page.TODAY -> TodayScreen(state, Modifier.padding(padding), { item ->
                if (item.kind == ScheduleKind.BLOCK) state.data.blocks.firstOrNull { it.id == item.sourceId }?.let { block ->
                    state.data.tasks.firstOrNull { it.id == block.taskId }?.let { task -> blockEditor = Triple(task, block, block.occurrenceDueEpochMillis) }
                } else editor = targetFor(item, state.data)
            },
                { task, due -> editor = EditorTarget.TaskTarget(task, due) },
                { task, due -> blockEditor = Triple(task, null, due) }, viewModel::toggleTask)
            Page.WEEK -> WeekScreen(state, viewModel, Modifier.padding(padding), onBlank = { date, minute -> editor = EditorTarget.EventTarget(defaultStart = date.atStartOfDay().plusMinutes(minute.toLong())) }) { item ->
                if (item.kind == ScheduleKind.BLOCK) state.data.blocks.firstOrNull { it.id == item.sourceId }?.let { block ->
                    state.data.tasks.firstOrNull { it.id == block.taskId }?.let { task -> blockEditor = Triple(task, block, block.occurrenceDueEpochMillis) }
                } else editor = targetFor(item, state.data)
            }
            Page.TASKS -> TaskPlannerScreen(state.data, state.currentDate, viewModel, Modifier.padding(padding),
                onEdit = { task, due -> editor = EditorTarget.TaskTarget(task, due) }, onSchedule = { task, due -> blockEditor = Triple(task, null, due) },
                onEditBlock = { block -> state.data.tasks.firstOrNull { it.id == block.taskId }?.let { task -> blockEditor = Triple(task, block, block.occurrenceDueEpochMillis) } })
            Page.SETTINGS -> SettingsScreen(state.data.semester, Modifier.padding(padding), viewModel::saveSemester, onExport, onImport, viewModel::restoreBeforeLastImport, viewModel.hasRestoreSnapshot())
        }
    }

    editor?.let { target ->
        when (target) {
            is EditorTarget.CourseTarget -> CourseEditor(target.value, state.data.semester, onDismiss = { editor = null }, onSave = { if (it.reminderMinutes != null) ensureNotificationPermission(); viewModel.saveCourse(it); editor = null }, onDelete = target.value?.let { value -> {{ viewModel.deleteCourse(value); editor = null }} })
            is EditorTarget.EventTarget -> EventEditor(target.value, target.defaultStart, target.occurrenceStart, onDismiss = { editor = null }, onSave = { value, onlyThis ->
                if (value.reminderMinutes != null) ensureNotificationPermission()
                if (onlyThis && target.value != null && target.occurrenceStart != null) viewModel.saveEventOccurrence(target.value, target.occurrenceStart, value) else viewModel.saveEvent(value)
                editor = null
            }, onDelete = target.value?.let { series -> {{ onlyThis: Boolean -> if (onlyThis && target.occurrenceStart != null) viewModel.deleteEventOccurrence(series, target.occurrenceStart) else viewModel.deleteEvent(series); editor = null }} })
            is EditorTarget.TaskTarget -> TaskEditor(target.value, target.occurrenceDue, onDismiss = { editor = null }, onSave = { value, onlyThis ->
                if (value.reminderMinutes != null) ensureNotificationPermission()
                if (onlyThis && target.value != null && target.occurrenceDue != null) viewModel.saveTaskOccurrence(target.value, target.occurrenceDue, value) else viewModel.saveTask(value)
                editor = null
            }, onDelete = target.value?.let { series -> {{ onlyThis: Boolean -> if (onlyThis && target.occurrenceDue != null) viewModel.deleteTaskOccurrence(series, target.occurrenceDue) else viewModel.deleteTask(series); editor = null }} })
        }
    }
    blockEditor?.let { (task, block, due) ->
        TaskBlockEditor(task, block, due, state.data, onDismiss = { blockEditor = null }, onSave = { viewModel.saveBlock(it); blockEditor = null },
            onDelete = block?.let { value -> { viewModel.deleteBlock(value); blockEditor = null } })
    }
}

private sealed interface EditorTarget {
    data class CourseTarget(val value: Course? = null) : EditorTarget
    data class EventTarget(val value: ScheduleEvent? = null, val defaultStart: LocalDateTime? = null, val occurrenceStart: Long? = null) : EditorTarget
    data class TaskTarget(val value: Task? = null, val occurrenceDue: Long? = null) : EditorTarget
}

private fun targetFor(item: ScheduleItem, data: AppData): EditorTarget = when (item.kind) {
    ScheduleKind.COURSE -> EditorTarget.CourseTarget(data.courses.firstOrNull { it.id == item.sourceId })
    ScheduleKind.EVENT -> EditorTarget.EventTarget(data.events.firstOrNull { it.id == item.sourceId }, Instant.ofEpochMilli(item.startMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(), item.startMillis)
    ScheduleKind.TASK -> EditorTarget.TaskTarget(data.tasks.firstOrNull { it.id == item.sourceId }, item.startMillis)
    ScheduleKind.BLOCK -> error("时间块由独立编辑器处理")
}

@Composable
private fun TodayScreen(state: MainUiState, modifier: Modifier, onItem: (ScheduleItem) -> Unit,
    onTask: (Task, Long?) -> Unit, onSchedule: (Task, Long?) -> Unit, onToggle: (Task, Long?) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(30_000) } }
    val active = state.todayItems.firstOrNull { it.endMillis != null && !it.completed && now in it.startMillis until it.endMillis }
    val next = state.todayItems.firstOrNull { it.startMillis > now && !it.completed }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(state.currentDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE")), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp)); Text("今天要做什么，一眼就知道", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusCard("正在进行", active?.title ?: "当前没有安排", active?.let { formatRange(it) } ?: "享受空闲时间", Modifier.weight(1f), MaterialTheme.colorScheme.primaryContainer)
                StatusCard("下一项", next?.title ?: "今天已无安排", next?.let { formatRange(it) } ?: "", Modifier.weight(1f), MaterialTheme.colorScheme.secondaryContainer)
            }
        }
        item { TodayTaskPanel(state.data, state.currentDate, now, onTask, onSchedule, onToggle) }
        item { Text("今日时间轴", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        if (state.todayItems.isEmpty()) item { EmptyState("今天还没有安排", "点击右下角 + 添加课程、计划或任务") }
        items(state.todayItems, key = { it.stableKey }) { item -> TimelineCard(item, now, onItem) }
    }
}

@Composable
private fun StatusCard(label: String, title: String, detail: String, modifier: Modifier, color: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp)); Text(title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TimelineCard(item: ScheduleItem, now: Long, onItem: (ScheduleItem) -> Unit) {
    val past = (item.endMillis ?: item.startMillis) < now
    Card(Modifier.fillMaxWidth().alpha(if (past || item.completed) .58f else 1f).clickable { onItem(item) }, border = if (item.conflict) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(48.dp).background(Color(item.color), RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(formatRange(item), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (item.subtitle.isNotBlank()) Text(item.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.conflict) Icon(Icons.Default.Warning, "时间冲突", tint = MaterialTheme.colorScheme.error)
            if (item.completed) Icon(Icons.Default.CheckCircle, "已完成")
        }
    }
}

@Composable
private fun WeekScreen(state: MainUiState, viewModel: MainViewModel, modifier: Modifier, onBlank: (LocalDate, Int) -> Unit, onItem: (ScheduleItem) -> Unit) {
    val vScroll = rememberScrollState()
    var selectedDay by remember(state.weekStart) { mutableStateOf<LocalDate?>(null) }
    var showJump by remember { mutableStateOf(false) }
    var jumpDate by remember { mutableStateOf(state.currentDate.format(dateFormat)) }
    var jumpError by remember { mutableStateOf(false) }
    val freeMinutes = state.data.semester?.let { config -> (0..6).sumOf { day ->
        PlanningEngine.freeSlots(state.weekStart.plusDays(day.toLong()), state.weekItems, config).sumOf { it.durationMinutes }
    } } ?: 0
    val unscheduledCount = state.data.tasks.count { task -> !task.completed && task.plannedEpochDay == null &&
        state.data.blocks.none { it.taskId == task.id } }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = viewModel::previousWeek) { Icon(Icons.Default.ChevronLeft, "上一周") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.weekStart.format(DateTimeFormatter.ofPattern("M月d日"))} – ${state.weekStart.plusDays(6).format(DateTimeFormatter.ofPattern("M月d日"))}", fontWeight = FontWeight.Bold)
                TextButton(onClick = viewModel::currentWeek) { Text("回到本周") }
            }
            IconButton(onClick = viewModel::nextWeek) { Icon(Icons.Default.ChevronRight, "下一周") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("可安排 ${freeMinutes / 60}小时${freeMinutes % 60}分钟 · $unscheduledCount 项待安排", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showJump = true }) { Text("跳转周次") }
        }
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            Spacer(Modifier.width(52.dp))
            repeat(7) { index ->
                val date = state.weekStart.plusDays(index.toLong())
                val dayFree = state.data.semester?.let { PlanningEngine.freeSlots(date, state.weekItems, it).sumOf { slot -> slot.durationMinutes } } ?: 0
                DayHeader(date, dayFree, Modifier.weight(1f).clickable { selectedDay = date })
            }
        }
        if (selectedDay != null) {
            TextButton(onClick = { selectedDay = null }) { Text("返回周视图") }
            WeekDayView(selectedDay!!, state, Modifier.fillMaxSize(), onItem)
        } else WeeklyGrid(state.weekStart, state.weekItems, Modifier.fillMaxSize().verticalScroll(vScroll), onBlank, onItem)
    }
    if (showJump) AlertDialog(onDismissRequest = { showJump = false }, title = { Text("跳转到日期所在周") },
        text = { Column { Input(jumpDate, { jumpDate = it; jumpError = false }, "日期 yyyy-MM-dd"); if (jumpError) Text("请输入有效日期", color = MaterialTheme.colorScheme.error) } },
        confirmButton = { TextButton(onClick = { runCatching { LocalDate.parse(jumpDate, dateFormat) }.onSuccess { viewModel.goToWeek(it); showJump = false }.onFailure { jumpError = true } }) { Text("跳转") } },
        dismissButton = { TextButton(onClick = { showJump = false }) { Text("取消") } })
}

@Composable
private fun DayHeader(date: LocalDate, freeMinutes: Int, modifier: Modifier = Modifier) {
    Surface(modifier, color = if (date == LocalDate.now()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(weekNames[date.dayOfWeek.value - 1].removePrefix("周"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(date.dayOfMonth.toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("余${freeMinutes / 60}h", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.EventAvailable, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp)); Text(title, fontWeight = FontWeight.Bold); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatRange(item: ScheduleItem): String {
    val start = Instant.ofEpochMilli(item.startMillis).atZone(ZoneId.systemDefault())
    return if (item.endMillis == null) "截止 ${start.format(timeFormat)}" else "${start.format(timeFormat)}–${Instant.ofEpochMilli(item.endMillis).atZone(ZoneId.systemDefault()).format(timeFormat)}"
}
private fun formatDateTime(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(dateTimeFormat)

@Composable
private fun CourseEditor(value: Course?, semester: SemesterConfig?, onDismiss: () -> Unit, onSave: (Course) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember(value?.id) { mutableStateOf(value?.title.orEmpty()) }
    var teacher by remember(value?.id) { mutableStateOf(value?.teacher.orEmpty()) }
    var room by remember(value?.id) { mutableStateOf(value?.room.orEmpty()) }
    var note by remember(value?.id) { mutableStateOf(value?.note.orEmpty()) }
    var day by remember(value?.id) { mutableStateOf((value?.dayOfWeek ?: LocalDate.now().dayOfWeek.value).toString()) }
    var startTime by remember(value?.id) { mutableStateOf(minutesText(value?.startMinute ?: 8 * 60)) }
    var endTime by remember(value?.id) { mutableStateOf(minutesText(value?.endMinute ?: 8 * 60 + 45)) }
    var startWeek by remember(value?.id) { mutableStateOf((value?.startWeek ?: 1).toString()) }
    var endWeek by remember(value?.id) { mutableStateOf((value?.endWeek ?: 20).toString()) }
    var parity by remember(value?.id) { mutableStateOf(value?.weekParity ?: "ALL") }
    val periods = remember(semester?.periodTimes) { ScheduleEngine.periodRanges(semester?.periodTimes.orEmpty()) }
    var usePeriods by remember(value?.id) { mutableStateOf(value?.startPeriod != null || value == null && periods.isNotEmpty()) }
    var startPeriod by remember(value?.id) { mutableIntStateOf(value?.startPeriod ?: 1) }
    var endPeriod by remember(value?.id) { mutableIntStateOf(value?.endPeriod ?: 1) }
    var remind by remember(value?.id) { mutableStateOf(value?.reminderMinutes != null) }
    var reminderMinutes by remember(value?.id) { mutableStateOf((value?.reminderMinutes ?: 10).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog(title = if (value == null) "新增课程" else "编辑课程", onDismiss, onDelete, onConfirm = {
        runCatching {
            require(title.isNotBlank()) { "请输入课程名称" }
            val start = if (usePeriods) periods.getOrNull(startPeriod - 1)?.first ?: error("开始节次无效") else parseMinutes(startTime)
            val end = if (usePeriods) periods.getOrNull(endPeriod - 1)?.second ?: error("结束节次无效") else parseMinutes(endTime)
            require(end > start) { "结束时间必须晚于开始时间" }
            val startW = startWeek.toInt(); val endW = endWeek.toInt()
            require(startW in 1..60 && endW >= startW) { "周次范围不正确" }
            Course(
                id = value?.id ?: 0,
                title = title.trim(),
                teacher = teacher.trim(),
                room = room.trim(),
                dayOfWeek = day.toInt().coerceIn(1, 7),
                startMinute = start,
                endMinute = end,
                startWeek = startW,
                endWeek = endW,
                weekParity = parity,
                color = value?.color ?: 0xFF6750A4,
                reminderMinutes = if (remind) reminderMinutes.toInt().coerceAtLeast(0) else null,
                note = note.trim(),
                startPeriod = if (usePeriods) startPeriod else null,
                endPeriod = if (usePeriods) endPeriod else null
            )
        }.onSuccess(onSave).onFailure { error = it.message }
    }) {
        Input(title, { title = it }, "课程名称 *")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Input(teacher, { teacher = it }, "教师", Modifier.weight(1f)); Input(room, { room = it }, "教室", Modifier.weight(1f)) }
        Input(note, { note = it }, "备注")
        Text("星期", fontWeight = FontWeight.SemiBold)
        LazyChoice((1..7).map { it.toString() to weekNames[it - 1] }, day) { day = it }
        if (periods.isNotEmpty()) LazyChoice(listOf("PERIOD" to "按节次", "TIME" to "按时间"), if (usePeriods) "PERIOD" else "TIME") { usePeriods = it == "PERIOD" }
        if (usePeriods && periods.isNotEmpty()) {
            Text("开始节次")
            LazyChoice(periods.indices.map { (it + 1).toString() to "第${it + 1}节" }, startPeriod.toString()) { startPeriod = it.toInt(); if (endPeriod < startPeriod) endPeriod = startPeriod }
            Text("结束节次")
            LazyChoice(periods.indices.filter { it + 1 >= startPeriod }.map { (it + 1).toString() to "第${it + 1}节" }, endPeriod.toString()) { endPeriod = it.toInt() }
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { DateTimeField(startTime, { startTime = it }, "开始时间", false, Modifier.weight(1f)); DateTimeField(endTime, { endTime = it }, "结束时间", false, Modifier.weight(1f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Input(startWeek, { startWeek = it }, "起始周", Modifier.weight(1f)); Input(endWeek, { endWeek = it }, "结束周", Modifier.weight(1f)) }
        Text("重复周", fontWeight = FontWeight.SemiBold)
        LazyChoice(listOf("ALL" to "每周", "ODD" to "单周", "EVEN" to "双周"), parity) { parity = it }
        ReminderFields(remind, { remind = it }, reminderMinutes, { reminderMinutes = it })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun EventEditor(value: ScheduleEvent?, defaultStart: LocalDateTime?, occurrenceStart: Long?, onDismiss: () -> Unit, onSave: (ScheduleEvent, Boolean) -> Unit, onDelete: ((Boolean) -> Unit)?) {
    val startDefault = defaultStart ?: LocalDateTime.now().withSecond(0).withNano(0).plusHours(1)
    val occurrenceEditing = value?.recurrence != null && value.recurrence != "NONE" && occurrenceStart != null
    val shownStart = if (occurrenceEditing) startDefault else value?.let { Instant.ofEpochMilli(it.startEpochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime() } ?: startDefault
    val shownDuration = value?.let { it.endEpochMillis - it.startEpochMillis } ?: 3_600_000L
    var title by remember(value?.id) { mutableStateOf(value?.title.orEmpty()) }
    var note by remember(value?.id) { mutableStateOf(value?.note.orEmpty()) }
    var start by remember(value?.id, occurrenceStart) { mutableStateOf(shownStart.format(dateTimeFormat)) }
    var end by remember(value?.id, occurrenceStart) { mutableStateOf(shownStart.plusNanos(shownDuration * 1_000_000).format(dateTimeFormat)) }
    var recurrence by remember(value?.id) { mutableStateOf(value?.recurrence ?: "NONE") }
    var weekdays by remember(value?.id) { mutableStateOf(value?.weekdaysCsv.orEmpty()) }
    var remind by remember(value?.id) { mutableStateOf(value?.reminderMinutes != null) }
    var reminderMinutes by remember(value?.id) { mutableStateOf((value?.reminderMinutes ?: 10).toString()) }
    var onlyThis by remember(value?.id, occurrenceStart) { mutableStateOf(occurrenceEditing) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog(if (value == null) "新增计划" else "编辑计划", onDismiss, onDelete?.let { { it(onlyThis) } }, onConfirm = {
        runCatching {
            require(title.isNotBlank()) { "请输入计划名称" }
            val chosenStart = parseDateTime(start); val chosenEnd = parseDateTime(end)
            require(chosenEnd > chosenStart) { "结束时间必须晚于开始时间" }
            val startMs = if (occurrenceEditing && !onlyThis) {
                val originalDay = Instant.ofEpochMilli(value!!.startEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                val chosenTime = Instant.ofEpochMilli(chosenStart).atZone(ZoneId.systemDefault()).toLocalTime()
                originalDay.atTime(chosenTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else chosenStart
            val endMs = startMs + (chosenEnd - chosenStart)
            require(recurrence != "CUSTOM" || weekdays.isNotBlank()) { "请至少选择一个重复星期" }
            ScheduleEvent(value?.id ?: 0, title.trim(), note.trim(), startMs, endMs, recurrence, if (recurrence == "CUSTOM") weekdays else "", value?.recurrenceEndEpochDay, value?.color ?: 0xFF006A6A, if (remind) reminderMinutes.toInt().coerceAtLeast(0) else null, value?.excludedEpochDaysCsv.orEmpty())
        }.onSuccess { onSave(it, onlyThis) }.onFailure { error = it.message }
    }) {
        if (occurrenceEditing) {
            Text("修改范围", fontWeight = FontWeight.SemiBold)
            LazyChoice(listOf("ONE" to "仅本次", "SERIES" to "整个系列"), if (onlyThis) "ONE" else "SERIES") { onlyThis = it == "ONE" }
            if (!onlyThis) Text("整个系列保留首次日期与已排除的日期，只更新时刻和其他属性。", style = MaterialTheme.typography.bodySmall)
        }
        Input(title, { title = it }, "计划名称 *")
        Input(note, { note = it }, "备注")
        DateTimeField(start, { start = it }, "开始时间")
        DateTimeField(end, { end = it }, "结束时间")
        Text("重复", fontWeight = FontWeight.SemiBold)
        LazyChoice(recurrenceChoices, recurrence) { recurrence = it }
        if (recurrence == "CUSTOM") WeekdaySelector(weekdays) { weekdays = it }
        ReminderFields(remind, { remind = it }, reminderMinutes, { reminderMinutes = it })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun TaskEditor(value: Task?, occurrenceDue: Long?, onDismiss: () -> Unit, onSave: (Task, Boolean) -> Unit, onDelete: ((Boolean) -> Unit)?) {
    val occurrenceEditing = value?.recurrence != null && value.recurrence != "NONE" && occurrenceDue != null
    var title by remember(value?.id) { mutableStateOf(value?.title.orEmpty()) }
    var note by remember(value?.id) { mutableStateOf(value?.note.orEmpty()) }
    var due by remember(value?.id, occurrenceDue) { mutableStateOf(if (occurrenceEditing) formatDateTime(occurrenceDue!!) else value?.let { formatDateTime(it.dueEpochMillis) } ?: LocalDateTime.now().plusHours(2).withSecond(0).withNano(0).format(dateTimeFormat)) }
    var hasDue by remember(value?.id) { mutableStateOf(value?.hasDueDate ?: false) }
    var planDate by remember(value?.id) { mutableStateOf(value?.plannedEpochDay?.let { LocalDate.ofEpochDay(it).format(dateFormat) }.orEmpty()) }
    var estimate by remember(value?.id) { mutableStateOf(value?.estimateMinutes?.toString().orEmpty()) }
    var priority by remember(value?.id) { mutableIntStateOf(value?.priority ?: 0) }
    var category by remember(value?.id) { mutableStateOf(value?.category.orEmpty()) }
    var showMore by remember(value?.id) { mutableStateOf(value != null) }
    var recurrence by remember(value?.id) { mutableStateOf(value?.recurrence ?: "NONE") }
    var weekdays by remember(value?.id) { mutableStateOf(value?.weekdaysCsv.orEmpty()) }
    var remind by remember(value?.id) { mutableStateOf(value?.reminderMinutes != null) }
    var reminderMinutes by remember(value?.id) { mutableStateOf((value?.reminderMinutes ?: 10).toString()) }
    var onlyThis by remember(value?.id, occurrenceDue) { mutableStateOf(occurrenceEditing) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorDialog(if (value == null) "新增任务" else "编辑任务", onDismiss, onDelete?.let { { it(onlyThis) } }, onConfirm = {
        runCatching {
            require(title.isNotBlank()) { "请输入任务名称" }
            require(recurrence == "NONE" || hasDue) { "重复任务需要截止时间" }
            require(recurrence != "CUSTOM" || weekdays.isNotBlank()) { "请至少选择一个重复星期" }
            val estimated = estimate.trim().takeIf { it.isNotEmpty() }?.toInt()
            require(estimated == null || estimated in 1..1440) { "预计用时应为 1–1440 分钟" }
            val chosenDue = parseDateTime(due)
            val effectiveDue = if (occurrenceEditing && !onlyThis) {
                val originalDay = Instant.ofEpochMilli(value!!.dueEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                val chosenTime = Instant.ofEpochMilli(chosenDue).atZone(ZoneId.systemDefault()).toLocalTime()
                originalDay.atTime(chosenTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else chosenDue
            Task(id = value?.id ?: 0, title = title.trim(), note = note.trim(), dueEpochMillis = effectiveDue,
                completed = value?.completed ?: false, recurrence = recurrence, weekdaysCsv = if (recurrence == "CUSTOM") weekdays else "",
                recurrenceEndEpochDay = value?.recurrenceEndEpochDay, reminderMinutes = if (remind && hasDue) reminderMinutes.toInt().coerceAtLeast(0) else null,
                excludedEpochDaysCsv = value?.excludedEpochDaysCsv.orEmpty(), hasDueDate = hasDue,
                plannedEpochDay = if (planDate.isBlank() || recurrence != "NONE") null else LocalDate.parse(planDate, dateFormat).toEpochDay(),
                estimateMinutes = estimated, priority = priority, category = category.trim(), completedEpochDaysCsv = value?.completedEpochDaysCsv.orEmpty())
        }.onSuccess { onSave(it, onlyThis) }.onFailure { error = it.message }
    }) {
        if (occurrenceEditing) {
            Text("修改范围", fontWeight = FontWeight.SemiBold)
            LazyChoice(listOf("ONE" to "仅本次", "SERIES" to "整个系列"), if (onlyThis) "ONE" else "SERIES") { onlyThis = it == "ONE" }
            if (!onlyThis) Text("整个系列保留首次日期与已排除的日期，只更新时刻和其他属性。", style = MaterialTheme.typography.bodySmall)
        }
        Input(title, { title = it }, "任务名称 *")
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(hasDue, { hasDue = it }); Spacer(Modifier.width(8.dp)); Text("设置截止时间") }
        if (hasDue) DateTimeField(due, { due = it }, "截止时间")
        if (recurrence == "NONE") Input(planDate, { planDate = it }, "计划日期 yyyy-MM-dd（可留空）")
        Text("预计用时")
        LazyChoice(listOf("15" to "15分钟", "30" to "30分钟", "60" to "1小时", "120" to "2小时"), estimate) { estimate = it }
        Input(estimate, { estimate = it }, "预计分钟数（可留空）")
        TextButton(onClick = { showMore = !showMore }) { Text(if (showMore) "收起更多选项" else "分类、重复与提醒") }
        if (showMore) {
            Input(note, { note = it }, "备注")
            Text("重要程度")
            LazyChoice(listOf("0" to "普通", "1" to "重要", "2" to "紧急"), priority.toString()) { priority = it.toInt() }
            Input(category, { category = it }, "分类（如课程作业、个人项目）")
            Text("重复", fontWeight = FontWeight.SemiBold)
            LazyChoice(recurrenceChoices, recurrence) { recurrence = it }
            if (recurrence == "CUSTOM") WeekdaySelector(weekdays) { weekdays = it }
            ReminderFields(remind, { remind = it }, reminderMinutes, { reminderMinutes = it })
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private val recurrenceChoices = listOf("NONE" to "不重复", "DAILY" to "每天", "WEEKLY" to "每周", "CUSTOM" to "自定义星期")

@Composable
private fun EditorDialog(title: String, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onConfirm: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("保存") } },
        dismissButton = { Row { if (onDelete != null) TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = onDismiss) { Text("取消") } } }
    )
}

@Composable
private fun Input(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(value, onChange, modifier, label = { Text(label) }, singleLine = true)
}

@Composable
private fun LazyChoice(choices: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (key, label) -> FilterChip(selected = selected == key, onClick = { onSelect(key) }, label = { Text(label) }) }
    }
}

@Composable
private fun WeekdaySelector(value: String, onChange: (String) -> Unit) {
    val selected = value.split(',').mapNotNull { it.toIntOrNull() }.toSet()
    Text("选择重复星期")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..7).forEach { day ->
            FilterChip(selected = day in selected, onClick = {
                onChange((if (day in selected) selected - day else selected + day).sorted().joinToString(","))
            }, label = { Text(weekNames[day - 1]) })
        }
    }
}

@Composable
private fun ReminderFields(enabled: Boolean, onEnabled: (Boolean) -> Unit, minutes: String, onMinutes: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(enabled, onEnabled); Spacer(Modifier.width(8.dp)); Text("开启提醒", Modifier.weight(1f))
        if (enabled) Input(minutes, onMinutes, "提前分钟", Modifier.width(112.dp))
    }
}

@Composable
private fun SettingsScreen(semester: SemesterConfig?, modifier: Modifier, onSave: (SemesterConfig) -> Unit, onExport: () -> Unit, onImport: () -> Unit,
    onRestorePrevious: () -> Unit, hasRestorePrevious: Boolean) {
    var name by remember(semester) { mutableStateOf(semester?.name ?: "当前学期") }
    var start by remember(semester) { mutableStateOf(semester?.let { LocalDate.ofEpochDay(it.startEpochDay).format(dateFormat) } ?: LocalDate.now().format(dateFormat)) }
    var weeks by remember(semester) { mutableStateOf((semester?.totalWeeks ?: 20).toString()) }
    var periods by remember(semester) { mutableStateOf(semester?.periodTimes ?: ScheduleRepository.DEFAULT_PERIODS) }
    var dayStart by remember(semester) { mutableStateOf(minutesText(semester?.dayStartMinute ?: 480)) }
    var dayEnd by remember(semester) { mutableStateOf(minutesText(semester?.dayEndMinute ?: 1320)) }
    var breakStart by remember(semester) { mutableStateOf(minutesText(semester?.breakStartMinute ?: 720)) }
    var breakEnd by remember(semester) { mutableStateOf(minutesText(semester?.breakEndMinute ?: 780)) }
    var buffer by remember(semester) { mutableStateOf((semester?.bufferMinutes ?: 10).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("学期与节次", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Input(name, { name = it }, "学期名称")
        Input(start, { start = it }, "开学日期 yyyy-MM-dd")
        Input(weeks, { weeks = it }, "学期周数")
        OutlinedTextField(periods, { periods = it }, Modifier.fillMaxWidth(), label = { Text("节次时间（逗号分隔）") }, minLines = 3)
        Text("可安排时间", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("用于显示空闲时间和安排任务。课程与已有计划仍按实际时间显示。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { DateTimeField(dayStart, { dayStart = it }, "每天开始", false, Modifier.weight(1f)); DateTimeField(dayEnd, { dayEnd = it }, "每天结束", false, Modifier.weight(1f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { DateTimeField(breakStart, { breakStart = it }, "休息开始", false, Modifier.weight(1f)); DateTimeField(breakEnd, { breakEnd = it }, "休息结束", false, Modifier.weight(1f)) }
        Input(buffer, { buffer = it }, "安排前后缓冲分钟")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            runCatching {
                val first = parseMinutes(dayStart); val last = parseMinutes(dayEnd)
                val restFirst = parseMinutes(breakStart); val restLast = parseMinutes(breakEnd)
                require(first < last && restFirst < restLast) { "请检查时间范围" }
                require(ScheduleEngine.periodRanges(periods).size == periods.split(',').size) { "节次时间格式不正确" }
                val gap = buffer.toInt()
                require(gap in 0..120) { "缓冲时间应为 0–120 分钟" }
                SemesterConfig(1, name.trim(), LocalDate.parse(start, dateFormat).toEpochDay(), weeks.toInt().coerceIn(1, 60), periods.trim(), first, last, restFirst, restLast, gap)
            }.onSuccess { onSave(it); error = null }.onFailure { error = it.message ?: "请检查设置格式" }
        }, Modifier.fillMaxWidth()) { Text("保存学期设置") }
        HorizontalDivider()
        Text("数据备份", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("数据仅保存在本机。恢复会先在应用内部保留恢复前快照。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onExport, Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text("导出 JSON 备份") }
        OutlinedButton(onClick = onImport, Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("从 JSON 恢复") }
        if (hasRestorePrevious) OutlinedButton(onClick = onRestorePrevious, Modifier.fillMaxWidth()) { Text("恢复上次导入前的数据") }
        Spacer(Modifier.height(80.dp))
    }
}

private fun minutesText(value: Int) = "%02d:%02d".format(value / 60, value % 60)
private fun parseMinutes(value: String): Int {
    val time = LocalTime.parse(value, timeFormat)
    return time.hour * 60 + time.minute
}
private fun parseDateTime(value: String): Long = try {
    LocalDateTime.parse(value, dateTimeFormat).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
} catch (_: DateTimeParseException) { throw IllegalArgumentException("日期时间格式应为 yyyy-MM-dd HH:mm") }

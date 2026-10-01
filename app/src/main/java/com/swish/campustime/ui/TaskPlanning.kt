package com.swish.campustime.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swish.campustime.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val dateOnly = DateTimeFormatter.ofPattern("M月d日")
private val clock = DateTimeFormatter.ofPattern("HH:mm")
private val zone get() = ZoneId.systemDefault()

internal data class TaskRow(val task: Task, val due: Long?, val completed: Boolean, val group: String) {
    val key get() = "${task.id}-${due ?: "inbox"}"
}

internal fun taskRows(data: AppData, today: LocalDate, now: Long = System.currentTimeMillis()): List<TaskRow> = buildList {
    data.tasks.forEach { task ->
        val dues = if (task.recurrence == "NONE") listOf(if (task.hasDueDate) task.dueEpochMillis else null)
        else ScheduleEngine.taskDueOccurrences(task, today.minusDays(7), today.plusDays(14)).map { it as Long? }
        dues.forEach { due ->
            val completed = if (task.recurrence == "NONE" || task.completed) task.completed else due?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay().toString() in task.completedEpochDaysCsv.split(',')
            } ?: false
            val dueDay = due?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            val planned = task.plannedEpochDay?.let(LocalDate::ofEpochDay)
            val blockToday = data.blocks.any { it.taskId == task.id && it.occurrenceDueEpochMillis == due &&
                Instant.ofEpochMilli(it.startEpochMillis).atZone(zone).toLocalDate() == today }
            val group = when {
                completed -> "DONE"
                due != null && due < now || planned != null && planned < today -> "OVERDUE"
                planned == today || dueDay == today || blockToday -> "TODAY"
                planned != null && planned in today.plusDays(1)..today.plusDays(7) || dueDay != null && dueDay in today.plusDays(1)..today.plusDays(7) -> "WEEK"
                else -> "INBOX"
            }
            add(TaskRow(task, due, completed, group))
        }
    }
}.sortedWith(compareBy<TaskRow> { listOf("OVERDUE", "TODAY", "WEEK", "INBOX", "DONE").indexOf(it.group) }
    .thenByDescending { it.task.priority }.thenBy { it.due ?: Long.MAX_VALUE })

@Composable
internal fun TaskPlannerScreen(data: AppData, today: LocalDate, viewModel: MainViewModel, modifier: Modifier,
    onEdit: (Task, Long?) -> Unit, onSchedule: (Task, Long?) -> Unit, onEditBlock: (TaskBlock) -> Unit) {
    var filter by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(30_000) } }
    val rows = remember(data, today, now) { taskRows(data, today, now) }
    val shown = rows.filter { (filter == "ALL" && it.group != "DONE" || it.group == filter) &&
        (search.isBlank() || it.task.title.contains(search, true) || it.task.category.contains(search, true) || it.task.note.contains(search, true)) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("任务", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("搜索任务、分类或备注") }, singleLine = true) }
        item {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to "待处理", "INBOX" to "待安排", "TODAY" to "今天", "WEEK" to "未来七天", "OVERDUE" to "逾期", "DONE" to "已完成").forEach { (key, label) ->
                    FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                }
            }
        }
        if (shown.isEmpty()) item { Text("这里暂时没有任务", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(shown, key = { it.key }) { row ->
            TaskPlanningCard(row, data, viewModel, onEdit, onSchedule, onEditBlock)
        }
    }
}

@Composable
private fun TaskPlanningCard(row: TaskRow, data: AppData, viewModel: MainViewModel,
    onEdit: (Task, Long?) -> Unit, onSchedule: (Task, Long?) -> Unit, onEditBlock: (TaskBlock) -> Unit) {
    var stepTitle by remember(row.key) { mutableStateOf("") }
    var stepsExpanded by remember(row.key) { mutableStateOf(false) }
    val blocks = data.blocks.filter { it.taskId == row.task.id && it.occurrenceDueEpochMillis == row.due }
    val steps = if (row.task.recurrence == "NONE") data.steps.filter { it.taskId == row.task.id } else emptyList()
    val scheduled = PlanningEngine.minutesScheduledForTask(row.task.id, row.due, data.blocks)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(row.completed, onCheckedChange = { viewModel.toggleTask(row.task, row.due) })
                Column(Modifier.weight(1f).clickable { onEdit(row.task, row.due) }) {
                    Text(row.task.title, fontWeight = FontWeight.SemiBold)
                    Text(buildList {
                        row.due?.let { add("截止 ${Instant.ofEpochMilli(it).atZone(zone).format(dateTime)}") }
                        row.task.plannedEpochDay?.let { add("计划 ${LocalDate.ofEpochDay(it).format(dateOnly)}") }
                        if (row.task.category.isNotBlank()) add(row.task.category)
                        if (row.task.priority > 0) add(if (row.task.priority == 2) "紧急" else "重要")
                    }.joinToString(" · ").ifBlank { "待安排" }, style = MaterialTheme.typography.bodySmall,
                        color = if (row.group == "OVERDUE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onEdit(row.task, row.due) }) { Icon(Icons.Default.Edit, "编辑任务") }
            }
            row.task.estimateMinutes?.let { Text("预计 $it 分钟 · 已安排 $scheduled 分钟 · 还需 ${(it - scheduled).coerceAtLeast(0)} 分钟", style = MaterialTheme.typography.bodySmall) }
            if (row.task.recurrence != "NONE" && row.task.completed) Text("整个重复系列已完成。取消勾选可恢复系列。", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSchedule(row.task, row.due) }) { Icon(Icons.Default.Schedule, null); Text("安排时间") }
                if (blocks.isNotEmpty()) Text("${blocks.size} 段执行时间", style = MaterialTheme.typography.bodySmall)
            }
            blocks.forEach { block ->
                TextButton(onClick = { onEditBlock(block) }) { Text("${Instant.ofEpochMilli(block.startEpochMillis).atZone(zone).format(dateTime)}–${Instant.ofEpochMilli(block.endEpochMillis).atZone(zone).format(clock)}") }
            }
            if (row.task.recurrence == "NONE") {
                TextButton(onClick = { stepsExpanded = !stepsExpanded }) { Text("步骤 ${steps.count { it.completed }}/${steps.size} ${if (stepsExpanded) "收起" else "展开"}") }
                if (stepsExpanded) {
                    steps.forEach { step ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(step.completed, onCheckedChange = { viewModel.toggleStep(step) })
                            Text(step.title, Modifier.weight(1f))
                            IconButton(onClick = { viewModel.deleteStep(step) }) { Icon(Icons.Default.Delete, "删除步骤") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(stepTitle, { stepTitle = it }, Modifier.weight(1f), label = { Text("添加步骤") }, singleLine = true)
                        IconButton(onClick = { if (stepTitle.isNotBlank()) { viewModel.addStep(row.task.id, stepTitle); stepTitle = "" } }) { Icon(Icons.Default.Add, "添加步骤") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TodayTaskPanel(data: AppData, today: LocalDate, now: Long, onTask: (Task, Long?) -> Unit,
    onSchedule: (Task, Long?) -> Unit, onToggle: (Task, Long?) -> Unit) {
    val rows = remember(data, today, now) { taskRows(data, today, now) }
    val selected = rows.filter { it.group == "OVERDUE" || it.group == "TODAY" }
        .sortedBy { if (it.group == "TODAY") 0 else 1 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("今日重点与待处理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (selected.isEmpty()) Text("目前没有待处理任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
        selected.take(6).forEach { row ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(row.completed, onCheckedChange = { onToggle(row.task, row.due) })
                    Column(Modifier.weight(1f).clickable { onTask(row.task, row.due) }) {
                        Text(row.task.title, fontWeight = FontWeight.SemiBold)
                        if (row.group == "OVERDUE") Text("已逾期 · 点击可调整", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onSchedule(row.task, row.due) }) { Text("安排") }
                }
            }
        }
        if (selected.size > 6) Text("另有 ${selected.size - 6} 项待处理，请到任务页查看。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun WeekDayView(date: LocalDate, state: MainUiState, modifier: Modifier, onItem: (ScheduleItem) -> Unit) {
    val items = state.weekItems.filter {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        it.startMillis < end && (it.endMillis ?: it.startMillis + 1) > start
    }
    val slots = state.data.semester?.let { PlanningEngine.freeSlots(date, items, it) }.orEmpty()
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(date.format(DateTimeFormatter.ofPattern("M月d日 EEEE")), style = MaterialTheme.typography.titleLarge) }
        item { Text("可安排 ${(slots.sumOf { it.durationMinutes }) / 60}小时${(slots.sumOf { it.durationMinutes }) % 60}分钟", color = MaterialTheme.colorScheme.primary) }
        if (slots.isNotEmpty()) item { Text("空档：" + slots.joinToString("、") { "${minuteText(it.startMinute)}–${minuteText(it.endMinute)}" }, style = MaterialTheme.typography.bodySmall) }
        items(items, key = { it.stableKey }) { item ->
            Card(Modifier.fillMaxWidth().clickable { onItem(item) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Text(if (item.endMillis == null) "截止 ${Instant.ofEpochMilli(item.startMillis).atZone(zone).format(clock)}" else
                        "${Instant.ofEpochMilli(item.startMillis).atZone(zone).format(clock)}–${Instant.ofEpochMilli(item.endMillis).atZone(zone).format(clock)}")
                    if (item.conflict) Text("时间冲突", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
internal fun TaskBlockEditor(task: Task, value: TaskBlock?, occurrenceDue: Long?, data: AppData,
    onDismiss: () -> Unit, onSave: (TaskBlock) -> Unit, onDelete: (() -> Unit)?) {
    val initial = value?.startEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
        ?: task.plannedEpochDay?.let { LocalDate.ofEpochDay(it) }?.takeIf { !it.isBefore(LocalDate.now()) }?.atTime(19, 0)
        ?: LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
    var start by remember(task.id, value?.id) { mutableStateOf(initial.format(dateTime)) }
    var end by remember(task.id, value?.id) { mutableStateOf((value?.endEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
        ?: initial.plusMinutes((task.estimateMinutes ?: 60).coerceAtMost(120).toLong())).format(dateTime)) }
    var error by remember { mutableStateOf<String?>(null) }
    val date = runCatching { LocalDateTime.parse(start, dateTime).toLocalDate() }.getOrNull()
    val allItems = date?.let { ScheduleEngine.buildItems(it, it, data.semester, data.courses, data.events, data.tasks,
        blocks = data.blocks.filter { block -> block.id != value?.id }) }.orEmpty()
    val slots = if (date != null && data.semester != null) PlanningEngine.freeSlots(date, allItems, data.semester) else emptyList()
    val already = PlanningEngine.minutesScheduledForTask(task.id, occurrenceDue, data.blocks) -
        (if (value != null) ((value.endEpochMillis - value.startEpochMillis) / 60_000).toInt() else 0)
    val remaining = (task.estimateMinutes ?: 0).minus(already).coerceAtLeast(0)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (value == null) "安排执行时间" else "调整执行时间") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(task.title, fontWeight = FontWeight.SemiBold)
                task.estimateMinutes?.let { Text("预计 $it 分钟，已安排 $already 分钟，还需 ${(it - already).coerceAtLeast(0)} 分钟") }
                DateTimeField(start, { start = it }, "开始")
                DateTimeField(end, { end = it }, "结束")
                Text("当天可用空档（已计入休息与缓冲）", style = MaterialTheme.typography.bodySmall)
                if (slots.isEmpty()) Text("没有可用空档")
                if (remaining > slots.sumOf { it.durationMinutes }) Text("当天空档还差 ${remaining - slots.sumOf { it.durationMinutes }} 分钟，可拆成多天安排或改期。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                slots.filter { it.durationMinutes >= 15 }.forEach { slot ->
                    TextButton(onClick = {
                        val day = date ?: return@TextButton
                        val minutes = (task.estimateMinutes?.minus(already)?.coerceAtLeast(15) ?: 60).coerceAtMost(slot.durationMinutes)
                        start = day.atStartOfDay().plusMinutes(slot.startMinute.toLong()).format(dateTime)
                        end = day.atStartOfDay().plusMinutes((slot.startMinute + minutes).toLong()).format(dateTime)
                    }) { Text("${minuteText(slot.startMinute)}–${minuteText(slot.endMinute)} · ${slot.durationMinutes}分钟") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = {
            runCatching {
                val startValue = LocalDateTime.parse(start, dateTime)
                val endValue = LocalDateTime.parse(end, dateTime)
                require(startValue.toLocalDate() == endValue.toLocalDate() && endValue.isAfter(startValue)) { "执行时间需在同一天，且结束晚于开始" }
                val first = startValue.hour * 60 + startValue.minute
                val last = endValue.hour * 60 + endValue.minute
                require(slots.any { first >= it.startMinute && last <= it.endMinute }) { "所选时间与已有安排或休息时间冲突" }
                TaskBlock(value?.id ?: 0, task.id, startValue.atZone(zone).toInstant().toEpochMilli(), endValue.atZone(zone).toInstant().toEpochMilli(), occurrenceDue)
            }.onSuccess(onSave).onFailure { error = it.message ?: "请检查时间" }
        }) { Text("保存") } },
        dismissButton = { Row { if (onDelete != null) TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = onDismiss) { Text("取消") } } })
}

private fun minuteText(value: Int) = "%02d:%02d".format(value / 60, value % 60)

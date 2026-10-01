package com.swish.campustime.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.swish.campustime.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate

data class MainUiState(
    val loading: Boolean = true,
    val data: AppData = AppData(null, emptyList(), emptyList(), emptyList()),
    val weekStart: LocalDate = ScheduleEngine.startOfWeek(LocalDate.now()),
    val todayItems: List<ScheduleItem> = emptyList(),
    val weekItems: List<ScheduleItem> = emptyList(),
    val currentDate: LocalDate = LocalDate.now(),
    val canUndo: Boolean = false,
    val message: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScheduleRepository(application, AppDatabase.get(application))
    private val weekStart = MutableStateFlow(ScheduleEngine.startOfWeek(LocalDate.now()))
    private val message = MutableStateFlow<String?>(null)
    private val currentDate = MutableStateFlow(LocalDate.now())
    private var pendingUndo: (suspend () -> Unit)? = null

    val uiState = combine(repository.data, weekStart, message, currentDate) { data, week, msg, today ->
        MainUiState(
            loading = false,
            data = data,
            weekStart = week,
            todayItems = ScheduleEngine.buildItems(today, today, data.semester, data.courses, data.events, data.tasks, blocks = data.blocks),
            weekItems = ScheduleEngine.buildItems(week, week.plusDays(6), data.semester, data.courses, data.events, data.tasks, blocks = data.blocks),
            currentDate = today,
            canUndo = pendingUndo != null,
            message = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            while (true) { currentDate.value = LocalDate.now(); delay(30_000) }
        }
        viewModelScope.launch {
            repository.ensureDefaults()
            runCatching { repository.reschedule() }
        }
    }

    fun previousWeek() { weekStart.value = weekStart.value.minusWeeks(1) }
    fun nextWeek() { weekStart.value = weekStart.value.plusWeeks(1) }
    fun currentWeek() { weekStart.value = ScheduleEngine.startOfWeek(LocalDate.now()) }
    fun goToWeek(date: LocalDate) { weekStart.value = ScheduleEngine.startOfWeek(date) }
    fun consumeMessage() { message.value = null }

    fun saveCourse(value: Course) = launchAction("课程已保存") { repository.saveCourse(value) }
    fun saveEvent(value: ScheduleEvent) = launchAction("计划已保存") { repository.saveEvent(value) }
    fun saveTask(value: Task) = launchAction("任务已保存") { repository.saveTask(value) }
    fun saveEventOccurrence(series: ScheduleEvent, occurrenceStart: Long, value: ScheduleEvent) = launchAction("本次计划已保存") { repository.saveEventOccurrence(series, occurrenceStart, value) }
    fun saveTaskOccurrence(series: Task, occurrenceDue: Long, value: Task) = launchAction("本次任务已保存") { repository.saveTaskOccurrence(series, occurrenceDue, value) }
    fun saveSemester(value: SemesterConfig) = launchAction("设置已保存") { repository.saveSemester(value) }
    fun deleteCourse(value: Course) = deleteWithUndo("课程已删除", { repository.deleteCourse(value) }, { repository.restoreCourse(value) })
    fun deleteEvent(value: ScheduleEvent) = deleteWithUndo("计划已删除", { repository.deleteEvent(value) }, { repository.restoreEvent(value) })
    fun deleteTask(value: Task) = viewModelScope.launch {
        val before = repository.snapshot()
        val steps = before.steps.filter { it.taskId == value.id }
        val blocks = before.blocks.filter { it.taskId == value.id }
        runCatching { repository.deleteTask(value) }.onSuccess {
            pendingUndo = { repository.restoreTask(value, steps, blocks) }; message.value = "任务已删除"
        }.onFailure { message.value = it.message ?: "删除失败" }
    }
    fun deleteEventOccurrence(series: ScheduleEvent, occurrenceStart: Long) = deleteWithUndo("本次计划已删除",
        { repository.deleteEventOccurrence(series, occurrenceStart) }, { repository.saveEvent(series) })
    fun deleteTaskOccurrence(series: Task, occurrenceDue: Long) = viewModelScope.launch {
        val blocks = repository.snapshot().blocks.filter { it.taskId == series.id && it.occurrenceDueEpochMillis == occurrenceDue }
        runCatching { repository.deleteTaskOccurrence(series, occurrenceDue) }.onSuccess {
            pendingUndo = { repository.saveTask(series); blocks.forEach { repository.restoreBlock(it) } }
            message.value = "本次任务已删除"
        }.onFailure { message.value = it.message ?: "删除失败" }
    }
    fun toggleTask(value: Task, occurrenceDue: Long? = null) = launchAction("完成状态已更新") { repository.toggleTask(value, occurrenceDue) }
    fun addStep(taskId: Long, title: String) = launchAction("步骤已添加") { repository.addStep(taskId, title) }
    fun toggleStep(value: TaskStep) = launchAction("步骤已更新") { repository.toggleStep(value) }
    fun deleteStep(value: TaskStep) = deleteWithUndo("步骤已删除", { repository.deleteStep(value) }, { repository.restoreStep(value) })
    fun saveBlock(value: TaskBlock) = launchAction("执行时间已安排") { repository.saveBlock(value) }
    fun deleteBlock(value: TaskBlock) = deleteWithUndo("执行时间已删除", { repository.deleteBlock(value) }, { repository.restoreBlock(value) })

    fun undoDelete() = viewModelScope.launch {
        val undo = pendingUndo ?: return@launch
        pendingUndo = null
        runCatching { undo() }.onSuccess { message.value = "已撤销删除" }
            .onFailure { message.value = it.message ?: "撤销失败" }
    }
    fun discardUndo() { pendingUndo = null }

    private fun deleteWithUndo(success: String, action: suspend () -> Unit, undo: suspend () -> Unit) = viewModelScope.launch {
        runCatching { action() }.onSuccess { pendingUndo = undo; message.value = success }
            .onFailure { message.value = it.message ?: "删除失败" }
    }

    fun exportBackup(onReady: (String) -> Unit) = viewModelScope.launch {
        runCatching { BackupService.encode(repository.snapshot()) }
            .onSuccess(onReady)
            .onFailure { message.value = it.message ?: "导出失败" }
    }

    fun importBackup(text: String) = viewModelScope.launch {
        runCatching {
            val imported = BackupService.decode(text)
            val before = BackupService.encode(repository.snapshot())
            getApplication<Application>().getSharedPreferences("backup", 0).edit { putString("before_last_restore", before) }
            repository.replaceAll(imported)
        }.onSuccess { message.value = "备份已恢复" }
            .onFailure { message.value = it.message ?: "恢复失败" }
    }

    fun hasRestoreSnapshot(): Boolean = getApplication<Application>().getSharedPreferences("backup", 0).contains("before_last_restore")

    fun restoreBeforeLastImport() = viewModelScope.launch {
        val prefs = getApplication<Application>().getSharedPreferences("backup", 0)
        val backup = prefs.getString("before_last_restore", null)
        if (backup == null) { message.value = "没有可恢复的快照"; return@launch }
        runCatching {
            val previous = BackupService.decode(backup)
            val current = BackupService.encode(repository.snapshot())
            repository.replaceAll(previous)
            prefs.edit { putString("before_last_restore", current) }
        }.onSuccess { message.value = "已恢复导入前的数据" }
            .onFailure { message.value = it.message ?: "恢复失败" }
    }

    private fun launchAction(success: String, block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onSuccess { message.value = success }.onFailure { message.value = it.message ?: "操作失败" }
    }
}

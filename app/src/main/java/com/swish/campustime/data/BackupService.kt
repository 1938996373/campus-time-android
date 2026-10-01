package com.swish.campustime.data

import org.json.JSONArray
import org.json.JSONObject

object BackupService {
    const val VERSION = 2

    fun encode(data: AppData): String = JSONObject().apply {
        put("format", "campus-time-backup")
        put("version", VERSION)
        put("semester", data.semester?.let(::semesterJson))
        put("courses", JSONArray(data.courses.map(::courseJson)))
        put("events", JSONArray(data.events.map(::eventJson)))
        put("tasks", JSONArray(data.tasks.map(::taskJson)))
        put("steps", JSONArray(data.steps.map(::stepJson)))
        put("blocks", JSONArray(data.blocks.map(::blockJson)))
    }.toString(2)

    fun decode(text: String): AppData {
        val root = JSONObject(text)
        require(root.optString("format") == "campus-time-backup") { "不是校园时间备份文件" }
        require(root.optInt("version") in 1..VERSION) { "不支持的备份版本" }
        val semester = root.getJSONObject("semester").let {
            SemesterConfig(1, it.getString("name"), it.getLong("startEpochDay"), it.getInt("totalWeeks"), it.getString("periodTimes"),
                it.optInt("dayStartMinute", 480), it.optInt("dayEndMinute", 1320), it.optInt("breakStartMinute", 720), it.optInt("breakEndMinute", 780), it.optInt("bufferMinutes", 10))
        }
        val courses = root.getJSONArray("courses").objects().map {
            Course(title = it.getString("title"), teacher = it.optString("teacher"), room = it.optString("room"), dayOfWeek = it.getInt("dayOfWeek"), startMinute = it.getInt("startMinute"), endMinute = it.getInt("endMinute"), startWeek = it.getInt("startWeek"), endWeek = it.getInt("endWeek"), weekParity = it.getString("weekParity"), color = it.getLong("color"), reminderMinutes = it.optNullableInt("reminderMinutes"), note = it.optString("note"), startPeriod = it.optNullableInt("startPeriod"), endPeriod = it.optNullableInt("endPeriod"))
        }
        val events = root.getJSONArray("events").objects().map {
            ScheduleEvent(title = it.getString("title"), note = it.optString("note"), startEpochMillis = it.getLong("startEpochMillis"), endEpochMillis = it.getLong("endEpochMillis"), recurrence = it.optString("recurrence", "NONE"), weekdaysCsv = it.optString("weekdaysCsv"), recurrenceEndEpochDay = it.optNullableLong("recurrenceEndEpochDay"), color = it.getLong("color"), reminderMinutes = it.optNullableInt("reminderMinutes"), excludedEpochDaysCsv = it.optString("excludedEpochDaysCsv"))
        }
        val tasks = root.getJSONArray("tasks").objects().map {
            Task(id = it.optLong("id"), title = it.getString("title"), note = it.optString("note"), dueEpochMillis = it.getLong("dueEpochMillis"), completed = it.optBoolean("completed"), recurrence = it.optString("recurrence", "NONE"), weekdaysCsv = it.optString("weekdaysCsv"), recurrenceEndEpochDay = it.optNullableLong("recurrenceEndEpochDay"), reminderMinutes = it.optNullableInt("reminderMinutes"), excludedEpochDaysCsv = it.optString("excludedEpochDaysCsv"), hasDueDate = it.optBoolean("hasDueDate", true), plannedEpochDay = it.optNullableLong("plannedEpochDay"), estimateMinutes = it.optNullableInt("estimateMinutes"), priority = it.optInt("priority"), category = it.optString("category"), completedEpochDaysCsv = it.optString("completedEpochDaysCsv"))
        }
        val steps = root.optJSONArray("steps")?.objects()?.map {
            TaskStep(taskId = it.getLong("taskId"), title = it.getString("title"), completed = it.optBoolean("completed"))
        }.orEmpty()
        val blocks = root.optJSONArray("blocks")?.objects()?.map {
            TaskBlock(taskId = it.getLong("taskId"), startEpochMillis = it.getLong("startEpochMillis"), endEpochMillis = it.getLong("endEpochMillis"), occurrenceDueEpochMillis = it.optNullableLong("occurrenceDueEpochMillis"))
        }.orEmpty()
        return AppData(semester, courses, events, tasks, steps, blocks)
    }

    private fun semesterJson(v: SemesterConfig) = JSONObject().apply { put("name", v.name); put("startEpochDay", v.startEpochDay); put("totalWeeks", v.totalWeeks); put("periodTimes", v.periodTimes); put("dayStartMinute", v.dayStartMinute); put("dayEndMinute", v.dayEndMinute); put("breakStartMinute", v.breakStartMinute); put("breakEndMinute", v.breakEndMinute); put("bufferMinutes", v.bufferMinutes) }
    private fun courseJson(v: Course) = JSONObject().apply { put("title", v.title); put("teacher", v.teacher); put("room", v.room); put("note", v.note); put("dayOfWeek", v.dayOfWeek); put("startMinute", v.startMinute); put("endMinute", v.endMinute); put("startWeek", v.startWeek); put("endWeek", v.endWeek); put("weekParity", v.weekParity); put("color", v.color); putNullable("reminderMinutes", v.reminderMinutes); putNullable("startPeriod", v.startPeriod); putNullable("endPeriod", v.endPeriod) }
    private fun eventJson(v: ScheduleEvent) = JSONObject().apply { put("title", v.title); put("note", v.note); put("startEpochMillis", v.startEpochMillis); put("endEpochMillis", v.endEpochMillis); put("recurrence", v.recurrence); put("weekdaysCsv", v.weekdaysCsv); putNullable("recurrenceEndEpochDay", v.recurrenceEndEpochDay); put("color", v.color); putNullable("reminderMinutes", v.reminderMinutes); put("excludedEpochDaysCsv", v.excludedEpochDaysCsv) }
    private fun taskJson(v: Task) = JSONObject().apply { put("id", v.id); put("title", v.title); put("note", v.note); put("dueEpochMillis", v.dueEpochMillis); put("completed", v.completed); put("recurrence", v.recurrence); put("weekdaysCsv", v.weekdaysCsv); putNullable("recurrenceEndEpochDay", v.recurrenceEndEpochDay); putNullable("reminderMinutes", v.reminderMinutes); put("excludedEpochDaysCsv", v.excludedEpochDaysCsv); put("hasDueDate", v.hasDueDate); putNullable("plannedEpochDay", v.plannedEpochDay); putNullable("estimateMinutes", v.estimateMinutes); put("priority", v.priority); put("category", v.category); put("completedEpochDaysCsv", v.completedEpochDaysCsv) }
    private fun stepJson(v: TaskStep) = JSONObject().apply { put("taskId", v.taskId); put("title", v.title); put("completed", v.completed) }
    private fun blockJson(v: TaskBlock) = JSONObject().apply { put("taskId", v.taskId); put("startEpochMillis", v.startEpochMillis); put("endEpochMillis", v.endEpochMillis); putNullable("occurrenceDueEpochMillis", v.occurrenceDueEpochMillis) }
    private fun JSONObject.putNullable(key: String, value: Any?) { if (value == null) put(key, JSONObject.NULL) else put(key, value) }
    private fun JSONObject.optNullableInt(key: String) = if (isNull(key)) null else getInt(key)
    private fun JSONObject.optNullableLong(key: String) = if (isNull(key)) null else getLong(key)
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
}

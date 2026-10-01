package com.swish.campustime.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import com.swish.campustime.data.AppData
import com.swish.campustime.data.ScheduleEngine
import com.swish.campustime.data.ScheduleKind
import java.time.LocalDate

class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("scheduled_reminders", Context.MODE_PRIVATE)

    fun scheduleAll(data: AppData) {
        cancelKnown()
        val today = LocalDate.now()
        val items = ScheduleEngine.buildItems(today, today.plusDays(60), data.semester, data.courses, data.events, data.tasks, blocks = data.blocks)
        val activeCodes = mutableSetOf<String>()
        items.forEach { item ->
            val minutes = when (item.kind) {
                ScheduleKind.COURSE -> data.courses.firstOrNull { it.id == item.sourceId }?.reminderMinutes
                ScheduleKind.EVENT -> data.events.firstOrNull { it.id == item.sourceId }?.reminderMinutes
                ScheduleKind.TASK -> data.tasks.firstOrNull { it.id == item.sourceId }?.reminderMinutes
                ScheduleKind.BLOCK -> null
            } ?: return@forEach
            if (item.completed) return@forEach
            val triggerAt = item.startMillis - minutes * 60_000L
            if (triggerAt <= System.currentTimeMillis()) return@forEach
            val code = item.stableKey.hashCode()
            val intent = reminderIntent(code, item.title, item.subtitle)
            if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            } else {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            }
            activeCodes += code.toString()
        }
        prefs.edit { putStringSet("codes", activeCodes) }
    }

    private fun cancelKnown() {
        prefs.getStringSet("codes", emptySet()).orEmpty().forEach { code ->
            alarms.cancel(reminderIntent(code.toInt(), "", ""))
        }
        prefs.edit { remove("codes") }
    }

    private fun reminderIntent(code: Int, title: String, text: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("text", text)
        }
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_ID = "schedule_reminders"
        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "日程提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "课程、计划和任务的本地提醒"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

package com.swish.campustime.data

import org.junit.Assert.*
import org.junit.Test

class BackupServiceTest {
    @Test fun oldBackupCanBeImportedWithNewDefaults() {
        val old = """{"format":"campus-time-backup","version":1,"semester":{"name":"旧学期","startEpochDay":20000,"totalWeeks":20,"periodTimes":"08:00-08:40"},"courses":[],"events":[],"tasks":[{"title":"旧任务","dueEpochMillis":1800000000000,"recurrence":"NONE"}]}"""
        val data = BackupService.decode(old)
        assertEquals(1, data.tasks.size)
        assertTrue(data.tasks.single().hasDueDate)
        assertNull(data.tasks.single().estimateMinutes)
        assertEquals(480, data.semester!!.dayStartMinute)
    }

    @Test fun newBackupKeepsTaskStepsAndTimeBlocks() {
        val data = AppData(SemesterConfig(name = "测试", startEpochDay = 20000, totalWeeks = 20, periodTimes = "08:00-08:40"),
            emptyList(), emptyList(), listOf(Task(id = 7, title = "报告", dueEpochMillis = 1800000000000, estimateMinutes = 90)),
            listOf(TaskStep(taskId = 7, title = "整理数据")),
            listOf(TaskBlock(taskId = 7, startEpochMillis = 1799990000000, endEpochMillis = 1799993600000, occurrenceDueEpochMillis = 1800000000000)))
        val restored = BackupService.decode(BackupService.encode(data))
        assertEquals(data.tasks.single().estimateMinutes, restored.tasks.single().estimateMinutes)
        assertEquals(7L, restored.steps.single().taskId)
        assertEquals(1800000000000L, restored.blocks.single().occurrenceDueEpochMillis)
    }
}

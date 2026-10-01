package com.swish.campustime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import com.swish.campustime.data.ScheduleItem
import com.swish.campustime.data.ScheduleKind
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WeekUiTest {
    @get:Rule val compose = createComposeRule()

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("week-qa")!!
        dir.mkdirs()
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun manualDateValidationAndRoundTrip() {
        var result = "2028-02-28 10:20"
        compose.setContent { MaterialTheme {
            var value by remember { mutableStateOf(result) }
            DateTimeField(value, { value = it; result = it }, "截止时间")
        } }
        compose.onNodeWithText("点击选择").performClick()
        compose.onNodeWithContentDescription("日期滚轮").assertExists()
        compose.onNodeWithContentDescription("小时滚轮").assertExists()
        compose.onNodeWithContentDescription("分钟滚轮").assertExists()
        screenshot("picker.png")
        compose.onNodeWithText("手动输入").performClick()
        compose.onNodeWithText("日").performTextClearance()
        compose.onNodeWithText("日").performTextInput("30")
        compose.onNodeWithText("确认").performClick()
        compose.onNodeWithText("请填写有效日期；小时 0–23，分钟 0–59").assertExists()
        assertEquals("2028-02-28 10:20", result)
        compose.onNodeWithText("日").performTextClearance()
        compose.onNodeWithText("日").performTextInput("29")
        compose.onNodeWithText("分钟").performTextClearance()
        compose.onNodeWithText("分钟").performTextInput("59")
        compose.onNodeWithText("滚轮选择").performClick()
        compose.onNodeWithText("2028-02-29 10:59").assertExists()
        compose.onNodeWithText("确认").performClick()
        compose.waitForIdle()
        assertEquals("2028-02-29 10:59", result)
    }

    @Test fun wheelGestureChangesTimeAndCancelPreservesValue() {
        var result = "10:20"
        compose.setContent { MaterialTheme {
            var value by remember { mutableStateOf(result) }
            DateTimeField(value, { value = it; result = it }, "开始时间", false)
        } }
        compose.onNodeWithText("点击选择").performClick()
        compose.onNodeWithContentDescription("日期滚轮").assertDoesNotExist()
        compose.onNodeWithContentDescription("分钟滚轮").performTouchInput { swipeUp(durationMillis = 600) }
        compose.waitForIdle()
        compose.onNodeWithText("确认").performClick()
        compose.waitForIdle()
        assertNotEquals("10:20", result)
        val saved = result
        compose.onNodeWithText("点击选择").performClick()
        compose.onNodeWithContentDescription("小时滚轮").performTouchInput { swipeUp(durationMillis = 600) }
        compose.onNodeWithText("取消").performClick()
        assertEquals(saved, result)
    }

    @Test fun overlappingCardsShowAllContentInOneColumn() {
        val monday = LocalDate.of(2026, 9, 21)
        val base = monday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun item(id: Int, title: String, start: Int, end: Int, details: String) =
            ScheduleItem("$id", id.toLong(), ScheduleKind.EVENT, title, details,
                base + start * 60000L, base + end * 60000L, 0xFF6750A4)
        val data = listOf(
            item(1, "材料力学", 480, 565, "老师：李老师\n教室：W1101\n备注：带课本与作业"),
            item(2, "小组讨论", 510, 540, "备注：讨论实验报告，整理数据后交给组长。"),
            item(3, "短时长长备注", 565, 566, "备注：" + "完整显示的备注内容。".repeat(12))
        )
        compose.setContent { MaterialTheme {
            WeeklyGrid(monday, data, Modifier.fillMaxSize().verticalScroll(rememberScrollState()), { _, _ -> }, {})
        } }
        compose.onNodeWithText("重叠 · 2项").assertExists()
        compose.onNodeWithText("材料力学").assertExists()
        compose.onNodeWithText("小组讨论").assertExists()
        val first = compose.onNodeWithText("材料力学").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("小组讨论").fetchSemanticsNode().boundsInRoot
        assertEquals(first.left, second.left, .5f)
        assertTrue(second.top > first.bottom)
        screenshot("week-combined.png")
        val longText = "备注：" + "完整显示的备注内容。".repeat(12)
        compose.onNodeWithText(longText).performScrollTo().assertExists()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(longText).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertFalse(layouts.single().hasVisualOverflow)
    }
}

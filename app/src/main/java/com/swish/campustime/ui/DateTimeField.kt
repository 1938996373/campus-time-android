package com.swish.campustime.ui

import android.widget.NumberPicker
import android.view.ContextThemeWrapper
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val fieldDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
private val fieldTime = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimeField(value: String, onChange: (String) -> Unit, label: String,
    withDate: Boolean = true, modifier: Modifier = Modifier.fillMaxWidth()) {
    var open by remember { mutableStateOf(false) }
    Surface(modifier.clickable { open = true }, shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge)
            Text("点击选择", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (open) {
        val initial = remember {
            runCatching {
                if (withDate) LocalDateTime.parse(value, fieldDateTime)
                else LocalDate.now().atTime(LocalTime.parse(value, fieldTime))
            }.getOrElse { LocalDateTime.now().withSecond(0).withNano(0) }
        }
        var selected by remember { mutableStateOf(initial) }
        var manual by remember { mutableStateOf(false) }
        var year by remember { mutableStateOf(initial.year.toString()) }
        var month by remember { mutableStateOf(initial.monthValue.toString()) }
        var day by remember { mutableStateOf(initial.dayOfMonth.toString()) }
        var hour by remember { mutableStateOf(initial.hour.toString()) }
        var minute by remember { mutableStateOf(initial.minute.toString()) }
        var error by remember { mutableStateOf<String?>(null) }
        fun manualValue(): LocalDateTime {
            if (withDate) require(year.toInt() in 1..9999)
            return LocalDateTime.of(
                if (withDate) LocalDate.of(year.toInt(), month.toInt(), day.toInt()) else selected.toLocalDate(),
                LocalTime.of(hour.toInt(), minute.toInt()))
        }
        fun toggle() {
            if (manual) {
                runCatching { manualValue() }.onSuccess { selected = it; manual = false; error = null }
                    .onFailure { error = "请填写有效日期；小时 0–23，分钟 0–59" }
            } else {
                year = selected.year.toString(); month = selected.monthValue.toString(); day = selected.dayOfMonth.toString()
                hour = selected.hour.toString(); minute = selected.minute.toString()
                manual = true; error = null
            }
        }
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = ::toggle) { Text(if (manual) "滚轮选择" else "手动输入") }
                }
                if (manual) {
                    if (withDate) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberInput(year, { year = it }, "年", Modifier.weight(1.4f))
                        NumberInput(month, { month = it }, "月", Modifier.weight(1f))
                        NumberInput(day, { day = it }, "日", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberInput(hour, { hour = it }, "小时", Modifier.weight(1f))
                        NumberInput(minute, { minute = it }, "分钟", Modifier.weight(1f))
                    }
                } else {
                    if (withDate) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { selected = selected.minusYears(1) }, enabled = selected.year > 1) { Text("上一年") }
                            Text("${selected.year}年")
                            TextButton(onClick = { selected = selected.plusYears(1) }, enabled = selected.year < 9999) { Text("下一年") }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (withDate) {
                            val first = LocalDate.of(selected.year, 1, 1)
                            val dates = remember(selected.year) {
                                List(first.lengthOfYear()) { first.plusDays(it.toLong()).format(DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)) }
                            }
                            PickerColumn("日期", dates, selected.dayOfYear - 1, Modifier.weight(2.2f)) {
                                selected = first.plusDays(it.toLong()).atTime(selected.toLocalTime())
                            }
                        }
                        PickerColumn("小时", (0..23).map { "%02d".format(it) }, selected.hour, Modifier.weight(1f)) { selected = selected.withHour(it) }
                        PickerColumn("分钟", (0..59).map { "%02d".format(it) }, selected.minute, Modifier.weight(1f)) { selected = selected.withMinute(it) }
                    }
                }
                val preview = if (manual) runCatching { manualValue() }.getOrNull() else selected
                if (preview != null) Text(preview.format(if (withDate) fieldDateTime else fieldTime), color = MaterialTheme.colorScheme.primary)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { open = false }) { Text("取消") }
                    Button(onClick = {
                        runCatching { if (manual) manualValue() else selected }.onSuccess {
                            onChange(it.format(if (withDate) fieldDateTime else fieldTime))
                            open = false
                        }.onFailure { error = "请填写有效日期；小时 0–23，分钟 0–59" }
                    }) { Text("确认") }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun NumberInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(value, { if (it.all(Char::isDigit)) onChange(it) }, modifier,
        label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
private fun PickerColumn(label: String, values: List<String>, selected: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    val latestOnSelect by rememberUpdatedState(onSelect)
    val light = MaterialTheme.colorScheme.surface.luminance() > .5f
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        AndroidView(
            factory = { context ->
                NumberPicker(ContextThemeWrapper(context, if (light) android.R.style.Theme_Material_Light else android.R.style.Theme_Material)).apply {
                    descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                    wrapSelectorWheel = false
                    setOnValueChangedListener { _, _, value -> latestOnSelect(value) }
                }
            },
            update = { picker ->
                if (Build.VERSION.SDK_INT >= 29) picker.textColor = textColor
                if (picker.tag != values) {
                    picker.displayedValues = null
                    picker.minValue = 0
                    picker.maxValue = values.lastIndex
                    picker.displayedValues = values.toTypedArray()
                    picker.tag = values
                }
                if (picker.value != selected) picker.value = selected
                picker.contentDescription = label
            },
            modifier = Modifier.fillMaxWidth().height(168.dp).semantics { contentDescription = label + "滚轮" }
        )
    }
}

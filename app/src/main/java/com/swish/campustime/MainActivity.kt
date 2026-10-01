package com.swish.campustime

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swish.campustime.data.BackupService
import com.swish.campustime.ui.CampusTimeApp
import com.swish.campustime.ui.CampusTimeTheme
import com.swish.campustime.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CampusTimeTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var pendingBackup by remember { mutableStateOf<String?>(null) }
                var pendingImport by remember { mutableStateOf<String?>(null) }
                var importError by remember { mutableStateOf<String?>(null) }
                val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    uri?.let { contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(pendingBackup.orEmpty()) } }
                    pendingBackup = null
                }
                val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let {
                        runCatching { contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() } ?: error("无法读取文件") }
                            .onSuccess { text ->
                                runCatching { BackupService.decode(text) }
                                    .onSuccess { pendingImport = text }
                                    .onFailure { importError = it.message ?: "备份文件无效" }
                            }.onFailure { importError = it.message ?: "读取失败" }
                    }
                }
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

                CampusTimeApp(
                    state = state,
                    viewModel = viewModel,
                    onExport = {
                        viewModel.exportBackup { json ->
                            pendingBackup = json
                            createDocument.launch("campus-time-backup.json")
                        }
                    },
                    onImport = { openDocument.launch(arrayOf("application/json", "text/plain")) },
                    ensureNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
                pendingImport?.let { text ->
                    val imported = remember(text) { BackupService.decode(text) }
                    AlertDialog(onDismissRequest = { pendingImport = null }, title = { Text("确认恢复备份") },
                        text = { Text("备份包含 ${imported.courses.size} 门课程、${imported.events.size} 项计划、${imported.tasks.size} 项任务、${imported.steps.size} 个步骤和 ${imported.blocks.size} 段执行时间。\n当前数据：${state.data.courses.size} 门课程、${state.data.events.size} 项计划、${state.data.tasks.size} 项任务。\n恢复将替换当前数据，并保留恢复前快照。") },
                        confirmButton = { TextButton(onClick = { viewModel.importBackup(text); pendingImport = null }) { Text("确认恢复") } },
                        dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("取消") } })
                }
                importError?.let { message ->
                    AlertDialog(onDismissRequest = { importError = null }, title = { Text("无法导入") }, text = { Text(message) },
                        confirmButton = { TextButton(onClick = { importError = null }) { Text("知道了") } })
                }
            }
        }
    }
}

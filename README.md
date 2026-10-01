# 校园时间（Campus Time）

一款中文、离线优先的 Android 课程表与时间管理应用。打开应用后直接进入「今日」；「本周」把课程、个人计划和任务截止时间放在同一视图中。

## 已实现功能

- **今日**：时间轴、当前与下一项安排、冲突提示。
- **本周**：周一至周日完整显示，纵向浏览课程、个人计划和任务截止点。
- **课程**：按周次与单双周安排，支持按节次或时间录入，并记录教师、教室、备注。
- **任务与规划**：截止时间、优先级、分类、步骤，以及可拆分的执行时间块。
- **重复安排**：支持修改或删除单次发生项，也支持操作整个系列。
- **本地提醒**：默认关闭，由用户开启后才请求通知权限。
- **数据管理**：本地 JSON 备份与恢复预览、恢复前快照、撤销删除。

应用不申请联网权限；课程和任务保存在设备本地。请自行备份重要数据，卸载应用前尤其如此。当前项目主要按个人使用场景开发，尚未完成在所有 Android 设备上的兼容性验证。

## 下载安装

前往 [GitHub Releases](https://github.com/1938996373/campus-time-android/releases/latest) 下载最新版 APK，安装到 Android 8.0 或更新版本的设备。升级前建议先在应用内导出 JSON 备份。安装包与发布说明中的 SHA-256 校验值可用于核对下载文件。

## 从源码构建

需要 JDK 17 或 21、Android SDK 36。仓库自带 Gradle Wrapper，首次构建需要下载 Android 构建依赖。

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

macOS / Linux 可将命令中的 `gradlew.bat` 换成 `./gradlew`。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。如需安装 Release 版本，请自行配置签名；仓库不包含签名密钥与本机 `signing.properties`。

## 技术栈

Kotlin、Jetpack Compose、Room、Gradle，最低支持 Android 8.0（API 26）。提醒通过 Android 本地通知与系统闹钟实现。

## 项目状态

当前应用版本为 **1.1.0**。源码公开供查看与交流；本仓库暂未附加开源许可证。欢迎通过 GitHub Issues 反馈问题，反馈时请勿附上包含真实课程、个人计划或备份数据的文件。

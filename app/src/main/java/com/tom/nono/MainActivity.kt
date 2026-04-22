package com.tom.nono

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tom.nono.data.DeviceSoundMode
import com.tom.nono.data.DelayedNotice
import com.tom.nono.data.DelayedNoticeStore
import com.tom.nono.data.HolidayCalendarConfig
import com.tom.nono.data.HolidayCalendarStore
import com.tom.nono.data.ManualDayOverride
import com.tom.nono.data.NotificationRule
import com.tom.nono.data.RuleDayMode
import com.tom.nono.data.RuleMode
import com.tom.nono.data.RuleStore
import com.tom.nono.data.ResendTriggerPriority
import com.tom.nono.data.RuntimeTuningSettingsStore
import com.tom.nono.data.lastSyncLabel
import com.tom.nono.service.AppNotificationGateService
import com.tom.nono.service.DelayedNotificationReceiver
import com.tom.nono.service.DelayedNotificationScheduler
import com.tom.nono.service.ListenerKeepAliveService
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startListenerKeepAliveService()
        setContent { NonoApp() }
    }

    override fun onStop() {
        super.onStop()
        requestRebindNotificationListener(this)
    }

    private fun startListenerKeepAliveService() {
        val intent = Intent(this, ListenerKeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

private enum class NonoTab(val title: String) {
    Notifications("通知"),
    Rules("规则"),
    Add("配置"),
    Settings("设置"),
}

private enum class AppLanguage(val prefValue: String) {
    System("system"),
    Chinese("zh"),
    English("en");

    companion object {
        fun fromPref(value: String?): AppLanguage = values().firstOrNull { it.prefValue == value } ?: System
    }
}

private class AppStrings(private val english: Boolean) {
    fun text(zh: String): String = if (english) englishText[zh] ?: zh else zh

    fun tabTitle(tab: NonoTab): String = text(tab.title)

    private companion object {
        val englishText = mapOf(
            "通知" to "Notifications",
            "规则" to "Rules",
            "配置" to "Add",
            "设置" to "Settings",
            "语言" to "Language",
            "跟随系统" to "System",
            "中文" to "Chinese",
            "英文" to "English",
            "通知访问权限" to "Notification access",
            "已开启，可按规则拦截通知。" to "Enabled. Notifications can be handled by rules.",
            "未开启，规则暂时不会生效。" to "Disabled. Rules are inactive for now.",
            "已开启" to "Enabled",
            "未开启" to "Disabled",
            "打开系统设置" to "Open system settings",
            "刷新状态" to "Refresh status",
            "重连通知监听服务" to "Reconnect notification listener",
            "重置监听组件绑定" to "Reset listener component binding",
            "申请通知发送权限" to "Request notification permission",
            "申请忽略电池优化" to "Ignore battery optimization",
            "打开精确闹钟设置" to "Open exact alarm settings",
            "打开勿扰访问" to "Open Do Not Disturb access",
            "后台驻留步骤" to "Background keep-alive steps",
            "1. 打开自启动管理" to "1. Open autostart management",
            "2. 打开后台运行/应用启动" to "2. Open background run/app launch",
            "3. 打开耗电保护设置" to "3. Open power protection settings",
            "4. 打开应用详情（检查）" to "4. Open app details",
            "工作日历" to "Workday calendar",
            "按工作日/调休判断" to "Use workday/holiday calendar",
            "在线日历地址" to "Online calendar URL",
            "同步到本地" to "Sync locally",
            "请假与调休" to "Leave and makeup days",
            "日期" to "Date",
            "备注" to "Note",
            "例如：年假 / 周日补班" to "Example: annual leave / Sunday makeup work",
            "请假/休息" to "Leave/rest",
            "调休加班" to "Makeup workday",
            "保存日期覆盖" to "Save date override",
            "暂时还没有手动日期覆盖。" to "No manual date overrides yet.",
            "配置管理" to "Configuration",
            "导出会生成一份 JSON 文件，导入时会覆盖当前本地规则。" to "Export creates a JSON file. Import replaces current local rules.",
            "导出配置" to "Export",
            "导入配置" to "Import",
            "当前还没有规则，去“新增配置”里添加第一条规则。" to "No rules yet. Add your first rule from Add.",
            "暂时没有延后中的通知。" to "No delayed notifications.",
            "暂时没有已收集的通知。" to "No collected notifications.",
            "立即通知" to "Notify now",
            "删除整组" to "Delete group",
            "删除" to "Delete",
            "收起搜索栏" to "Hide search",
            "展开搜索栏" to "Show search",
            "搜索已安装应用" to "Search installed apps",
            "输入应用名或包名的一部分" to "Enter part of an app name or package",
            "没有找到匹配应用，可以手动填写包名。" to "No matching apps. You can enter the package manually.",
            "收起全部应用" to "Hide all apps",
            "展开全部应用" to "Show all apps",
            "没有读取到应用列表。" to "No app list was loaded.",
            "规则启用" to "Rule enabled",
            "保存配置" to "Save rule",
            "应用名称" to "App name",
            "包名" to "Package name",
            "例如 com.tencent.mm" to "Example: com.tencent.mm",
            "例如：老板白名单" to "Example: boss whitelist",
            "提醒时间" to "Reminder time",
            "匹配到规则后，Nono 会在下一个符合条件的 HH:mm 时间发出本地提醒。" to "After a match, Nono sends a local reminder at the next eligible HH:mm time.",
            "全天" to "All day",
            "开始时间" to "Start time",
            "结束时间" to "End time",
            "生效日期" to "Active days",
            "全部" to "All",
            "整包匹配" to "Match whole package",
            "开启后会使用 * 匹配该应用的所有通知。" to "When enabled, * matches all notifications from this app.",
            "关键字" to "Keywords",
            "每行一个联系人名、群名或频道名" to "One contact, group, or channel per line",
            "当前将忽略关键字，直接匹配该应用全部通知。" to "Keywords are ignored; all notifications from this app match.",
            "留空表示匹配该应用全部通知；例如：老板、项目群、值班通知" to "Leave empty to match all notifications; e.g. boss, project group, duty alert",
            "通知模式" to "Notification mode",
            "屏蔽" to "Block",
            "允许" to "Allow",
            "延后" to "Delay",
            "收集" to "Collect",
            "规则日类型" to "Rule day type",
            "工作" to "Work",
            "休息" to "Rest",
            "系统响铃模式" to "System sound mode",
            "保持" to "Keep",
            "响铃" to "Ring",
            "震动" to "Vibrate",
            "静音" to "Silent",
            "选择" to "Select",
            "未命名应用" to "Unnamed app",
            "新规则" to "New rule",
            "请填写包名" to "Please enter package name",
            "删除规则" to "Delete rule",
            "预览" to "Preview",
            "当前时间状态" to "Current time status",
            "不生效" to "Inactive",
            "后台驻留参数（秒）" to "Background parameters (seconds)",
            "监听重连间隔" to "Listener reconnect interval",
            "活跃检查间隔" to "Active check interval",
            "空闲检查间隔" to "Idle check interval",
            "重发方法" to "Resend method",
            "可分别测试普通、复用意图、代理回放、气泡、全屏、原闹钟。" to "You can try normal, reused intent, proxy replay, bubble, full screen, and original alarm.",
            "仅普通重发" to "Normal only",
            "普通优先" to "Normal first",
            "保真优先" to "Fidelity first",
            "复用意图" to "Reuse intent",
            "代理回放" to "Proxy replay",
            "气泡优先" to "Bubble first",
            "全屏优先" to "Full screen first",
            "原闹钟" to "Original alarm",
            "闹钟优先" to "Alarm first",
            "保存后台参数" to "Save background parameters",
            "条" to "items",
            "通知发送权限" to "Notification permission",
            "精确闹钟" to "Exact alarm",
            "电池优化豁免" to "Battery optimization exemption",
            "可用" to "Available",
            "受限（延后提醒可能不准时）" to "Limited (delayed reminders may be inaccurate)",
            "未开启（离开 App 后可能被限制）" to "Disabled (may be restricted after leaving the app)",
            "监听诊断" to "Listener diagnostics",
            "监听底层状态" to "Listener technical status",
            "尚未收到监听回调（如果已触发通知，请重连监听服务后再观察）" to "No listener callback yet. If a notification has already arrived, reconnect the listener and observe again.",
            "通知发送权限已授予" to "Notification permission granted",
            "未授予通知发送权限" to "Notification permission was not granted",
            "导出失败" to "Export failed",
            "导入失败，文件格式不正确" to "Import failed. File format is invalid.",
            "配置已导出" to "Configuration exported",
            "配置已导入" to "Configuration imported",
            "已新增规则" to "Rule added",
            "无法打开对应应用" to "Unable to open the app",
            "已请求重连通知监听" to "Notification listener reconnect requested",
            "已重置组件绑定并请求重连" to "Listener component binding reset and reconnect requested",
            "通知发送权限已开启" to "Notification permission is enabled",
            "日期格式请使用 YYYY-MM-DD" to "Use date format YYYY-MM-DD",
            "已保存日期覆盖" to "Date override saved",
            "后台参数已保存并生效" to "Background parameters saved and applied",
            "未找到自启动管理入口，请手动在系统管家中设置" to "Autostart settings were not found. Please configure them in the system manager.",
            "未找到后台运行入口，请手动在电池/应用启动中设置" to "Background run settings were not found. Please configure them in Battery/App launch.",
            "未找到耗电保护入口，请手动在电池设置中配置" to "Power protection settings were not found. Please configure them in Battery settings.",
            "立即通知失败，请检查通知权限" to "Notify now failed. Please check notification permission.",
            "已立即通知" to "Notified",
            "已关闭中国工作日历" to "China workday calendar disabled",
            "同步中..." to "Syncing...",
            "系统应用" to "System app",
            "周一" to "Mon",
            "周二" to "Tue",
            "周三" to "Wed",
            "周四" to "Thu",
            "周五" to "Fri",
            "周六" to "Sat",
            "周日" to "Sun",
        )
    }
}

private val LocalAppStrings = staticCompositionLocalOf { AppStrings(false) }

@Composable
private fun s(zh: String): String = LocalAppStrings.current.text(zh)

@Composable
private fun rememberAppStrings(language: AppLanguage): AppStrings {
    val configuration = LocalConfiguration.current
    val systemLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        configuration.locale
    }
    val useEnglish = when (language) {
        AppLanguage.System -> systemLocale.language.lowercase(Locale.ROOT) != "zh"
        AppLanguage.Chinese -> false
        AppLanguage.English -> true
    }
    return remember(language, systemLocale.language) { AppStrings(useEnglish) }
}

private const val APP_PREFS_NAME = "nono_app_prefs"
private const val LANGUAGE_PREF_KEY = "language"

private fun loadLanguageSetting(context: Context): AppLanguage =
    AppLanguage.fromPref(context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE).getString(LANGUAGE_PREF_KEY, null))

private fun saveLanguageSetting(context: Context, language: AppLanguage) {
    context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(LANGUAGE_PREF_KEY, language.prefValue)
        .apply()
}

private data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val icon: Drawable?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NonoApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { RuleStore(context) }
    val delayedNoticeStore = remember { DelayedNoticeStore(context) }
    val holidayCalendarStore = remember { HolidayCalendarStore(context) }
    var rules by remember { mutableStateOf(store.loadRules()) }
    var delayedNotices by remember { mutableStateOf(delayedNoticeStore.loadNotices()) }
    var holidayConfig by remember { mutableStateOf(holidayCalendarStore.loadConfig()) }
    var manualOverrides by remember { mutableStateOf(holidayCalendarStore.loadOverrides()) }
    var calendarStatus by remember { mutableStateOf(holidayConfig.lastSyncMessage.ifBlank { holidayConfig.lastSyncLabel() }) }
    var selectedTab by rememberSaveable { mutableStateOf(NonoTab.Notifications) }
    var languageSetting by rememberSaveable { mutableStateOf(loadLanguageSetting(context)) }
    val appStrings = rememberAppStrings(languageSetting)
    var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    val installedApps = remember { loadInstalledApps(context) }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.serializeRules(rules)) }
        }.onSuccess {
            toast(context, appStrings.text("配置已导出"))
        }.onFailure {
            toast(context, appStrings.text("导出失败"))
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (imported != null && store.importRules(imported)) {
            rules = store.loadRules()
            toast(context, appStrings.text("配置已导入"))
        } else {
            toast(context, appStrings.text("导入失败，文件格式不正确"))
        }
    }

    val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        toast(context, appStrings.text(if (granted) "通知发送权限已授予" else "未授予通知发送权限"))
    }

    LaunchedEffect(Unit) {
        listenerEnabled = isNotificationListenerEnabled(context)
        if (listenerEnabled) {
            requestRebindNotificationListener(context)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delayedNotices = delayedNoticeStore.loadNotices()
            delay(800)
        }
    }

    CompositionLocalProvider(LocalAppStrings provides appStrings) {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFF3F2EE), Color(0xFFEEEEE9), Color(0xFFF8F7F4)),
                    ),
                ),
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                bottomBar = {
                    NavigationBar(
//                        containerColor = Color(0xFFF6F5F2).copy(alpha = 0.96f),
                        containerColor = Color(0xFFFFFF).copy(alpha = 0.96f),
                        windowInsets = WindowInsets(0, 0, 0, 12),
                    ) {
                        NonoTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                        NonoTab.Rules -> Icons.Outlined.Rule
                                        NonoTab.Add -> Icons.Outlined.PostAdd
                                        NonoTab.Notifications -> Icons.Outlined.Notifications
                                        NonoTab.Settings -> Icons.Outlined.Settings
                                    },
                                        contentDescription = appStrings.tabTitle(tab),
                                    )
                                },
                                label = { Text(appStrings.tabTitle(tab)) },
                            )
                        }
                    }
                },
            ) { innerPadding ->
                when (selectedTab) {
                    NonoTab.Rules -> RulesTab(
                        rules = rules,
                        installedApps = installedApps,
                        onRuleChange = { updatedRule ->
                            val updated = rules.map { if (it.id == updatedRule.id) updatedRule else it }
                            rules = updated
                            store.saveRules(updated)
                            delayedNotices = delayedNoticeStore.loadNotices()
                        },
                        onDelete = { rule ->
                            val updated = rules.filterNot { it.id == rule.id }
                            rules = updated
                            store.saveRules(updated)
                            delayedNotices = delayedNoticeStore.loadNotices()
                        },
                        onMoveRule = { fromIndex, toIndex ->
                            if (fromIndex !in rules.indices || toIndex !in rules.indices || fromIndex == toIndex) {
                                return@RulesTab
                            }
                            val updated = rules.toMutableList().apply {
                                add(toIndex, removeAt(fromIndex))
                            }
                            rules = updated
                            store.saveRules(updated)
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                    NonoTab.Add -> AddRuleTab(
                        installedApps = installedApps,
                        onAdd = { newRule ->
                            val updated = rules + newRule
                            rules = updated
                            store.saveRules(updated)
                            delayedNotices = delayedNoticeStore.loadNotices()
                            selectedTab = NonoTab.Rules
                            toast(context, appStrings.text("已新增规则"))
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                    NonoTab.Notifications -> NotificationsTab(
                        notices = delayedNotices,
                        installedApps = installedApps,
                        onOpenNotice = { notice ->
                            val intent = DelayedNotificationReceiver.buildFallbackLaunchIntent(context, notice.packageName)
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                toast(context, appStrings.text("无法打开对应应用"))
                            }
                        },
                        onDeleteNotice = { notice ->
                            DelayedNotificationScheduler.cancel(context, notice.id)
                            delayedNoticeStore.removeNotice(notice.id)
                            delayedNotices = delayedNoticeStore.loadNotices()
                        },
                        onDeleteGroup = { noticeIds ->
                            noticeIds.forEach { id ->
                                DelayedNotificationScheduler.cancel(context, id)
                                delayedNoticeStore.removeNotice(id)
                            }
                            delayedNotices = delayedNoticeStore.loadNotices()
                        },
                        onNotifyGroupNow = { groupNotices ->
                            var successCount = 0
                            groupNotices.forEach { notice ->
                                val notified = notifyNoticeNow(context, notice)
                                if (notified) {
                                    successCount += 1
                                    DelayedNotificationScheduler.cancel(context, notice.id)
                                    delayedNoticeStore.removeNotice(notice.id)
                                }
                            }
                            delayedNotices = delayedNoticeStore.loadNotices()
                            toast(
                                context,
                                if (successCount > 0) {
                                    "${appStrings.text("已立即通知")} $successCount ${appStrings.text("条")}"
                                } else {
                                    appStrings.text("立即通知失败，请检查通知权限")
                                },
                            )
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                    NonoTab.Settings -> SettingsTab(
                        listenerEnabled = listenerEnabled,
                        holidayConfig = holidayConfig,
                        manualOverrides = manualOverrides,
                        calendarStatus = calendarStatus,
                        languageSetting = languageSetting,
                        onRefresh = { listenerEnabled = isNotificationListenerEnabled(context) },
                        onRebindListener = {
                            requestRebindNotificationListener(context)
                            listenerEnabled = isNotificationListenerEnabled(context)
                            toast(context, appStrings.text("已请求重连通知监听"))
                        },
                        onResetListenerComponent = {
                            resetNotificationListenerComponent(context)
                            requestRebindNotificationListener(context)
                            listenerEnabled = isNotificationListenerEnabled(context)
                            toast(context, appStrings.text("已重置组件绑定并请求重连"))
                        },
                        onRequestIgnoreBatteryOptimization = {
                            requestIgnoreBatteryOptimization(context)
                        },
                        onRequestPostNotificationPermission = {
                            if (hasPostNotificationPermission(context)) {
                                toast(context, appStrings.text("通知发送权限已开启"))
                            } else {
                                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onOpenSettings = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        onOpenPolicySettings = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                        onExport = { exportLauncher.launch("nono-rules.json") },
                        onImport = { importLauncher.launch("application/json") },
                        onCalendarEnabledChange = { enabled ->
                            val updatedConfig = holidayConfig.copy(useChinaWorkdayCalendar = enabled)
                            holidayConfig = updatedConfig
                            holidayCalendarStore.saveConfig(updatedConfig)
                            calendarStatus = if (enabled) updatedConfig.lastSyncLabel() else appStrings.text("已关闭中国工作日历")
                        },
                        onCalendarUrlChange = { remoteUrl ->
                            val updatedConfig = holidayConfig.copy(remoteUrl = remoteUrl.trim())
                            holidayConfig = updatedConfig
                            holidayCalendarStore.saveConfig(updatedConfig)
                        },
                        onCalendarSync = {
                            scope.launch {
                                calendarStatus = appStrings.text("同步中...")
                                val result = withContext(Dispatchers.IO) {
                                    holidayCalendarStore.syncNow()
                                }
                                holidayConfig = if (result.success && result.syncedAtMillis > 0L) {
                                    holidayConfig.copy(
                                        lastSyncAtMillis = result.syncedAtMillis,
                                        lastSyncMessage = result.message,
                                    )
                                } else {
                                    holidayCalendarStore.loadConfig()
                                }
                                calendarStatus = buildString {
                                    append(result.message)
                                    if (result.debugInfo.isNotBlank()) {
                                        append("\n")
                                        append(result.debugInfo)
                                    }
                                }
                            }
                        },
                        onAddOverride = { dateText, isWorkday, note ->
                            val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                            if (date == null) {
                                toast(context, appStrings.text("日期格式请使用 YYYY-MM-DD"))
                            } else {
                                holidayCalendarStore.addOverride(
                                    ManualDayOverride(
                                        date = date,
                                        isWorkday = isWorkday,
                                        note = note.trim(),
                                    ),
                                )
                                manualOverrides = holidayCalendarStore.loadOverrides()
                                toast(context, appStrings.text("已保存日期覆盖"))
                            }
                        },
                        onDeleteOverride = { date ->
                            holidayCalendarStore.removeOverride(date)
                            manualOverrides = holidayCalendarStore.loadOverrides()
                        },
                        onLanguageChange = { language ->
                            languageSetting = language
                            saveLanguageSetting(context, language)
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun RulesTab(
    rules: List<NotificationRule>,
    installedApps: List<InstalledAppInfo>,
    onRuleChange: (NotificationRule) -> Unit,
    onDelete: (NotificationRule) -> Unit,
    onMoveRule: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandedRuleIds = remember { mutableStateListOf<String>() }
    var draggingRuleId by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
//        item {
//            IntroCard(
//                title = "规则",
//                subtitle = "",
//            )
//        }

        if (rules.isEmpty()) {
            item {
                EmptyStateCard("当前还没有规则，去“新增配置”里添加第一条规则。")
            }
        } else {
            itemsIndexed(rules, key = { _, rule -> rule.id }) { _, rule ->
                val expanded = rule.id in expandedRuleIds
                RuleEditorCard(
                    rule = rule,
                    appInfo = installedApps.firstOrNull { it.packageName == rule.packageName },
                    expanded = expanded,
                    isDragging = draggingRuleId == rule.id,
                    onToggleExpanded = {
                        if (expanded) expandedRuleIds.remove(rule.id) else expandedRuleIds.add(rule.id)
                    },
                    onDragStart = { draggingRuleId = rule.id },
                    onDragMove = { direction ->
                        val currentIndex = rules.indexOfFirst { it.id == rule.id }
                        if (currentIndex == -1) return@RuleEditorCard
                        val targetIndex = (currentIndex + direction).coerceIn(0, rules.lastIndex)
                        if (targetIndex != currentIndex) {
                            onMoveRule(currentIndex, targetIndex)
                        }
                    },
                    onDragEnd = { draggingRuleId = null },
                    onRuleChange = onRuleChange,
                    onDelete = { onDelete(rule) },
                )
            }
        }
    }
}

@Composable
private fun NotificationsTab(
    notices: List<DelayedNotice>,
    installedApps: List<InstalledAppInfo>,
    onOpenNotice: (DelayedNotice) -> Unit,
    onDeleteNotice: (DelayedNotice) -> Unit,
    onDeleteGroup: (List<String>) -> Unit,
    onNotifyGroupNow: (List<DelayedNotice>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandedGroups = remember { mutableStateListOf<String>() }
    val delayedGrouped = notices
        .asSequence()
        .filter { it.notifyEnabled }
        .sortedBy { it.scheduledAtMillis }
        .groupBy { notice -> "${formatNoticeTime(notice.scheduledAtMillis)}|${notice.appName}" }
    val collectedGrouped = notices
        .asSequence()
        .filter { !it.notifyEnabled }
        .sortedByDescending { it.createdAtMillis }
        .groupBy { notice -> "${notice.appName}|${notice.packageName}" }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
//        item {
//            IntroCard(
//                title = "通知",
//                subtitle = "",
//            )
//        }

        if (delayedGrouped.isEmpty()) {
            item {
                EmptyStateCard("暂时没有延后中的通知。")
            }
        } else {
            delayedGrouped.forEach { (groupKey, groupNotices) ->
                val timeLabel = formatNoticeTime(groupNotices.first().scheduledAtMillis)
                val appLabel = groupNotices.first().appName
                val appInfo = installedApps.firstOrNull { it.packageName == groupNotices.first().packageName }
                val uiKey = "delay|$groupKey"
                item(key = uiKey) {
                    val expanded = uiKey in expandedGroups
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.animateContentSize(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (expanded) expandedGroups.remove(uiKey) else expandedGroups.add(uiKey)
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(drawable = appInfo?.icon, contentDescription = appLabel, size = 38.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(appLabel, fontWeight = FontWeight.SemiBold)
                                    Text("$timeLabel · ${groupNotices.size} ${s("条")}", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = { onNotifyGroupNow(groupNotices) }) {
                                    Text(s("立即通知"))
                                }
                                IconButton(onClick = { onDeleteGroup(groupNotices.map { it.id }) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = s("删除整组"),
                                    )
                                }
                                Icon(
                                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = null,
                                )
                            }
                            if (expanded) {
                                Spacer(modifier = Modifier.height(10.dp))
                                groupNotices.forEach { notice ->
                                    val displayTitle = DelayedNotificationReceiver.buildDisplayTitle(
                                        title = notice.title,
                                        appName = notice.appName,
                                    )
                                    val displayText = DelayedNotificationReceiver.buildDisplayText(notice.text).take(120)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onOpenNotice(notice) },
                                        ) {
                                            Text(displayTitle, fontWeight = FontWeight.SemiBold)
                                            Text(displayText, style = MaterialTheme.typography.bodySmall)
                                        }
                                        IconButton(onClick = { onDeleteNotice(notice) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = s("删除"),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (collectedGrouped.isEmpty()) {
            item {
                EmptyStateCard("暂时没有已收集的通知。")
            }
        } else {
            collectedGrouped.forEach { (groupKey, groupNotices) ->
                val appLabel = groupNotices.first().appName.ifBlank { groupNotices.first().packageName }
                val appInfo = installedApps.firstOrNull { it.packageName == groupNotices.first().packageName }
                val uiKey = "collect|$groupKey"
                item(key = uiKey) {
                    val expanded = uiKey in expandedGroups
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.animateContentSize(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (expanded) expandedGroups.remove(uiKey) else expandedGroups.add(uiKey)
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(drawable = appInfo?.icon, contentDescription = appLabel, size = 38.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(appLabel, fontWeight = FontWeight.SemiBold)
                                    Text("${groupNotices.size} ${s("条")}", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = { onNotifyGroupNow(groupNotices) }) {
                                    Text(s("立即通知"))
                                }
                                IconButton(onClick = { onDeleteGroup(groupNotices.map { it.id }) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = s("删除整组"),
                                    )
                                }
                                Icon(
                                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = null,
                                )
                            }
                            if (expanded) {
                                Spacer(modifier = Modifier.height(10.dp))
                                groupNotices.forEach { notice ->
                                    val displayTitle = DelayedNotificationReceiver.buildDisplayTitle(
                                        title = notice.title,
                                        appName = notice.appName,
                                    )
                                    val displayText = DelayedNotificationReceiver.buildDisplayText(notice.text).take(120)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onOpenNotice(notice) },
                                        ) {
                                            Text(displayTitle, fontWeight = FontWeight.SemiBold)
                                            Text(displayText, style = MaterialTheme.typography.bodySmall)
                                        }
                                        IconButton(onClick = { onDeleteNotice(notice) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = s("删除"),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddRuleTab(
    installedApps: List<InstalledAppInfo>,
    onAdd: (NotificationRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearchBar by rememberSaveable { mutableStateOf(true) }
    var showAllApps by rememberSaveable { mutableStateOf(false) }
    var appName by rememberSaveable { mutableStateOf("") }
    var packageName by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var startText by rememberSaveable { mutableStateOf("09:00") }
    var endText by rememberSaveable { mutableStateOf("18:00") }
    var allDay by rememberSaveable { mutableStateOf(false) }
    var remindAtText by rememberSaveable { mutableStateOf("09:00") }
    var targetsText by rememberSaveable { mutableStateOf("") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var mode by rememberSaveable { mutableStateOf(RuleMode.BLOCK) }
    var soundMode by rememberSaveable { mutableStateOf(DeviceSoundMode.KEEP) }
    var dayMode by rememberSaveable { mutableStateOf(RuleDayMode.WORKDAY) }
    var blockAll by rememberSaveable { mutableStateOf(false) }
    var selectedDays by remember { mutableStateOf(defaultActiveDays()) }

    val filteredApps = remember(installedApps, searchQuery) {
        val keyword = searchQuery.trim().lowercase()
        if (keyword.isBlank()) emptyList() else {
            installedApps.filter {
                it.label.lowercase().contains(keyword) || it.packageName.lowercase().contains(keyword)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
//        item {
//            IntroCard(
//                title = "配置",
//                subtitle = "",
//            )
//        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Button(
                        onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) {
                                searchQuery = ""
                                showAllApps = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (showSearchBar) s("收起搜索栏") else s("展开搜索栏"))
                    }

                    if (showSearchBar) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                if (it.isNotBlank()) showAllApps = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(s("搜索已安装应用")) },
                            placeholder = { Text(s("输入应用名或包名的一部分")) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        )

                        if (searchQuery.isNotBlank()) {
                            AppListCard(
                                apps = filteredApps,
                                emptyText = "没有找到匹配应用，可以手动填写包名。",
                                onSelect = { app ->
                                    appName = app.label
                                    packageName = app.packageName
                                    searchQuery = ""
                                    showSearchBar = false
                                    showAllApps = false
                                },
                            )
                        }
                    }

                    if (searchQuery.isBlank()) {
                        Button(onClick = { showAllApps = !showAllApps }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showAllApps) s("收起全部应用") else s("展开全部应用"))
                        }
                        if (showAllApps) {
                            AppListCard(
                                apps = installedApps,
                                emptyText = "没有读取到应用列表。",
                                onSelect = { app ->
                                    appName = app.label
                                    packageName = app.packageName
                                },
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(s("规则启用"), fontWeight = FontWeight.SemiBold)
                    }

                    RuleFormFields(
                        appName = appName,
                        onAppNameChange = { appName = it },
                        packageName = packageName,
                        onPackageNameChange = { packageName = it },
                        note = note,
                        onNoteChange = { note = it },
                        mode = mode,
                        onModeChange = { mode = it },
                        remindAtText = remindAtText,
                        onRemindAtTextChange = { remindAtText = it },
                        soundMode = soundMode,
                        onSoundModeChange = { soundMode = it },
                        dayMode = dayMode,
                        onDayModeChange = { dayMode = it },
                        startText = startText,
                        onStartTextChange = { startText = it },
                        endText = endText,
                        onEndTextChange = { endText = it },
                        allDay = allDay,
                        onAllDayChange = { allDay = it },
                        selectedDays = selectedDays,
                        onSelectedDaysChange = { selectedDays = it },
                        blockAll = blockAll,
                        onBlockAllChange = { blockAll = it },
                        targetsText = targetsText,
                        onTargetsTextChange = { targetsText = it },
                    )

                    Button(
                        onClick = {
                            onAdd(
                                NotificationRule(
                                    appName = appName.ifBlank { "未命名应用" },
                                    packageName = packageName.trim(),
                                    note = note.trim(),
                                    enabled = enabled,
                                    mode = mode,
                                    remindAtMinutes = remindAtText.toMinutesOrDefault(9 * 60),
                                    soundMode = soundMode,
                                    startMinutes = startText.toMinutesOrDefault(9 * 60),
                                    endMinutes = endText.toMinutesOrDefault(18 * 60),
                                    allDay = allDay,
                                    dayMode = dayMode,
                                    activeDays = selectedDays,
                                    targets = if (blockAll) listOf("*") else splitTargets(targetsText),
                                ),
                            )
                            searchQuery = ""
                            showSearchBar = true
                            showAllApps = false
                            appName = ""
                            packageName = ""
                            note = ""
                            startText = "09:00"
                            endText = "18:00"
                            allDay = false
                            remindAtText = "09:00"
                            targetsText = ""
                            enabled = true
                            mode = RuleMode.BLOCK
                            soundMode = DeviceSoundMode.KEEP
                            dayMode = RuleDayMode.WORKDAY
                            blockAll = false
                            selectedDays = defaultActiveDays()
                        },
                        enabled = packageName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(s("保存配置"))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    listenerEnabled: Boolean,
    holidayConfig: HolidayCalendarConfig,
    manualOverrides: List<ManualDayOverride>,
    calendarStatus: String,
    languageSetting: AppLanguage,
    onRefresh: () -> Unit,
    onRebindListener: () -> Unit,
    onResetListenerComponent: () -> Unit,
    onRequestIgnoreBatteryOptimization: () -> Unit,
    onRequestPostNotificationPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPolicySettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCalendarEnabledChange: (Boolean) -> Unit,
    onCalendarUrlChange: (String) -> Unit,
    onCalendarSync: () -> Unit,
    onAddOverride: (String, Boolean, String) -> Unit,
    onDeleteOverride: (LocalDate) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val strings = LocalAppStrings.current
    val hasPostNotificationPermission = hasPostNotificationPermission(context)
    val canScheduleExactAlarms =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val ignoringBatteryOptimizations =
        (context.getSystemService(PowerManager::class.java))
            .isIgnoringBatteryOptimizations(context.packageName)
    var listenerDiagTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_500)
            listenerDiagTick++
        }
    }
    val listenerDiagnostic = remember(listenerDiagTick) { loadListenerDiagnostic(context) }
    val listenerTechStatus = remember(listenerDiagTick) { notificationListenerTechStatus(context) }
    val runtimeTuning = remember { RuntimeTuningSettingsStore.load(context) }
    var rebindIntervalSecText by rememberSaveable {
        mutableStateOf((runtimeTuning.rebindIntervalMs / 1000L).toString())
    }
    var activeCheckIntervalSecText by rememberSaveable {
        mutableStateOf((runtimeTuning.activeCheckIntervalMs / 1000L).toString())
    }
    var idleCheckIntervalSecText by rememberSaveable {
        mutableStateOf((runtimeTuning.idleCheckIntervalMs / 1000L).toString())
    }
    var resendPriority by rememberSaveable {
        mutableStateOf(runtimeTuning.resendTriggerPriority.name)
    }

    var overrideDateText by rememberSaveable { mutableStateOf("") }
    var overrideNoteText by rememberSaveable { mutableStateOf("") }
    var overrideIsWorkday by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
//        item {
//                IntroCard(
//                    title = "设置",
//                    subtitle = "",
//                )
//            }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s("语言"), fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = languageSetting == AppLanguage.System,
                            onClick = { onLanguageChange(AppLanguage.System) },
                            label = { Text(s("跟随系统")) },
                        )
                        FilterChip(
                            selected = languageSetting == AppLanguage.Chinese,
                            onClick = { onLanguageChange(AppLanguage.Chinese) },
                            label = { Text(s("中文")) },
                        )
                        FilterChip(
                            selected = languageSetting == AppLanguage.English,
                            onClick = { onLanguageChange(AppLanguage.English) },
                            label = { Text(s("英文")) },
                        )
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s("通知访问权限"), fontWeight = FontWeight.SemiBold)
                            Text(
                                if (listenerEnabled) s("已开启，可按规则拦截通知。") else s("未开启，规则暂时不会生效。"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        AssistChip(onClick = {}, label = { Text(if (listenerEnabled) s("已开启") else s("未开启")) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onOpenSettings, modifier = Modifier.weight(1f)) { Text(s("打开系统设置")) }
                        Button(onClick = onRefresh, modifier = Modifier.weight(1f)) { Text(s("刷新状态")) }
                    }
                    Button(onClick = onRebindListener, modifier = Modifier.fillMaxWidth()) {
                        Text(s("重连通知监听服务"))
                    }
                    Button(onClick = onResetListenerComponent, modifier = Modifier.fillMaxWidth()) {
                        Text(s("重置监听组件绑定"))
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationPermission) {
                        Button(onClick = onRequestPostNotificationPermission, modifier = Modifier.fillMaxWidth()) {
                            Text(s("申请通知发送权限"))
                        }
                    }
                    Text(
                        text = "${s("通知发送权限")}: " + if (hasPostNotificationPermission) s("已开启") else s("未开启"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "${s("精确闹钟")}: " + if (canScheduleExactAlarms) s("可用") else s("受限（延后提醒可能不准时）"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "${s("电池优化豁免")}: " + if (ignoringBatteryOptimizations) s("已开启") else s("未开启（离开 App 后可能被限制）"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!ignoringBatteryOptimizations) {
                        Button(onClick = onRequestIgnoreBatteryOptimization, modifier = Modifier.fillMaxWidth()) {
                            Text(s("申请忽略电池优化"))
                        }
                    }
                    Text(
                        text = if (listenerDiagnostic.isNotBlank()) {
                            "${s("监听诊断")}: $listenerDiagnostic"
                        } else {
                            "${s("监听诊断")}: ${s("尚未收到监听回调（如果已触发通知，请重连监听服务后再观察）")}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "${s("监听底层状态")}: $listenerTechStatus",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(s("后台驻留参数（秒）"), fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = rebindIntervalSecText,
                        onValueChange = { rebindIntervalSecText = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(s("监听重连间隔")) },
                    )
                    OutlinedTextField(
                        value = activeCheckIntervalSecText,
                        onValueChange = { activeCheckIntervalSecText = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(s("活跃检查间隔")) },
                    )
                    OutlinedTextField(
                        value = idleCheckIntervalSecText,
                        onValueChange = { idleCheckIntervalSecText = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(s("空闲检查间隔")) },
                    )
                    Text(s("重发方法"), fontWeight = FontWeight.SemiBold)
                    Text(
                        s("可分别测试普通、复用意图、代理回放、气泡、全屏、原闹钟。"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = resendPriority == ResendTriggerPriority.NORMAL_ONLY.name,
                                onClick = { resendPriority = ResendTriggerPriority.NORMAL_ONLY.name },
                                label = { Text(s("仅普通重发")) },
                            )
                            FilterChip(
                                selected = resendPriority == ResendTriggerPriority.NORMAL_FIRST.name,
                                onClick = { resendPriority = ResendTriggerPriority.NORMAL_FIRST.name },
                                label = { Text(s("普通优先")) },
                            )
                            FilterChip(
                                selected = resendPriority == ResendTriggerPriority.FIDELITY_FIRST.name,
                                onClick = { resendPriority = ResendTriggerPriority.FIDELITY_FIRST.name },
                                label = { Text(s("保真优先")) },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = resendPriority == ResendTriggerPriority.REUSE_INTENT.name,
                                onClick = { resendPriority = ResendTriggerPriority.REUSE_INTENT.name },
                                label = { Text(s("复用意图")) },
                            )
                            FilterChip(
                                selected = resendPriority == ResendTriggerPriority.PROXY_REPLAY.name,
                                onClick = { resendPriority = ResendTriggerPriority.PROXY_REPLAY.name },
                                label = { Text(s("代理回放")) },
                            )
                            FilterChip(
                                selected = resendPriority == ResendTriggerPriority.BUBBLE_FIRST.name,
                                onClick = { resendPriority = ResendTriggerPriority.BUBBLE_FIRST.name },
                                label = { Text(s("气泡优先")) },
                            )
                            FilterChip(
                                selected = resendPriority == ResendTriggerPriority.FULL_SCREEN_FIRST.name,
                                onClick = { resendPriority = ResendTriggerPriority.FULL_SCREEN_FIRST.name },
                                label = { Text(s("全屏优先")) },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = resendPriority == ResendTriggerPriority.ALARM_ONLY.name,
                                onClick = { resendPriority = ResendTriggerPriority.ALARM_ONLY.name },
                                label = { Text(s("原闹钟")) },
                            )
                            FilterChip(
                                selected = resendPriority == ResendTriggerPriority.ALARM_FIRST.name,
                                onClick = { resendPriority = ResendTriggerPriority.ALARM_FIRST.name },
                                label = { Text(s("闹钟优先")) },
                            )
                        }
                    }
                    Button(
                        onClick = {
                            val rebindSec = rebindIntervalSecText.toIntOrNull() ?: 90
                            val activeSec = activeCheckIntervalSecText.toIntOrNull() ?: 60
                            val idleSec = idleCheckIntervalSecText.toIntOrNull() ?: 180
                            RuntimeTuningSettingsStore.saveIntervalsSeconds(
                                context = context,
                                rebindIntervalSec = rebindSec,
                                activeCheckIntervalSec = activeSec,
                                idleCheckIntervalSec = idleSec,
                            )
                            val priority = runCatching { ResendTriggerPriority.valueOf(resendPriority) }
                                .getOrDefault(ResendTriggerPriority.NORMAL_FIRST)
                            RuntimeTuningSettingsStore.saveResendTriggerPriority(context, priority)
                            restartListenerKeepAliveService(context)
                            toast(context, strings.text("后台参数已保存并生效"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(s("保存后台参数"))
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms) {
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(s("打开精确闹钟设置"))
                        }
                    }
                    Button(onClick = onOpenPolicySettings, modifier = Modifier.fillMaxWidth()) {
                        Text(s("打开勿扰访问"))
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s("后台驻留步骤"), fontWeight = FontWeight.SemiBold)
//                    Text(
//                        "用于提升后台存活率（尤其华为/荣耀）。建议依次完成以下设置：",
//                        style = MaterialTheme.typography.bodySmall,
//                    )
                    Button(
                        onClick = {
                            val ok = openAutostartSettings(context)
                            if (!ok) toast(context, strings.text("未找到自启动管理入口，请手动在系统管家中设置"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(s("1. 打开自启动管理")) }
                    Button(
                        onClick = {
                            val ok = openBackgroundRunSettings(context)
                            if (!ok) toast(context, strings.text("未找到后台运行入口，请手动在电池/应用启动中设置"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(s("2. 打开后台运行/应用启动")) }
                    Button(
                        onClick = {
                            val ok = openPowerOptimizationSettings(context)
                            if (!ok) toast(context, strings.text("未找到耗电保护入口，请手动在电池设置中配置"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(s("3. 打开耗电保护设置")) }
                    Button(
                        onClick = { openAppDetailsSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(s("4. 打开应用详情（检查）")) }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(s("工作日历"), fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = holidayConfig.useChinaWorkdayCalendar,
                            onCheckedChange = onCalendarEnabledChange,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(s("按工作日/调休判断"), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    OutlinedTextField(
                        value = holidayConfig.remoteUrl,
                        onValueChange = onCalendarUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(s("在线日历地址")) },
                        placeholder = { Text("https://timor.tech/api/holiday/year") },
                    )
                    Text(holidayConfig.lastSyncLabel(), style = MaterialTheme.typography.bodySmall)
                    if (calendarStatus.isNotBlank()) {
                        Text(calendarStatus, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D635F))
                    }
                    Button(onClick = onCalendarSync, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(s("同步到本地"))
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(s("请假与调休"), fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = overrideDateText,
                        onValueChange = { overrideDateText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(s("日期")) },
                        placeholder = { Text("2010-01-02") },
                    )
                    OutlinedTextField(
                        value = overrideNoteText,
                        onValueChange = { overrideNoteText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(s("备注")) },
                        placeholder = { Text(s("例如：年假 / 周日补班")) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !overrideIsWorkday,
                            onClick = { overrideIsWorkday = false },
                            label = { Text(s("请假/休息")) },
                        )
                        FilterChip(
                            selected = overrideIsWorkday,
                            onClick = { overrideIsWorkday = true },
                            label = { Text(s("调休加班")) },
                        )
                    }
                    Button(
                        onClick = {
                            onAddOverride(overrideDateText, overrideIsWorkday, overrideNoteText)
                            overrideDateText = ""
                            overrideNoteText = ""
                            overrideIsWorkday = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(s("保存日期覆盖"))
                    }

                    if (manualOverrides.isEmpty()) {
                        Text(s("暂时还没有手动日期覆盖。"), style = MaterialTheme.typography.bodySmall)
                    } else {
                        manualOverrides.forEach { override ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${override.date} · ${if (override.isWorkday) s("调休加班") else s("请假/休息")}",
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (override.note.isNotBlank()) {
                                        Text(override.note, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = { onDeleteOverride(override.date) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = s("删除日期覆盖"))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(s("配置管理"), fontWeight = FontWeight.SemiBold)
                    Text(s("导出会生成一份 JSON 文件，导入时会覆盖当前本地规则。"), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(s("导出配置"))
                        }
                        Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(s("导入配置"))
                        }
                    }
                }
            }
        }

//        item {
//            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)), shape = RoundedCornerShape(24.dp)) {
//                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
//                    Text(s("应用列表权限限制"), fontWeight = FontWeight.SemiBold)
//                    Text(s("1. 已经启用 QUERY_ALL_PACKAGES 来获取完整已安装应用列表。"))
//                    Text(s("2. 当前 Android 对第三方 App 的更高应用列表权限基本没有更上一层。"))
//                    Text(s("3. 如果仍有缺失，很可能是工作 Profile、双开空间或 OEM 私有容器里的应用，普通 App 无法全部枚举。"))
//                }
//            }
//        }
    }
}

@Composable
private fun IntroCard(
    title: String,
    subtitle: String,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F6F2)), shape = RoundedCornerShape(28.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF5B6A63)),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, color = Color(0xFF1F2421), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(subtitle, color = Color(0xFF5D635F), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AppListCard(
    apps: List<InstalledAppInfo>,
    emptyText: String,
    onSelect: (InstalledAppInfo) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF2F1ED))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (apps.isEmpty()) {
            Text(s(emptyText), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        } else {
            apps.forEach { app ->
                AppSuggestionRow(app = app, onClick = { onSelect(app) })
            }
        }
    }
}

@Composable
private fun AppSuggestionRow(
    app: InstalledAppInfo,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(drawable = app.icon, contentDescription = app.label, size = 32.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (app.isSystemApp) "${app.packageName} · ${s("系统应用")}" else app.packageName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(s("选择"), color = Color(0xFF57675E), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AppIcon(
    drawable: Drawable?,
    contentDescription: String,
    size: Dp,
) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        },
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp)),
        update = { view ->
            view.contentDescription = contentDescription
            view.setImageDrawable(drawable)
        },
    )
}

@Composable
private fun RuleFormFields(
    appName: String,
    onAppNameChange: (String) -> Unit,
    packageName: String,
    onPackageNameChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    mode: RuleMode,
    onModeChange: (RuleMode) -> Unit,
    remindAtText: String,
    onRemindAtTextChange: (String) -> Unit,
    soundMode: DeviceSoundMode,
    onSoundModeChange: (DeviceSoundMode) -> Unit,
    dayMode: RuleDayMode,
    onDayModeChange: (RuleDayMode) -> Unit,
    startText: String,
    onStartTextChange: (String) -> Unit,
    endText: String,
    onEndTextChange: (String) -> Unit,
    allDay: Boolean,
    onAllDayChange: (Boolean) -> Unit,
    selectedDays: Set<DayOfWeek>,
    onSelectedDaysChange: (Set<DayOfWeek>) -> Unit,
    blockAll: Boolean,
    onBlockAllChange: (Boolean) -> Unit,
    targetsText: String,
    onTargetsTextChange: (String) -> Unit,
) {
    OutlinedTextField(value = appName, onValueChange = onAppNameChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(s("应用名称")) })
    OutlinedTextField(value = packageName, onValueChange = onPackageNameChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(s("包名")) }, placeholder = { Text(s("例如 com.tencent.mm")) })
    OutlinedTextField(value = note, onValueChange = onNoteChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(s("备注")) }, placeholder = { Text(s("例如：老板白名单")) })

    ModeSelector(selectedMode = mode, onModeChange = onModeChange)
    RuleDayModeSelector(selectedMode = dayMode, onModeChange = onDayModeChange)
    if (mode == RuleMode.DELAY) {
        OutlinedTextField(
            value = remindAtText,
            onValueChange = onRemindAtTextChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(s("提醒时间")) },
            placeholder = { Text("09:00") },
            supportingText = { Text(s("匹配到规则后，Nono 会在下一个符合条件的 HH:mm 时间发出本地提醒。")) },
        )
    }
    SoundModeSelector(selectedMode = soundMode, onModeChange = onSoundModeChange)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = allDay, onCheckedChange = onAllDayChange)
        Spacer(modifier = Modifier.width(10.dp))
        Text(s("全天"), fontWeight = FontWeight.SemiBold)
    }

    if (!allDay) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = startText, onValueChange = onStartTextChange, modifier = Modifier.weight(1f), singleLine = true, label = { Text(s("开始时间")) }, placeholder = { Text("09:00") })
            OutlinedTextField(value = endText, onValueChange = onEndTextChange, modifier = Modifier.weight(1f), singleLine = true, label = { Text(s("结束时间")) }, placeholder = { Text("18:00") })
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s("生效日期"), fontWeight = FontWeight.SemiBold)
        val dayChips = listOf<DayOfWeek?>(null) + weekDays
        dayChips.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { day ->
                    if (day == null) {
                        FilterChip(
                            selected = selectedDays.size == weekDays.size,
                            onClick = { onSelectedDaysChange(weekDays.toSet()) },
                            label = { Text(s("全部")) },
                        )
                    } else {
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                onSelectedDaysChange(
                                    if (day in selectedDays) selectedDays - day else selectedDays + day,
                                )
                            },
                            label = { Text(s(day.cnLabel)) },
                        )
                    }
                }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = blockAll, onCheckedChange = onBlockAllChange)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(s("整包匹配"), fontWeight = FontWeight.SemiBold)
            Text(s("开启后会使用 * 匹配该应用的所有通知。"), style = MaterialTheme.typography.bodySmall)
        }
    }

    OutlinedTextField(
        value = targetsText,
        onValueChange = onTargetsTextChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = 4,
        label = { Text(s("关键字")) },
        placeholder = { Text(s("每行一个联系人名、群名或频道名")) },
        supportingText = {
            Text(
                if (blockAll) {
                    s("当前将忽略关键字，直接匹配该应用全部通知。")
                } else {
                    s("留空表示匹配该应用全部通知；例如：老板、项目群、值班通知")
                },
            )
        },
    )
}

@Composable
private fun ModeSelector(
    selectedMode: RuleMode,
    onModeChange: (RuleMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s("通知模式"), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedMode == RuleMode.BLOCK, onClick = { onModeChange(RuleMode.BLOCK) }, label = { Text(s("屏蔽")) })
            FilterChip(selected = selectedMode == RuleMode.ALLOW, onClick = { onModeChange(RuleMode.ALLOW) }, label = { Text(s("允许")) })
            FilterChip(selected = selectedMode == RuleMode.DELAY, onClick = { onModeChange(RuleMode.DELAY) }, label = { Text(s("延后")) })
            FilterChip(selected = selectedMode == RuleMode.COLLECT_ONLY, onClick = { onModeChange(RuleMode.COLLECT_ONLY) }, label = { Text(s("收集")) })
        }
    }
}

@Composable
private fun RuleDayModeSelector(
    selectedMode: RuleDayMode,
    onModeChange: (RuleDayMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s("规则日类型"), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedMode == RuleDayMode.WORKDAY, onClick = { onModeChange(RuleDayMode.WORKDAY) }, label = { Text(s("工作")) })
            FilterChip(selected = selectedMode == RuleDayMode.RESTDAY, onClick = { onModeChange(RuleDayMode.RESTDAY) }, label = { Text(s("休息")) })
            FilterChip(selected = selectedMode == RuleDayMode.ALL, onClick = { onModeChange(RuleDayMode.ALL) }, label = { Text(s("全部")) })
        }
    }
}
@Composable
private fun SoundModeSelector(
    selectedMode: DeviceSoundMode,
    onModeChange: (DeviceSoundMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s("系统响铃模式"), fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedMode == DeviceSoundMode.KEEP,
                onClick = { onModeChange(DeviceSoundMode.KEEP) },
                label = { Text(s("保持")) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = selectedMode == DeviceSoundMode.RING,
                onClick = { onModeChange(DeviceSoundMode.RING) },
                label = { Text(s("响铃")) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = selectedMode == DeviceSoundMode.VIBRATE,
                onClick = { onModeChange(DeviceSoundMode.VIBRATE) },
                label = { Text(s("震动")) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = selectedMode == DeviceSoundMode.SILENT,
                onClick = { onModeChange(DeviceSoundMode.SILENT) },
                label = { Text(s("静音")) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)), shape = RoundedCornerShape(22.dp)) {
        Text(s(message), modifier = Modifier.padding(18.dp))
    }
}

@Composable
private fun RuleEditorCard(
    rule: NotificationRule,
    appInfo: InstalledAppInfo?,
    expanded: Boolean,
    isDragging: Boolean,
    onToggleExpanded: () -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (direction: Int) -> Unit,
    onDragEnd: () -> Unit,
    onRuleChange: (NotificationRule) -> Unit,
    onDelete: () -> Unit,
) {
    var appName by remember(rule.id) { mutableStateOf(rule.appName) }
    var packageName by remember(rule.id) { mutableStateOf(rule.packageName) }
    var note by remember(rule.id) { mutableStateOf(rule.note) }
    var enabled by remember(rule.id) { mutableStateOf(rule.enabled) }
    var mode by remember(rule.id) { mutableStateOf(rule.mode) }
    var remindAtText by remember(rule.id) { mutableStateOf(rule.remindAtMinutes.toHourMinute()) }
    var soundMode by remember(rule.id) { mutableStateOf(rule.soundMode) }
    var dayMode by remember(rule.id) { mutableStateOf(rule.dayMode) }
    var startText by remember(rule.id) { mutableStateOf(rule.startMinutes.toHourMinute()) }
    var endText by remember(rule.id) { mutableStateOf(rule.endMinutes.toHourMinute()) }
    var allDay by remember(rule.id) { mutableStateOf(rule.allDay) }
    var targetsText by remember(rule.id) { mutableStateOf(if (rule.targets == listOf("*")) "*" else rule.targets.joinToString("\n")) }
    var blockAll by remember(rule.id) { mutableStateOf(rule.targets == listOf("*")) }
    var selectedDays by remember(rule.id) { mutableStateOf(rule.activeDays) }
    val dragStepPx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    var dragOffsetY by remember(rule.id) { mutableStateOf(0f) }
    var dragAccumulatedY by remember(rule.id) { mutableStateOf(0f) }

    fun resolvedTargets(): List<String> = if (blockAll) listOf("*") else splitTargets(targetsText)

    fun persist() {
        onRuleChange(
            rule.copy(
                appName = appName.ifBlank { "未命名应用" },
                packageName = packageName.trim(),
                note = note.trim(),
                enabled = enabled,
                mode = mode,
                remindAtMinutes = remindAtText.toMinutesOrDefault(rule.remindAtMinutes),
                soundMode = soundMode,
                startMinutes = startText.toMinutesOrDefault(rule.startMinutes),
                endMinutes = endText.toMinutesOrDefault(rule.endMinutes),
                allDay = allDay,
                dayMode = dayMode,
                activeDays = selectedDays,
                targets = resolvedTargets(),
            ),
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isDragging) Color(0xFFF2EFE7) else Color(0xFFF8F7F4)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .offset { IntOffset(0, dragOffsetY.toInt()) }
            .animateContentSize(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(drawable = appInfo?.icon, contentDescription = appName, size = 40.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(appName.ifBlank { s("新规则") }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = note.ifBlank { packageName.ifBlank { s("请填写包名") } },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            "${s(rule.dayModeLabel())} · " + when (mode) {
                                RuleMode.BLOCK -> s("屏蔽")
                                RuleMode.ALLOW -> s("允许")
                                RuleMode.DELAY -> s("延后")
                                RuleMode.COLLECT_ONLY -> s("收集")
                            },
                        )
                    },
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .pointerInput(rule.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragOffsetY = 0f
                                    dragAccumulatedY = 0f
                                    onDragStart()
                                },
                                onDragEnd = {
                                    dragOffsetY = 0f
                                    dragAccumulatedY = 0f
                                    onDragEnd()
                                },
                                onDragCancel = {
                                    dragOffsetY = 0f
                                    dragAccumulatedY = 0f
                                    onDragEnd()
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y
                                dragAccumulatedY += dragAmount.y
                                while (dragAccumulatedY >= dragStepPx) {
                                    onDragMove(1)
                                    dragAccumulatedY -= dragStepPx
                                }
                                while (dragAccumulatedY <= -dragStepPx) {
                                    onDragMove(-1)
                                    dragAccumulatedY += dragStepPx
                                }
                            }
                        }
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.Outlined.UnfoldMore,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        persist()
                    },
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = s("删除规则"))
                }
            }

            val previewRule = rule.copy(
                appName = appName.ifBlank { "未命名应用" },
                packageName = packageName.trim(),
                note = note.trim(),
                enabled = enabled,
                mode = mode,
                remindAtMinutes = remindAtText.toMinutesOrDefault(rule.remindAtMinutes),
                soundMode = soundMode,
                startMinutes = startText.toMinutesOrDefault(rule.startMinutes),
                endMinutes = endText.toMinutesOrDefault(rule.endMinutes),
                allDay = allDay,
                dayMode = dayMode,
                activeDays = selectedDays,
                targets = resolvedTargets(),
            )
            val previewTargets = previewRule.targets.joinToString(separator = " / ")
            val previewMode = when (previewRule.mode) {
                RuleMode.BLOCK -> s("屏蔽")
                RuleMode.ALLOW -> s("允许")
                RuleMode.DELAY -> "${s("延后")} ${previewRule.remindAtMinutes.toHourMinute()}"
                RuleMode.COLLECT_ONLY -> s("收集")
            }
            val now = LocalDateTime.now()
            val context = androidx.compose.ui.platform.LocalContext.current
            val holidayCalendarStore = remember { HolidayCalendarStore(context) }
            val isWorkingDate = holidayCalendarStore.isWorkingDate(now.toLocalDate())
            val nowStatus = previewRule.currentTimeStatusLabel(now = now, isWorkingDate = isWorkingDate)
            val previewDays = previewRule.activeDays.sortedBy { it.value }.joinToString(" ") { s(it.cnLabel) }
            Text(
                text = "${s("预览")}: ${s(previewRule.dayModeLabel())} | $previewMode | ${previewRule.timeRangeLabel()} | $previewDays | $previewTargets",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "${s("当前时间状态")}: ${s(nowStatus)}",
                style = MaterialTheme.typography.bodySmall,
            )

            if (expanded) {
                RuleFormFields(
                    appName = appName,
                    onAppNameChange = { appName = it; persist() },
                    packageName = packageName,
                    onPackageNameChange = { packageName = it; persist() },
                    note = note,
                    onNoteChange = { note = it; persist() },
                    mode = mode,
                    onModeChange = { mode = it; persist() },
                    remindAtText = remindAtText,
                    onRemindAtTextChange = { remindAtText = it; persist() },
                    soundMode = soundMode,
                    onSoundModeChange = { soundMode = it; persist() },
                    dayMode = dayMode,
                    onDayModeChange = { dayMode = it; persist() },
                    startText = startText,
                    onStartTextChange = { startText = it; persist() },
                    endText = endText,
                    onEndTextChange = { endText = it; persist() },
                    allDay = allDay,
                    onAllDayChange = { allDay = it; persist() },
                    selectedDays = selectedDays,
                    onSelectedDaysChange = { selectedDays = it; persist() },
                    blockAll = blockAll,
                    onBlockAllChange = { blockAll = it; persist() },
                    targetsText = targetsText,
                    onTargetsTextChange = { targetsText = it; persist() },
                )

                HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.6f))
            }
        }
    }
}

private fun loadInstalledApps(context: Context): List<InstalledAppInfo> {
    val packageManager = context.packageManager
    val safeFlagsLong =
        PackageManager.MATCH_DISABLED_COMPONENTS.toLong() or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS.toLong()

    fun installedApplicationsByFlags(flagsLong: Long): List<ApplicationInfo> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flagsLong))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(flagsLong.toInt())
        }
    }.getOrDefault(emptyList())

    fun packageInfosByFlags(flagsLong: Long): List<ApplicationInfo> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flagsLong))
                .mapNotNull { it.applicationInfo }
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flagsLong.toInt())
                .mapNotNull { it.applicationInfo }
        }
    }.getOrDefault(emptyList())

    val byAppApi = installedApplicationsByFlags(safeFlagsLong).ifEmpty { installedApplicationsByFlags(0L) }
    val byPkgApi = packageInfosByFlags(safeFlagsLong).ifEmpty { packageInfosByFlags(0L) }

    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val launcherPackageNames = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }
    }.getOrDefault(emptyList()).map { it.activityInfo.packageName }.distinct()

    val byLauncher = launcherPackageNames.mapNotNull { pkg ->
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(pkg, 0)
            }
        }.getOrNull()
    }

    val installedApplications = (byAppApi + byPkgApi + byLauncher).distinctBy { it.packageName }

    return installedApplications
        .asSequence()
        .map { app ->
            InstalledAppInfo(
                label = packageManager.getApplicationLabel(app).toString(),
                packageName = app.packageName,
                isSystemApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                icon = runCatching { packageManager.getApplicationIcon(app.packageName) }.getOrNull(),
            )
        }
        .sortedWith(compareBy<InstalledAppInfo> { it.label.lowercase() }.thenBy { it.packageName.lowercase() })
        .toList()
}

private fun defaultActiveDays(): Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val expected = ComponentName(context, com.tom.nono.service.AppNotificationGateService::class.java)
        .flattenToString()
    return flat?.contains(expected) == true
}

private fun requestRebindNotificationListener(context: Context) {
    val component = ComponentName(context, com.tom.nono.service.AppNotificationGateService::class.java)
    runCatching { NotificationListenerService.requestRebind(component) }
}

private fun resetNotificationListenerComponent(context: Context) {
    val component = ComponentName(context, com.tom.nono.service.AppNotificationGateService::class.java)
    val pm = context.packageManager
    runCatching {
        pm.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}

private fun notificationListenerTechStatus(context: Context): String {
    val component = ComponentName(context, com.tom.nono.service.AppNotificationGateService::class.java)
    val expected = component.flattenToString()
    val enabledRaw = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    val enabledContains = enabledRaw.contains(expected)
    val componentState = context.packageManager.getComponentEnabledSetting(component)
    val stateLabel = when (componentState) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "enabled"
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "disabled"
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> "disabled_user"
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> "disabled_until_used"
        else -> "default"
    }
    return "contains=$enabledContains, component=$stateLabel"
}

private fun requestIgnoreBatteryOptimization(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val powerManager = context.getSystemService(PowerManager::class.java)
    if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) return
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}

private fun restartListenerKeepAliveService(context: Context) {
    val intent = Intent(context, ListenerKeepAliveService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun openAutostartSettings(context: Context): Boolean {
    val intents = listOf(
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
        Intent().setComponent(ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        Intent().setComponent(ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity")),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${context.packageName}") },
    )
    return openFirstAvailableIntent(context, intents)
}

private fun openBackgroundRunSettings(context: Context): Boolean {
    val intents = listOf(
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity")),
        Intent().setComponent(ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.optimize.process.ProtectActivity")),
        Intent().setComponent(ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.optimize.bootstart.BootStartActivity")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${context.packageName}") },
    )
    return openFirstAvailableIntent(context, intents)
}

private fun openPowerOptimizationSettings(context: Context): Boolean {
    val intents = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.power.ui.HwPowerManagerActivity")),
        Intent().setComponent(ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.power.ui.HwPowerManagerActivity")),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${context.packageName}") },
    )
    return openFirstAvailableIntent(context, intents)
}

private fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}

private fun openFirstAvailableIntent(context: Context, intents: List<Intent>): Boolean {
    val pm = context.packageManager
    intents.forEach { intent ->
        val safeIntent = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val canOpen = safeIntent.resolveActivity(pm) != null
        if (canOpen) {
            runCatching { context.startActivity(safeIntent) }
                .onSuccess { return true }
        }
    }
    return false
}

private fun loadListenerDiagnostic(context: Context): String {
    val prefs = context.getSharedPreferences(AppNotificationGateService.DIAG_PREF, Context.MODE_PRIVATE)
    val ts = prefs.getLong(AppNotificationGateService.DIAG_LAST_TS, 0L)
    val event = prefs.getString(AppNotificationGateService.DIAG_LAST_EVENT, "").orEmpty()
    val pkg = prefs.getString(AppNotificationGateService.DIAG_LAST_PACKAGE, "").orEmpty()
    val pid = prefs.getString(AppNotificationGateService.DIAG_PROCESS, "").orEmpty()
    if (ts <= 0L && event.isBlank()) return ""
    val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(ts))
    val base = if (pkg.isNotBlank()) "$time | $event | $pkg" else "$time | $event"
    return if (pid.isNotBlank()) "$base | pid=$pid" else base
}

private fun hasPostNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun notifyNoticeNow(context: Context, notice: DelayedNotice): Boolean =
    DelayedNotificationReceiver.notifyReminder(
        context = context,
        title = notice.title,
        text = notice.text,
        appName = notice.appName,
        packageName = notice.packageName,
        originalContentIntent = null,
        originalActionIntents = emptyList(),
        originalActionTitles = emptyList(),
        originalDeleteIntent = null,
        originalBubbleIntent = null,
        originalFullScreenIntent = null,
        requestCode = notice.id.toIntOrNull() ?: (notice.id.hashCode() and Int.MAX_VALUE),
    )

private fun Int.toHourMinute(): String = "%02d:%02d".format(this / 60, this % 60)

private fun formatNoticeTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))

private fun String.toMinutesOrDefault(defaultValue: Int): Int {
    val parts = trim().split(":")
    if (parts.size != 2) return defaultValue
    val hour = parts[0].toIntOrNull() ?: return defaultValue
    val minute = parts[1].toIntOrNull() ?: return defaultValue
    if (hour !in 0..23 || minute !in 0..59) return defaultValue
    return hour * 60 + minute
}

private fun splitTargets(raw: String): List<String> {
    val targets = raw
        .split('\n', ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return if (targets.contains("*")) listOf("*") else targets
}

private fun NotificationRule.currentTimeStatusLabel(
    now: LocalDateTime,
    isWorkingDate: Boolean,
): String {
    val isActiveNow =
        matchesDayContext(isWorkingDate) &&
            now.dayOfWeek in activeDays &&
            isInWorkingWindow(
                nowDay = now.dayOfWeek,
                nowTime = now.toLocalTime(),
            )

    if (!isActiveNow) return "不生效"

    return when (mode) {
        RuleMode.BLOCK -> "屏蔽"
        RuleMode.ALLOW -> "允许"
        RuleMode.DELAY -> "延后"
        RuleMode.COLLECT_ONLY -> "收集"
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private val weekDays = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

private val DayOfWeek.cnLabel: String
    get() = when (this) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }


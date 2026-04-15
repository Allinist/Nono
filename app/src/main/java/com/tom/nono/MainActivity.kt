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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.tom.nono.data.lastSyncLabel
import com.tom.nono.service.AppNotificationGateService
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
    Notifications("\u901a\u77e5"),
    Rules("\u89c4\u5219"),
    Add("\u914d\u7f6e"),
    Settings("\u8bbe\u7f6e"),
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
    var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    val installedApps = remember { loadInstalledApps(context) }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.serializeRules(rules)) }
        }.onSuccess {
            toast(context, "\u914d\u7f6e\u5df2\u5bfc\u51fa")
        }.onFailure {
            toast(context, "\u5bfc\u51fa\u5931\u8d25")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (imported != null && store.importRules(imported)) {
            rules = store.loadRules()
            toast(context, "\u914d\u7f6e\u5df2\u5bfc\u5165")
        } else {
            toast(context, "\u5bfc\u5165\u5931\u8d25\uff0c\u6587\u4ef6\u683c\u5f0f\u4e0d\u6b63\u786e")
        }
    }

    val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        toast(context, if (granted) "\u901a\u77e5\u53d1\u9001\u6743\u9650\u5df2\u6388\u4e88" else "\u672a\u6388\u4e88\u901a\u77e5\u53d1\u9001\u6743\u9650")
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
                                        contentDescription = tab.title,
                                    )
                                },
                                label = { Text(tab.title) },
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
                            toast(context, "\u5df2\u65b0\u589e\u89c4\u5219")
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                    NonoTab.Notifications -> NotificationsTab(
                        notices = delayedNotices,
                        modifier = Modifier.padding(innerPadding),
                    )
                    NonoTab.Settings -> SettingsTab(
                        listenerEnabled = listenerEnabled,
                        holidayConfig = holidayConfig,
                        manualOverrides = manualOverrides,
                        calendarStatus = calendarStatus,
                        onRefresh = { listenerEnabled = isNotificationListenerEnabled(context) },
                        onRebindListener = {
                            requestRebindNotificationListener(context)
                            listenerEnabled = isNotificationListenerEnabled(context)
                            toast(context, "\u5df2\u8bf7\u6c42\u91cd\u8fde\u901a\u77e5\u76d1\u542c")
                        },
                        onResetListenerComponent = {
                            resetNotificationListenerComponent(context)
                            requestRebindNotificationListener(context)
                            listenerEnabled = isNotificationListenerEnabled(context)
                            toast(context, "\u5df2\u91cd\u7f6e\u7ec4\u4ef6\u7ed1\u5b9a\u5e76\u8bf7\u6c42\u91cd\u8fde")
                        },
                        onRequestIgnoreBatteryOptimization = {
                            requestIgnoreBatteryOptimization(context)
                        },
                        onRequestPostNotificationPermission = {
                            if (hasPostNotificationPermission(context)) {
                                toast(context, "\u901a\u77e5\u53d1\u9001\u6743\u9650\u5df2\u5f00\u542f")
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
                            calendarStatus = if (enabled) updatedConfig.lastSyncLabel() else "已关闭中国工作日历"    
                        },
                        onCalendarUrlChange = { remoteUrl ->
                            val updatedConfig = holidayConfig.copy(remoteUrl = remoteUrl.trim())
                            holidayConfig = updatedConfig
                            holidayCalendarStore.saveConfig(updatedConfig)
                        },
                        onCalendarSync = {
                            scope.launch {
                                calendarStatus = "同步中..."
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
                                toast(context, "日期格式请使用 YYYY-MM-DD")
                            } else {
                                holidayCalendarStore.addOverride(
                                    ManualDayOverride(
                                        date = date,
                                        isWorkday = isWorkday,
                                        note = note.trim(),
                                    ),
                                )
                                manualOverrides = holidayCalendarStore.loadOverrides()
                                toast(context, "已保存日期覆盖")
                            }
                        },
                        onDeleteOverride = { date ->
                            holidayCalendarStore.removeOverride(date)
                            manualOverrides = holidayCalendarStore.loadOverrides()
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
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
        item {
            IntroCard(
                title = "\u89c4\u5219\u603b\u89c8",
                subtitle = "\u89c4\u5219\u9ed8\u8ba4\u6298\u53e0\u663e\u793a\uff0c\u5c55\u5f00\u540e\u53ef\u7f16\u8f91\u5907\u6ce8\u3001\u6a21\u5f0f\u3001\u65f6\u95f4\u548c\u5173\u952e\u5b57\u3002",
            )
        }

        if (rules.isEmpty()) {
            item {
                EmptyStateCard("\u5f53\u524d\u8fd8\u6ca1\u6709\u89c4\u5219\uff0c\u53bb\u201c\u65b0\u589e\u914d\u7f6e\u201d\u91cc\u6dfb\u52a0\u7b2c\u4e00\u6761\u89c4\u5219\u3002")
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
    modifier: Modifier = Modifier,
) {
    val expandedGroups = remember { mutableStateListOf<String>() }
    val grouped = notices
        .sortedBy { it.scheduledAtMillis }
        .groupBy { notice -> "${formatNoticeTime(notice.scheduledAtMillis)}|${notice.appName}" }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IntroCard(
                title = "\u5ef6\u540e\u901a\u77e5",
                subtitle = "\u6309\u63d0\u9192\u65f6\u95f4\u548c\u5e94\u7528\u5206\u7ec4\u67e5\u770b\u7b49\u5f85\u88ab Nono \u91cd\u65b0\u53d1\u51fa\u7684\u901a\u77e5\u3002",
            )
        }

        if (grouped.isEmpty()) {
            item {
                EmptyStateCard("\u6682\u65f6\u6ca1\u6709\u5ef6\u540e\u4e2d\u7684\u901a\u77e5\u3002")
            }
        } else {
            grouped.forEach { (groupKey, groupNotices) ->
                val timeLabel = formatNoticeTime(groupNotices.first().scheduledAtMillis)
                val appLabel = groupNotices.first().appName
                item(key = groupKey) {
                    val expanded = groupKey in expandedGroups
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
                                        if (expanded) expandedGroups.remove(groupKey) else expandedGroups.add(groupKey)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(appLabel, fontWeight = FontWeight.SemiBold)
                                    Text("$timeLabel · ${groupNotices.size} \u6761", style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(
                                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = null,
                                )
                            }
                            if (expanded) {
                                Spacer(modifier = Modifier.height(10.dp))
                                groupNotices.forEach { notice ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                    ) {
                                        Text(notice.title.ifBlank { "\u5ef6\u540e\u63d0\u9192" }, fontWeight = FontWeight.SemiBold)
                                        Text(notice.text.take(120), style = MaterialTheme.typography.bodySmall)
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
        item {
            IntroCard(
                title = "\u65b0\u589e\u914d\u7f6e",
                subtitle = "\u652f\u6301\u641c\u7d22\u5e94\u7528\u540d\u548c\u5305\u540d\uff0c\u4e5f\u53ef\u4ee5\u5c55\u5f00\u5168\u90e8\u5df2\u5b89\u88c5\u5e94\u7528\u540e\u76f4\u63a5\u9009\u62e9\u3002",
            )
        }

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
                        Text(if (showSearchBar) "\u6536\u8d77\u641c\u7d22\u680f" else "\u5c55\u5f00\u641c\u7d22\u680f")
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
                            label = { Text("\u641c\u7d22\u5df2\u5b89\u88c5\u5e94\u7528") },
                            placeholder = { Text("\u8f93\u5165\u5e94\u7528\u540d\u6216\u5305\u540d\u7684\u4e00\u90e8\u5206") },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        )

                        if (searchQuery.isNotBlank()) {
                            AppListCard(
                                apps = filteredApps,
                                emptyText = "\u6ca1\u6709\u627e\u5230\u5339\u914d\u5e94\u7528\uff0c\u53ef\u4ee5\u624b\u52a8\u586b\u5199\u5305\u540d\u3002",
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
                            Text(if (showAllApps) "\u6536\u8d77\u5168\u90e8\u5e94\u7528" else "\u5c55\u5f00\u5168\u90e8\u5e94\u7528")
                        }
                        if (showAllApps) {
                            AppListCard(
                                apps = installedApps,
                                emptyText = "\u6ca1\u6709\u8bfb\u53d6\u5230\u5e94\u7528\u5217\u8868\u3002",
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
                        Text("\u89c4\u5219\u542f\u7528", fontWeight = FontWeight.SemiBold)
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
                                    appName = appName.ifBlank { "\u672a\u547d\u540d\u5e94\u7528" },
                                    packageName = packageName.trim(),
                                    note = note.trim(),
                                    enabled = enabled,
                                    mode = mode,
                                    remindAtMinutes = remindAtText.toMinutesOrDefault(9 * 60),
                                    soundMode = soundMode,
                                    startMinutes = startText.toMinutesOrDefault(9 * 60),
                                    endMinutes = endText.toMinutesOrDefault(18 * 60),
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
                        Text("\u4fdd\u5b58\u914d\u7f6e")
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
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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

    var overrideDateText by rememberSaveable { mutableStateOf("") }
    var overrideNoteText by rememberSaveable { mutableStateOf("") }
    var overrideIsWorkday by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            IntroCard(
                title = "\u8bbe\u7f6e",
                subtitle = "\u901a\u77e5\u6743\u9650\u3001\u5bfc\u5165\u5bfc\u51fa\uff0c\u4ee5\u53ca\u5b8c\u6574\u5e94\u7528\u5217\u8868\u7684\u83b7\u53d6\u8bf4\u660e\u90fd\u653e\u5728\u8fd9\u91cc\u3002",
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("\u901a\u77e5\u8bbf\u95ee\u6743\u9650", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (listenerEnabled) "\u5df2\u5f00\u542f\uff0c\u53ef\u6309\u89c4\u5219\u62e6\u622a\u901a\u77e5\u3002" else "\u672a\u5f00\u542f\uff0c\u89c4\u5219\u6682\u65f6\u4e0d\u4f1a\u751f\u6548\u3002",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        AssistChip(onClick = {}, label = { Text(if (listenerEnabled) "\u5df2\u5f00\u542f" else "\u672a\u5f00\u542f") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onOpenSettings, modifier = Modifier.weight(1f)) { Text("\u6253\u5f00\u7cfb\u7edf\u8bbe\u7f6e") }
                        Button(onClick = onRefresh, modifier = Modifier.weight(1f)) { Text("\u5237\u65b0\u72b6\u6001") }
                    }
                    Button(onClick = onRebindListener, modifier = Modifier.fillMaxWidth()) {
                        Text("\u91cd\u8fde\u901a\u77e5\u76d1\u542c\u670d\u52a1")
                    }
                    Button(onClick = onResetListenerComponent, modifier = Modifier.fillMaxWidth()) {
                        Text("\u91cd\u7f6e\u76d1\u542c\u7ec4\u4ef6\u7ed1\u5b9a")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationPermission) {
                        Button(onClick = onRequestPostNotificationPermission, modifier = Modifier.fillMaxWidth()) {
                            Text("\u7533\u8bf7\u901a\u77e5\u53d1\u9001\u6743\u9650")
                        }
                    }
                    Text(
                        text = "\u901a\u77e5\u53d1\u9001\u6743\u9650: " + if (hasPostNotificationPermission) "\u5df2\u5f00\u542f" else "\u672a\u5f00\u542f",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "\u7cbe\u786e\u95f9\u949f: " + if (canScheduleExactAlarms) "\u53ef\u7528" else "\u53d7\u9650\uff08\u5ef6\u540e\u63d0\u9192\u53ef\u80fd\u4e0d\u51c6\u65f6\uff09",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "\u7535\u6c60\u4f18\u5316\u8c41\u514d: " + if (ignoringBatteryOptimizations) "\u5df2\u5f00\u542f" else "\u672a\u5f00\u542f\uff08\u79bb\u5f00 App \u540e\u53ef\u80fd\u88ab\u9650\u5236\uff09",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!ignoringBatteryOptimizations) {
                        Button(onClick = onRequestIgnoreBatteryOptimization, modifier = Modifier.fillMaxWidth()) {
                            Text("\u7533\u8bf7\u5ffd\u7565\u7535\u6c60\u4f18\u5316")
                        }
                    }
                    Text(
                        text = if (listenerDiagnostic.isNotBlank()) {
                            "\u76d1\u542c\u8bca\u65ad: $listenerDiagnostic"
                        } else {
                            "\u76d1\u542c\u8bca\u65ad: \u5c1a\u672a\u6536\u5230\u76d1\u542c\u56de\u8c03\uff08\u5982\u679c\u5df2\u89e6\u53d1\u901a\u77e5\uff0c\u8bf7\u91cd\u8fde\u76d1\u542c\u670d\u52a1\u540e\u518d\u89c2\u5bdf\uff09"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "\u76d1\u542c\u5e95\u5c42\u72b6\u6001: $listenerTechStatus",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms) {
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("\u6253\u5f00\u7cbe\u786e\u95f9\u949f\u8bbe\u7f6e")
                        }
                    }
                    Button(onClick = onOpenPolicySettings, modifier = Modifier.fillMaxWidth()) {
                        Text("\u6253\u5f00\u52ff\u6270\u8bbf\u95ee")
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("中国工作日历", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = holidayConfig.useChinaWorkdayCalendar,
                            onCheckedChange = onCalendarEnabledChange,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("按中国工作日/调休判断", fontWeight = FontWeight.SemiBold)
                            Text(
                                "开启后，规则会结合节假日、调休上班日和你手动设置的请假/加班日期。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = holidayConfig.remoteUrl,
                        onValueChange = onCalendarUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("在线日历地址") },
                        placeholder = { Text("https://timor.tech/api/holiday/year") },
                    )
                    Text(holidayConfig.lastSyncLabel(), style = MaterialTheme.typography.bodySmall)
                    if (calendarStatus.isNotBlank()) {
                        Text(calendarStatus, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D635F))
                    }
                    Button(onClick = onCalendarSync, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("同步到本地")
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("请假与调休", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = overrideDateText,
                        onValueChange = { overrideDateText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("日期") },
                        placeholder = { Text("2026-04-14") },
                    )
                    OutlinedTextField(
                        value = overrideNoteText,
                        onValueChange = { overrideNoteText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("备注") },
                        placeholder = { Text("例如：年假 / 周日补班") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !overrideIsWorkday,
                            onClick = { overrideIsWorkday = false },
                            label = { Text("请假/休息") },
                        )
                        FilterChip(
                            selected = overrideIsWorkday,
                            onClick = { overrideIsWorkday = true },
                            label = { Text("调休加班") },
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
                        Text("保存日期覆盖")
                    }

                    if (manualOverrides.isEmpty()) {
                        Text("暂时还没有手动日期覆盖。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        manualOverrides.forEach { override ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${override.date} · ${if (override.isWorkday) "调休加班" else "请假/休息"}",
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (override.note.isNotBlank()) {
                                        Text(override.note, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = { onDeleteOverride(override.date) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "删除日期覆盖")
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
                    Text("\u914d\u7f6e\u7ba1\u7406", fontWeight = FontWeight.SemiBold)
                    Text("\u5bfc\u51fa\u4f1a\u751f\u6210\u4e00\u4efd JSON \u6587\u4ef6\uff0c\u5bfc\u5165\u65f6\u4f1a\u8986\u76d6\u5f53\u524d\u672c\u5730\u89c4\u5219\u3002", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("\u5bfc\u51fa\u914d\u7f6e")
                        }
                        Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("\u5bfc\u5165\u914d\u7f6e")
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("\u5e94\u7528\u5217\u8868\u6743\u9650\u9650\u5236", fontWeight = FontWeight.SemiBold)
                    Text("1. \u5df2\u7ecf\u542f\u7528 QUERY_ALL_PACKAGES \u6765\u83b7\u53d6\u5b8c\u6574\u5df2\u5b89\u88c5\u5e94\u7528\u5217\u8868\u3002")
                    Text("2. \u5f53\u524d Android \u5bf9\u7b2c\u4e09\u65b9 App \u7684\u66f4\u9ad8\u5e94\u7528\u5217\u8868\u6743\u9650\u57fa\u672c\u6ca1\u6709\u66f4\u4e0a\u4e00\u5c42\u3002")
                    Text("3. \u5982\u679c\u4ecd\u6709\u7f3a\u5931\uff0c\u5f88\u53ef\u80fd\u662f\u5de5\u4f5c Profile\u3001\u53cc\u5f00\u7a7a\u95f4\u6216 OEM \u79c1\u6709\u5bb9\u5668\u91cc\u7684\u5e94\u7528\uff0c\u666e\u901a App \u65e0\u6cd5\u5168\u90e8\u679a\u4e3e\u3002")
                }
            }
        }
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
            Spacer(modifier = Modifier.height(6.dp))
            Text(subtitle, color = Color(0xFF5D635F), style = MaterialTheme.typography.bodyMedium)
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
            Text(emptyText, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
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
                text = if (app.isSystemApp) "${app.packageName} \u00b7 \u7cfb\u7edf\u5e94\u7528" else app.packageName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("\u9009\u62e9", color = Color(0xFF57675E), fontWeight = FontWeight.SemiBold)
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
    selectedDays: Set<DayOfWeek>,
    onSelectedDaysChange: (Set<DayOfWeek>) -> Unit,
    blockAll: Boolean,
    onBlockAllChange: (Boolean) -> Unit,
    targetsText: String,
    onTargetsTextChange: (String) -> Unit,
) {
    OutlinedTextField(value = appName, onValueChange = onAppNameChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("\u5e94\u7528\u540d\u79f0") })
    OutlinedTextField(value = packageName, onValueChange = onPackageNameChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("\u5305\u540d") }, placeholder = { Text("\u4f8b\u5982 com.tencent.mm") })
    OutlinedTextField(value = note, onValueChange = onNoteChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("\u5907\u6ce8") }, placeholder = { Text("\u4f8b\u5982\uff1a\u8001\u677f\u767d\u540d\u5355") })

    ModeSelector(selectedMode = mode, onModeChange = onModeChange)
    RuleDayModeSelector(selectedMode = dayMode, onModeChange = onDayModeChange)
    if (mode == RuleMode.DELAY) {
        OutlinedTextField(
            value = remindAtText,
            onValueChange = onRemindAtTextChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("\u63d0\u9192\u65f6\u95f4") },
            placeholder = { Text("09:00") },
            supportingText = { Text("\u5339\u914d\u5230\u89c4\u5219\u540e\uff0cNono \u4f1a\u5728\u4e0b\u4e00\u4e2a\u7b26\u5408\u6761\u4ef6\u7684 HH:mm \u65f6\u95f4\u53d1\u51fa\u672c\u5730\u63d0\u9192\u3002") },
        )
    }
    SoundModeSelector(selectedMode = soundMode, onModeChange = onSoundModeChange)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = startText, onValueChange = onStartTextChange, modifier = Modifier.weight(1f), singleLine = true, label = { Text("\u5f00\u59cb\u65f6\u95f4") }, placeholder = { Text("09:00") })
        OutlinedTextField(value = endText, onValueChange = onEndTextChange, modifier = Modifier.weight(1f), singleLine = true, label = { Text("\u7ed3\u675f\u65f6\u95f4") }, placeholder = { Text("18:00") })
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("\u751f\u6548\u65e5\u671f", fontWeight = FontWeight.SemiBold)
        val dayChips = listOf<DayOfWeek?>(null) + weekDays
        dayChips.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { day ->
                    if (day == null) {
                        FilterChip(
                            selected = selectedDays.size == weekDays.size,
                            onClick = { onSelectedDaysChange(weekDays.toSet()) },
                            label = { Text("\u5168\u90e8") },
                        )
                    } else {
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                onSelectedDaysChange(
                                    if (day in selectedDays) selectedDays - day else selectedDays + day,
                                )
                            },
                            label = { Text(day.cnLabel) },
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
            Text("\u6574\u5305\u5339\u914d", fontWeight = FontWeight.SemiBold)
            Text("\u5f00\u542f\u540e\u4f1a\u4f7f\u7528 * \u5339\u914d\u8be5\u5e94\u7528\u7684\u6240\u6709\u901a\u77e5\u3002", style = MaterialTheme.typography.bodySmall)
        }
    }

    OutlinedTextField(
        value = targetsText,
        onValueChange = onTargetsTextChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = 4,
        label = { Text("\u5173\u952e\u5b57") },
        placeholder = { Text("\u6bcf\u884c\u4e00\u4e2a\u8054\u7cfb\u4eba\u540d\u3001\u7fa4\u540d\u6216\u9891\u9053\u540d") },
        supportingText = {
            Text(
                if (blockAll) {
                    "\u5f53\u524d\u5c06\u5ffd\u7565\u5173\u952e\u5b57\uff0c\u76f4\u63a5\u5339\u914d\u8be5\u5e94\u7528\u5168\u90e8\u901a\u77e5\u3002"
                } else {
                    "\u7559\u7a7a\u8868\u793a\u5339\u914d\u8be5\u5e94\u7528\u5168\u90e8\u901a\u77e5\uff1b\u4f8b\u5982\uff1a\u8001\u677f\u3001\u9879\u76ee\u7fa4\u3001\u503c\u73ed\u901a\u77e5"
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
        Text("\u901a\u77e5\u6a21\u5f0f", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedMode == RuleMode.BLOCK, onClick = { onModeChange(RuleMode.BLOCK) }, label = { Text("\u5c4f\u853d\u901a\u77e5") })
            FilterChip(selected = selectedMode == RuleMode.ALLOW, onClick = { onModeChange(RuleMode.ALLOW) }, label = { Text("\u5141\u8bb8\u901a\u77e5") })
            FilterChip(selected = selectedMode == RuleMode.DELAY, onClick = { onModeChange(RuleMode.DELAY) }, label = { Text("\u5ef6\u540e\u901a\u77e5") })
        }
    }
}

@Composable
private fun RuleDayModeSelector(
    selectedMode: RuleDayMode,
    onModeChange: (RuleDayMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("\u89c4\u5219\u65e5\u7c7b\u578b", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedMode == RuleDayMode.WORKDAY, onClick = { onModeChange(RuleDayMode.WORKDAY) }, label = { Text("\u5de5\u4f5c") })
            FilterChip(selected = selectedMode == RuleDayMode.RESTDAY, onClick = { onModeChange(RuleDayMode.RESTDAY) }, label = { Text("\u4f11\u606f") })
            FilterChip(selected = selectedMode == RuleDayMode.ALL, onClick = { onModeChange(RuleDayMode.ALL) }, label = { Text("\u5168\u90e8") })
        }
    }
}
@Composable
private fun SoundModeSelector(
    selectedMode: DeviceSoundMode,
    onModeChange: (DeviceSoundMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("\u7cfb\u7edf\u54cd\u94c3\u6a21\u5f0f", fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedMode == DeviceSoundMode.KEEP,
                onClick = { onModeChange(DeviceSoundMode.KEEP) },
                label = { Text("\u4fdd\u6301") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = selectedMode == DeviceSoundMode.RING,
                onClick = { onModeChange(DeviceSoundMode.RING) },
                label = { Text("\u54cd\u94c3") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = selectedMode == DeviceSoundMode.VIBRATE,
                onClick = { onModeChange(DeviceSoundMode.VIBRATE) },
                label = { Text("\u9707\u52a8") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = selectedMode == DeviceSoundMode.SILENT,
                onClick = { onModeChange(DeviceSoundMode.SILENT) },
                label = { Text("\u9759\u97f3") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)), shape = RoundedCornerShape(22.dp)) {
        Text(message, modifier = Modifier.padding(18.dp))
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
                appName = appName.ifBlank { "\u672a\u547d\u540d\u5e94\u7528" },
                packageName = packageName.trim(),
                note = note.trim(),
                enabled = enabled,
                mode = mode,
                remindAtMinutes = remindAtText.toMinutesOrDefault(rule.remindAtMinutes),
                soundMode = soundMode,
                startMinutes = startText.toMinutesOrDefault(rule.startMinutes),
                endMinutes = endText.toMinutesOrDefault(rule.endMinutes),
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
                    Text(appName.ifBlank { "\u65b0\u89c4\u5219" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = note.ifBlank { packageName.ifBlank { "\u8bf7\u586b\u5199\u5305\u540d" } },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            "${rule.dayModeLabel()} · " + when (mode) {
                                RuleMode.BLOCK -> "\u5c4f\u853d"
                                RuleMode.ALLOW -> "\u5141\u8bb8"
                                RuleMode.DELAY -> "\u5ef6\u540e"
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
                    Icon(Icons.Outlined.Delete, contentDescription = "\u5220\u9664\u89c4\u5219")
                }
            }

            val previewRule = rule.copy(
                appName = appName.ifBlank { "\u672a\u547d\u540d\u5e94\u7528" },
                packageName = packageName.trim(),
                note = note.trim(),
                enabled = enabled,
                mode = mode,
                remindAtMinutes = remindAtText.toMinutesOrDefault(rule.remindAtMinutes),
                soundMode = soundMode,
                startMinutes = startText.toMinutesOrDefault(rule.startMinutes),
                endMinutes = endText.toMinutesOrDefault(rule.endMinutes),
                dayMode = dayMode,
                activeDays = selectedDays,
                targets = resolvedTargets(),
            )
            val previewTargets = previewRule.targets.joinToString(separator = " / ")
            val previewMode = when (previewRule.mode) {
                RuleMode.BLOCK -> "\u5c4f\u853d"
                RuleMode.ALLOW -> "\u5141\u8bb8"
                RuleMode.DELAY -> "\u63d0\u9192 ${previewRule.remindAtMinutes.toHourMinute()}"
            }
            val now = LocalDateTime.now()
            val context = androidx.compose.ui.platform.LocalContext.current
            val holidayCalendarStore = remember { HolidayCalendarStore(context) }
            val isWorkingDate = holidayCalendarStore.isWorkingDate(now.toLocalDate())
            val nowStatus = previewRule.currentTimeStatusLabel(now = now, isWorkingDate = isWorkingDate)
            Text(
                text = "\u9884\u89c8: ${previewRule.dayModeLabel()} | $previewMode | ${previewRule.timeRangeLabel()} | ${previewRule.dayLabel()} | $previewTargets",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "\u5f53\u524d\u65f6\u95f4\u72b6\u6001: $nowStatus",
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

    if (!isActiveNow) return "\u4e0d\u751f\u6548"

    return when (mode) {
        RuleMode.BLOCK -> "\u5c4f\u853d"
        RuleMode.ALLOW -> "\u5141\u8bb8"
        RuleMode.DELAY -> "\u5ef6\u540e"
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
        DayOfWeek.MONDAY -> "\u5468\u4e00"
        DayOfWeek.TUESDAY -> "\u5468\u4e8c"
        DayOfWeek.WEDNESDAY -> "\u5468\u4e09"
        DayOfWeek.THURSDAY -> "\u5468\u56db"
        DayOfWeek.FRIDAY -> "\u5468\u4e94"
        DayOfWeek.SATURDAY -> "\u5468\u516d"
        DayOfWeek.SUNDAY -> "\u5468\u65e5"
    }


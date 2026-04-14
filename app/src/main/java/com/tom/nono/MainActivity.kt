package com.tom.nono

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NonoApp() }
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

    LaunchedEffect(Unit) {
        listenerEnabled = isNotificationListenerEnabled(context)
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
                        onMoveUp = { rule ->
                            val index = rules.indexOfFirst { it.id == rule.id }
                            if (index > 0) {
                                val updated = rules.toMutableList().apply {
                                    add(index - 1, removeAt(index))
                                }
                                rules = updated
                                store.saveRules(updated)
                            }
                        },
                        onMoveDown = { rule ->
                            val index = rules.indexOfFirst { it.id == rule.id }
                            if (index in 0 until rules.lastIndex) {
                                val updated = rules.toMutableList().apply {
                                    add(index + 1, removeAt(index))
                                }
                                rules = updated
                                store.saveRules(updated)
                            }
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
    onMoveUp: (NotificationRule) -> Unit,
    onMoveDown: (NotificationRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandedRuleIds = remember { mutableStateListOf<String>() }
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
            items(rules, key = { it.id }) { rule ->
                val expanded = rule.id in expandedRuleIds
                RuleEditorCard(
                    rule = rule,
                    appInfo = installedApps.firstOrNull { it.packageName == rule.packageName },
                    expanded = expanded,
                    onToggleExpanded = {
                        if (expanded) expandedRuleIds.remove(rule.id) else expandedRuleIds.add(rule.id)
                    },
                    canMoveUp = rules.indexOfFirst { it.id == rule.id } > 0,
                    canMoveDown = rules.indexOfFirst { it.id == rule.id } < rules.lastIndex,
                    onMoveUp = { onMoveUp(rule) },
                    onMoveDown = { onMoveDown(rule) },
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
                            },
                        )
                    } else {
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

                    OutlinedTextField(value = appName, onValueChange = { appName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("\u5e94\u7528\u540d\u79f0") })
                    OutlinedTextField(value = packageName, onValueChange = { packageName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("\u5305\u540d") }, placeholder = { Text("\u4f8b\u5982 com.tencent.mm") })
                    OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("\u5907\u6ce8") }, placeholder = { Text("\u4f8b\u5982\uff1a\u8001\u677f\u767d\u540d\u5355") })

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("\u89c4\u5219\u542f\u7528", fontWeight = FontWeight.SemiBold)
                    }

                    ModeSelector(selectedMode = mode, onModeChange = { mode = it })
                    RuleDayModeSelector(selectedMode = dayMode, onModeChange = { dayMode = it })
                    if (mode == RuleMode.DELAY) {
                        OutlinedTextField(
                            value = remindAtText,
                            onValueChange = { remindAtText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("\u63d0\u9192\u65f6\u95f4") },
                            placeholder = { Text("09:00") },
                            supportingText = { Text("\u5339\u914d\u5230\u89c4\u5219\u540e\uff0cNono \u4f1a\u5728\u4e0b\u4e00\u4e2a\u7b26\u5408\u6761\u4ef6\u7684 HH:mm \u65f6\u95f4\u53d1\u51fa\u672c\u5730\u63d0\u9192\u3002") },
                        )
                    }
                    SoundModeSelector(selectedMode = soundMode, onModeChange = { soundMode = it })

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = startText, onValueChange = { startText = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("\u5f00\u59cb\u65f6\u95f4") }, placeholder = { Text("09:00") })
                        OutlinedTextField(value = endText, onValueChange = { endText = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("\u7ed3\u675f\u65f6\u95f4") }, placeholder = { Text("18:00") })
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
                                            onClick = { selectedDays = weekDays.toSet() },
                                            label = { Text("\u5168\u90e8") },
                                        )
                                    } else {
                                        FilterChip(
                                            selected = day in selectedDays,
                                            onClick = {
                                                selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                                            },
                                            label = { Text(day.cnLabel) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = blockAll, onCheckedChange = { blockAll = it })
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("\u6574\u5305\u5339\u914d", fontWeight = FontWeight.SemiBold)
                            Text("\u5f00\u542f\u540e\u4f1a\u4f7f\u7528 * \u5339\u914d\u8be5\u5e94\u7528\u7684\u6240\u6709\u901a\u77e5\u3002", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    OutlinedTextField(
                        value = targetsText,
                        onValueChange = { targetsText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        label = { Text("\u5173\u952e\u5b57") },
                        placeholder = { Text("\u6bcf\u884c\u4e00\u4e2a\u8054\u7cfb\u4eba\u540d\u3001\u7fa4\u540d\u6216\u9891\u9053\u540d") },
                        supportingText = { Text(if (blockAll) "\u5f53\u524d\u5c06\u5ffd\u7565\u5173\u952e\u5b57\uff0c\u76f4\u63a5\u5339\u914d\u8be5\u5e94\u7528\u5168\u90e8\u901a\u77e5\u3002" else "\u4f8b\u5982\uff1a\u8001\u677f\u3001\u9879\u76ee\u7fa4\u3001\u503c\u73ed\u901a\u77e5") },
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
        Text("规则日类型", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedMode == RuleDayMode.WORKDAY, onClick = { onModeChange(RuleDayMode.WORKDAY) }, label = { Text("工作") })
            FilterChip(selected = selectedMode == RuleDayMode.RESTDAY, onClick = { onModeChange(RuleDayMode.RESTDAY) }, label = { Text("休息") })
            FilterChip(selected = selectedMode == RuleDayMode.ALL, onClick = { onModeChange(RuleDayMode.ALL) }, label = { Text("全部") })
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
    onToggleExpanded: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
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
    var selectedDays by remember(rule.id) { mutableStateOf(rule.activeDays) }

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
                targets = splitTargets(targetsText),
            ),
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7F4)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.animateContentSize(),
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
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移规则")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移规则")
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        persist()
                    },
                )
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "\u6536\u8d77\u89c4\u5219" else "\u5c55\u5f00\u89c4\u5219",
                    )
                }
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
                targets = splitTargets(targetsText),
            )
            val previewTargets = previewRule.targets.joinToString(separator = " / ")
            val previewMode = when (previewRule.mode) {
                RuleMode.BLOCK -> "\u5c4f\u853d"
                RuleMode.ALLOW -> "\u5141\u8bb8"
                RuleMode.DELAY -> "\u63d0\u9192 ${previewRule.remindAtMinutes.toHourMinute()}"
            }
            Text(
                text = "\u9884\u89c8: ${previewRule.dayModeLabel()} | $previewMode | ${previewRule.timeRangeLabel()} | ${previewRule.dayLabel()} | $previewTargets",
                style = MaterialTheme.typography.bodySmall,
            )

            if (expanded) {
                OutlinedTextField(value = appName, onValueChange = { appName = it; persist() }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("\u5e94\u7528\u540d\u79f0") })
                OutlinedTextField(value = packageName, onValueChange = { packageName = it; persist() }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("\u5305\u540d") })
                OutlinedTextField(value = note, onValueChange = { note = it; persist() }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("\u5907\u6ce8") })
                ModeSelector(selectedMode = mode, onModeChange = { mode = it; persist() })
                if (mode == RuleMode.DELAY) {
                    OutlinedTextField(
                        value = remindAtText,
                        onValueChange = { remindAtText = it; persist() },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("\u63d0\u9192\u65f6\u95f4") },
                    )
                }
                SoundModeSelector(selectedMode = soundMode, onModeChange = { soundMode = it; persist() })

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = startText, onValueChange = { startText = it; persist() }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("\u5f00\u59cb") })
                    OutlinedTextField(value = endText, onValueChange = { endText = it; persist() }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("\u7ed3\u675f") })
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
                                        onClick = {
                                            selectedDays = weekDays.toSet()
                                            persist()
                                        },
                                        label = { Text("\u5168\u90e8") },
                                    )
                                } else {
                                    FilterChip(
                                        selected = day in selectedDays,
                                        onClick = {
                                            selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                                            persist()
                                        },
                                        label = { Text(day.cnLabel) },
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = targetsText,
                    onValueChange = { targetsText = it; persist() },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    label = { Text("\u5173\u952e\u5b57") },
                    placeholder = { Text("\u6bcf\u884c\u4e00\u4e2a\u8054\u7cfb\u4eba\u3001\u7fa4\u540d\u6216\u4f7f\u7528 * \u5339\u914d\u6574\u5305") },
                    supportingText = { Text("\u4f7f\u7528 * \u4f5c\u4e3a\u552f\u4e00\u5173\u952e\u5b57\u65f6\uff0c\u8868\u793a\u5339\u914d\u8be5\u5e94\u7528\u6240\u6709\u901a\u77e5\u3002") },
                )

                HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.6f))
            }
        }
    }
}

private fun loadInstalledApps(context: Context): List<InstalledAppInfo> {
    val packageManager = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PackageManager.ApplicationInfoFlags.of(
            (
                PackageManager.MATCH_DISABLED_COMPONENTS.toLong() or
                    PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS.toLong() or
                    PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()
                ),
        )
    } else {
        null
    }
    val installedApplications = if (flags != null) {
        packageManager.getInstalledApplications(flags)
    } else {
        @Suppress("DEPRECATION")
        packageManager.getInstalledApplications(
            PackageManager.MATCH_DISABLED_COMPONENTS or
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS or
                PackageManager.MATCH_UNINSTALLED_PACKAGES,
        )
    }
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

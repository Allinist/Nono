package com.tom.nono

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PostAdd
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tom.nono.data.NotificationRule
import com.tom.nono.data.RuleStore
import java.time.DayOfWeek

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NonoApp()
        }
    }
}

private enum class NonoTab(val title: String) {
    Rules("规则"),
    Add("新增配置"),
    Settings("设置"),
}

private data class InstalledAppInfo(
    val label: String,
    val packageName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NonoApp() {
    val context = LocalContext.current
    val store = remember { RuleStore(context) }
    var rules by remember { mutableStateOf(store.loadRules()) }
    var selectedTab by rememberSaveable { mutableStateOf(NonoTab.Rules) }
    var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    val installedApps = remember { loadInstalledApps(context) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(store.serializeRules(rules))
            }
        }.onSuccess {
            toast(context, "配置已导出")
        }.onFailure {
            toast(context, "导出失败")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (imported != null && store.importRules(imported)) {
            rules = store.loadRules()
            toast(context, "配置已导入")
        } else {
            toast(context, "导入失败，文件格式不正确")
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
                        colors = listOf(Color(0xFFF4F2EE), Color(0xFFEFEEE9), Color(0xFFF7F6F3)),
                    ),
                ),
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    NavigationBar(containerColor = Color(0xFFF6F5F2)) {
                        NonoTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            NonoTab.Rules -> Icons.Outlined.Rule
                                            NonoTab.Add -> Icons.Outlined.PostAdd
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
                        onRuleChange = { updatedRule ->
                            val updated = rules.map { if (it.id == updatedRule.id) updatedRule else it }
                            rules = updated
                            store.saveRules(updated)
                        },
                        onDelete = { rule ->
                            val updated = rules.filterNot { it.id == rule.id }
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
                            selectedTab = NonoTab.Rules
                            toast(context, "已新增规则")
                        },
                        modifier = Modifier.padding(innerPadding),
                    )

                    NonoTab.Settings -> SettingsTab(
                        listenerEnabled = listenerEnabled,
                        onRefresh = { listenerEnabled = isNotificationListenerEnabled(context) },
                        onOpenSettings = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onExport = { exportLauncher.launch("nono-rules.json") },
                        onImport = { importLauncher.launch("application/json") },
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
    onRuleChange: (NotificationRule) -> Unit,
    onDelete: (NotificationRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroCard(
                title = "规则总览",
                subtitle = "在这里编辑已有规则，适合微调联系人、群名、工作时段和整包静音策略。",
            )
        }

        if (rules.isEmpty()) {
            item {
                EmptyStateCard("当前还没有规则，去“新增配置”里添加第一条规则。")
            }
        } else {
            items(rules, key = { it.id }) { rule ->
                RuleEditorCard(
                    rule = rule,
                    onRuleChange = onRuleChange,
                    onDelete = { onDelete(rule) },
                )
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
    var startText by rememberSaveable { mutableStateOf("09:00") }
    var endText by rememberSaveable { mutableStateOf("18:00") }
    var targetsText by rememberSaveable { mutableStateOf("") }
    var blockAll by rememberSaveable { mutableStateOf(false) }
    var selectedDays by remember { mutableStateOf(defaultActiveDays()) }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val needle = searchQuery.trim().lowercase()
            installedApps.filter {
                it.label.lowercase().contains(needle) || it.packageName.lowercase().contains(needle)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroCard(
                title = "新增配置",
                subtitle = "支持搜索本机已安装应用，点击即可自动填充应用名和包名。应用这类办公软件建议开启整包屏蔽。",
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.isNotBlank()) showAllApps = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("搜索已安装应用") },
                        placeholder = { Text("输入应用名或包名，例如 微信 / 应用") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    )

                    if (searchQuery.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFFF4F5F3))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            filteredApps.take(24).forEach { app ->
                                AppSuggestionRow(
                                    app = app,
                                    onClick = {
                                        appName = app.label
                                        packageName = app.packageName
                                        if (app.label.contains("应用")) {
                                            blockAll = true
                                        }
                                    },
                                )
                            }
                            if (filteredApps.isEmpty()) {
                                Text(
                                    "没有找到匹配应用，可以手动填写包名。",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showAllApps = !showAllApps },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (showAllApps) "收起全部应用" else "展开全部应用")
                            }
                            if (showAllApps) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFFF4F5F3))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    installedApps.forEach { app ->
                                        AppSuggestionRow(
                                            app = app,
                                            onClick = {
                                                appName = app.label
                                                packageName = app.packageName
                                                if (app.label.contains("应用")) {
                                                    blockAll = true
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("应用名称") },
                    )

                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("包名") },
                        placeholder = { Text("例如 com.tencent.mm") },
                        supportingText = { Text("自动获取方式：在上方搜索已安装应用并点击即可填充。") },
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = startText,
                            onValueChange = { startText = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("开始时间") },
                            placeholder = { Text("09:00") },
                        )
                        OutlinedTextField(
                            value = endText,
                            onValueChange = { endText = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("结束时间") },
                            placeholder = { Text("18:00") },
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("生效日期", fontWeight = FontWeight.SemiBold)
                        weekDays.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { day ->
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

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = blockAll, onCheckedChange = { blockAll = it })
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("整包屏蔽", fontWeight = FontWeight.SemiBold)
                            Text(
                                "适合应用这类办公软件。开启后会使用 * 匹配该应用的所有通知。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    OutlinedTextField(
                        value = targetsText,
                        onValueChange = { targetsText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        label = { Text("关键字") },
                        placeholder = { Text("每行一个联系人名、群名或频道名") },
                        supportingText = {
                            Text(if (blockAll) "当前将忽略关键字，直接屏蔽该应用全部通知。" else "例如：老板、项目群、值班通知")
                        },
                    )

                    Button(
                        onClick = {
                            onAdd(
                                NotificationRule(
                                    appName = appName.ifBlank { "未命名应用" },
                                    packageName = packageName.trim(),
                                    startMinutes = startText.toMinutesOrDefault(9 * 60),
                                    endMinutes = endText.toMinutesOrDefault(18 * 60),
                                    activeDays = selectedDays,
                                    targets = if (blockAll) listOf("*") else splitTargets(targetsText),
                                ),
                            )
                            searchQuery = ""
                            appName = ""
                            packageName = ""
                            targetsText = ""
                            blockAll = false
                            selectedDays = defaultActiveDays()
                        },
                        enabled = packageName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存配置")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    listenerEnabled: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroCard(
                title = "设置",
                subtitle = "通知权限、配置导入导出，以及应用搜索和包名自动获取的方法都放在这里。",
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("通知访问权限", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (listenerEnabled) "已开启，可按规则拦截通知。" else "未开启，规则暂时不会生效。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(if (listenerEnabled) "已开启" else "未开启") },
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                            Text("打开系统设置")
                        }
                        Button(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                            Text("刷新状态")
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("配置管理", fontWeight = FontWeight.SemiBold)
                    Text("导出会生成一份 JSON 文件，导入时会覆盖当前本地规则。", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("导出配置")
                        }
                        Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("导入配置")
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("包名自动获取方法", fontWeight = FontWeight.SemiBold)
                    Text("1. 进入“新增配置”标签页。")
                    Text("2. 在搜索框输入应用名，例如“微信”或“应用”。")
                    Text("3. 点击搜索结果，应用名和包名会自动填入表单。")
                    Text("4. 如果搜索不到，也可以手动填写包名。")
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    title: String,
    subtitle: String,
) {
    Surface(
        color = Color(0xFFF7F6F2),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF556B63)),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = Color(0xFF202522), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(subtitle, color = Color(0xFF5C645F), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Text(message, modifier = Modifier.padding(18.dp))
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
        Icon(Icons.Outlined.PostAdd, contentDescription = null, tint = Color(0xFF1F6E66))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("选择", color = Color(0xFF1F6E66), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RuleEditorCard(
    rule: NotificationRule,
    onRuleChange: (NotificationRule) -> Unit,
    onDelete: () -> Unit,
) {
    var appName by remember(rule.id) { mutableStateOf(rule.appName) }
    var packageName by remember(rule.id) { mutableStateOf(rule.packageName) }
    var startText by remember(rule.id) { mutableStateOf(rule.startMinutes.toHourMinute()) }
    var endText by remember(rule.id) { mutableStateOf(rule.endMinutes.toHourMinute()) }
    var targetsText by remember(rule.id) { mutableStateOf(if (rule.targets == listOf("*")) "*" else rule.targets.joinToString("\n")) }
    var enabled by remember(rule.id) { mutableStateOf(rule.enabled) }
    var selectedDays by remember(rule.id) { mutableStateOf(rule.activeDays) }

    fun persist() {
        onRuleChange(
            rule.copy(
                appName = appName.ifBlank { "未命名应用" },
                packageName = packageName.trim(),
                enabled = enabled,
                startMinutes = startText.toMinutesOrDefault(rule.startMinutes),
                endMinutes = endText.toMinutesOrDefault(rule.endMinutes),
                activeDays = selectedDays,
                targets = splitTargets(targetsText),
            ),
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(appName.ifBlank { "新规则" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(packageName.ifBlank { "请填写包名" }, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        persist()
                    },
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除规则")
                }
            }

            OutlinedTextField(
                value = appName,
                onValueChange = {
                    appName = it
                    persist()
                },
                label = { Text("应用名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = packageName,
                onValueChange = {
                    packageName = it
                    persist()
                },
                label = { Text("包名") },
                placeholder = { Text("例如 com.tencent.mm") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = {
                        startText = it
                        persist()
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("开始") },
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = {
                        endText = it
                        persist()
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("结束") },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("生效日期", fontWeight = FontWeight.SemiBold)
                weekDays.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { day ->
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

            OutlinedTextField(
                value = targetsText,
                onValueChange = {
                    targetsText = it
                    persist()
                },
                label = { Text("关键字") },
                placeholder = { Text("每行一个联系人、群名或使用 * 屏蔽整包") },
                supportingText = { Text("使用 * 作为唯一关键字时，表示非工作时间屏蔽该应用所有通知。") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )

            HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.6f))
            val previewRule = rule.copy(
                appName = appName.ifBlank { "未命名应用" },
                packageName = packageName.trim(),
                enabled = enabled,
                startMinutes = startText.toMinutesOrDefault(rule.startMinutes),
                endMinutes = endText.toMinutesOrDefault(rule.endMinutes),
                activeDays = selectedDays,
                targets = splitTargets(targetsText),
            )
            val previewTargets = previewRule.targets.joinToString(separator = " / ")
            Text(
                text = "预览: ${previewRule.timeRangeLabel()} | ${previewRule.dayLabel()} | $previewTargets",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun loadInstalledApps(context: Context): List<InstalledAppInfo> {
    val packageManager = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(launcherIntent, 0)
        .asSequence()
        .map {
            InstalledAppInfo(
                label = it.loadLabel(packageManager).toString(),
                packageName = it.activityInfo.packageName,
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
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
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }

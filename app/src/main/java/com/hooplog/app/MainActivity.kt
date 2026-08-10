package com.hooplog.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HoopLogApp() }
    }
}

data class UiState(
    val today: String = LocalDate.now().toString(),
    val entries: List<DailyEntry> = emptyList(),
    val items: List<TrainingItem> = emptyList(),
    val summaries: List<DaySummary> = emptyList(),
    val selectedHistory: String? = null,
    val historyEntries: List<DailyEntry> = emptyList(),
    val updateSettings: UpdateSettings = UpdateSettings(),
    val updateInfo: UpdateInfo? = null,
    val message: String? = null
)

class HoopLogViewModel(application: Application) : AndroidViewModel(application) {
    private val trainingStore = TrainingStore(application)
    private val settingsStore = SettingsStore(application)
    private val updateChecker = UpdateChecker()

    var state by mutableStateOf(UiState())
        private set

    init {
        reload()
    }

    fun reload() {
        val today = LocalDate.now().toString()
        trainingStore.ensureEntriesFor(today)
        val selected = state.selectedHistory
        state = state.copy(
            today = today,
            entries = trainingStore.entriesFor(today),
            items = trainingStore.activeItems(),
            summaries = trainingStore.summaries(),
            historyEntries = selected?.let { trainingStore.entriesFor(it) } ?: emptyList(),
            updateSettings = settingsStore.loadUpdateSettings()
        )
    }

    fun toggle(entry: DailyEntry, completed: Boolean) {
        trainingStore.toggleEntry(entry.id, completed)
        reload()
    }

    fun saveItem(id: Long?, title: String, durationSeconds: Int, sets: Int, restSeconds: Int) {
        if (title.isBlank()) {
            state = state.copy(message = "請輸入訓練項目")
            return
        }
        trainingStore.saveItem(id, title, durationSeconds, sets, restSeconds)
        reload()
    }

    fun archiveItem(id: Long) {
        trainingStore.archiveItem(id)
        reload()
    }

    fun selectHistory(date: String) {
        state = state.copy(selectedHistory = date, historyEntries = trainingStore.entriesFor(date))
    }

    fun saveUpdateSettings(owner: String, repo: String) {
        settingsStore.saveUpdateSettings(UpdateSettings(owner, repo))
        reload()
    }

    suspend fun checkUpdate() {
        val settings = state.updateSettings
        if (settings.owner.isBlank() || settings.repo.isBlank()) {
            state = state.copy(message = "請先填入 GitHub owner 與 repo")
            return
        }
        runCatching {
            withContext(Dispatchers.IO) {
                updateChecker.check(settings.owner, settings.repo, BuildConfig.VERSION_NAME)
            }
        }.onSuccess {
            state = state.copy(updateInfo = it, message = null)
        }.onFailure {
            state = state.copy(message = "無法取得 GitHub Release")
        }
    }
}

enum class Screen { Today, History, Settings }

@Composable
fun HoopLogApp(vm: HoopLogViewModel = viewModel()) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = androidx.compose.ui.graphics.Color(0xFF111111),
            background = androidx.compose.ui.graphics.Color(0xFFFAFAFA),
            surface = androidx.compose.ui.graphics.Color.White
        )
    ) {
        var screen by remember { mutableStateOf(Screen.Today) }
        Scaffold(
            topBar = { AppBar(screen) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == Screen.Today,
                        onClick = { screen = Screen.Today },
                        icon = { Icon(Icons.Outlined.CheckCircle, null) },
                        label = { Text("今日") }
                    )
                    NavigationBarItem(
                        selected = screen == Screen.History,
                        onClick = { screen = Screen.History },
                        icon = { Icon(Icons.Outlined.History, null) },
                        label = { Text("回顧") }
                    )
                    NavigationBarItem(
                        selected = screen == Screen.Settings,
                        onClick = { screen = Screen.Settings },
                        icon = { Icon(Icons.Outlined.Settings, null) },
                        label = { Text("設定") }
                    )
                }
            },
            floatingActionButton = {
                if (screen == Screen.Settings) {
                    var editing by remember { mutableStateOf<TrainingItem?>(null) }
                    var showDialog by remember { mutableStateOf(false) }
                    FloatingActionButton(onClick = {
                        editing = null
                        showDialog = true
                    }) {
                        Icon(Icons.Outlined.Add, contentDescription = "新增")
                    }
                    if (showDialog) {
                        ItemDialog(
                            item = editing,
                            onDismiss = { showDialog = false },
                            onSave = { title, duration, sets, rest ->
                                vm.saveItem(editing?.id, title, duration, sets, rest)
                                showDialog = false
                            }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                vm.state.message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                when (screen) {
                    Screen.Today -> TodayScreen(vm.state, vm::toggle, vm::saveItem, vm::archiveItem)
                    Screen.History -> HistoryScreen(vm.state, vm::selectHistory)
                    Screen.Settings -> SettingsScreen(vm)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(screen: Screen) {
    TopAppBar(
        title = {
            Text(
                when (screen) {
                    Screen.Today -> "HoopLog"
                    Screen.History -> "訓練回顧"
                    Screen.Settings -> "項目與更新"
                }
            )
        }
    )
}

@Composable
private fun TodayScreen(
    state: UiState,
    onToggle: (DailyEntry, Boolean) -> Unit,
    onSaveItem: (Long?, String, Int, Int, Int) -> Unit,
    onArchiveItem: (Long) -> Unit
) {
    val entries = state.entries
    val completed = entries.count { it.completed }
    var timerEntry by remember { mutableStateOf<DailyEntry?>(null) }
    var editing by remember { mutableStateOf<TrainingItem?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("$completed / ${entries.size}", style = MaterialTheme.typography.displaySmall)
        Text("今日完成", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.id }) { entry ->
                TrainingRow(
                    title = entry.title,
                    detail = "${formatDuration(entry.durationSeconds)} · ${entry.sets} 組 · 休息 ${entry.restSeconds} 秒",
                    checked = entry.completed,
                    onChecked = { onToggle(entry, it) },
                    onClick = { timerEntry = entry },
                    onEdit = {
                        editing = state.items.firstOrNull { it.id == entry.itemId }
                            ?: TrainingItem(entry.itemId, entry.title, entry.durationSeconds, entry.sets, entry.restSeconds)
                    },
                    onDelete = { onArchiveItem(entry.itemId) }
                )
            }
        }
    }

    timerEntry?.let { entry ->
        TrainingTimerDialog(
            entry = entry,
            onDismiss = { timerEntry = null },
            onFinish = {
                onToggle(entry, true)
                timerEntry = null
            }
        )
    }

    editing?.let { item ->
        ItemDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { title, duration, sets, rest ->
                onSaveItem(item.id, title, duration, sets, rest)
                editing = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrainingRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (onEdit != null || onDelete != null) menuOpen = true
                    }
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = onChecked)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        ItemActionMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onEdit = onEdit,
            onDelete = onDelete
        )
    }
}

@Composable
private fun HistoryScreen(state: UiState, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.summaries, key = { it.date }) { summary ->
                Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().clickable { onSelect(summary.date) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(summary.date, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text("${summary.completed} / ${summary.total}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(state.selectedHistory ?: "選擇日期", style = MaterialTheme.typography.titleLarge)
            }
            items(state.historyEntries, key = { it.id }) { entry ->
                TrainingRow(
                    title = entry.title,
                    detail = "${formatDuration(entry.durationSeconds)} · ${entry.sets} 組 · 休息 ${entry.restSeconds} 秒",
                    checked = entry.completed,
                    onChecked = {},
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: HoopLogViewModel) {
    var editItem by remember { mutableStateOf<TrainingItem?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var owner by remember(vm.state.updateSettings) { mutableStateOf(vm.state.updateSettings.owner) }
    var repo by remember(vm.state.updateSettings) { mutableStateOf(vm.state.updateSettings.repo) }
    var checking by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("訓練項目", style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.state.items, key = { it.id }) { item ->
                EditableItemRow(
                    item = item,
                    onEdit = {
                        editItem = item
                        showDialog = true
                    },
                    onDelete = { vm.archiveItem(item.id) }
                )
            }
        }

        Text("GitHub 更新", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(owner, { owner = it }, label = { Text("Owner") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(repo, { repo = it }, label = { Text("Repo") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveUpdateSettings(owner, repo) }) {
                Text("儲存")
            }
            Button(
                enabled = !checking,
                onClick = {
                    checking = true
                }
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text("檢查")
            }
        }
        vm.state.updateInfo?.let { info ->
            Text("最新版本：${info.latestVersion}")
            Button(onClick = { UpdateChecker().openRelease(context, info.releaseUrl) }) {
                Text(if (info.isNewer) "開啟下載頁" else "檢視 Release")
            }
        }
    }

    if (checking) {
        LaunchedEffect(Unit) {
            vm.checkUpdate()
            checking = false
        }
    }

    if (showDialog) {
        ItemDialog(
            item = editItem,
            onDismiss = { showDialog = false },
            onSave = { title, duration, sets, rest ->
                vm.saveItem(editItem?.id, title, duration, sets, rest)
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditableItemRow(
    item: TrainingItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { menuOpen = true }
                )
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatDuration(item.durationSeconds)} · ${item.sets} 組 · 休息 ${item.restSeconds} 秒",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        ItemActionMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onEdit = onEdit,
            onDelete = onDelete
        )
    }
}

@Composable
private fun ItemDialog(
    item: TrainingItem?,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int) -> Unit
) {
    var title by remember(item) { mutableStateOf(item?.title ?: "") }
    var duration by remember(item) { mutableStateOf(((item?.durationSeconds ?: 600) / 60).coerceAtLeast(1).toString()) }
    var sets by remember(item) { mutableStateOf((item?.sets ?: 3).toString()) }
    var rest by remember(item) { mutableStateOf((item?.restSeconds ?: 60).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "新增項目" else "修改項目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("名稱") }, singleLine = true)
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit) },
                    label = { Text("訓練時間（分鐘）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = sets,
                    onValueChange = { sets = it.filter(Char::isDigit) },
                    label = { Text("組數") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = rest,
                    onValueChange = { rest = it.filter(Char::isDigit) },
                    label = { Text("組間休息秒數") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title,
                        (duration.toIntOrNull() ?: 1) * 60,
                        sets.toIntOrNull() ?: 1,
                        rest.toIntOrNull() ?: 0
                    )
                }
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ItemActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        onEdit?.let {
            DropdownMenuItem(
                text = { Text("修改") },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    onDismiss()
                    it()
                }
            )
        }
        onDelete?.let {
            DropdownMenuItem(
                text = { Text("刪除") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = {
                    onDismiss()
                    it()
                }
            )
        }
    }
}

@Composable
private fun TrainingTimerDialog(
    entry: DailyEntry,
    onDismiss: () -> Unit,
    onFinish: () -> Unit
) {
    var remaining by remember(entry.id) { mutableStateOf(entry.durationSeconds.coerceAtLeast(1)) }
    var running by remember(entry.id) { mutableStateOf(false) }
    val progress = 1f - remaining.toFloat() / entry.durationSeconds.coerceAtLeast(1).toFloat()

    LaunchedEffect(running, remaining) {
        if (running && remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (running && remaining == 0) {
            onFinish()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    formatDuration(remaining),
                    style = MaterialTheme.typography.displayMedium
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${entry.sets} 組 · 組間休息 ${entry.restSeconds} 秒")
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { running = !running }) {
                    Text(
                        when {
                            running -> "暫停"
                            remaining == entry.durationSeconds -> "開始"
                            else -> "繼續"
                        }
                    )
                }
                TextButton(onClick = onFinish) {
                    Text("提前結束")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

private fun formatDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val remainder = safeSeconds % 60
    return "%02d:%02d".format(minutes, remainder)
}

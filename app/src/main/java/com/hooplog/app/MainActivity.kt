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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.YearMonth
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
            historyEntries = selected?.let { trainingStore.entriesFor(it, ensure = false) } ?: emptyList(),
            updateSettings = settingsStore.loadUpdateSettings()
        )
    }

    fun toggle(entry: DailyEntry, completed: Boolean) {
        trainingStore.toggleEntry(entry.id, completed)
        reload()
    }

    fun updateEntryPlan(
        entry: DailyEntry,
        mode: TrainingMode,
        durationSeconds: Int,
        repsPerSet: Int,
        sets: Int,
        restSeconds: Int,
        completedSets: Int? = null,
        setPlans: List<TrainingSetPlan>? = null
    ) {
        trainingStore.updateEntryPlan(entry.id, mode, durationSeconds, repsPerSet, sets, restSeconds, completedSets, setPlans)
        reload()
    }

    fun saveItem(id: Long?, title: String, tag: String, colorHex: String, priority: Int, mode: TrainingMode, durationSeconds: Int, repsPerSet: Int, sets: Int, restSeconds: Int) {
        if (title.isBlank()) {
            state = state.copy(message = "請輸入訓練項目")
            return
        }
        trainingStore.saveItem(id, title, tag, colorHex, priority, mode, durationSeconds, repsPerSet, sets, restSeconds)
        reload()
    }

    fun archiveItem(id: Long) {
        trainingStore.archiveItem(id)
        reload()
    }

    fun selectHistory(date: String) {
        state = state.copy(selectedHistory = date, historyEntries = trainingStore.entriesFor(date, ensure = false))
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
                            onSave = { title, tag, color, priority, mode, duration, reps, sets, rest ->
                                vm.saveItem(editing?.id, title, tag, color, priority, mode, duration, reps, sets, rest)
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
                    Screen.Today -> TodayScreen(vm.state, vm::toggle, vm::updateEntryPlan, vm::saveItem, vm::archiveItem)
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
    onUpdateEntryPlan: (DailyEntry, TrainingMode, Int, Int, Int, Int, Int?, List<TrainingSetPlan>?) -> Unit,
    onSaveItem: (Long?, String, String, String, Int, TrainingMode, Int, Int, Int, Int) -> Unit,
    onArchiveItem: (Long) -> Unit
) {
    val entries = state.entries
    var selectedTag by remember { mutableStateOf("全部") }
    val tags = remember(entries) { listOf("全部") + entries.map { it.tag }.distinct().sorted() }
    val visibleEntries = entries
        .filter { selectedTag == "全部" || it.tag == selectedTag }
        .sortedWith(compareBy<DailyEntry> { it.priority }.thenBy { it.tag }.thenBy { it.title })
    val completed = entries.count { it.completed }
    var timerEntry by remember { mutableStateOf<DailyEntry?>(null) }
    var editing by remember { mutableStateOf<TrainingItem?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("$completed / ${entries.size}", style = MaterialTheme.typography.displaySmall)
        Text("今日完成", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tags, key = { it }) { tag ->
                ChoiceButton(
                    text = tag,
                    selected = selectedTag == tag,
                    compact = true,
                    onClick = { selectedTag = tag }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleEntries, key = { it.id }) { entry ->
                TrainingRow(
                    title = entry.title,
                    detail = entry.planDetail(),
                    colorHex = entry.colorHex,
                    checked = entry.completed,
                    onChecked = { onToggle(entry, it) },
                    onClick = { timerEntry = entry },
                    onEdit = {
                        editing = state.items.firstOrNull { it.id == entry.itemId }
                            ?: TrainingItem(entry.itemId, entry.title, entry.tag, entry.colorHex, entry.priority, entry.mode, entry.durationSeconds, entry.repsPerSet, entry.sets, entry.restSeconds)
                    },
                    onDelete = { onArchiveItem(entry.itemId) }
                )
            }
        }
    }

    timerEntry?.let { entry ->
        TrainingTimerDialog(
            entry = entry,
            onPlanChange = { mode, duration, reps, sets, rest, completed, plans ->
                onUpdateEntryPlan(entry, mode, duration, reps, sets, rest, completed, plans)
            },
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
            onSave = { title, tag, color, priority, mode, duration, reps, sets, rest ->
                onSaveItem(item.id, title, tag, color, priority, mode, duration, reps, sets, rest)
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
    colorHex: String,
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
            color = parseColor(colorHex, MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(8.dp),
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
    var month by remember { mutableStateOf(YearMonth.now()) }
    val summaries = remember(state.summaries) { state.summaries.associateBy { it.date } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("<") }
            Text("${month.year}-${month.monthValue.toString().padStart(2, '0')}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { month = month.plusMonths(1) }) { Text(">") }
        }
        CalendarMonth(month, summaries, onSelect)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(state.selectedHistory ?: "選擇日期", style = MaterialTheme.typography.titleMedium)
            }
            items(state.historyEntries, key = { it.id }) { entry ->
                TrainingRow(
                    title = entry.title,
                    detail = "${entry.completedSets}/${entry.sets} 組 · ${entry.planDetail()}",
                    colorHex = entry.colorHex,
                    checked = entry.completed,
                    onChecked = {},
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun CalendarMonth(
    month: YearMonth,
    summaries: Map<String, DaySummary>,
    onSelect: (String) -> Unit
) {
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value % 7
    val cells = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                Text(it, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val summary = date?.let { summaries[it.toString()] }
                    Surface(
                        tonalElevation = if (summary == null) 0.dp else 1.dp,
                        shape = RoundedCornerShape(8.dp),
                        color = if (summary == null) MaterialTheme.colorScheme.surface else Color(0xFFEAF7EE),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clickable(enabled = date != null) { date?.let { onSelect(it.toString()) } }
                    ) {
                        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(date?.dayOfMonth?.toString() ?: "", style = MaterialTheme.typography.bodySmall)
                            Text(summary?.let { "${it.completed}/${it.total}" } ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                repeat(7 - week.size) {
                    Spacer(Modifier.weight(1f).height(56.dp))
                }
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
            onSave = { title, tag, color, priority, mode, duration, reps, sets, rest ->
                vm.saveItem(editItem?.id, title, tag, color, priority, mode, duration, reps, sets, rest)
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
            color = parseColor(item.colorHex, MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(8.dp),
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
                    item.planDetail(),
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
    onSave: (String, String, String, Int, TrainingMode, Int, Int, Int, Int) -> Unit
) {
    var title by remember(item) { mutableStateOf(item?.title ?: "") }
    var tag by remember(item) { mutableStateOf(item?.tag ?: "每日訓練") }
    var colorHex by remember(item) { mutableStateOf(item?.colorHex ?: "#F4F1FF") }
    var priority by remember(item) { mutableStateOf((item?.priority ?: 3).toString()) }
    var mode by remember(item) { mutableStateOf(item?.mode ?: TrainingMode.Time) }
    var duration by remember(item) { mutableStateOf(((item?.durationSeconds ?: 600) / 60).coerceAtLeast(1).toString()) }
    var reps by remember(item) { mutableStateOf((item?.repsPerSet ?: 10).toString()) }
    var sets by remember(item) { mutableStateOf((item?.sets ?: 3).toString()) }
    var rest by remember(item) { mutableStateOf((item?.restSeconds ?: 60).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "新增項目" else "修改項目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("名稱") }, singleLine = true)
                OutlinedTextField(tag, { tag = it }, label = { Text("標籤") }, singleLine = true)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("每日訓練", "每周訓練", "特化訓練"), key = { it }) { preset ->
                        ChoiceButton(
                            text = preset,
                            selected = tag == preset,
                            compact = true,
                            onClick = { tag = preset }
                        )
                    }
                }
                Text("顏色", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cardColorChoices, key = { it }) { color ->
                        ColorChoice(
                            colorHex = color,
                            selected = colorHex == color,
                            onClick = { colorHex = color }
                        )
                    }
                }
                OutlinedTextField(
                    value = priority,
                    onValueChange = { priority = it.filter(Char::isDigit).take(1) },
                    label = { Text("Priority 1-5") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton("計時", mode == TrainingMode.Time) { mode = TrainingMode.Time }
                    ChoiceButton("次數", mode == TrainingMode.Reps) { mode = TrainingMode.Reps }
                }
                if (mode == TrainingMode.Time) {
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it.filter(Char::isDigit) },
                        label = { Text("每組訓練時間（分鐘）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                } else {
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it.filter(Char::isDigit) },
                        label = { Text("每組次數") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
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
                        tag,
                        colorHex,
                        priority.toIntOrNull()?.coerceIn(1, 5) ?: 3,
                        mode,
                        (duration.toIntOrNull() ?: 1) * 60,
                        reps.toIntOrNull() ?: 1,
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
    onPlanChange: (TrainingMode, Int, Int, Int, Int, Int?, List<TrainingSetPlan>?) -> Unit,
    onDismiss: () -> Unit,
    onFinish: () -> Unit
) {
    var mode by remember(entry.id) { mutableStateOf(entry.mode) }
    var workSeconds by remember(entry.id) { mutableStateOf(entry.durationSeconds.coerceAtLeast(1)) }
    var repsPerSet by remember(entry.id) { mutableStateOf(entry.repsPerSet.coerceAtLeast(1)) }
    var setCount by remember(entry.id) { mutableStateOf(entry.sets.coerceAtLeast(1)) }
    var restSeconds by remember(entry.id) { mutableStateOf(entry.restSeconds.coerceAtLeast(0)) }
    var setPlans by remember(entry.id) { mutableStateOf(entry.setPlans.ifEmpty { entry.defaultSetPlans() }) }
    var activeSet by remember(entry.id) { mutableStateOf((setPlans.indexOfFirst { !it.completed } + 1).takeIf { it > 0 } ?: setCount) }
    var isResting by remember(entry.id) { mutableStateOf(false) }
    var remaining by remember(entry.id) { mutableStateOf(setPlans.getOrNull(activeSet - 1)?.durationSeconds ?: workSeconds) }
    var running by remember(entry.id) { mutableStateOf(false) }
    val activePlan = setPlans.getOrNull(activeSet - 1) ?: TrainingSetPlan(mode, workSeconds, repsPerSet)
    val phaseSeconds = if (isResting) restSeconds.coerceAtLeast(1) else activePlan.durationSeconds.coerceAtLeast(1)
    val progress = 1f - remaining.toFloat() / phaseSeconds.toFloat()
    val completedSets = setPlans.count { it.completed }

    LaunchedEffect(running, remaining) {
        if (running && remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }

    LaunchedEffect(running, remaining, activeSet, isResting, setCount, restSeconds, setPlans) {
        if (!running || remaining > 0) return@LaunchedEffect
        when {
            !isResting -> {
                val updated = setPlans.markSetCompleted(activeSet)
                setPlans = updated
                onPlanChange(mode, workSeconds, repsPerSet, updated.size, restSeconds, updated.count { it.completed }, updated)
                if (updated.all { it.completed }) {
                    onFinish()
                } else if (restSeconds > 0) {
                    isResting = true
                    remaining = restSeconds
                } else {
                    activeSet = updated.indexOfFirst { !it.completed } + 1
                    remaining = updated[activeSet - 1].durationSeconds
                }
            }
            isResting -> {
                isResting = false
                activeSet = (setPlans.indexOfFirst { !it.completed } + 1).takeIf { it > 0 } ?: setPlans.size
                remaining = setPlans.getOrNull(activeSet - 1)?.durationSeconds ?: workSeconds
            }
        }
    }

    fun applyPlan(
        nextMode: TrainingMode = mode,
        nextWork: Int = workSeconds,
        nextReps: Int = repsPerSet,
        nextSets: Int = setCount,
        nextRest: Int = restSeconds,
        nextCompleted: Int = completedSets,
        nextPlans: List<TrainingSetPlan>? = null
    ) {
        val safeWork = nextWork.coerceAtLeast(5)
        val safeReps = nextReps.coerceAtLeast(1)
        val safeSets = nextSets.coerceAtLeast(1)
        val safeRest = nextRest.coerceAtLeast(0)
        val sourcePlans = nextPlans ?: setPlans.map {
            it.copy(mode = nextMode, durationSeconds = safeWork, reps = safeReps)
        }
        val plans = sourcePlans
            .normalizePlans(nextMode, safeWork, safeReps, safeSets)
            .let { plans ->
                if (nextPlans == null && nextCompleted != completedSets) {
                    plans.mapIndexed { index, plan -> plan.copy(completed = index < nextCompleted.coerceIn(0, safeSets)) }
                } else {
                    plans
                }
            }
        val safeCompleted = plans.count { it.completed }
        mode = nextMode
        workSeconds = safeWork
        repsPerSet = safeReps
        setCount = safeSets
        restSeconds = safeRest
        setPlans = plans
        activeSet = (plans.indexOfFirst { !it.completed } + 1).takeIf { it > 0 } ?: safeSets
        remaining = if (running) {
            remaining.coerceAtMost(if (isResting) safeRest.coerceAtLeast(1) else plans[activeSet - 1].durationSeconds)
        } else if (isResting) {
            safeRest
        } else {
            plans[activeSet - 1].durationSeconds
        }
        if (isResting && safeRest == 0) {
            isResting = false
            remaining = plans[activeSet - 1].durationSeconds
        }
        onPlanChange(nextMode, safeWork, safeReps, safeSets, safeRest, safeCompleted, plans)
    }

    fun updateSetPlan(setNumber: Int, transform: (TrainingSetPlan) -> TrainingSetPlan) {
        val updated = setPlans.mapIndexed { index, plan ->
            if (index == setNumber - 1) {
                transform(plan).let {
                    it.copy(durationSeconds = it.durationSeconds.coerceAtLeast(5), reps = it.reps.coerceAtLeast(1))
                }
            } else {
                plan
            }
        }
        setPlans = updated
        if (setNumber == activeSet && !running && !isResting) {
            remaining = updated[setNumber - 1].durationSeconds
        }
        onPlanChange(mode, workSeconds, repsPerSet, updated.size, restSeconds, updated.count { it.completed }, updated)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton("計時", mode == TrainingMode.Time) { applyPlan(nextMode = TrainingMode.Time) }
                    ChoiceButton("次數", mode == TrainingMode.Reps) { applyPlan(nextMode = TrainingMode.Reps) }
                }
                CounterControl(
                    label = "組數",
                    value = "${setCount} 組",
                    onDecrease = { applyPlan(nextSets = (setCount - 1).coerceAtLeast(completedSets.coerceAtLeast(1))) },
                    onIncrease = { applyPlan(nextSets = setCount + 1) }
                )
                if (mode == TrainingMode.Time) {
                    CounterControl(
                        label = "每組時間",
                        value = formatDuration(workSeconds),
                        onDecrease = { applyPlan(nextWork = workSeconds - 30) },
                        onIncrease = { applyPlan(nextWork = workSeconds + 30) }
                    )
                } else {
                    CounterControl(
                        label = "每組次數",
                        value = "${repsPerSet} 次",
                        onDecrease = { applyPlan(nextReps = repsPerSet - 1) },
                        onIncrease = { applyPlan(nextReps = repsPerSet + 1) }
                    )
                }
                CounterControl(
                    label = "休息時間",
                    value = formatDuration(restSeconds),
                    onDecrease = { applyPlan(nextRest = restSeconds - 15) },
                    onIncrease = { applyPlan(nextRest = restSeconds + 15) }
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(setCount) { index ->
                        val setNumber = index + 1
                            val plan = setPlans[index]
                            SetCard(
                                setNumber = setNumber,
                            plan = plan,
                            enabled = !plan.completed && setNumber == activeSet,
                            remaining = if (setNumber == activeSet) remaining else plan.durationSeconds,
                            running = running && setNumber == activeSet && !isResting,
                            resting = isResting && setNumber == activeSet,
                            progress = if (setNumber == activeSet) progress else 0f,
                            onModeChange = { nextMode -> updateSetPlan(setNumber) { it.copy(mode = nextMode) } },
                            onDurationChange = { nextDuration -> updateSetPlan(setNumber) { it.copy(durationSeconds = nextDuration) } },
                            onRepsChange = { nextReps -> updateSetPlan(setNumber) { it.copy(reps = nextReps) } },
                            onUndo = {
                                running = false
                                isResting = false
                                val updated = setPlans.markSetIncomplete(setNumber)
                                applyPlan(nextPlans = updated)
                            },
                            onStartPause = {
                                if (plan.mode == TrainingMode.Time) {
                                    activeSet = setNumber
                                    running = !running
                                }
                            },
                            onComplete = {
                                running = false
                                isResting = false
                                val updated = setPlans.markSetCompleted(setNumber)
                                applyPlan(nextPlans = updated)
                                if (setNumber >= setCount) onFinish()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                applyPlan(nextCompleted = setCount)
                onFinish()
            }) {
                Text("全部完成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

@Composable
private fun SetCard(
    setNumber: Int,
    plan: TrainingSetPlan,
    enabled: Boolean,
    remaining: Int,
    running: Boolean,
    resting: Boolean,
    progress: Float,
    onModeChange: (TrainingMode) -> Unit,
    onDurationChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    onUndo: () -> Unit,
    onStartPause: () -> Unit,
    onComplete: () -> Unit
) {
    Surface(tonalElevation = 1.dp, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("第 $setNumber 組", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            plan.completed -> "已完成"
                            resting -> "休息 ${formatDuration(remaining)}"
                            plan.mode == TrainingMode.Time -> formatDuration(remaining)
                            else -> "${plan.reps} 次"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Checkbox(checked = plan.completed, onCheckedChange = { if (!plan.completed) onComplete() })
            }
            if (!plan.completed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton("時間", plan.mode == TrainingMode.Time) { onModeChange(TrainingMode.Time) }
                    ChoiceButton("次數", plan.mode == TrainingMode.Reps) { onModeChange(TrainingMode.Reps) }
                }
                if (plan.mode == TrainingMode.Time) {
                    CounterControl(
                        label = "本組時間",
                        value = formatDuration(plan.durationSeconds),
                        onDecrease = { onDurationChange(plan.durationSeconds - 30) },
                        onIncrease = { onDurationChange(plan.durationSeconds + 30) }
                    )
                } else {
                    CounterControl(
                        label = "本組次數",
                        value = "${plan.reps} 次",
                        onDecrease = { onRepsChange(plan.reps - 1) },
                        onIncrease = { onRepsChange(plan.reps + 1) }
                    )
                }
            }
            if (plan.completed) {
                TextButton(onClick = onUndo) {
                    Text("復原本組")
                }
            }
            if (enabled && plan.mode == TrainingMode.Time) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onStartPause) {
                        Text(if (running) "暫停" else "開始")
                    }
                    TextButton(onClick = onComplete) {
                        Text("完成本組")
                    }
                }
            }
            if (enabled && plan.mode == TrainingMode.Reps) {
                TextButton(onClick = onComplete) {
                    Text("完成本組")
                }
            }
        }
    }
}

@Composable
private fun CounterControl(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDecrease) {
            Text("-")
        }
        Text(value, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onIncrease) {
            Text("+")
        }
    }
}

@Composable
private fun ChoiceButton(
    text: String,
    selected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(if (selected) "✓ $text" else text, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ColorChoice(
    colorHex: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = parseColor(colorHex, MaterialTheme.colorScheme.surface),
            contentColor = Color(0xFF111111)
        )
    ) {
        Text(if (selected) "✓" else " ")
    }
}

private fun formatDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val remainder = safeSeconds % 60
    return "%02d:%02d".format(minutes, remainder)
}

private val cardColorChoices = listOf(
    "#F4F1FF",
    "#EAF7EE",
    "#FFF3D8",
    "#EAF3FF",
    "#FCECEC",
    "#F2F2F2"
)

private fun parseColor(value: String, fallback: Color): Color =
    runCatching {
        val clean = value.removePrefix("#")
        Color(
            red = clean.substring(0, 2).toInt(16),
            green = clean.substring(2, 4).toInt(16),
            blue = clean.substring(4, 6).toInt(16)
        )
    }.getOrElse { fallback }

private fun DailyEntry.planDetail(): String = when (mode) {
    TrainingMode.Time -> "$tag · 計時 · 每組 ${formatDuration(durationSeconds)} · ${sets} 組 · 休息 ${restSeconds} 秒"
    TrainingMode.Reps -> "$tag · 次數 · 每組 ${repsPerSet} 次 · ${sets} 組 · 休息 ${restSeconds} 秒"
}

private fun TrainingItem.planDetail(): String = when (mode) {
    TrainingMode.Time -> "$tag · 計時 · 每組 ${formatDuration(durationSeconds)} · ${sets} 組 · 休息 ${restSeconds} 秒"
    TrainingMode.Reps -> "$tag · 次數 · 每組 ${repsPerSet} 次 · ${sets} 組 · 休息 ${restSeconds} 秒"
}

private fun DailyEntry.defaultSetPlans(): List<TrainingSetPlan> =
    List(sets.coerceAtLeast(1)) { index ->
        TrainingSetPlan(
            mode = mode,
            durationSeconds = durationSeconds.coerceAtLeast(1),
            reps = repsPerSet.coerceAtLeast(1),
            completed = index < completedSets
        )
    }

private fun List<TrainingSetPlan>.normalizePlans(
    mode: TrainingMode,
    durationSeconds: Int,
    repsPerSet: Int,
    sets: Int
): List<TrainingSetPlan> {
    val default = TrainingSetPlan(mode, durationSeconds.coerceAtLeast(5), repsPerSet.coerceAtLeast(1))
    return List(sets.coerceAtLeast(1)) { index ->
        getOrNull(index) ?: default
    }
}

private fun List<TrainingSetPlan>.markSetCompleted(setNumber: Int): List<TrainingSetPlan> =
    mapIndexed { index, plan ->
        if (index == setNumber - 1) plan.copy(completed = true) else plan
    }

private fun List<TrainingSetPlan>.markSetIncomplete(setNumber: Int): List<TrainingSetPlan> =
    mapIndexed { index, plan ->
        if (index == setNumber - 1) plan.copy(completed = false) else plan
    }

package com.hooplog.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
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
    val weekEntries: Map<String, List<DailyEntry>> = emptyMap(),
    val items: List<TrainingItem> = emptyList(),
    val summaries: List<DaySummary> = emptyList(),
    val todaySession: DaySession = DaySession(LocalDate.now().toString(), null, null, 0),
    val selectedHistory: String? = null,
    val historyEntries: List<DailyEntry> = emptyList(),
    val tags: List<TrainingTag> = emptyList(),
    val uiSettings: UiSettings = UiSettings(),
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
        val weekDates = LocalDate.now().weekDates()
        trainingStore.ensureEntriesFor(today)
        val selected = state.selectedHistory
        state = state.copy(
            today = today,
            entries = trainingStore.entriesFor(today),
            weekEntries = weekDates.associate { date ->
                val key = date.toString()
                key to trainingStore.entriesFor(key)
            },
            items = trainingStore.activeItems(),
            summaries = trainingStore.summaries(),
            todaySession = trainingStore.sessionFor(today),
            historyEntries = selected?.let { trainingStore.historyEntriesFor(it) } ?: emptyList(),
            tags = trainingStore.tags(),
            uiSettings = settingsStore.loadUiSettings(),
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

    fun saveItem(id: Long?, title: String, tag: String, colorHex: String, priority: Int, mode: TrainingMode, durationSeconds: Int, repsPerSet: Int, sets: Int, restSeconds: Int, comment: String, videoUrl: String) {
        if (title.isBlank()) {
            state = state.copy(message = "請輸入訓練項目")
            return
        }
        trainingStore.saveItem(id, title, tag, colorHex, priority, mode, durationSeconds, repsPerSet, sets, restSeconds, comment, videoUrl)
        reload()
    }

    fun startTodaySession() {
        trainingStore.startSession(state.today)
        reload()
    }

    fun archiveItem(id: Long) {
        trainingStore.archiveItem(id)
        reload()
    }

    fun saveTag(originalName: String?, tag: TrainingTag) {
        trainingStore.saveTag(originalName, tag)
        reload()
    }

    fun deleteTag(name: String) {
        trainingStore.deleteTag(name)
        reload()
    }

    fun saveUiSettings(settings: UiSettings) {
        settingsStore.saveUiSettings(settings)
        reload()
    }

    fun selectHistory(date: String) {
        state = state.copy(selectedHistory = date, historyEntries = trainingStore.historyEntriesFor(date))
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
    val ui = vm.state.uiSettings
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = parseColor(ui.primaryColorHex, Color(0xFF111111)),
            background = androidx.compose.ui.graphics.Color(0xFFFAFAFA),
            surface = parseColor(ui.surfaceColorHex, Color.White)
        ),
        typography = MaterialTheme.typography.copy(
            bodySmall = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * ui.fontScale).sp),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontSize = (14 * ui.fontScale).sp),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontSize = (16 * ui.fontScale).sp),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontSize = (22 * ui.fontScale).sp),
            displaySmall = MaterialTheme.typography.displaySmall.copy(fontSize = (36 * ui.fontScale).sp),
            displayMedium = MaterialTheme.typography.displayMedium.copy(fontSize = (45 * ui.fontScale).sp)
        ),
        shapes = MaterialTheme.shapes.copy(
            small = RoundedCornerShape(ui.cardRadius.dp),
            medium = RoundedCornerShape(ui.cardRadius.dp),
            large = RoundedCornerShape((ui.cardRadius + 4).dp)
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
                            availableTags = vm.state.tags,
                            onDismiss = { showDialog = false },
                            onSave = { title, tag, color, priority, mode, duration, reps, sets, rest, comment, videoUrl ->
                                vm.saveItem(editing?.id, title, tag, color, priority, mode, duration, reps, sets, rest, comment, videoUrl)
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
                    Screen.Today -> TodayScreen(vm.state, vm::toggle, vm::updateEntryPlan, vm::saveItem, vm::archiveItem, vm::startTodaySession)
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

private enum class HomeMode { Day, Week }

@Composable
private fun TodayScreen(
    state: UiState,
    onToggle: (DailyEntry, Boolean) -> Unit,
    onUpdateEntryPlan: (DailyEntry, TrainingMode, Int, Int, Int, Int, Int?, List<TrainingSetPlan>?) -> Unit,
    onSaveItem: (Long?, String, String, String, Int, TrainingMode, Int, Int, Int, Int, String, String) -> Unit,
    onArchiveItem: (Long) -> Unit,
    onStartSession: () -> Unit
) {
    val entries = state.entries
    var homeMode by remember { mutableStateOf(HomeMode.Day) }
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
        Text(
            if (state.todaySession.startedAt == null) "尚未開始計時" else "當日訓練時長 ${formatDuration(state.todaySession.durationSeconds)}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceButton("本日", homeMode == HomeMode.Day) { homeMode = HomeMode.Day }
            ChoiceButton("本周", homeMode == HomeMode.Week) { homeMode = HomeMode.Week }
        }
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
        if (homeMode == HomeMode.Day) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleEntries, key = { it.id }) { entry ->
                    TrainingRow(
                        title = entry.title,
                        detail = entry.planDetail(),
                        colorHex = entry.colorHex,
                        checked = entry.completed,
                        onChecked = { onToggle(entry, it) },
                        onClick = {
                            onStartSession()
                            timerEntry = entry
                        },
                        onEdit = {
                            editing = state.items.firstOrNull { it.id == entry.itemId }
                                ?: TrainingItem(entry.itemId, entry.title, entry.tag, entry.colorHex, entry.priority, entry.mode, entry.durationSeconds, entry.repsPerSet, entry.sets, entry.restSeconds, entry.comment, entry.videoUrl)
                        },
                        onDelete = { onArchiveItem(entry.itemId) }
                    )
                }
            }
        } else {
            WeekOverview(
                today = LocalDate.parse(state.today),
                weekEntries = state.weekEntries,
                selectedTag = selectedTag
            )
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
            availableTags = state.tags,
            onDismiss = { editing = null },
            onSave = { title, tag, color, priority, mode, duration, reps, sets, rest, comment, videoUrl ->
                onSaveItem(item.id, title, tag, color, priority, mode, duration, reps, sets, rest, comment, videoUrl)
                editing = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekOverview(
    today: LocalDate,
    weekEntries: Map<String, List<DailyEntry>>,
    selectedTag: String
) {
    var detailDate by remember { mutableStateOf<LocalDate?>(null) }
    val dates = remember(today) { today.weekDates() }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(dates, key = { it.toString() }) { date ->
            val entries = weekEntries[date.toString()].orEmpty()
                .filter { selectedTag == "全部" || it.tag == selectedTag }
                .sortedWith(compareBy<DailyEntry> { it.priority }.thenBy { it.tag }.thenBy { it.title })
            val completed = entries.count { it.completed }
            val isToday = date == today
            Surface(
                tonalElevation = if (isToday) 5.dp else 1.dp,
                color = if (isToday) Color(0xFFEAF3FF) else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { detailDate = date },
                        onLongClick = { detailDate = date }
                    )
            ) {
                Row(Modifier.padding(if (isToday) 16.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${weekdayLabel(date.dayOfWeek.value)} ${date.monthValue}/${date.dayOfMonth}",
                            style = if (isToday) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (entries.isEmpty()) "無訓練" else "$completed / ${entries.size} 完成",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (isToday) {
                        Text("今天", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    detailDate?.let { date ->
        val entries = weekEntries[date.toString()].orEmpty()
            .filter { selectedTag == "全部" || it.tag == selectedTag }
            .sortedWith(compareBy<DailyEntry> { it.priority }.thenBy { it.tag }.thenBy { it.title })
        AlertDialog(
            onDismissRequest = { detailDate = null },
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            title = { Text("${weekdayLabel(date.dayOfWeek.value)} ${date}") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entries.isEmpty()) {
                        item { Text("這天沒有訓練") }
                    } else {
                        items(entries, key = { it.id }) { entry ->
                            Text("${if (entry.completed) "✓" else "□"} ${entry.title} · ${entry.completedSets}/${entry.sets} 組", style = MaterialTheme.typography.bodyMedium)
                            Text(entry.planDetail(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailDate = null }) { Text("關閉") }
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
            shape = MaterialTheme.shapes.small,
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
    var detailDate by remember { mutableStateOf<String?>(null) }
    val summaries = remember(state.summaries) { state.summaries.associateBy { it.date } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("<") }
            Text("${month.year}-${month.monthValue.toString().padStart(2, '0')}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { month = month.plusMonths(1) }) { Text(">") }
        }
        CalendarMonth(month, summaries) { date ->
            onSelect(date)
            detailDate = date
        }
        Spacer(Modifier.weight(1f))
    }
    detailDate?.let { date ->
        val summary = state.summaries.firstOrNull { it.date == date }
        AlertDialog(
            onDismissRequest = { detailDate = null },
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            title = { Text("$date · ${formatDuration(summary?.durationSeconds ?: 0)}") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    if (state.historyEntries.isEmpty()) {
                        item { Text("這天沒有訓練紀錄") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailDate = null }) { Text("關閉") }
            }
        )
    }
}

@Composable
private fun HistoryDetailList(entries: List<DailyEntry>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries, key = { it.id }) { entry ->
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
                        shape = MaterialTheme.shapes.small,
                        color = if (summary == null) MaterialTheme.colorScheme.surface else Color(0xFFEAF7EE),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clickable(enabled = date != null) { date?.let { onSelect(it.toString()) } }
                    ) {
                        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(date?.dayOfMonth?.toString() ?: "", style = MaterialTheme.typography.bodySmall)
                            Text(summary?.let { "${it.completed}/${it.total} ${formatDuration(it.durationSeconds)}" } ?: "", style = MaterialTheme.typography.bodySmall)
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
    var editTag by remember { mutableStateOf<TrainingTag?>(null) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showAdvancedDialog by remember { mutableStateOf(false) }
    var owner by remember(vm.state.updateSettings) { mutableStateOf(vm.state.updateSettings.owner) }
    var repo by remember(vm.state.updateSettings) { mutableStateOf(vm.state.updateSettings.repo) }
    var checking by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("訓練項目", style = MaterialTheme.typography.titleLarge) }
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
        item { Text("標籤管理", style = MaterialTheme.typography.titleLarge) }
        items(vm.state.tags, key = { it.name }) { tag ->
            Surface(
                color = parseColor(tag.colorHex, MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.small,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tag.name, style = MaterialTheme.typography.titleMedium)
                        Text("${tag.schedule.label()} · priority ${tag.priority} · ${weekdayLabel(tag.weeklyDay)}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {
                        editTag = tag
                        showTagDialog = true
                    }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "修改")
                    }
                    IconButton(onClick = { vm.deleteTag(tag.name) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "刪除")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    editTag = null
                    showTagDialog = true
                }) { Text("新增標籤") }
                Button(onClick = { showAdvancedDialog = true }) { Text("進階設定") }
            }
        }
        item { Text("GitHub 更新", style = MaterialTheme.typography.titleLarge) }
        item { OutlinedTextField(owner, { owner = it }, label = { Text("Owner") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(repo, { repo = it }, label = { Text("Repo") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveUpdateSettings(owner, repo) }) { Text("儲存") }
                Button(enabled = !checking, onClick = { checking = true }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("檢查")
                }
            }
        }
        vm.state.updateInfo?.let { info ->
            item {
                Text("最新版本：${info.latestVersion}")
                Button(onClick = { UpdateChecker().openRelease(context, info.releaseUrl) }) {
                    Text(if (info.isNewer) "開啟下載頁" else "檢視 Release")
                }
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
            availableTags = vm.state.tags,
            onDismiss = { showDialog = false },
            onSave = { title, tag, color, priority, mode, duration, reps, sets, rest, comment, videoUrl ->
                vm.saveItem(editItem?.id, title, tag, color, priority, mode, duration, reps, sets, rest, comment, videoUrl)
                showDialog = false
            }
        )
    }
    if (showTagDialog) {
        TagDialog(
            tag = editTag,
            onDismiss = { showTagDialog = false },
            onSave = { tag ->
                vm.saveTag(editTag?.name, tag)
                showTagDialog = false
            }
        )
    }
    if (showAdvancedDialog) {
        AdvancedSettingsDialog(
            settings = vm.state.uiSettings,
            onDismiss = { showAdvancedDialog = false },
            onSave = {
                vm.saveUiSettings(it)
                showAdvancedDialog = false
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
            shape = MaterialTheme.shapes.small,
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
private fun TagDialog(
    tag: TrainingTag?,
    onDismiss: () -> Unit,
    onSave: (TrainingTag) -> Unit
) {
    var name by remember(tag) { mutableStateOf(tag?.name ?: "") }
    var colorHex by remember(tag) { mutableStateOf(tag?.colorHex ?: "#F4F1FF") }
    var priority by remember(tag) { mutableStateOf((tag?.priority ?: 3).toString()) }
    var schedule by remember(tag) { mutableStateOf(tag?.schedule ?: TagSchedule.Manual) }
    var weeklyDay by remember(tag) { mutableStateOf(tag?.weeklyDay ?: 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag == null) "新增標籤" else "修改標籤") },
        text = {
            ScrollableDialogContent {
                OutlinedTextField(name, { name = it }, label = { Text("標籤名稱") }, singleLine = true)
                ColorPicker(label = "顏色", colorHex = colorHex, onColorChange = { colorHex = it })
                OutlinedTextField(
                    value = priority,
                    onValueChange = { priority = it.filter(Char::isDigit).take(1) },
                    label = { Text("Priority 1-5") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { ChoiceButton("每日", schedule == TagSchedule.Daily) { schedule = TagSchedule.Daily } }
                    item { ChoiceButton("每周", schedule == TagSchedule.Weekly) { schedule = TagSchedule.Weekly } }
                    item { ChoiceButton("手動", schedule == TagSchedule.Manual) { schedule = TagSchedule.Manual } }
                }
                if (schedule == TagSchedule.Weekly) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((1..7).toList(), key = { it }) { day ->
                            ChoiceButton(weekdayLabel(day), weeklyDay == day, compact = true) { weeklyDay = day }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    TrainingTag(
                        name = name.trim().ifBlank { "未命名標籤" },
                        colorHex = colorHex,
                        priority = priority.toIntOrNull()?.coerceIn(1, 5) ?: 3,
                        schedule = schedule,
                        weeklyDay = weeklyDay
                    )
                )
            }) { Text("儲存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun AdvancedSettingsDialog(
    settings: UiSettings,
    onDismiss: () -> Unit,
    onSave: (UiSettings) -> Unit
) {
    var primary by remember(settings) { mutableStateOf(settings.primaryColorHex) }
    var surface by remember(settings) { mutableStateOf(settings.surfaceColorHex) }
    var radius by remember(settings) { mutableStateOf(settings.cardRadius.toString()) }
    var fontScale by remember(settings) { mutableStateOf(((settings.fontScale * 100).toInt()).toString()) }
    var densityScale by remember(settings) { mutableStateOf(((settings.densityScale * 100).toInt()).toString()) }
    var style by remember(settings) { mutableStateOf(settings.style) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("進階介面設定") },
        text = {
            ScrollableDialogContent {
                Text("介面風格", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton("Minimal", style == "Minimal") { style = "Minimal" }
                    ChoiceButton("Soft", style == "Soft") { style = "Soft" }
                }
                ColorPicker(label = "主色", colorHex = primary, onColorChange = { primary = it })
                ColorPicker(label = "介面底色", colorHex = surface, onColorChange = { surface = it })
                OutlinedTextField(radius, { radius = it.filter(Char::isDigit).take(2) }, label = { Text("卡片弧度 0-16") }, singleLine = true)
                OutlinedTextField(fontScale, { fontScale = it.filter(Char::isDigit).take(3) }, label = { Text("字體大小 %") }, singleLine = true)
                OutlinedTextField(densityScale, { densityScale = it.filter(Char::isDigit).take(3) }, label = { Text("介面大小 %") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    UiSettings(
                        primaryColorHex = primary,
                        surfaceColorHex = surface,
                        cardRadius = radius.toIntOrNull()?.coerceIn(0, 16) ?: 8,
                        fontScale = ((fontScale.toIntOrNull() ?: 100) / 100f).coerceIn(0.85f, 1.25f),
                        densityScale = ((densityScale.toIntOrNull() ?: 100) / 100f).coerceIn(0.85f, 1.2f),
                        style = style
                    )
                )
            }) { Text("套用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ItemDialog(
    item: TrainingItem?,
    availableTags: List<TrainingTag>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, TrainingMode, Int, Int, Int, Int, String, String) -> Unit
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
    var comment by remember(item) { mutableStateOf(item?.comment ?: "") }
    var videoUrl by remember(item) { mutableStateOf(item?.videoUrl ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "新增項目" else "修改項目") },
        text = {
            ScrollableDialogContent {
                OutlinedTextField(title, { title = it }, label = { Text("名稱") }, singleLine = true)
                OutlinedTextField(tag, { tag = it }, label = { Text("標籤") }, singleLine = true)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(availableTags, key = { it.name }) { preset ->
                        ChoiceButton(
                            text = preset.name,
                            selected = tag == preset.name,
                            compact = true,
                            onClick = {
                                tag = preset.name
                                colorHex = preset.colorHex
                                priority = preset.priority.toString()
                            }
                        )
                    }
                }
                ColorPicker(label = "顏色", colorHex = colorHex, onColorChange = { colorHex = it })
                OutlinedTextField(
                    value = priority,
                    onValueChange = { priority = it.filter(Char::isDigit).take(1) },
                    label = { Text("Priority 1-5") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { ChoiceButton("計時", mode == TrainingMode.Time) { mode = TrainingMode.Time } }
                    item { ChoiceButton("次數", mode == TrainingMode.Reps) { mode = TrainingMode.Reps } }
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
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    label = { Text("教學影片連結") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                        rest.toIntOrNull() ?: 0,
                        comment,
                        videoUrl
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
    val context = LocalContext.current
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (entry.comment.isNotBlank()) {
                    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Text(entry.comment, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (entry.videoUrl.isNotBlank()) {
                    Button(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.videoUrl)))
                        }
                    }) {
                        Text("開啟教學影片")
                    }
                }
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
    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
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
        shape = MaterialTheme.shapes.small,
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
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = parseColor(colorHex, MaterialTheme.colorScheme.surface),
            contentColor = Color(0xFF111111)
        )
    ) {
        Text(if (selected) "✓" else " ")
    }
}

@Composable
private fun ScrollableDialogContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun ColorPicker(
    label: String,
    colorHex: String,
    onColorChange: (String) -> Unit
) {
    var typed by remember(colorHex) { mutableStateOf(colorHex.normalizeColorInput()) }
    val hsv = colorHex.toHsvOrDefault()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Surface(
                color = parseColor(colorHex, MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.small,
                tonalElevation = 1.dp,
                modifier = Modifier.height(32.dp).weight(0.35f)
            ) {}
        }
        ColorWheel(
            hue = hsv.hue,
            saturation = hsv.saturation,
            value = hsv.value,
            onChange = { next ->
                val hex = next.toHex()
                typed = hex
                onColorChange(hex)
            }
        )
        OutlinedTextField(
            value = typed,
            onValueChange = { value ->
                typed = value.uppercase().take(7)
                typed.normalizeColorInput().takeIf { it.isValidColorHex() }?.let(onColorChange)
            },
            label = { Text("色碼 #RRGGBB") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (HsvColor) -> Unit
) {
    val hueColors = listOf(
        Color.Red,
        Color.Yellow,
        Color.Green,
        Color.Cyan,
        Color.Blue,
        Color.Magenta,
        Color.Red
    )
    val selectedHue = HsvColor(hue, 1f, 1f).toComposeColor()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(hue, saturation, value) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val wheelRadius = minOf(size.width, size.height) * 0.46f
                    val ringWidth = wheelRadius * 0.22f
                    val innerRadius = wheelRadius - ringWidth - 8.dp.toPx()
                    val distance = hypot(offset.x - center.x, offset.y - center.y)
                    if (distance in (wheelRadius - ringWidth)..wheelRadius) {
                        val degrees = ((atan2(offset.y - center.y, offset.x - center.x) * 180f / PI.toFloat()) + 360f) % 360f
                        onChange(HsvColor(degrees, saturation, value))
                    } else if (distance <= innerRadius) {
                        val nextSaturation = ((offset.x - center.x) / innerRadius / 2f + 0.5f).coerceIn(0f, 1f)
                        val nextValue = (0.5f - (offset.y - center.y) / innerRadius / 2f).coerceIn(0f, 1f)
                        onChange(HsvColor(hue, nextSaturation, nextValue))
                    }
                }
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val wheelRadius = minOf(size.width, size.height) * 0.46f
        val ringWidth = wheelRadius * 0.22f
        val innerRadius = wheelRadius - ringWidth - 8.dp.toPx()
        val hueAngle = hue * PI.toFloat() / 180f
        val hueMarkerRadius = wheelRadius - ringWidth / 2f
        val hueMarker = Offset(
            center.x + cos(hueAngle) * hueMarkerRadius,
            center.y + sin(hueAngle) * hueMarkerRadius
        )
        val toneMarker = Offset(
            center.x + (saturation * 2f - 1f) * innerRadius,
            center.y + ((1f - value) * 2f - 1f) * innerRadius
        )

        drawCircle(
            brush = Brush.sweepGradient(hueColors, center),
            radius = wheelRadius - ringWidth / 2f,
            center = center,
            style = Stroke(width = ringWidth, cap = StrokeCap.Round)
        )
        drawCircle(color = selectedHue, radius = innerRadius, center = center)
        drawCircle(
            brush = Brush.horizontalGradient(
                listOf(Color.White, Color.Transparent),
                startX = center.x - innerRadius,
                endX = center.x + innerRadius
            ),
            radius = innerRadius,
            center = center
        )
        drawCircle(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black),
                startY = center.y - innerRadius,
                endY = center.y + innerRadius
            ),
            radius = innerRadius,
            center = center
        )
        drawCircle(color = Color.White, radius = 8.dp.toPx(), center = hueMarker, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color = HsvColor(hue, saturation, value).toComposeColor(), radius = 11.dp.toPx(), center = hueMarker)
        drawCircle(color = Color.White, radius = 7.dp.toPx(), center = toneMarker, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color = Color(0x66000000), radius = 9.dp.toPx(), center = toneMarker, style = Stroke(width = 1.dp.toPx()))
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

private val spectrumColors = listOf(
    "#EF4444",
    "#F97316",
    "#F59E0B",
    "#EAB308",
    "#84CC16",
    "#22C55E",
    "#14B8A6",
    "#06B6D4",
    "#3B82F6",
    "#6366F1",
    "#8B5CF6",
    "#D946EF",
    "#EC4899"
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

private fun String.normalizeColorInput(): String {
    val raw = trim().uppercase()
    val withHash = if (raw.startsWith("#")) raw else "#$raw"
    val digits = withHash.drop(1).filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
    return "#$digits"
}

private fun String.isValidColorHex(): Boolean = Regex("^#[0-9A-F]{6}$").matches(this)

private data class HsvColor(val hue: Float, val saturation: Float, val value: Float)

private fun String.toHsvOrDefault(): HsvColor {
    val base = normalizeColorInput().takeIf { it.isValidColorHex() } ?: "#3B82F6"
    val clean = base.removePrefix("#")
    val r = clean.substring(0, 2).toInt(16) / 255f
    val g = clean.substring(2, 4).toInt(16) / 255f
    val b = clean.substring(4, 6).toInt(16) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> (60f * ((g - b) / delta) + 360f) % 360f
        max == g -> 60f * ((b - r) / delta) + 120f
        else -> 60f * ((r - g) / delta) + 240f
    }
    val saturation = if (max == 0f) 0f else delta / max
    return HsvColor(hue, saturation.coerceIn(0f, 1f), max.coerceIn(0f, 1f))
}

private fun HsvColor.toHex(): String {
    val cleanHue = ((hue % 360f) + 360f) % 360f
    val cleanSaturation = saturation.coerceIn(0f, 1f)
    val cleanValue = value.coerceIn(0f, 1f)
    val chroma = cleanValue * cleanSaturation
    val x = chroma * (1f - kotlin.math.abs((cleanHue / 60f) % 2f - 1f))
    val match = cleanValue - chroma
    val (r1, g1, b1) = when {
        cleanHue < 60f -> Triple(chroma, x, 0f)
        cleanHue < 120f -> Triple(x, chroma, 0f)
        cleanHue < 180f -> Triple(0f, chroma, x)
        cleanHue < 240f -> Triple(0f, x, chroma)
        cleanHue < 300f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    val r = ((r1 + match) * 255f).roundToInt().coerceIn(0, 255)
    val g = ((g1 + match) * 255f).roundToInt().coerceIn(0, 255)
    val b = ((b1 + match) * 255f).roundToInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

private fun HsvColor.toComposeColor(): Color = parseColor(toHex(), Color.White)

private fun toneColors(colorHex: String): List<String> {
    val base = colorHex.normalizeColorInput().takeIf { it.isValidColorHex() } ?: "#3B82F6"
    val clean = base.removePrefix("#")
    val r = clean.substring(0, 2).toInt(16)
    val g = clean.substring(2, 4).toInt(16)
    val b = clean.substring(4, 6).toInt(16)
    return listOf(0.25f, 0.45f, 0.65f, 0.85f, 1.0f).map { mix ->
        val nr = (255 - ((255 - r) * mix)).toInt().coerceIn(0, 255)
        val ng = (255 - ((255 - g) * mix)).toInt().coerceIn(0, 255)
        val nb = (255 - ((255 - b) * mix)).toInt().coerceIn(0, 255)
        "#%02X%02X%02X".format(nr, ng, nb)
    }
}

private fun TagSchedule.label(): String = when (this) {
    TagSchedule.Daily -> "每日"
    TagSchedule.Weekly -> "每周"
    TagSchedule.Manual -> "手動"
}

private fun weekdayLabel(day: Int): String = when (day.coerceIn(1, 7)) {
    1 -> "週一"
    2 -> "週二"
    3 -> "週三"
    4 -> "週四"
    5 -> "週五"
    6 -> "週六"
    else -> "週日"
}

private fun LocalDate.weekDates(): List<LocalDate> {
    val monday = minusDays((dayOfWeek.value - 1).toLong())
    return List(7) { monday.plusDays(it.toLong()) }
}

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

package com.hooplog.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HoopLogApp() }
    }
}

data class UiState(
    val today: String = LocalDate.now().toString(),
    val activeDate: String = LocalDate.now().toString(),
    val entries: List<DailyEntry> = emptyList(),
    val weekEntries: Map<String, List<DailyEntry>> = emptyMap(),
    val items: List<TrainingItem> = emptyList(),
    val summaries: List<DaySummary> = emptyList(),
    val todaySession: DaySession = DaySession(LocalDate.now().toString(), null, null, 0),
    val selectedHistory: String? = null,
    val historyEntries: List<DailyEntry> = emptyList(),
    val completedEntries: List<DailyEntry> = emptyList(),
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
        val activeDate = state.activeDate.ifBlank { today }
        val weekDates = LocalDate.now().weekDates()
        trainingStore.ensureEntriesFor(activeDate)
        val selected = state.selectedHistory
        state = state.copy(
            today = today,
            activeDate = activeDate,
            entries = trainingStore.entriesFor(activeDate),
            weekEntries = weekDates.associate { date ->
                val key = date.toString()
                key to trainingStore.entriesFor(key)
            },
            items = trainingStore.activeItems(),
            summaries = trainingStore.summaries(),
            todaySession = trainingStore.sessionFor(activeDate),
            historyEntries = selected?.let { trainingStore.historyEntriesFor(it) } ?: emptyList(),
            completedEntries = trainingStore.completedEntries(),
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
        trainingStore.saveItem(id, title, tag, colorHex, priority, mode, durationSeconds, repsPerSet, sets, restSeconds, comment, videoUrl, state.activeDate)
        reload()
    }

    fun startTodaySession() {
        trainingStore.startSession(state.activeDate)
        reload()
    }

    fun changeActiveDate(date: LocalDate) {
        state = state.copy(activeDate = date.toString())
        reload()
    }

    fun archiveItem(id: Long) {
        trainingStore.archiveItem(id, state.activeDate)
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

    suspend fun uploadGoogleBackup(context: android.content.Context, account: GoogleSignInAccount) {
        runCatching {
            GoogleDriveSync.upload(context, account, trainingStore.exportBackup())
        }.onSuccess { email ->
            state = state.copy(message = "已同步到 Google：$email")
        }.onFailure {
            state = state.copy(message = it.message ?: "Google 同步失敗")
        }
    }

    suspend fun downloadGoogleBackup(context: android.content.Context, account: GoogleSignInAccount) {
        runCatching {
            val json = GoogleDriveSync.download(context, account)
            trainingStore.importBackup(json)
        }.onSuccess {
            reload()
            state = state.copy(message = "已從 Google 還原資料")
        }.onFailure {
            state = state.copy(message = it.message ?: "Google 還原失敗")
        }
    }

    fun showMessage(message: String) {
        state = state.copy(message = message)
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

private val TrainlyBackground = Color(0xFFE8F0F8)
private val TrainlySurface = Color(0xFFF8FAFD)
private val TrainlyPanel = Color(0xFFD4E0EA)
private val TrainlyPrimary = Color(0xFFE26761)
private val TrainlyInk = Color(0xFF2D3948)
private val TrainlyMuted = Color(0xFF7C8998)
private val TrainlySoftAccent = Color(0xFFFFE5E1)

@Composable
fun HoopLogApp(vm: HoopLogViewModel = viewModel()) {
    val ui = vm.state.uiSettings
    val primary = parseColor(ui.primaryColorHex, TrainlyPrimary)
    val surface = parseColor(ui.surfaceColorHex, TrainlySurface)
    val cardRadius = ui.cardRadius.coerceAtLeast(20)
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = primary,
            onPrimary = Color.White,
            secondary = TrainlyPanel,
            background = TrainlyBackground,
            onBackground = TrainlyInk,
            surface = surface,
            onSurface = TrainlyInk,
            surfaceVariant = Color(0xFFEFF4F8),
            onSurfaceVariant = TrainlyMuted
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
            small = RoundedCornerShape((cardRadius - 8).dp),
            medium = RoundedCornerShape(cardRadius.dp),
            large = RoundedCornerShape((cardRadius + 8).dp)
        )
    ) {
        var screen by remember { mutableStateOf(Screen.Today) }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { AppBar(screen) },
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 12.dp)
                        .shadow(18.dp, RoundedCornerShape(36.dp)),
                    shape = RoundedCornerShape(36.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp
                    ) {
                        val itemColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TrainlyInk,
                            selectedTextColor = TrainlyInk,
                            indicatorColor = MaterialTheme.colorScheme.secondary,
                            unselectedIconColor = TrainlyMuted,
                            unselectedTextColor = TrainlyMuted
                        )
                        NavigationBarItem(
                            selected = screen == Screen.Today,
                            onClick = { screen = Screen.Today },
                            icon = { Icon(Icons.Outlined.CheckCircle, null) },
                            label = { Text("今日") },
                            colors = itemColors
                        )
                        NavigationBarItem(
                            selected = screen == Screen.History,
                            onClick = { screen = Screen.History },
                            icon = { Icon(Icons.Outlined.History, null) },
                            label = { Text("回顧") },
                            colors = itemColors
                        )
                        NavigationBarItem(
                            selected = screen == Screen.Settings,
                            onClick = { screen = Screen.Settings },
                            icon = { Icon(Icons.Outlined.Settings, null) },
                            label = { Text("設定") },
                            colors = itemColors
                        )
                    }
                }
            },
            floatingActionButton = {
                if (screen == Screen.Settings) {
                    var editing by remember { mutableStateOf<TrainingItem?>(null) }
                    var showDialog by remember { mutableStateOf(false) }
                    FloatingActionButton(onClick = {
                        editing = null
                        showDialog = true
                    }, containerColor = TrainlyPrimary, contentColor = Color.White, shape = RoundedCornerShape(24.dp)) {
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
                    Screen.Today -> TodayScreen(vm.state, vm::toggle, vm::updateEntryPlan, vm::saveItem, vm::archiveItem, vm::startTodaySession, vm::changeActiveDate)
                    Screen.History -> HistoryScreen(vm.state, vm::selectHistory, vm::updateEntryPlan)
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
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = TrainlyInk
        ),
        title = {
            Text(
                when (screen) {
                    Screen.Today -> "Trainly"
                    Screen.History -> "訓練回顧"
                    Screen.Settings -> "項目與更新"
                }
            )
        }
    )
}

private enum class HomeMode { Day, Week }
private enum class HistoryViewMode { Calendar, Year }

@Composable
private fun TodayScreen(
    state: UiState,
    onToggle: (DailyEntry, Boolean) -> Unit,
    onUpdateEntryPlan: (DailyEntry, TrainingMode, Int, Int, Int, Int, Int?, List<TrainingSetPlan>?) -> Unit,
    onSaveItem: (Long?, String, String, String, Int, TrainingMode, Int, Int, Int, Int, String, String) -> Unit,
    onArchiveItem: (Long) -> Unit,
    onStartSession: () -> Unit,
    onChangeDate: (LocalDate) -> Unit
) {
    val entries = state.entries
    val activeDate = LocalDate.parse(state.activeDate)
    val today = LocalDate.parse(state.today)
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onChangeDate(activeDate.minusDays(1)) }) { Text("<") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(activeDate.toString(), style = MaterialTheme.typography.titleMedium)
                Text(if (activeDate == today) "今天" else "補記日期", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onChangeDate(activeDate.plusDays(1)) }) { Text(">") }
        }
        Spacer(Modifier.height(8.dp))
        Text("$completed / ${entries.size}", style = MaterialTheme.typography.displaySmall)
        Text(if (activeDate == today) "今日完成" else "補記完成", style = MaterialTheme.typography.bodyMedium)
        Text(
            if (state.todaySession.startedAt == null) "尚未開始計時" else "訓練時長 ${formatDuration(state.todaySession.durationSeconds)}",
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
                tonalElevation = 0.dp,
                color = if (isToday) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(if (isToday) 14.dp else 7.dp, MaterialTheme.shapes.medium)
                    .combinedClickable(
                        onClick = { detailDate = date },
                        onLongClick = { detailDate = date }
                    )
            ) {
                Row(Modifier.padding(if (isToday) 18.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
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
            tonalElevation = 0.dp,
            color = parseColor(colorHex, MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (onEdit != null || onDelete != null) menuOpen = true
                    }
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
private fun HistoryScreen(
    state: UiState,
    onSelect: (String) -> Unit,
    onUpdateEntryPlan: (DailyEntry, TrainingMode, Int, Int, Int, Int, Int?, List<TrainingSetPlan>?) -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var viewMode by remember { mutableStateOf(HistoryViewMode.Calendar) }
    var heatmapYear by remember { mutableStateOf(LocalDate.now().year) }
    var detailDate by remember { mutableStateOf<String?>(null) }
    var editingEntry by remember { mutableStateOf<DailyEntry?>(null) }
    val summaries = remember(state.summaries) { state.summaries.associateBy { it.date } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceButton("月曆", viewMode == HistoryViewMode.Calendar) { viewMode = HistoryViewMode.Calendar }
            ChoiceButton("年度", viewMode == HistoryViewMode.Year) { viewMode = HistoryViewMode.Year }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                if (viewMode == HistoryViewMode.Calendar) month = month.minusMonths(1) else heatmapYear -= 1
            }) { Text("<") }
            Text(
                if (viewMode == HistoryViewMode.Calendar) "${month.year}-${month.monthValue.toString().padStart(2, '0')}" else heatmapYear.toString(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = {
                if (viewMode == HistoryViewMode.Calendar) month = month.plusMonths(1) else heatmapYear += 1
            }) { Text(">") }
        }
        TrainingTrendChart(month, state.completedEntries, summaries)
        if (viewMode == HistoryViewMode.Calendar) {
            CalendarMonth(month, summaries) { date ->
                onSelect(date)
                detailDate = date
            }
        } else {
            ContributionHeatmap(
                year = heatmapYear,
                entries = state.completedEntries,
                onSelect = { date ->
                    onSelect(date)
                    detailDate = date
                }
            )
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
                            onClick = {
                                editingEntry = entry
                                detailDate = null
                            }
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
    editingEntry?.let { entry ->
        TrainingTimerDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onPlanChange = { mode, duration, reps, sets, rest, completed, plans ->
                onUpdateEntryPlan(entry, mode, duration, reps, sets, rest, completed, plans)
            },
            onFinish = { editingEntry = null }
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
private fun TrainingTrendChart(
    month: YearMonth,
    entries: List<DailyEntry>,
    summaries: Map<String, DaySummary>
) {
    val monthDays = remember(month, summaries) {
        (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day).toString()
            date to summaries[date]
        }
    }
    val monthEntries = remember(month, entries) {
        entries.filter { YearMonth.from(LocalDate.parse(it.date)) == month }
    }
    val series = remember(monthEntries) {
        monthEntries
            .groupBy { it.itemId }
            .map { (_, itemEntries) ->
                val first = itemEntries.first()
                TrendSeries(first.title, first.colorHex, itemEntries.groupingBy { it.date }.eachCount())
            }
            .sortedBy { it.title }
    }
    val maxCompleted = monthDays.maxOfOrNull { (date, _) ->
        series.sumOf { it.counts[date] ?: 0 }
    }?.coerceAtLeast(1) ?: 1
    val totalCompleted = monthDays.sumOf { it.second?.completed ?: 0 }
    val totalSeconds = monthDays.sumOf { it.second?.durationSeconds ?: 0 }
    val primaryColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surface
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("趨勢", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("$totalCompleted 次 · ${formatDuration(totalSeconds)}", style = MaterialTheme.typography.bodySmall)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val barGap = 3.dp.toPx()
            val chartHeight = size.height - 18.dp.toPx()
            val barWidth = ((size.width - barGap * (monthDays.size - 1)) / monthDays.size).coerceAtLeast(2.dp.toPx())
            monthDays.forEachIndexed { index, (date, summary) ->
                val left = index * (barWidth + barGap)
                if ((summary?.completed ?: 0) == 0) {
                    drawRoundRect(
                        color = emptyColor,
                        topLeft = Offset(left, chartHeight - 2.dp.toPx()),
                        size = Size(barWidth, 2.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
                var stackBottom = chartHeight
                series.forEach { item ->
                    val count = item.counts[date] ?: 0
                    if (count > 0) {
                        val color = parseColor(item.colorHex, primaryColor)
                        val barHeight = (chartHeight * count.toFloat() / maxCompleted.toFloat()).coerceAtLeast(4.dp.toPx())
                        stackBottom -= barHeight
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(left, stackBottom),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(2.dp.toPx())
                        )
                    }
                }
            }
            series.forEach { item ->
                val color = parseColor(item.colorHex, primaryColor)
                var lastPoint: Offset? = null
                monthDays.forEachIndexed { index, (date, _) ->
                    val count = item.counts[date] ?: 0
                    if (count > 0) {
                        val x = index * (barWidth + barGap) + barWidth / 2
                        val y = chartHeight - (chartHeight * count.toFloat() / maxCompleted.toFloat())
                        val point = Offset(x, y)
                        lastPoint?.let {
                            drawLine(color = color, start = it, end = point, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        }
                        drawCircle(color = color, radius = 3.dp.toPx(), center = point)
                        lastPoint = point
                    }
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(series.take(8), key = { it.title }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        color = parseColor(item.colorHex, primaryColor),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(10.dp).fillParentMaxWidth(0.035f)
                    ) {}
                    Text(item.title, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private data class TrendSeries(
    val title: String,
    val colorHex: String,
    val counts: Map<String, Int>
)

@Composable
private fun ContributionHeatmap(
    year: Int,
    entries: List<DailyEntry>,
    onSelect: (String) -> Unit
) {
    val start = remember(year) { LocalDate.of(year, 1, 1) }
    val end = remember(year) { LocalDate.of(year, 12, 31) }
    val leading = start.dayOfWeek.value % 7
    val dates = remember(year) {
        List(leading) { null } + generateSequence(start) { date ->
            date.plusDays(1).takeIf { !it.isAfter(end) }
        }.toList()
    }
    val weeks = remember(dates) { dates.chunked(7) }
    val counts = remember(year, entries) {
        entries
            .filter { LocalDate.parse(it.date).year == year }
            .groupingBy { it.date }
            .eachCount()
    }
    val total = counts.values.sum()
    val maxCount = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$total 次訓練 in $year", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(weeks.size) { weekIndex ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(7) { dayIndex ->
                        val date = weeks[weekIndex].getOrNull(dayIndex)
                        val count = date?.let { counts[it.toString()] ?: 0 } ?: 0
                        val level = if (date == null || count == 0) 0 else ((count.toFloat() / maxCount) * 4).roundToInt().coerceIn(1, 4)
                        Surface(
                            color = contributionColor(level),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .height(14.dp)
                                .fillParentMaxWidth(0.032f)
                                .clickable(enabled = date != null && count > 0) {
                                    date?.let { onSelect(it.toString()) }
                                }
                        ) {}
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Less", style = MaterialTheme.typography.bodySmall)
            (0..4).forEach { level ->
                Surface(
                    color = contributionColor(level),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(12.dp).fillMaxWidth(0.035f)
                ) {}
            }
            Text("More", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun contributionColor(level: Int): Color = when (level) {
    1 -> Color(0xFFFFD8D2)
    2 -> Color(0xFFF2A8A0)
    3 -> Color(0xFFE26761)
    4 -> Color(0xFFB94A45)
    else -> Color(0xFFEFF4F8)
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
                        tonalElevation = 0.dp,
                        shape = RoundedCornerShape(18.dp),
                        color = if (summary == null) MaterialTheme.colorScheme.surface else TrainlySoftAccent,
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
    val scope = rememberCoroutineScope()
    var googleAccount by remember { mutableStateOf(GoogleDriveSync.lastSignedInAccount(context)) }
    var syncing by remember { mutableStateOf(false) }
    var googleStatus by remember { mutableStateOf(googleAccount?.email?.let { "已登入：$it" } ?: "尚未登入 Google") }
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
        }.onSuccess { account ->
            googleAccount = account
            googleStatus = "已登入：${account.email.orEmpty()}"
            vm.showMessage("已登入 Google：${account.email.orEmpty()}")
        }.onFailure {
            googleStatus = googleSignInErrorMessage(it, result.resultCode)
            vm.showMessage(googleStatus)
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SettingsDrawerSection("訓練項目", "${vm.state.items.size} 個項目", initiallyExpanded = true) {
                vm.state.items.forEach { item ->
                    EditableItemRow(
                        item = item,
                        onEdit = {
                            editItem = item
                            showDialog = true
                        },
                        onDelete = { vm.archiveItem(item.id) }
                    )
                }
                if (vm.state.items.isEmpty()) {
                    Text("尚未新增訓練項目", style = MaterialTheme.typography.bodyMedium, color = TrainlyMuted)
                }
            }
        }
        item {
            SettingsDrawerSection("標籤管理", "${vm.state.tags.size} 個標籤") {
                vm.state.tags.forEach { tag ->
                    Surface(
                        color = parseColor(tag.colorHex, MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth().shadow(8.dp, MaterialTheme.shapes.medium)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        editTag = null
                        showTagDialog = true
                    }) { Text("新增標籤") }
                    Button(onClick = { showAdvancedDialog = true }) { Text("進階設定") }
                }
            }
        }
        item {
            SettingsDrawerSection("GitHub 更新", "${owner}/${repo}") {
                OutlinedTextField(owner, { owner = it }, label = { Text("Owner") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(repo, { repo = it }, label = { Text("Repo") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveUpdateSettings(owner, repo) }) { Text("儲存") }
                    Button(enabled = !checking, onClick = { checking = true }) {
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
        }
        item {
            val account = googleAccount
            SettingsDrawerSection("Google 同步", googleStatus) {
                Text(googleStatus, style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Button(enabled = !syncing, onClick = {
                            googleStatus = "正在開啟 Google 登入..."
                            val client = GoogleSignIn.getClient(context, GoogleDriveSync.signInOptions)
                            googleLauncher.launch(client.signInIntent)
                        }) { Text(if (account == null) "登入 Google" else "切換帳號") }
                    }
                    item {
                        Button(enabled = account != null && !syncing, onClick = {
                            val active = googleAccount ?: return@Button
                            syncing = true
                            googleStatus = "正在上傳到 Google..."
                            scope.launch {
                                vm.uploadGoogleBackup(context, active)
                                googleStatus = vm.state.message ?: "同步完成"
                                syncing = false
                            }
                        }) { Text("上傳同步") }
                    }
                    item {
                        Button(enabled = account != null && !syncing, onClick = {
                            val active = googleAccount ?: return@Button
                            syncing = true
                            googleStatus = "正在從 Google 下載..."
                            scope.launch {
                                vm.downloadGoogleBackup(context, active)
                                googleStatus = vm.state.message ?: "還原完成"
                                syncing = false
                            }
                        }) { Text("下載還原") }
                    }
                    item {
                        TextButton(enabled = account != null && !syncing, onClick = {
                            GoogleSignIn.getClient(context, GoogleDriveSync.signInOptions).signOut()
                            googleAccount = null
                            googleStatus = "已登出 Google"
                            vm.showMessage(googleStatus)
                        }) { Text("登出") }
                    }
                }
                Text("同步會使用 Google Drive AppData 私有空間。下載還原會覆蓋本機資料。", style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun SettingsDrawerSection(
    title: String,
    subtitle: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Surface(
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().shadow(8.dp, MaterialTheme.shapes.medium)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TrainlyMuted)
                }
                Text(if (expanded) "收合" else "展開", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
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
            tonalElevation = 0.dp,
            color = parseColor(item.colorHex, MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { menuOpen = true }
                )
        ) {
            Column(Modifier.padding(16.dp)) {
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
                    ChoiceButton("Soft Active", style == "Soft Active") { style = "Soft Active" }
                }
                ColorPicker(label = "主色", colorHex = primary, onColorChange = { primary = it })
                ColorPicker(label = "介面底色", colorHex = surface, onColorChange = { surface = it })
                OutlinedTextField(radius, { radius = it.filter(Char::isDigit).take(2) }, label = { Text("卡片弧度 8-32") }, singleLine = true)
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
                        cardRadius = radius.toIntOrNull()?.coerceIn(8, 32) ?: 24,
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
                    value = setCount.toString(),
                    onDecrease = { applyPlan(nextSets = (setCount - 1).coerceAtLeast(completedSets.coerceAtLeast(1))) },
                    onIncrease = { applyPlan(nextSets = setCount + 1) },
                    onValueInput = { input ->
                        input.toIntOrNull()?.let { applyPlan(nextSets = it.coerceAtLeast(completedSets.coerceAtLeast(1))) }
                    }
                )
                if (mode == TrainingMode.Time) {
                    CounterControl(
                        label = "每組秒數",
                        value = workSeconds.toString(),
                        onDecrease = { applyPlan(nextWork = workSeconds - 30) },
                        onIncrease = { applyPlan(nextWork = workSeconds + 30) },
                        onValueInput = { input ->
                            input.toIntOrNull()?.let { applyPlan(nextWork = it) }
                        }
                    )
                } else {
                    CounterControl(
                        label = "每組次數",
                        value = repsPerSet.toString(),
                        onDecrease = { applyPlan(nextReps = repsPerSet - 1) },
                        onIncrease = { applyPlan(nextReps = repsPerSet + 1) },
                        onValueInput = { input ->
                            input.toIntOrNull()?.let { applyPlan(nextReps = it) }
                        }
                    )
                }
                CounterControl(
                    label = "休息秒數",
                    value = restSeconds.toString(),
                    onDecrease = { applyPlan(nextRest = restSeconds - 15) },
                    onIncrease = { applyPlan(nextRest = restSeconds + 15) },
                    onValueInput = { input ->
                        input.toIntOrNull()?.let { applyPlan(nextRest = it) }
                    }
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
    Surface(
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().shadow(7.dp, MaterialTheme.shapes.medium)
    ) {
        Column(Modifier.padding(if (plan.completed) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "第 $setNumber 組",
                        style = if (plan.completed) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
                    )
                    Text(
                        when {
                            plan.completed -> "已完成"
                            resting -> "休息 ${formatDuration(remaining)}"
                            plan.mode == TrainingMode.Time -> formatDuration(remaining)
                            else -> "${plan.reps} 次"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (plan.completed) {
                    TextButton(onClick = onUndo) {
                        Text("復原")
                    }
                } else {
                    Checkbox(checked = false, onCheckedChange = { onComplete() })
                }
            }
            if (!plan.completed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton("時間", plan.mode == TrainingMode.Time) { onModeChange(TrainingMode.Time) }
                    ChoiceButton("次數", plan.mode == TrainingMode.Reps) { onModeChange(TrainingMode.Reps) }
                }
                if (plan.mode == TrainingMode.Time) {
                    CounterControl(
                        label = "本組秒數",
                        value = plan.durationSeconds.toString(),
                        onDecrease = { onDurationChange(plan.durationSeconds - 30) },
                        onIncrease = { onDurationChange(plan.durationSeconds + 30) },
                        onValueInput = { input ->
                            input.toIntOrNull()?.let(onDurationChange)
                        }
                    )
                } else {
                    CounterControl(
                        label = "本組次數",
                        value = plan.reps.toString(),
                        onDecrease = { onRepsChange(plan.reps - 1) },
                        onIncrease = { onRepsChange(plan.reps + 1) },
                        onValueInput = { input ->
                            input.toIntOrNull()?.let(onRepsChange)
                        }
                    )
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
    onIncrease: () -> Unit,
    onValueInput: ((String) -> Unit)? = null
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
        if (onValueInput == null) {
            Text(value, style = MaterialTheme.typography.titleMedium)
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = { onValueInput(it.filter(Char::isDigit).take(4)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.75f)
            )
        }
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
        shape = RoundedCornerShape(28.dp),
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

private fun googleSignInErrorMessage(error: Throwable, resultCode: Int? = null): String {
    val apiError = error as? ApiException
    return when (apiError?.statusCode) {
        10 -> "Google OAuth 尚未設定，請在 Google Cloud 建立 Android OAuth client 並填入 app 簽章 SHA-1"
        12501 -> "Google 登入已取消"
        12500 -> "Google 登入失敗，請確認 Google Cloud 的 Android OAuth、Drive API、OAuth 同意畫面與測試使用者已設定"
        else -> error.message ?: "Google 登入失敗（resultCode: ${resultCode ?: "unknown"}）"
    }
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

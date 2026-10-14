package kr.ac.dongguk.classalert

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kr.ac.dongguk.classalert.data.*
import kr.ac.dongguk.classalert.notification.NotificationChannels
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter

data class UiState(val events: List<EventEntity> = emptyList(), val syncing: Boolean = false, val message: String? = null)

class MainViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val root = app as ClassAlertApp
    private val status = MutableStateFlow(Pair(false, null as String?))
    val home = combine(root.repository.upcoming(), status) { events, s -> UiState(events, s.first, s.second) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
    val all = root.repository.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hidden = root.repository.hidden().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings get() = root.settings
    init { if (settings.calendarUrl != null) sync() }
    fun sync() = viewModelScope.launch {
        status.value = true to null
        val result = root.repository.sync()
        status.value = false to result.fold({ it.toString() + "개 일정을 동기화했습니다." }, { it.message ?: "동기화 실패" })
    }
    fun save(url: String, offsets: String) {
        root.settings.calendarUrl = url.trim()
        root.settings.alertOffsetsHours = offsets.split(',').mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }
        sync()
    }
    fun hide(event: EventEntity) = viewModelScope.launch { root.repository.hide(event) }
    fun restore(event: EventEntity) = viewModelScope.launch { root.repository.restore(event) }
    fun setCourseAlias(code: String, name: String) = viewModelScope.launch {
        root.settings.setCourseAlias(code, name)
        root.repository.restoreAlarms()
    }
    fun scheduleTestAlarm(): Boolean = root.alarms.scheduleTest()
}

class MainActivity : ComponentActivity() {
    private val vm by viewModels<MainViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF6750A4))) { ClassAlertUi(vm, ::sendTestNotification) } }
    }
    private fun sendTestNotification() {
        val n = NotificationCompat.Builder(this, NotificationChannels.DEADLINES).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ClassAlert 테스트 알림").setContentText("알림이 정상적으로 설정되었습니다.").build()
        getSystemService(NotificationManager::class.java).notify(101, n)
    }
}

@Composable
private fun ClassAlertUi(vm: MainViewModel, testNotification: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(bottomBar = {
        NavigationBar {
            listOf("홈" to Icons.Default.Home, "전체 일정" to Icons.Default.DateRange, "설정" to Icons.Default.Settings).forEachIndexed { i, item ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(item.second, null) }, label = { Text(item.first) })
            }
        }
    }) { pad -> Box(Modifier.padding(pad)) { when (tab) { 0 -> Home(vm); 1 -> AllEvents(vm); else -> Settings(vm, testNotification) } } }
}

@Composable private fun Home(vm: MainViewModel) {
    val state by vm.home.collectAsState()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("ClassAlert", style = MaterialTheme.typography.headlineMedium); Text("앞으로 2주 일정", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = vm::sync) { Icon(Icons.Default.Refresh, "동기화") }
        }
        if (state.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let { Text(it, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary) }
        if (state.events.isEmpty() && !state.syncing) EmptyState() else EventList(state.events, vm::hide)
    }
}

@Composable private fun AllEvents(vm: MainViewModel) {
    val events by vm.all.collectAsState()
    val hidden by vm.hidden.collectAsState()
    var showHidden by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("전체 일정", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (hidden.isNotEmpty()) TextButton(onClick = { showHidden = true }) { Text("숨긴 일정 ${hidden.size}개") }
        }
        Spacer(Modifier.height(12.dp))
        if (events.isEmpty()) EmptyState() else EventList(events, vm::hide)
    }
    if (showHidden) AlertDialog(
        onDismissRequest = { showHidden = false },
        title = { Text("숨긴 일정") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(hidden, key = { it.uid }) { event ->
                    val course = CourseCatalog.label(LocalContext.current, event.category)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            course?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                            Text(event.title)
                            Text(formatDate(event.startsAt), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { vm.restore(event) }) { Text("복원") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showHidden = false }) { Text("닫기") } }
    )
}

@Composable private fun EventList(events: List<EventEntity>, hide: (EventEntity) -> Unit) = LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    items(events, key = { it.uid }) { e ->
        var showDetails by remember { mutableStateOf(false) }
        val course = CourseCatalog.label(LocalContext.current, e.category)
        Card(onClick = { showDetails = true }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (e.type == EventType.ASSIGNMENT) Icons.Default.Edit else Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(if (e.type == EventType.LECTURE_END) "온라인 강의 학습 종료 예정" else if (e.type == EventType.ASSIGNMENT) "과제" else e.type.name, style = MaterialTheme.typography.labelMedium)
                course?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                Text(e.title, style = MaterialTheme.typography.titleMedium)
                Text(formatDate(e.startsAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("눌러서 상세 보기", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        } }
        if (showDetails) AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(e.title) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (e.type == EventType.LECTURE_END) "온라인 강의 학습 종료 예정" else if (e.type == EventType.ASSIGNMENT) "과제" else "일정")
                course?.let { Text("과목: $it") }
                Text("일시: ${formatDate(e.startsAt)}")
                if (e.description.isNotBlank()) Text(e.description)
                Text("숨기면 홈과 전체 일정에서 사라지며 예약 알림도 취소됩니다. 숨긴 일정 목록에서 언제든 복원할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = { hide(e); showDetails = false }) { Text("이 일정 숨기기", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDetails = false }) { Text("닫기") } }
        )
    }
}

@Composable private fun EmptyState() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("표시할 일정이 없습니다.\n설정에서 캘린더 URL을 등록해 주세요.") }

@Composable private fun Settings(vm: MainViewModel, testNotification: () -> Unit) {
    val events by vm.all.collectAsState()
    var url by remember { mutableStateOf(vm.settings.calendarUrl.orEmpty()) }
    var offsets by remember { mutableStateOf(vm.settings.alertOffsetsHours.joinToString(",")) }
    var assignments by remember { mutableStateOf(vm.settings.assignmentAlerts) }
    var lectures by remember { mutableStateOf(vm.settings.lectureAlerts) }
    var scheduledMessage by remember { mutableStateOf<String?>(null) }
    var scheduleAfterPermission by remember { mutableStateOf(false) }
    var showCourseNames by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (scheduleAfterPermission) {
                val exact = vm.scheduleTestAlarm()
                scheduledMessage = if (exact) "1분 뒤 예약 알림을 보냅니다." else "1분 뒤 알림을 예약했습니다. 정확한 알람 권한이 없어 조금 늦을 수 있습니다."
            } else testNotification()
        }
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("설정", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("eClass 캘린더 내보내기 URL") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Text("URL에는 개인 토큰이 포함됩니다. 기기 내 Android Keystore로 암호화하며 화면에는 숨깁니다.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(offsets, { offsets = it }, Modifier.fillMaxWidth(), label = { Text("알림 시점(시간 전, 쉼표 구분)") }, supportingText = { Text("기본값: 72,24,3") })
        Row(verticalAlignment = Alignment.CenterVertically) { Text("과제 알림", Modifier.weight(1f)); Switch(assignments, { assignments = it; vm.settings.assignmentAlerts = it }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("온라인 강의 종료 알림", Modifier.weight(1f)); Switch(lectures, { lectures = it; vm.settings.lectureAlerts = it }) }
        OutlinedButton(onClick = { showCourseNames = true }, Modifier.fillMaxWidth()) { Text("과목명 관리") }
        Button(onClick = { vm.save(url, offsets) }, Modifier.fillMaxWidth()) { Text("저장하고 동기화") }
        OutlinedButton(onClick = {
            val context = vm.getApplication<android.app.Application>()
            scheduleAfterPermission = false
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else testNotification()
        }, Modifier.fillMaxWidth()) { Text("테스트 알림 보내기") }
        OutlinedButton(onClick = {
            val context = vm.getApplication<android.app.Application>()
            scheduleAfterPermission = true
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val exact = vm.scheduleTestAlarm()
                scheduledMessage = if (exact) "1분 뒤 예약 알림을 보냅니다. 앱을 닫고 기다려 보세요." else "1분 뒤 알림을 예약했습니다. 정확한 알람 권한이 없어 조금 늦을 수 있습니다."
            }
        }, Modifier.fillMaxWidth()) { Text("1분 뒤 예약 알림 테스트") }
        scheduledMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Text("백그라운드 동기화는 배터리 정책에 따라 늦어질 수 있습니다. 정확한 알람 권한이 없으면 시스템이 알림을 지연할 수 있으며, 앱을 강제 종료한 경우 제조사 정책에 따라 동작이 제한될 수 있습니다.", style = MaterialTheme.typography.bodySmall)
    }
    if (showCourseNames) CourseNameDialog(events, vm, { showCourseNames = false })
}

@Composable private fun CourseNameDialog(events: List<EventEntity>, vm: MainViewModel, close: () -> Unit) {
    val context = LocalContext.current
    val codes = remember(events) { events.mapNotNull { CourseCatalog.extractCode(it.category) }.distinct().sorted() }
    val values = remember(codes) { mutableStateMapOf<String, String>().apply {
        codes.forEach { code -> put(code, vm.settings.courseAlias(code) ?: CourseCatalog.label(context, code).orEmpty()) }
    } }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("과목명 관리") },
        text = {
            if (codes.isEmpty()) Text("먼저 일정을 동기화해 주세요.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(codes, key = { it }) { code ->
                    OutlinedTextField(
                        value = values[code].orEmpty(),
                        onValueChange = { values[code] = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(code) },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { codes.forEach { vm.setCourseAlias(it, values[it].orEmpty()) }; close() }) { Text("저장") } },
        dismissButton = { TextButton(onClick = close) { Text("취소") } }
    )
}

private fun formatDate(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm"))


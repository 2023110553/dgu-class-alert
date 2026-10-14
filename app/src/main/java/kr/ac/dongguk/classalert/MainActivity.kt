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
    Column(Modifier.fillMaxSize().padding(20.dp)) { Text("전체 일정", style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(12.dp)); if (events.isEmpty()) EmptyState() else EventList(events, vm::hide) }
}

@Composable private fun EventList(events: List<EventEntity>, hide: (EventEntity) -> Unit) = LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    items(events, key = { it.uid }) { e ->
        Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (e.type == EventType.ASSIGNMENT) Icons.Default.Edit else Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(if (e.type == EventType.LECTURE_END) "온라인 강의 학습 종료 예정" else if (e.type == EventType.ASSIGNMENT) "과제" else e.type.name, style = MaterialTheme.typography.labelMedium)
                Text(e.title, style = MaterialTheme.typography.titleMedium)
                Text(formatDate(e.startsAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { hide(e) }) { Icon(Icons.Default.VisibilityOff, "숨기기") }
        } }
    }
}

@Composable private fun EmptyState() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("표시할 일정이 없습니다.\n설정에서 캘린더 URL을 등록해 주세요.") }

@Composable private fun Settings(vm: MainViewModel, testNotification: () -> Unit) {
    var url by remember { mutableStateOf(vm.settings.calendarUrl.orEmpty()) }
    var offsets by remember { mutableStateOf(vm.settings.alertOffsetsHours.joinToString(",")) }
    var assignments by remember { mutableStateOf(vm.settings.assignmentAlerts) }
    var lectures by remember { mutableStateOf(vm.settings.lectureAlerts) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) testNotification() }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("설정", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("eClass 캘린더 내보내기 URL") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Text("URL에는 개인 토큰이 포함됩니다. 기기 내 Android Keystore로 암호화하며 화면에는 숨깁니다.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(offsets, { offsets = it }, Modifier.fillMaxWidth(), label = { Text("알림 시점(시간 전, 쉼표 구분)") }, supportingText = { Text("기본값: 72,24,3") })
        Row(verticalAlignment = Alignment.CenterVertically) { Text("과제 알림", Modifier.weight(1f)); Switch(assignments, { assignments = it; vm.settings.assignmentAlerts = it }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("온라인 강의 종료 알림", Modifier.weight(1f)); Switch(lectures, { lectures = it; vm.settings.lectureAlerts = it }) }
        Button(onClick = { vm.save(url, offsets) }, Modifier.fillMaxWidth()) { Text("저장하고 동기화") }
        OutlinedButton(onClick = {
            val context = vm.getApplication<android.app.Application>()
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else testNotification()
        }, Modifier.fillMaxWidth()) { Text("테스트 알림 보내기") }
        Text("백그라운드 동기화는 배터리 정책에 따라 늦어질 수 있습니다. 정확한 알람 권한이 없으면 시스템이 알림을 지연할 수 있으며, 앱을 강제 종료한 경우 제조사 정책에 따라 동작이 제한될 수 있습니다.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatDate(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm"))


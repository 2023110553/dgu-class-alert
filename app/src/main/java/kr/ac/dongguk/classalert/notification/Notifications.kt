package kr.ac.dongguk.classalert.notification

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import kr.ac.dongguk.classalert.MainActivity
import kr.ac.dongguk.classalert.data.*
import kotlinx.coroutines.*
import java.time.Duration

object NotificationChannels {
    const val DEADLINES = "deadlines"
    const val SYNC_ERRORS = "sync_errors"
    fun create(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(NotificationChannel(DEADLINES, "학습 마감 알림", NotificationManager.IMPORTANCE_HIGH).apply { description = "과제와 온라인 강의 학습 종료 예정 알림" })
            createNotificationChannel(NotificationChannel(SYNC_ERRORS, "동기화 문제", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "토큰 만료 등 eClass 동기화 문제" })
        }
    }
}

class AlarmScheduler(private val context: Context) {
    private val manager get() = context.getSystemService(AlarmManager::class.java)
    fun schedule(event: EventEntity, settings: SecureSettings) {
        if (!event.notificationEnabled || event.hidden || !enabled(event.type, settings)) { cancel(event); return }
        settings.alertOffsetsHours.forEach { hours ->
            val at = event.startsAt - Duration.ofHours(hours).toMillis()
            if (at > System.currentTimeMillis()) {
                val pending = pending(event, hours, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) ?: return@forEach
                if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }
    }
    fun cancel(event: EventEntity) = listOf(72L, 24L, 3L).plus(SecureSettings(context).alertOffsetsHours).distinct().forEach {
        pending(event, it, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let(manager::cancel)
    }
    fun scheduleTest(delayMillis: Long = 60_000L): Boolean {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra("uid", "scheduled-test")
            .putExtra("title", "1분 예약 알림이 정상 작동했습니다.")
            .putExtra("type", EventType.OTHER.name)
            .putExtra("hours", 0L)
            .putExtra("scheduled_test", true)
        val pending = PendingIntent.getBroadcast(context, TEST_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = System.currentTimeMillis() + delayMillis
        val exact = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
        if (exact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        return exact
    }
    private fun enabled(type: EventType, s: SecureSettings) = when (type) {
        EventType.ASSIGNMENT -> s.assignmentAlerts
        EventType.LECTURE_END -> s.lectureAlerts
        else -> false
    }
    private fun pending(event: EventEntity, hours: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra("uid", event.uid).putExtra("title", event.title)
            .putExtra("type", event.type.name).putExtra("hours", hours)
            .putExtra("course", CourseCatalog.label(context, event.category))
        return PendingIntent.getBroadcast(context, (event.uid + ":" + hours).hashCode(), intent, flags)
    }
    companion object { private const val TEST_REQUEST_CODE = 910_001 }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "학습 일정"
        val hours = intent.getLongExtra("hours", 0)
        val type = runCatching { EventType.valueOf(intent.getStringExtra("type")!!) }.getOrDefault(EventType.OTHER)
        val isTest = intent.getBooleanExtra("scheduled_test", false)
        val course = intent.getStringExtra("course")
        val label = when {
            isTest -> "ClassAlert 예약 알림 테스트"
            type == EventType.ASSIGNMENT -> "과제 마감"
            else -> "온라인 강의 학습 종료 예정"
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, NotificationChannels.DEADLINES)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(
                if (isTest) label else (course?.let { "[$it] " }.orEmpty() + label + " " + formatOffset(hours))
            )
            .setContentText(title).setStyle(NotificationCompat.BigTextStyle().bigText(title)).setAutoCancel(true).setContentIntent(open).build()
        context.getSystemService(NotificationManager::class.java).notify((intent.getStringExtra("uid") + hours).hashCode(), notification)
    }
    private fun formatOffset(hours: Long) = if (hours % 24L == 0L) (hours / 24).toString() + "일 전" else hours.toString() + "시간 전"
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { (context.applicationContext as kr.ac.dongguk.classalert.ClassAlertApp).repository.restoreAlarms() } finally { result.finish() }
        }
    }
}


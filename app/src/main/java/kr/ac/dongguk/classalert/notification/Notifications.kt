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
    fun create(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(DEADLINES, "학습 마감 알림", NotificationManager.IMPORTANCE_HIGH).apply { description = "과제와 온라인 강의 학습 종료 예정 알림" }
        )
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
    private fun enabled(type: EventType, s: SecureSettings) = when (type) {
        EventType.ASSIGNMENT -> s.assignmentAlerts
        EventType.LECTURE_END -> s.lectureAlerts
        else -> false
    }
    private fun pending(event: EventEntity, hours: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra("uid", event.uid).putExtra("title", event.title)
            .putExtra("type", event.type.name).putExtra("hours", hours)
        return PendingIntent.getBroadcast(context, (event.uid + ":" + hours).hashCode(), intent, flags)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "학습 일정"
        val hours = intent.getLongExtra("hours", 0)
        val type = runCatching { EventType.valueOf(intent.getStringExtra("type")!!) }.getOrDefault(EventType.OTHER)
        val label = if (type == EventType.ASSIGNMENT) "과제 마감" else "온라인 강의 학습 종료 예정"
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, NotificationChannels.DEADLINES)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(label + " " + formatOffset(hours))
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


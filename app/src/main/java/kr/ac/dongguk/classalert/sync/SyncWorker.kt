package kr.ac.dongguk.classalert.sync

import android.content.Context
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kr.ac.dongguk.classalert.ClassAlertApp
import kr.ac.dongguk.classalert.MainActivity
import kr.ac.dongguk.classalert.notification.NotificationChannels

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result = (applicationContext as ClassAlertApp).repository.sync()
        if (result.isSuccess) return Result.success()
        if (runAttemptCount < 3) return Result.retry()
        val open = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.SYNC_ERRORS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("eClass 동기화를 확인해 주세요")
            .setContentText(result.exceptionOrNull()?.message ?: "캘린더 URL을 다시 확인해 주세요.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(result.exceptionOrNull()?.message))
            .setAutoCancel(true).setContentIntent(open).build()
        applicationContext.getSystemService(NotificationManager::class.java).notify(920_001, notification)
        return Result.failure()
    }
    companion object { const val NAME = "daily-eclass-sync" }
}


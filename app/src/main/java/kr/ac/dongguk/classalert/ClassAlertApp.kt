package kr.ac.dongguk.classalert

import android.app.Application
import androidx.work.*
import kr.ac.dongguk.classalert.data.AppDatabase
import kr.ac.dongguk.classalert.data.EventRepository
import kr.ac.dongguk.classalert.data.SecureSettings
import kr.ac.dongguk.classalert.notification.AlarmScheduler
import kr.ac.dongguk.classalert.notification.NotificationChannels
import kr.ac.dongguk.classalert.sync.SyncWorker
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ClassAlertApp : Application() {
    lateinit var repository: EventRepository
    lateinit var settings: SecureSettings
    lateinit var alarms: AlarmScheduler

    override fun onCreate() {
        super.onCreate()
        settings = SecureSettings(this)
        alarms = AlarmScheduler(this)
        repository = EventRepository(AppDatabase.get(this), OkHttpClient(), settings, alarms)
        NotificationChannels.create(this)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        )
    }
}


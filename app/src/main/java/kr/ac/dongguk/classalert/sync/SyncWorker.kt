package kr.ac.dongguk.classalert.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kr.ac.dongguk.classalert.ClassAlertApp

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result = (applicationContext as ClassAlertApp).repository.sync()
        return if (result.isSuccess) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
    companion object { const val NAME = "daily-eclass-sync" }
}


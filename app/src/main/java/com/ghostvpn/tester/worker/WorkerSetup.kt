package com.ghostvpn.tester.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkerSetup {
    private const val WORK_NAME = "vpn_test_work"

    fun setupPeriodicWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Run every 6 hours
        val workRequest = PeriodicWorkRequestBuilder<VpnTestWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    fun triggerNow(context: Context) {
        val request = androidx.work.OneTimeWorkRequestBuilder<VpnTestWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}

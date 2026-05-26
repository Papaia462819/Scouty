package com.scouty.app.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class MapPackDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val trailCode = inputData.getString(InputTrailCode)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()
        val forceMetered = inputData.getBoolean(InputForceMetered, false)
        return when (MapPackRepository.get(applicationContext).downloadRoutePack(trailCode, forceMetered)) {
            MapPackDownloadResult.Ready -> Result.success()
            MapPackDownloadResult.WaitingForConfirmation -> Result.success()
            MapPackDownloadResult.Failed -> Result.failure()
        }
    }

    companion object {
        const val InputTrailCode = "trail_code"
        const val InputForceMetered = "force_metered"
    }
}

package com.vektor.emergency

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vektor.data.local.db.EmergencyQueueDao
import com.vektor.data.remote.VectorGoClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EmergencyRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val emergencyQueueDao: EmergencyQueueDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val unsynced = emergencyQueueDao.getUnsyncedPayloads()
        if (unsynced.isEmpty()) return Result.success()

        var allSuccess = true
        for (payload in unsynced) {
            try {
                val response = VectorGoClient.api.sendEmergencyPayload(payload.payloadJson)
                if (response.isSuccessful) {
                    emergencyQueueDao.markSynced(payload.id)
                } else {
                    allSuccess = false
                }
            } catch (e: Exception) {
                allSuccess = false
            }
        }
        
        return if (allSuccess) Result.success() else Result.retry()
    }
}

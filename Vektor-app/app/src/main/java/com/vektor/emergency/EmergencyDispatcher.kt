package com.vektor.emergency

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vektor.data.local.db.EmergencyPayloadEntity
import com.vektor.data.local.db.EmergencyQueueDao
import com.vektor.data.local.prefs.ProfileDataStore
import com.vektor.data.remote.VectorGoClient
import com.vektor.domain.model.EmergencyPayload
import com.vektor.domain.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyDispatcher @Inject constructor(
    private val profileStore: ProfileDataStore,
    private val emergencyQueueDao: EmergencyQueueDao,
    @ApplicationContext private val context: Context
) {
    private val tag = "EmergencyDispatcher"

    suspend fun dispatch(lat: Double, lng: Double) {
        val profileJson = profileStore.getProfileSync()
        val profile: UserProfile? = profileJson?.let {
            try { Json { ignoreUnknownKeys = true }.decodeFromString(it) } catch (_: Exception) { null }
        }

        val uid = profile?.uid ?: "unknown"
        val responderBrief = buildString {
            if (profile != null) {
                append("Blood: ${profile.bloodGroup}. ")
                if (profile.allergies.isNotEmpty()) append("Allergies: ${profile.allergies.joinToString(", ")}. ")
                if (profile.conditions.isNotEmpty()) append("Conditions: ${profile.conditions.joinToString(", ")}. ")
                if (profile.medications.isNotEmpty()) append("Meds: ${profile.medications.joinToString(", ")}.")
            } else {
                append("No profile data available.")
            }
        }

        val payload = EmergencyPayload(
            uid = uid,
            responderBrief = responderBrief,
            locationLat = lat,
            locationLng = lng,
            timestamp = System.currentTimeMillis(),
            networkStatus = "unknown"
        )

        val payloadJson = Json.encodeToString(payload)
        val entity = EmergencyPayloadEntity(
            uid = uid,
            payloadJson = payloadJson,
            timestamp = payload.timestamp,
            synced = false
        )
        emergencyQueueDao.insertPayload(entity)

        // Try to send to server
        var synced = false
        try {
            val response = VectorGoClient.api.sendEmergencyPayload(payloadJson)
            if (response.isSuccessful) {
                // Mark the most recently inserted record synced
                val unsynced = emergencyQueueDao.getUnsyncedPayloads()
                unsynced.lastOrNull { it.uid == uid && it.timestamp == payload.timestamp }
                    ?.let { emergencyQueueDao.markSynced(it.id) }
                synced = true
            }
        } catch (e: Exception) {
            Log.w(tag, "Network send failed, scheduling retry: ${e.message}")
        }

        if (!synced) {
            val workRequest = OneTimeWorkRequestBuilder<EmergencyRetryWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }

        // Send SMS to emergency contacts
        profile?.emergencyContacts?.forEach { contact ->
            if (contact.phone.isNotBlank()) {
                try {
                    @Suppress("DEPRECATION")
                    val smsManager: SmsManager = SmsManager.getDefault()
                    val message = "EMERGENCY ALERT from ${profile.name}. " +
                        "Location: https://maps.google.com/?q=$lat,$lng. " +
                        "Blood: ${profile.bloodGroup}. " +
                        if (profile.allergies.isNotEmpty()) "Allergies: ${profile.allergies.joinToString(", ")}." else ""
                    smsManager.sendTextMessage(contact.phone, null, message, null, null)
                } catch (e: Exception) {
                    Log.e(tag, "SMS to ${contact.phone} failed: ${e.message}")
                }
            }
        }
    }
}

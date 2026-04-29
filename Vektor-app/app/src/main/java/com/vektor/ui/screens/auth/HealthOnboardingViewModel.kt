package com.vektor.ui.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vektor.data.local.prefs.ProfileDataStore
import com.vektor.domain.model.EmergencyContact
import com.vektor.domain.model.UserProfile
import com.vektor.qr.QrManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class HealthOnboardingViewModel @Inject constructor(
    private val profileStore: ProfileDataStore,
    private val qrManager: QrManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        const val TOTAL_STEPS = 9
    }

    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    // Mutable fields filled in by each step
    var fullName: String = ""
    var dob: String = ""
    var bloodGroup: String = "Unknown"
    var allergies: List<String> = emptyList()
    var conditions: List<String> = emptyList()
    var medications: List<String> = emptyList()
    var emergencyContactName: String = ""
    var emergencyContactPhone: String = ""
    var emergencyContactRelation: String = ""
    var insuranceProvider: String = ""
    var insurancePolicyNo: String = ""
    var medicalRecordPath: String = ""

    fun nextStep() {
        if (_step.value < TOTAL_STEPS - 1) _step.value++
    }

    fun saveProfile(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing: UserProfile? = profileStore.getProfileSync()?.let {
                try { Json { ignoreUnknownKeys = true }.decodeFromString(it) } catch (_: Exception) { null }
            }

            val profile = UserProfile(
                uid = uid,
                name = fullName.ifBlank { existing?.name ?: uid },
                dob = dob.ifBlank { existing?.dob ?: "" },
                bloodGroup = bloodGroup.ifBlank { existing?.bloodGroup ?: "Unknown" },
                allergies = allergies.ifEmpty { existing?.allergies ?: emptyList() },
                conditions = conditions.ifEmpty { existing?.conditions ?: emptyList() },
                medications = medications.ifEmpty { existing?.medications ?: emptyList() },
                emergencyContacts = if (emergencyContactName.isNotBlank()) {
                    listOf(EmergencyContact(
                        name = emergencyContactName,
                        phone = emergencyContactPhone,
                        relation = emergencyContactRelation
                    ))
                } else {
                    existing?.emergencyContacts ?: emptyList()
                },
                medicalHistory = existing?.medicalHistory ?: "",
                insuranceProvider = insuranceProvider.ifBlank { existing?.insuranceProvider ?: "" },
                insurancePolicyNo = insurancePolicyNo.ifBlank { existing?.insurancePolicyNo ?: "" },
                medicalRecordPath = medicalRecordPath.ifBlank { existing?.medicalRecordPath ?: "" }
            )

            profileStore.saveProfileSync(Json.encodeToString(profile))
            qrManager.generateAndSave(uid)
        }
    }
}

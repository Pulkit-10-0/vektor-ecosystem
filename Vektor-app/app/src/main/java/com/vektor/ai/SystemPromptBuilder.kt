package com.vektor.ai

import com.vektor.data.local.prefs.ProfileDataStore
import kotlinx.serialization.json.Json
import com.vektor.domain.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemPromptBuilder @Inject constructor(
    private val profileStore: ProfileDataStore
) {
    fun buildSync(): String {
        val pJson = profileStore.getProfileSync() ?: return "You are Vektor, an emergency AI assistant. No profile data available."
        val p = try {
            Json { ignoreUnknownKeys = true }.decodeFromString<UserProfile>(pJson)
        } catch (e: Exception) {
            return "You are Vektor. Failed to parse profile."
        }
        
        return buildString {
            appendLine("You are Vektor, a personal emergency health assistant.")
            appendLine("You run entirely on-device. You have no internet access.")
            appendLine("Always prioritise the user's safety. Keep responses concise.")
            appendLine()
            appendLine("USER MEDICAL PROFILE:")
            appendLine("Name: ${p.name}")
            appendLine("Date of birth: ${p.dob}")
            appendLine("Blood group: ${p.bloodGroup}")
            if (p.allergies.isNotEmpty())
                appendLine("ALLERGIES (critical): ${p.allergies.joinToString(", ")}")
            if (p.conditions.isNotEmpty())
                appendLine("Chronic conditions: ${p.conditions.joinToString(", ")}")
            if (p.medications.isNotEmpty())
                appendLine("Current medications: ${p.medications.joinToString(", ")}")
            appendLine()
            appendLine("In any emergency context, ALWAYS lead with blood group, allergies, conditions.")
            appendLine("Never invent medical information not listed above.")
        }
    }
}

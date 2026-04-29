package com.vektor.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class EmergencyContact(
    val name: String,
    val phone: String,
    val relation: String
)

@Serializable
data class UserProfile(
    val uid: String,
    val name: String,
    val dob: String,
    val bloodGroup: String,
    val allergies: List<String>,
    val conditions: List<String>,
    val medications: List<String>,
    val emergencyContacts: List<EmergencyContact>,
    val medicalHistory: String,
    val insuranceProvider: String,
    val insurancePolicyNo: String,
    val medicalRecordPath: String = ""
)

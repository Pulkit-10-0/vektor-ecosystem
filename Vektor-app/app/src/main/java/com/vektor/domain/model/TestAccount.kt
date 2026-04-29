package com.vektor.domain.model

object TestAccount {
    const val USERNAME = "admin"
    const val PASSWORD = "1234"
    const val UID = "1"

    val profile = UserProfile(
        uid = "1",
        name = "Arjun Mehta",
        dob = "1998-04-12",
        bloodGroup = "O+",
        allergies = listOf("Penicillin", "Latex"),
        conditions = listOf("Type 2 Diabetes"),
        medications = listOf("Metformin 500mg twice daily"),
        emergencyContacts = listOf(
            EmergencyContact(name = "Priya Mehta", phone = "+91-9876543210", relation = "Sister")
        ),
        medicalHistory = "Patient has well-controlled Type 2 Diabetes diagnosed in 2020. " +
            "No cardiac history. Penicillin allergy confirmed (anaphylaxis risk). " +
            "Latex allergy (contact dermatitis). Last HbA1c: 6.8% (March 2026).",
        insuranceProvider = "Star Health",
        insurancePolicyNo = "SH-2024-XXXX"
    )
}

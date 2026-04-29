package com.vektor.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class EmergencyPayload(
    val uid: String,
    val responderBrief: String,
    val locationLat: Double,
    val locationLng: Double,
    val timestamp: Long,
    val networkStatus: String
)

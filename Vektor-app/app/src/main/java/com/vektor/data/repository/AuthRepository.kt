package com.vektor.data.repository

import com.vektor.data.local.prefs.ProfileDataStore
import com.vektor.domain.model.EmergencyContact
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

import com.vektor.domain.model.TestAccount
import com.vektor.domain.model.UserProfile
import com.vektor.qr.QrManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Singleton
class AuthRepository @Inject constructor(
    private val profileStore: ProfileDataStore,
    private val qrManager: QrManager
) {
    private val prefs = profileStore.prefs

    fun signup(username: String, password: String): Result<String> {
        if (prefs.contains(KEY_USERNAME)) return Result.failure(Exception("Account exists"))
        val uid = UUID.randomUUID().toString()
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD_HASH, password.sha256())
            .putString(KEY_UID, uid)
            .apply()
        val defaultProfile = UserProfile(
            uid = uid,
            name = username,
            dob = "",
            bloodGroup = "Unknown",
            allergies = emptyList(),
            conditions = emptyList(),
            medications = emptyList(),
            emergencyContacts = listOf(EmergencyContact(name = "", phone = "", relation = "")),
            medicalHistory = "",
            insuranceProvider = "",
            insurancePolicyNo = ""
        )
        profileStore.saveProfileSync(Json.encodeToString(defaultProfile))
        qrManager.generateAndSave(uid)
        profileStore.saveLoggedIn(uid)
        return Result.success(uid)
    }

    fun login(username: String, password: String): Result<String> {
        // Test account — always works, no stored hash needed
        if (username == TestAccount.USERNAME && password == TestAccount.PASSWORD) {
            loadTestProfile()
            profileStore.saveLoggedIn(TestAccount.UID)
            return Result.success(TestAccount.UID)
        }
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return Result.failure(Exception("No account"))
        val storedUser = prefs.getString(KEY_USERNAME, null) ?: return Result.failure(Exception("No account"))
        return if (storedUser == username && password.sha256() == storedHash) {
            val uid = prefs.getString(KEY_UID, "") ?: ""
            if (profileStore.getProfileSync() == null) {
                val defaultProfile = UserProfile(
                    uid = uid,
                    name = storedUser,
                    dob = "",
                    bloodGroup = "Unknown",
                    allergies = emptyList(),
                    conditions = emptyList(),
                    medications = emptyList(),
                    emergencyContacts = listOf(EmergencyContact(name = "", phone = "", relation = "")),
                    medicalHistory = "",
                    insuranceProvider = "",
                    insurancePolicyNo = ""
                )
                profileStore.saveProfileSync(Json.encodeToString(defaultProfile))
            }
            qrManager.generateAndSave(uid)
            profileStore.saveLoggedIn(uid)
            Result.success(uid)
        } else {
            Result.failure(Exception("Invalid credentials"))
        }
    }

    fun logout() {
        profileStore.clearLoggedIn()
        // Clear auth credentials but keep profile data
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD_HASH)
            .remove(KEY_UID)
            .apply()
    }

    fun getLoggedInUid(): String? = profileStore.getLoggedInUid()

    private fun loadTestProfile() {
        val profileJson = Json.encodeToString(TestAccount.profile)
        profileStore.saveProfileSync(profileJson)
        qrManager.generateAndSave(TestAccount.UID)
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val KEY_USERNAME = "auth_username"
        const val KEY_PASSWORD_HASH = "auth_password_hash"
        const val KEY_UID = "auth_uid"
    }
}

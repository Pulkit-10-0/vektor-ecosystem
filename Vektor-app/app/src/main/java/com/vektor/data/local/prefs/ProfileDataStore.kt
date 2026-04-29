package com.vektor.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileDataStore @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "vektor_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveProfileSync(profileJson: String) {
        prefs.edit().putString("auth_profile_data", profileJson).apply()
    }

    fun getProfileSync(): String? {
        return prefs.getString("auth_profile_data", null)
    }

    // ── Persistent login ──────────────────────────────────────────────────────

    fun saveLoggedIn(uid: String) {
        prefs.edit().putString("auth_uid", uid).apply()
    }

    fun clearLoggedIn() {
        prefs.edit().remove("auth_uid").apply()
    }

    fun isLoggedIn(): Boolean = prefs.contains("auth_uid")

    fun getLoggedInUid(): String? = prefs.getString("auth_uid", null)
}

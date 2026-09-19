package com.irinteractivestudios.kabadiwalaconnect.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Encrypted key-value storage backed by the Android Keystore.
 *
 * Holds sensitive local credentials, including the configured backend JWT.
 * If the Keystore is unavailable, values are kept in memory for the current
 * process only. Persisting bearer/refresh tokens in plaintext would turn a
 * device compatibility fallback into an account-takeover vulnerability.
 */
interface SecureStorage {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)

    companion object Keys {
        /** Backend bearer token for the current collector session. */
        const val AUTH_TOKEN = "auth_token"
        const val REFRESH_TOKEN = "refresh_token"

        /** Server-issued collector id (or local development id). */
        const val COLLECTOR_ID = "collector_id"

        const val SESSION_EXPIRY = "session_expiry"
        const val ACCOUNT_EMAIL = "account_email"
        const val ACCOUNT_PHONE = "account_phone"
        const val ACCOUNT_DISPLAY_NAME = "account_display_name"
        const val ACCOUNT_AREA_NAME = "account_area_name"
        const val ACCOUNT_ROLE = "account_role"
        const val ACCOUNT_VERIFICATION_STATUS = "account_verification_status"
        const val ACCOUNT_LANGUAGE = "account_language"
        const val ACCOUNT_PROFILE_ID = "account_profile_id"
        const val ACCOUNT_LATITUDE = "account_latitude"
        const val ACCOUNT_LONGITUDE = "account_longitude"
        const val ACCOUNT_PERMISSIONS = "account_permissions"

        /** Current FCM token and a retryable token awaiting authenticated registration. */
        const val PUSH_TOKEN = "push_token"
        const val PENDING_PUSH_TOKEN = "pending_push_token"

        /** Opaque server cursor used by bidirectional change reconciliation. */
        const val SYNC_CURSOR = "sync_cursor"

        /** Opaque cursor used by foreground activity polling. */
        const val ACTIVITY_CURSOR = "activity_cursor"
    }
}

class KeystoreSecureStorage(context: Context) : SecureStorage {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences? by lazy { openPrefs() }
    private val processOnlyValues = ConcurrentHashMap<String, String>()

    /**
     * Creating the Android Keystore master key is relatively expensive on a
     * first launch. A clean install cannot contain an encrypted session, so do
     * not initialize Keystore merely to prove that an absent preferences file
     * contains no value. Existing installs still open the encrypted store and
     * retain full session compatibility.
     */
    private fun encryptedStoreExists(): Boolean = File(
        appContext.applicationInfo.dataDir,
        "shared_prefs/kc_secure_prefs.xml"
    ).isFile

    private fun openPrefs(): SharedPreferences? {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "kc_secure_prefs",
                masterKeyAlias,
                appContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) { null }
    }

    override fun put(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply() ?: processOnlyValues.put(key, value)
    }

    override fun get(key: String): String? {
        processOnlyValues[key]?.let { return it }
        if (!encryptedStoreExists()) return null
        return try {
            prefs?.getString(key, null)?.also { processOnlyValues[key] = it }
        } catch (_: Exception) {
            processOnlyValues[key]
        }
    }

    override fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
        processOnlyValues.remove(key)
    }
}

/** In-memory fake for tests and previews. */
class InMemorySecureStorage : SecureStorage {
    private val map = mutableMapOf<String, String>()
    override fun put(key: String, value: String) {
        map[key] = value
    }

    override fun get(key: String): String? = map[key]
    override fun remove(key: String) {
        map.remove(key)
    }
}

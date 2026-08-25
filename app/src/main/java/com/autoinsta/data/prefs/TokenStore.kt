package com.autoinsta.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Holds the Instagram access token, encrypted at rest.
 *
 * ## Why not Room
 * The database file is plain SQLite. Anything with device access — root, an extracted
 * backup, a debugger on a developer build — can read it. This token can publish to the
 * account, so it gets stronger treatment: `EncryptedSharedPreferences` encrypts the value
 * with a key held in the Android Keystore, which is hardware-backed on most devices and
 * cannot be extracted even from a rooted phone.
 *
 * The *account* (username, id, expiry) stays in Room — it isn't secret, and the UI needs
 * to observe it. Only the token itself lives here.
 */
class TokenStore(
    private val context: Context,
) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** The long-lived access token, or null when not connected. */
    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    /** When the stored token was issued — needed for Meta's 24-hour refresh rule. */
    fun getIssuedAt(): Long = prefs.getLong(KEY_ISSUED_AT, 0L)

    /** When the stored token stops working. */
    fun getExpiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)

    fun save(accessToken: String, issuedAtMillis: Long, expiresAtMillis: Long) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_ISSUED_AT, issuedAtMillis)
            .putLong(KEY_EXPIRES_AT, expiresAtMillis)
            .apply()
    }

    /** Wipe everything — on disconnect, or when a token is beyond saving. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hasToken(): Boolean = !getAccessToken().isNullOrBlank()

    private companion object {
        const val FILE_NAME = "autoinsta_secure_prefs"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_ISSUED_AT = "issued_at"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}

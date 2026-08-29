package com.autoinsta.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The token store, including the failure that used to kill the app on launch.
 *
 * ## The bug this exists for
 * `android:allowBackup` was on with no exclusions, so Android's backup included the
 * *encrypted* preferences file. Keystore keys are never backed up. Restore the backup —
 * or move phones with Smart Switch, or have the package reinstalled under a new UID —
 * and the ciphertext comes back with no key that can read it. `androidx.security` throws
 * while **opening** the file, from a background coroutine on launch, and the app dies
 * every single time it starts.
 *
 * Found for real: it crashed the instrumented suite on the Fold 7 after
 * `connectedAndroidTest` uninstalled and reinstalled the app.
 */
@RunWith(AndroidJUnit4::class)
class TokenStoreTest {

    private lateinit var context: Context
    private var savedToken: String? = null
    private var savedIssuedAt = 0L
    private var savedExpiresAt = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // This runs against the real device store, so the owner's own login is put back
        // afterwards — the same rule the queue tests follow for the posting schedule.
        val store = TokenStore(context)
        savedToken = store.getAccessToken()
        savedIssuedAt = store.getIssuedAt()
        savedExpiresAt = store.getExpiresAt()
    }

    @After
    fun tearDown() {
        val store = TokenStore(context)
        val token = savedToken
        if (token != null) store.save(token, savedIssuedAt, savedExpiresAt) else store.clear()
    }

    /** Exactly what a restored backup leaves behind: a keyset nothing can decrypt. */
    private fun corruptTheStoredKeyset() {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_KEYSET, "this is not a keyset")
            .putString(VALUE_KEYSET, "this is not a keyset either")
            .commit()
    }

    @Test
    fun anUnreadableKeysetIsThrownAwayInsteadOfCrashing() {
        TokenStore(context).save("a-real-token", 1_000L, 2_000L)
        corruptTheStoredKeyset()

        // On the old code this line threw AEADBadTagException and took the process with it.
        val store = TokenStore(context)

        assertNull("the unreadable token must be gone, not fatal", store.getAccessToken())
        assertFalse(store.hasToken())
    }

    @Test
    fun theStoreIsUsableAgainAfterRecovering() {
        corruptTheStoredKeyset()

        val store = TokenStore(context)
        store.save("fresh-token", 10L, 20L)

        assertEquals("fresh-token", store.getAccessToken())
        assertEquals(10L, store.getIssuedAt())
        assertEquals(20L, store.getExpiresAt())
        assertTrue(store.hasToken())
    }

    @Test
    fun aTokenSurvivesBeingReadBackNormally() {
        val store = TokenStore(context)
        store.save("round-trip", 111L, 222L)

        val reopened = TokenStore(context)
        assertEquals("round-trip", reopened.getAccessToken())
        assertEquals(111L, reopened.getIssuedAt())
        assertEquals(222L, reopened.getExpiresAt())
    }

    @Test
    fun clearRemovesEverything() {
        val store = TokenStore(context)
        store.save("gone-soon", 1L, 2L)

        store.clear()

        assertNull(store.getAccessToken())
        assertFalse(store.hasToken())
    }

    private companion object {
        const val FILE_NAME = "autoinsta_secure_prefs"
        // The names androidx.security uses for the two Tink keysets it stores in the file.
        const val KEY_KEYSET = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        const val VALUE_KEYSET = "__androidx_security_crypto_encrypted_prefs_value_keyset__"
    }
}

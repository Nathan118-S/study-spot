package education.studyspot.app.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the bearer session token encrypted with an AES-GCM key held in the
 * Android Keystore (the key never leaves the device). Excluded from backups
 * (res/xml/data_extraction_rules.xml). The plaintext is cached in memory so
 * every request doesn't hit the Keystore.
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()
    private var cached: String? = null
    private var loaded = false

    var token: String?
        get() = synchronized(lock) {
            if (!loaded) {
                cached = read()
                loaded = true
            }
            cached
        }
        set(value) = synchronized(lock) {
            cached = value?.takeIf { it.isNotEmpty() }
            loaded = true
            write(cached)
        }

    private fun read(): String? {
        val stored = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
        } catch (e: Exception) {
            // Key lost (e.g. restored onto another device): treat as signed out.
            Log.w(TAG, "Couldn't decrypt the stored session; signing out", e)
            prefs.edit { remove(KEY_TOKEN) }
            null
        }
    }

    private fun write(value: String?) {
        if (value == null) {
            prefs.edit { remove(KEY_TOKEN) }
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val out = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs.edit { putString(KEY_TOKEN, Base64.encodeToString(out, Base64.NO_WRAP)) }
        } catch (e: Exception) {
            // Keep the in-memory session; it just won't survive a restart.
            Log.e(TAG, "Couldn't encrypt the session token", e)
            prefs.edit { remove(KEY_TOKEN) }
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val TAG = "TokenStore"
        const val PREFS = "secure_session"
        const val KEY_TOKEN = "token"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "studyspot_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

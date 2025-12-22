package org.rustygnome.pulse.data

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurityHelper(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val KEY_ALIAS = "PulseDataKey"
    }

    init {
        initKey()
    }

    private fun initKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
            keyGenerator.init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            )
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun encrypt(value: String?): String? {
        if (value.isNullOrEmpty()) return value
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        
        // Format: IV:EncryptedPayload
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encryptedValue: String?): String? {
        if (encryptedValue.isNullOrEmpty()) return encryptedValue
        try {
            val combined = Base64.decode(encryptedValue, Base64.NO_WRAP)
            val iv = ByteArray(12) // GCM IV size is usually 12 bytes
            System.arraycopy(combined, 0, iv, 0, iv.size)
            val encrypted = ByteArray(combined.size - iv.size)
            System.arraycopy(combined, iv.size, encrypted, 0, encrypted.size)

            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
    }

    fun saveCredentials(resourceId: Long, credentials: Map<String, String>) {
        val editor = sharedPreferences.edit()
        credentials.forEach { (key, value) ->
            editor.putString("${resourceId}_$key", value)
        }
        editor.apply()
    }

    fun getCredentials(resourceId: Long, keys: Set<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        keys.forEach { key ->
            sharedPreferences.getString("${resourceId}_$key", null)?.let {
                result[key] = it
            }
        }
        return result
    }

    fun deleteCredentials(resourceId: Long) {
        val prefix = "${resourceId}_"
        val editor = sharedPreferences.edit()
        sharedPreferences.all.keys.filter { it.startsWith(prefix) }.forEach {
            editor.remove(it)
        }
        editor.apply()
    }
}

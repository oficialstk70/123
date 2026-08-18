package pt.rebeliptv.app.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStorage(context: Context) {

    private val preferences = context.getSharedPreferences(
        "rebel_iptv_config",
        Context.MODE_PRIVATE
    )

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private val keyAlias = "RebelIPTVKey"

    init {
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )

            val specification = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .build()

            keyGenerator.init(specification)
            keyGenerator.generateKey()
        }
    }

    fun saveConfiguration(
        host: String,
        username: String,
        password: String
    ) {
        preferences.edit()
            .putString("host", host)
            .putString("username", username)
            .putString("password", encrypt(password))
            .apply()
    }

    fun getHost(): String? {
        return preferences.getString("host", null)
    }

    fun getUsername(): String? {
        return preferences.getString("username", null)
    }

    fun getPassword(): String? {
        val encrypted = preferences.getString("password", null)
            ?: return null

        return decrypt(encrypted)
    }

    fun hasConfiguration(): Boolean {
        return !getHost().isNullOrBlank() &&
            !getUsername().isNullOrBlank() &&
            !getPassword().isNullOrBlank()
    }

    fun clearConfiguration() {
        preferences.edit().clear().apply()
    }

    private fun getSecretKey(): SecretKey {
        return (keyStore.getKey(
            keyAlias,
            null
        ) as SecretKey)
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(
            Cipher.ENCRYPT_MODE,
            getSecretKey()
        )

        val iv = cipher.iv
        val encrypted = cipher.doFinal(
            value.toByteArray(StandardCharsets.UTF_8)
        )

        val combined = ByteArray(
            iv.size + encrypted.size
        )

        System.arraycopy(
            iv,
            0,
            combined,
            0,
            iv.size
        )

        System.arraycopy(
            encrypted,
            0,
            combined,
            iv.size,
            encrypted.size
        )

        return android.util.Base64.encodeToString(
            combined,
            android.util.Base64.NO_WRAP
        )
    }

    private fun decrypt(value: String): String {
        val combined = android.util.Base64.decode(
            value,
            android.util.Base64.NO_WRAP
        )

        val ivSize = 12

        require(combined.size > ivSize) {
            "Dados encriptados inválidos."
        }

        val iv = combined.copyOfRange(
            0,
            ivSize
        )

        val encrypted = combined.copyOfRange(
            ivSize,
            combined.size
        )

        val cipher = Cipher.getInstance(
            "AES/GCM/NoPadding"
        )

        cipher.init(
            Cipher.DECRYPT_MODE,
            getSecretKey(),
            GCMParameterSpec(
                128,
                iv
            )
        )

        return String(
            cipher.doFinal(encrypted),
            StandardCharsets.UTF_8
        )
    }
}

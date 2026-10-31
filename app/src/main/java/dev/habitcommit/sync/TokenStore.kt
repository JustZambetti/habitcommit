package dev.habitcommit.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the fine-grained PAT encrypted with an Android Keystore AES-256-GCM key.
 *
 * The ciphertext lives in filesDir/sync, excluded from cloud backup and device
 * transfer in the manifest rules as defense in depth (allowBackup is off too).
 * The token is never written to SharedPreferences, logs or commit messages.
 */
class TokenStore(context: Context) {

    private val file = File(File(context.filesDir, "sync"), "token.bin")

    fun hasToken(): Boolean = file.isFile && file.length() > 0

    fun save(token: String) {
        val trimmed = token.trim()
        require(trimmed.isNotEmpty()) { "token vuoto" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))

        val out = java.io.ByteArrayOutputStream()
        out.write(VERSION.toInt())
        out.write(iv.size)
        out.write(iv)
        out.write(ciphertext)

        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(out.toByteArray())
        if (!tmp.renameTo(file)) {
            file.writeBytes(out.toByteArray())
            tmp.delete()
        }
    }

    /** Returns null when there is no token or the keystore key is no longer usable. */
    fun load(): String? {
        if (!hasToken()) return null
        return try {
            val bytes = file.readBytes()
            require(bytes.size > 2 && bytes[0] == VERSION) { "formato token sconosciuto" }
            val ivSize = bytes[1].toInt() and 0xFF
            require(bytes.size > 2 + ivSize) { "token troncato" }
            val iv = bytes.copyOfRange(2, 2 + ivSize)
            val ciphertext = bytes.copyOfRange(2 + ivSize, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return null, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            // Never log the exception detail here: it can carry crypto material.
            null
        }
    }

    fun clear() {
        file.delete()
    }

    private fun key(): SecretKey {
        existingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return keyStore.getKey(ALIAS, null) as? SecretKey
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "habitcommit.github.token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
    }
}

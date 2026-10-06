package app.cove.companion.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets with a non-exportable Android Keystore AES-256-GCM key named [alias]. */
class SecretBox(private val alias: String) : Sealer {
    /** Encrypts [plain]; the result is `iv || ciphertext` and is safe to store anywhere. */
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    /** Reverses [encrypt]; throws if the data was tampered with or the key is gone. */
    override fun decrypt(box: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, box, 0, IV_BYTES))
        return cipher.doFinal(box, IV_BYTES, box.size - IV_BYTES)
    }

    /** Text convenience over [encrypt] producing Base64. */
    fun encryptString(plain: String): String = Base64.encodeToString(encrypt(plain.toByteArray()), Base64.NO_WRAP)

    /** Text convenience over [decrypt]; null when [text] cannot be decrypted. */
    fun decryptString(text: String): String? =
        runCatching { String(decrypt(Base64.decode(text, Base64.NO_WRAP))) }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

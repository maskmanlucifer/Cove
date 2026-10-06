package app.cove.companion.security

/** Encrypts and decrypts small byte arrays; [SecretBox] is the Keystore-backed implementation. */
interface Sealer {
    /** Returns a self-contained ciphertext for [plain]. */
    fun encrypt(plain: ByteArray): ByteArray

    /** Reverses [encrypt]; throws when the data was tampered with or the key is gone. */
    fun decrypt(box: ByteArray): ByteArray
}

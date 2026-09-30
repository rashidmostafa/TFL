package app.tfl.core.crypto

/** A cryptographic operation failed. Messages never contain key material or plaintext. */
open class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Decryption failed: the data was tampered with, truncated, or opened with the wrong key or label. */
class AuthenticationException(message: String) : CryptoException(message)

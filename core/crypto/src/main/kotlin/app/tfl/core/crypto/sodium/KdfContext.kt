package app.tfl.core.crypto.sodium

/** An 8-character `crypto_kdf` context. Each purpose has its own, so derived keys never collide. */
class KdfContext(private val name: String) {
    init {
        require(name.length == 8 && name.all { it.code in 0x21..0x7E }) { "KDF context must be 8 printable ASCII characters" }
    }

    fun bytes(): ByteArray = name.encodeToByteArray()

    override fun toString(): String = name

    companion object {
        /** Master seed → Ed25519 identity signing key. */
        val IDENTITY_SIGN = KdfContext("TFLsign_")

        /** Master seed → X25519 key-agreement key. */
        val IDENTITY_KEX = KdfContext("TFLkex__")

        /** Master seed → key for encrypted backups to a friend. */
        val BACKUP = KdfContext("TFLbkup_")

        /** PIN root → verifier compared at unlock. */
        val PIN_VERIFIER = KdfContext("TFLpinvf")

        /** PIN root → key that wraps the unlock key. */
        val PIN_KEK = KdfContext("TFLpinkk")
    }
}

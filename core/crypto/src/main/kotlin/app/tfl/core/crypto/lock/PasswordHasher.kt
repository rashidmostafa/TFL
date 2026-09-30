package app.tfl.core.crypto.lock

import app.tfl.core.crypto.CryptoException
import app.tfl.core.crypto.sodium.SodiumApi
import javax.inject.Inject

/** Argon2id cost. Stored with each PIN so the same cost is always reproduced. */
data class KdfParams(val opsLimit: Long, val memLimitBytes: Long) {

    val memLimitMebibytes: Long get() = memLimitBytes / MEBIBYTE

    companion object {
        private const val MEBIBYTE = 1024L * 1024L

        /** libsodium's `crypto_pwhash_*_MODERATE`: 3 passes over 256 MiB. */
        val MODERATE = KdfParams(opsLimit = 3, memLimitBytes = 256 * MEBIBYTE)

        /** Tried in order at PIN setup; slower or memory-starved phones step down. */
        val TIERS = listOf(MODERATE, KdfParams(3, 128 * MEBIBYTE), KdfParams(2, 64 * MEBIBYTE))
    }
}

/** Turns a PIN into a 32-byte root key. An interface so tests can count calls and use cheap costs. */
interface PasswordHasher {
    /** @throws CryptoException if the derivation can't run (usually out of memory). */
    fun derive(password: ByteArray, salt: ByteArray, params: KdfParams): ByteArray
}

class Argon2idHasher @Inject constructor(private val sodium: SodiumApi) : PasswordHasher {
    override fun derive(password: ByteArray, salt: ByteArray, params: KdfParams): ByteArray =
        sodium.argon2id(ROOT_BYTES, password, salt, params.opsLimit, params.memLimitBytes)
            ?: throw CryptoException("Not enough memory to check the PIN")

    private companion object {
        const val ROOT_BYTES = 32
    }
}

/** Picks the Argon2id cost when a PIN is set. Production benchmarks the phone; tests use a cheap cost. */
fun interface PinCostPolicy {
    fun choose(): KdfParams
}

class BenchmarkedPinCost @Inject constructor(private val benchmarker: KdfBenchmarker) : PinCostPolicy {
    override fun choose(): KdfParams = benchmarker.choose()
}

/** How long one derivation took on this phone, for choosing a tier and for the debug benchmark. */
data class KdfBenchmark(val params: KdfParams, val millis: Long?)

class KdfBenchmarker @Inject constructor(
    private val sodium: SodiumApi,
    private val hasher: PasswordHasher,
) {
    /** Times one derivation per tier; `millis` is null when a tier couldn't run. */
    fun measure(tiers: List<KdfParams> = KdfParams.TIERS): List<KdfBenchmark> = tiers.map { KdfBenchmark(it, time(it)) }

    /** The costliest tier that runs within [budgetMillis] on this phone (falls back to the cheapest). */
    fun choose(budgetMillis: Long = DEFAULT_BUDGET_MILLIS): KdfParams =
        KdfParams.TIERS.firstOrNull { tier -> time(tier)?.let { it <= budgetMillis } == true } ?: KdfParams.TIERS.last()

    private fun time(params: KdfParams): Long? {
        val password = sodium.randomBytes(6)
        val salt = sodium.randomBytes(SodiumApi.ARGON2_SALT_BYTES)
        val start = System.nanoTime()
        return try {
            sodium.wipe(hasher.derive(password, salt, params))
            (System.nanoTime() - start) / 1_000_000
        } catch (_: CryptoException) {
            null
        } finally {
            sodium.wipe(password)
        }
    }

    private companion object {
        const val DEFAULT_BUDGET_MILLIS = 2_000L
    }
}

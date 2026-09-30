package app.tfl.core.session

import app.tfl.core.crypto.identity.IdentityKeyDerivation
import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.crypto.lock.PinVault
import app.tfl.core.crypto.pairing.IssuedCode
import app.tfl.core.crypto.pairing.PairingCodes
import app.tfl.core.crypto.pairing.PairingScan
import app.tfl.core.crypto.pairing.ScannedCode
import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.database.DatabaseLockedException
import app.tfl.core.database.repository.IdentityRepository
import app.tfl.core.model.security.Profile
import app.tfl.core.session.di.WorkDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What a friend's scanned code proves about the two phones (see [PairingIdentity.proofFor]). */
enum class PairingProof {
    /** It answers none of this phone's recent codes: only this phone has scanned. */
    ONE_WAY,

    /**
     * It answers a code this phone showed, so they scanned this phone: this phone can verify them.
     * Their phone verifies this one once it scans this phone's answer.
     */
    THEY_SCANNED_ME,

    /** Also, the code of this phone's they answered was itself an answer to theirs: both phones are verified. */
    BOTH_VERIFIED,
}

/**
 * This phone's side of pairing in person: it makes the unlocked profile's pairing codes, checks
 * scanned ones against this phone's clock, and remembers the codes it showed in the last 5 minutes.
 * A friend's code that answers one of those proves both phones scanned each other.
 *
 * Each code is signed with the identity key, derived from the seed just for that signature and
 * wiped with the seed straight after, as for [AppSession.recoveryPhrase].
 */
@Singleton
class PairingIdentity @Inject constructor(
    private val session: AppSession,
    private val vault: PinVault,
    private val derivation: IdentityKeyDerivation,
    private val codes: PairingCodes,
    private val identities: IdentityRepository,
    private val clock: DeviceClock,
    private val sodium: SodiumApi,
    @param:WorkDispatcher private val work: CoroutineDispatcher,
) {
    /**
     * A code this phone showed, timed by time since boot (which setting the clock can't stretch).
     * [answered] is the identity key of the friend whose code it answered.
     */
    private class Shown(val profile: Profile, val answerId: ByteArray, val shownAtElapsed: Long, val answered: ByteArray?)

    private val shown = ArrayDeque<Shown>()

    /**
     * A new code for the unlocked profile, answering [answering] when given.
     * @throws DatabaseLockedException while locked.
     */
    suspend fun newCode(answering: ScannedCode? = null): IssuedCode = withContext(work) {
        val profile = session.profile ?: throw DatabaseLockedException()
        val displayName = checkNotNull(identities.get()) { "No identity" }.displayName
        val unlockKey = session.unlockKeyCopy()
        val code = try {
            val seed = vault.openSeed(profile, unlockKey)
            try {
                derivation.derive(seed).use { keys ->
                    codes.create(keys, displayName, clock.currentTimeMillis(), answering?.answerId)
                }
            } finally {
                sodium.wipe(seed)
            }
        } finally {
            sodium.wipe(unlockKey)
        }
        remember(Shown(profile, code.answerId, clock.elapsedRealtime(), answering?.keys?.signPublicKey))
        code
    }

    /**
     * Checks scanned QR text: see [PairingCodes.parse]. Refuses this profile's own codes.
     * @throws DatabaseLockedException while locked.
     */
    suspend fun check(text: String): PairingScan = withContext(work) {
        codes.parse(text, clock.currentTimeMillis(), identities.get()?.signPublicKey)
    }

    /** Whether [code] answers a code this profile showed in the last 5 minutes, and what that proves. */
    fun proofFor(code: ScannedCode): PairingProof {
        val answers = code.answers ?: return PairingProof.ONE_WAY
        val profile = session.profile ?: return PairingProof.ONE_WAY
        val mine = synchronized(shown) {
            prune()
            shown.lastOrNull { it.profile == profile && it.answerId.contentEquals(answers) }
        } ?: return PairingProof.ONE_WAY
        val answeredThem = mine.answered?.contentEquals(code.keys.signPublicKey) == true
        return if (answeredThem) PairingProof.BOTH_VERIFIED else PairingProof.THEY_SCANNED_ME
    }

    /**
     * The id of the newest code this profile showed, still within 5 minutes. Only debug tools use it,
     * to make a test friend's code that answers this phone's. Ids are hashes of public codes.
     */
    fun latestAnswerId(): ByteArray? {
        val profile = session.profile ?: return null
        return synchronized(shown) {
            prune()
            shown.lastOrNull { it.profile == profile }?.answerId?.copyOf()
        }
    }

    private fun remember(code: Shown) = synchronized(shown) {
        prune()
        shown.addLast(code)
        while (shown.size > MAX_REMEMBERED) shown.removeFirst()
    }

    private fun prune() {
        val now = clock.elapsedRealtime()
        shown.removeAll { now - it.shownAtElapsed > PairingCodes.VALIDITY_MILLIS }
    }

    private companion object {
        /** One code a minute plus answers: far more than 5 minutes of pairing produces. */
        const val MAX_REMEMBERED = 32
    }
}

package app.tfl.core.database.repository

import app.tfl.core.database.ContactDao
import app.tfl.core.database.ContactEntity
import app.tfl.core.database.ContactKeyEntity
import app.tfl.core.database.DatabaseHolder
import app.tfl.core.model.contact.Contact
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.contact.KeySource
import app.tfl.core.model.contact.PreviousKey
import app.tfl.core.model.contact.Verification
import app.tfl.core.model.contact.VerificationMethod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Friends and their keys, and the rules that change them: adding from a pairing scan, verifying,
 * and key changes (the old key kept in history; the friend unverified until checked again).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ContactRepository @Inject constructor(private val holder: DatabaseHolder) {

    /** Everyone, by the name shown; empty while locked. */
    val contacts: Flow<List<Contact>> = holder.database.flatMapLatest { database ->
        database?.contactDao()?.observeAll()?.map { list -> list.map { it.toModel() } } ?: flowOf(emptyList())
    }

    fun contact(id: Long): Flow<Contact?> = holder.database.flatMapLatest { database ->
        database?.contactDao()?.observe(id)?.map { it?.toModel() } ?: flowOf(null)
    }

    /** Keys the friend used before, newest first. */
    fun keyHistory(id: Long): Flow<List<PreviousKey>> = holder.database.flatMapLatest { database ->
        database?.contactDao()?.observeKeyHistory(id)?.map { list -> list.map { it.toModel() } } ?: flowOf(emptyList())
    }

    suspend fun get(id: Long): Contact? = dao().get(id)?.toModel()

    suspend fun findByKey(signPublicKey: ByteArray): Contact? = dao().findByKey(signPublicKey)?.toModel()

    /** Friends called [displayName] whose key isn't [keys]': maybe the same person with a new key. */
    suspend fun othersNamed(displayName: String, keys: ContactKeys): List<Contact> =
        dao().findByDisplayName(displayName).filterNot { it.signPublicKey.contentEquals(keys.signPublicKey) }.map { it.toModel() }

    /**
     * Stores a friend from a valid pairing code. A new friend is verified in person when [mutual]
     * (their code answered ours), otherwise unverified. A known friend gets their newest name and,
     * when [mutual], is verified in person; a scan never lowers their status.
     */
    suspend fun recordPairing(displayName: String, keys: ContactKeys, mutual: Boolean, nowMillis: Long): Contact {
        val dao = dao()
        val existing = dao.findByKey(keys.signPublicKey)
        if (existing == null) {
            val contact = ContactEntity(
                displayName = displayName,
                nickname = null,
                signPublicKey = keys.signPublicKey,
                kexPublicKey = keys.kexPublicKey,
                fingerprint = keys.fingerprintHex,
                onionAddress = null,
                keySource = KeySource.IN_PERSON_SCAN.name,
                keySinceMillis = nowMillis,
                verification = Verification.UNVERIFIED.name,
                verifiedBy = null,
                verifiedAtMillis = null,
                firstSeenAtMillis = nowMillis,
                lastPairedAtMillis = nowMillis,
                keyChangedAtMillis = null,
                blocked = false,
            ).let { if (mutual) it.verified(VerificationMethod.IN_PERSON_PAIRING, nowMillis) else it }
            val id = dao.insert(contact)
            return contact.copy(id = id).toModel()
        }
        // Same identity key, different key-agreement key: only a changed or broken app does this.
        // Treat it like any key change rather than trust it silently.
        if (!existing.kexPublicKey.contentEquals(keys.kexPublicKey)) {
            changeKeys(existing.id, keys, KeySource.IN_PERSON_SCAN, nowMillis)
        }
        val current = checkNotNull(dao.get(existing.id))
        val updated = current.copy(displayName = displayName, lastPairedAtMillis = nowMillis)
            .let { if (mutual) it.verified(VerificationMethod.IN_PERSON_PAIRING, nowMillis) else it }
        dao.update(updated)
        return updated.toModel()
    }

    /**
     * A known friend presents a different identity key: the old key goes to their history, the new
     * one becomes current, and they're unverified and flagged until the new key is verified.
     */
    suspend fun changeKeys(id: Long, keys: ContactKeys, source: KeySource, nowMillis: Long, displayName: String? = null): Contact {
        val dao = dao()
        val current = checkNotNull(dao.get(id)) { "No contact $id" }
        if (current.signPublicKey.contentEquals(keys.signPublicKey) && current.kexPublicKey.contentEquals(keys.kexPublicKey)) {
            return current.toModel()
        }
        val owner = dao.findByKey(keys.signPublicKey)
        require(owner == null || owner.id == id) { "That key belongs to another contact" }
        val previous = ContactKeyEntity(
            contactId = id,
            signPublicKey = current.signPublicKey,
            kexPublicKey = current.kexPublicKey,
            fingerprint = current.fingerprint,
            firstSeenAtMillis = current.keySinceMillis,
            replacedAtMillis = nowMillis,
            replacedBy = source.name,
        )
        val replacement = current.copy(
            displayName = displayName ?: current.displayName,
            signPublicKey = keys.signPublicKey,
            kexPublicKey = keys.kexPublicKey,
            fingerprint = keys.fingerprintHex,
            keySource = source.name,
            keySinceMillis = nowMillis,
            verification = Verification.UNVERIFIED.name,
            verifiedBy = null,
            verifiedAtMillis = null,
            keyChangedAtMillis = nowMillis,
        )
        dao.replaceKeys(previous, replacement)
        return replacement.toModel()
    }

    /** Verified in person, or by matching safety numbers; this also resolves a key change. */
    suspend fun markVerified(id: Long, method: VerificationMethod, nowMillis: Long) {
        val dao = dao()
        dao.update(checkNotNull(dao.get(id)) { "No contact $id" }.verified(method, nowMillis))
    }

    /** Your own name for them; blank removes it. */
    suspend fun setNickname(id: Long, nickname: String?) {
        val dao = dao()
        dao.update(checkNotNull(dao.get(id)).copy(nickname = nickname?.trim()?.takeIf { it.isNotEmpty() }))
    }

    suspend fun setBlocked(id: Long, blocked: Boolean) {
        val dao = dao()
        dao.update(checkNotNull(dao.get(id)).copy(blocked = blocked))
    }

    /**
     * Removes the friend and every key they used. With `secure_delete` on (see the database
     * factory), the rows are overwritten inside the encrypted file, not just unlinked.
     */
    suspend fun delete(id: Long) = dao().delete(id)

    private fun dao(): ContactDao = holder.require().contactDao()

    private fun ContactEntity.verified(method: VerificationMethod, nowMillis: Long) = copy(
        verification = Verification.VERIFIED.name,
        verifiedBy = method.name,
        verifiedAtMillis = nowMillis,
        keyChangedAtMillis = null,
    )

    private fun ContactEntity.toModel() = Contact(
        id = id,
        displayName = displayName,
        nickname = nickname,
        keys = ContactKeys(signPublicKey, kexPublicKey, fingerprint),
        keySource = enumOr(keySource, KeySource.IN_PERSON_SCAN),
        verification = enumOr(verification, Verification.UNVERIFIED),
        verifiedBy = enumOrNull<VerificationMethod>(verifiedBy),
        verifiedAtMillis = verifiedAtMillis,
        firstSeenAtMillis = firstSeenAtMillis,
        lastPairedAtMillis = lastPairedAtMillis,
        keyChangedAtMillis = keyChangedAtMillis,
        blocked = blocked,
    )

    private fun ContactKeyEntity.toModel() = PreviousKey(
        keys = ContactKeys(signPublicKey, kexPublicKey, fingerprint),
        firstSeenAtMillis = firstSeenAtMillis,
        replacedAtMillis = replacedAtMillis,
        replacedBy = enumOr(replacedBy, KeySource.IN_PERSON_SCAN),
    )

    /** Unknown names (say, from a newer version) read as the safe default instead of crashing. */
    private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        name?.let { value -> enumValues<E>().firstOrNull { it.name == value } }
}

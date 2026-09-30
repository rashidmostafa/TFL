package app.tfl.core.session

import app.tfl.core.crypto.identity.Fingerprints
import app.tfl.core.crypto.identity.IdentityKeyDerivation
import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.database.DatabaseFactory
import app.tfl.core.database.IdentityEntity
import app.tfl.core.database.repository.SettingValue
import javax.inject.Inject

/** A new profile's database: its random file name and key. The caller wipes [databaseKey]. */
class CreatedProfile(val databaseName: String, val databaseKey: ByteArray, val fingerprint: String)

/**
 * Creates a profile database (identity row + initial settings) directly through the factory, so the
 * decoy can be created while the real profile is open. File names are random, so they reveal nothing.
 */
class ProfileCreator @Inject constructor(
    private val sodium: SodiumApi,
    private val factory: DatabaseFactory,
    private val derivation: IdentityKeyDerivation,
    private val fingerprints: Fingerprints,
) {
    suspend fun create(
        displayName: String,
        seed: ByteArray,
        createdAtMillis: Long,
        settings: List<SettingValue<*>>,
    ): CreatedProfile {
        val name = "tfl-" + sodium.randomBytes(6).joinToString("") { "%02x".format(it) } + ".db"
        val key = sodium.randomBytes(KEY_BYTES)
        val fingerprint = derivation.derive(seed).use { keys ->
            val fingerprint = fingerprints.of(keys.signPublicKey)
            val opened = factory.open(name, key)
            try {
                opened.database.identityDao().upsert(
                    IdentityEntity(
                        displayName = displayName,
                        fingerprint = fingerprint,
                        signPublicKey = keys.signPublicKey,
                        kexPublicKey = keys.kexPublicKey,
                        createdAtMillis = createdAtMillis,
                        derivationVersion = derivation.version,
                    ),
                )
                opened.database.settingsDao().putAll(settings.map { it.toEntity() })
            } catch (e: Exception) {
                opened.close()
                factory.delete(name)
                throw e
            }
            opened.close()
            fingerprint
        }
        return CreatedProfile(name, key, fingerprint)
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}

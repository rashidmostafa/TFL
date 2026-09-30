package app.tfl.core.database.repository

import app.tfl.core.database.DatabaseHolder
import app.tfl.core.database.IdentityEntity
import app.tfl.core.database.SettingEntity
import app.tfl.core.model.identity.Identity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class IdentityRepository @Inject constructor(private val holder: DatabaseHolder) {

    /** Null while locked or before an identity exists. */
    val identity: Flow<Identity?> = holder.database.flatMapLatest { database ->
        database?.identityDao()?.observe()?.map { it?.toModel() } ?: flowOf(null)
    }

    suspend fun get(): Identity? = holder.require().identityDao().get()?.toModel()

    suspend fun save(identity: Identity, derivationVersion: Int) {
        holder.require().identityDao().upsert(
            IdentityEntity(
                displayName = identity.displayName,
                fingerprint = identity.fingerprintHex,
                signPublicKey = identity.signPublicKey,
                kexPublicKey = identity.kexPublicKey,
                createdAtMillis = identity.createdAtMillis,
                derivationVersion = derivationVersion,
            ),
        )
    }

    private fun IdentityEntity.toModel() = Identity(
        displayName = displayName,
        fingerprintHex = fingerprint,
        signPublicKey = signPublicKey,
        kexPublicKey = kexPublicKey,
        createdAtMillis = createdAtMillis,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SettingsRepository @Inject constructor(private val holder: DatabaseHolder) {

    /** The stored value, or the key's default while locked or never set. */
    fun <T> observe(key: SettingKey<T>): Flow<T> = holder.database
        .flatMapLatest { database ->
            database?.settingsDao()?.observe(key.name)?.map { stored -> stored?.let(key.decode) ?: key.default }
                ?: flowOf(key.default)
        }
        .distinctUntilChanged()

    suspend fun <T> get(key: SettingKey<T>): T =
        holder.require().settingsDao().get(key.name)?.let(key.decode) ?: key.default

    suspend fun <T> set(key: SettingKey<T>, value: T) {
        holder.require().settingsDao().put(SettingEntity(key.name, key.encode(value)))
    }

    /** Writes several settings in one transaction. */
    suspend fun setAll(vararg values: SettingValue<*>) {
        holder.require().settingsDao().putAll(values.map { it.toEntity() })
    }
}

/** A key paired with a value, for [SettingsRepository.setAll]. */
class SettingValue<T>(private val key: SettingKey<T>, private val value: T) {
    fun toEntity() = SettingEntity(key.name, key.encode(value))
}

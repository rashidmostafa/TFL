package app.tfl.core.database

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Something tried to use the database while TFL is locked. */
class DatabaseLockedException : IllegalStateException("TFL is locked")

/**
 * The currently open profile database, or null while locked. Repositories follow [database] with
 * `flatMapLatest`, so locking cancels their queries before the database closes, and unlocking
 * resubscribes them.
 */
@Singleton
class DatabaseHolder @Inject constructor(private val factory: DatabaseFactory) {

    private val current = MutableStateFlow<TflDatabase?>(null)
    private var opened: OpenedDatabase? = null

    val database: StateFlow<TflDatabase?> = current.asStateFlow()

    val isOpen: Boolean get() = current.value != null

    /** @throws DatabaseKeyException if [key] doesn't open the file. */
    @Synchronized
    fun open(name: String, key: ByteArray) {
        close()
        val handle = factory.open(name, key)
        opened = handle
        current.value = handle.database
    }

    @Synchronized
    fun close() {
        current.value = null
        opened?.close()
        opened = null
    }

    fun delete(name: String) = factory.delete(name)

    fun require(): TflDatabase = current.value ?: throw DatabaseLockedException()
}

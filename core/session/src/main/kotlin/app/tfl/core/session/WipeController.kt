package app.tfl.core.session

import android.content.Context
import app.tfl.core.common.log.TflLog
import app.tfl.core.crypto.lock.PinVault
import app.tfl.core.database.DatabaseHolder
import app.tfl.core.session.restart.ProcessRestarter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes everything TFL keeps on this phone. The hardware keys go first (crypto-shredding): once
 * they're gone, no copy of the encrypted files, even one flash storage kept around, can be read.
 */
@Singleton
class WipeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: PinVault,
    private val database: DatabaseHolder,
    private val disguise: CalculatorDisguise,
    private val restarter: ProcessRestarter,
) {

    fun wipeEverything() {
        database.close()
        runCatching { vault.destroy() }.onFailure { TflLog.w(TAG, it) { "Destroying keys failed" } }
        runCatching { disguise.resetLauncher() }
        deleteAppData()
        restarter.restart()
    }

    fun deleteDatabase(name: String) = database.delete(name)

    /** Profile databases left behind by an onboarding that never reached its commit point. */
    fun deleteProfileDatabases() {
        context.databaseList().filter { it.startsWith(PROFILE_PREFIX) && it.endsWith(".db") }.forEach(context::deleteDatabase)
    }

    /** Empties every app-private directory, internal and external. */
    fun deleteAppData() {
        appDataDirectories().forEach { directory ->
            directory.listFiles()?.forEach { it.deleteRecursively() }
        }
    }

    internal fun appDataDirectories(): List<File> = buildList {
        add(context.filesDir)
        add(context.noBackupFilesDir)
        add(context.cacheDir)
        context.getDatabasePath(PROFILE_PREFIX).parentFile?.let(::add)
        add(File(context.dataDir, "shared_prefs"))
        addAll(context.getExternalFilesDirs(null).filterNotNull())
        addAll(context.externalCacheDirs.filterNotNull())
    }

    private companion object {
        const val TAG = "WipeController"
        const val PROFILE_PREFIX = "tfl-"
    }
}

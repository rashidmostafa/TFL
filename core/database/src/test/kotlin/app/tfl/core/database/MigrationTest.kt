package app.tfl.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Phones updating from Phase 1 open their existing database at the new version, data intact. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TflDatabase::class.java)

    @Test
    fun `version 1 gains the contact tables and keeps its identity and settings`() {
        helper.createDatabase(NAME, 1).use { database ->
            database.execSQL(
                "INSERT INTO identity (id, displayName, fingerprint, signPublicKey, kexPublicKey, createdAtMillis, derivationVersion) " +
                    "VALUES (1, 'Alice', 'F162BE7D2746777DB5A0FF5D6329EC0E', x'01', x'02', 5, 1)",
            )
            database.execSQL("INSERT INTO settings (`key`, value) VALUES ('security.auto_lock', 'SECONDS_30')")
        }

        helper.runMigrationsAndValidate(NAME, 2, true).use { database ->
            fun count(sql: String) = database.query(sql).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            assertEquals(1, count("SELECT COUNT(*) FROM identity WHERE displayName = 'Alice'"))
            assertEquals(1, count("SELECT COUNT(*) FROM settings WHERE value = 'SECONDS_30'"))
            assertEquals(0, count("SELECT COUNT(*) FROM contacts"))
            assertEquals(0, count("SELECT COUNT(*) FROM contact_keys"))
        }
    }

    private companion object {
        const val NAME = "tfl-migration-test.db"
    }
}

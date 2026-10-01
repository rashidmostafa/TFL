package app.tfl.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Phones updating from an earlier phase open their existing database at the new version, data intact. */
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

    @Test
    fun `version 2 gains the chat tables and keeps its friends`() {
        helper.createDatabase(NAME, 2).use { database ->
            database.execSQL(
                "INSERT INTO contacts (id, displayName, signPublicKey, kexPublicKey, fingerprint, keySource, keySinceMillis, " +
                    "verification, firstSeenAtMillis, lastPairedAtMillis, blocked) " +
                    "VALUES (4, 'Bob', x'01', x'02', 'B0B', 'IN_PERSON_SCAN', 5, 'VERIFIED', 5, 5, 0)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 3, true).use { database ->
            fun count(sql: String) = database.query(sql).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            assertEquals(1, count("SELECT COUNT(*) FROM contacts WHERE displayName = 'Bob'"))
            for (table in listOf("conversations", "messages", "reactions", "outbox", "seen_messages")) {
                assertEquals(table, 0, count("SELECT COUNT(*) FROM $table"))
            }
            // Bob's first conversation and message fit the new tables.
            database.execSQL(
                "INSERT INTO conversations (id, contactId, expiresAfterSeconds, timerChangedAtMillis, lastReadAtMillis, updatedAtMillis) VALUES (1, 4, 0, 0, 0, 9)",
            )
            database.execSQL(
                "INSERT INTO messages (msgId, conversationId, outgoing, kind, text, createdAtMillis, sortAtMillis, editNumber, deleted, expiresAfterSeconds) " +
                    "VALUES (x'00112233445566778899AABBCCDDEEFF', 1, 1, 'TEXT', 'hi Bob', 9, 9, 0, 0, 0)",
            )
            assertEquals(1, count("SELECT COUNT(*) FROM messages WHERE conversationId = 1"))
        }
    }

    private companion object {
        const val NAME = "tfl-migration-test.db"
    }
}

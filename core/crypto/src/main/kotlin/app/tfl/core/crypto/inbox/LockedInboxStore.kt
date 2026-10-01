package app.tfl.core.crypto.inbox

import app.tfl.core.crypto.CryptoException
import app.tfl.core.crypto.keystore.HardwareKeyAlias
import app.tfl.core.crypto.keystore.HardwareKeys
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * `inbox.bin`: what the transport receives while TFL is locked, kept until the next unlock. The
 * records hold envelopes still sealed to the identity key, and each record is encrypted again with
 * the Keystore key `tfl.inbox`, so the file shows nothing but its size. Records are appended as
 * `length (4 bytes) ‖ IV ‖ ciphertext ‖ tag`; a record cut short by a crash is ignored.
 */
class LockedInboxStore(directory: File, private val keys: HardwareKeys) {

    private val file = File(directory, FILE_NAME)
    private val temp = File(directory, "$FILE_NAME.tmp")

    /** Adds [record]; false when the inbox is full (the sender then keeps it queued). */
    @Synchronized
    fun append(record: ByteArray): Boolean {
        keys.ensureKey(HardwareKeyAlias.INBOX)
        val blob = keys.encrypt(HardwareKeyAlias.INBOX, record)
        if (file.length() + Int.SIZE_BYTES + blob.size > MAX_BYTES) return false
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).use { out ->
            DataOutputStream(out).apply {
                writeInt(blob.size)
                write(blob)
                flush()
            }
            out.fd.sync()
        }
        return true
    }

    /** Every readable record, in order. Records the key can't open (changed, or the key is gone) are skipped. */
    @Synchronized
    fun readAll(): List<ByteArray> {
        if (!file.exists()) return emptyList()
        val records = mutableListOf<ByteArray>()
        DataInputStream(file.inputStream().buffered()).use { input ->
            while (true) {
                val size = try {
                    input.readInt()
                } catch (e: EOFException) {
                    break
                }
                if (size !in 1..MAX_BYTES) break
                val blob = ByteArray(size)
                try {
                    input.readFully(blob)
                } catch (e: EOFException) {
                    break
                }
                try {
                    records += keys.decrypt(HardwareKeyAlias.INBOX, blob)
                } catch (e: CryptoException) {
                    continue
                }
            }
        }
        return records
    }

    /** Keeps only [records] (those another profile will read at its own unlock). Atomic. */
    @Synchronized
    fun replaceAll(records: List<ByteArray>) {
        if (records.isEmpty()) {
            clear()
            return
        }
        file.parentFile?.mkdirs()
        FileOutputStream(temp).use { out ->
            val data = DataOutputStream(out)
            for (record in records) {
                val blob = keys.encrypt(HardwareKeyAlias.INBOX, record)
                data.writeInt(blob.size)
                data.write(blob)
            }
            data.flush()
            out.fd.sync()
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    @Synchronized
    fun clear() {
        temp.delete()
        file.delete()
    }

    companion object {
        const val FILE_NAME = "inbox.bin"

        /** About 500 of the largest messages, or tens of thousands of short ones. */
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}

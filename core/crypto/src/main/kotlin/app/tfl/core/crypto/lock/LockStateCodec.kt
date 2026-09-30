package app.tfl.core.crypto.lock

import app.tfl.core.crypto.CryptoException
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.KeyStorageLevel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** The lock-state file's contents are unreadable: corrupted, truncated, or from an unknown version. */
class LockStateFormatException(message: String) : CryptoException(message)

/**
 * Versioned binary encoding of [LockState]: a magic number, a version byte, then each field in a
 * fixed order. Byte arrays are length-prefixed and enums are stored by name.
 */
internal object LockStateCodec {
    private val MAGIC = byteArrayOf('T'.code.toByte(), 'F'.code.toByte(), 'L'.code.toByte(), 'S'.code.toByte())
    private const val VERSION = 1
    private const val MAX_FIELD_BYTES = 4096

    fun encode(state: LockState): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.write(MAGIC)
            out.writeByte(VERSION)
            out.writeLong(state.kdf.opsLimit)
            out.writeLong(state.kdf.memLimitBytes)
            out.writeUTF(state.keyStorage.name)
            out.writeSlot(state.real)
            out.writeProfile(state.realKeys)
            out.writeUTF(state.duressMode.name)
            out.writeSlot(state.duress)
            out.writeOptional(state.decoyKeys) { writeProfile(it) }
            out.writeOptional(state.biometricUnlockKey) { writeBytes(it) }
            out.writeInt(state.attempts.failures)
            out.writeInt(state.attempts.bootCount)
            out.writeLong(state.attempts.elapsedAtLastFailure)
            out.writeInt(state.wipeAfterFailures)
            out.writeOptional(state.disguise) {
                writeBytes(it.key)
                writeBytes(it.hash)
            }
        }
        return buffer.toByteArray()
    }

    fun decode(bytes: ByteArray): LockState = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val magic = ByteArray(MAGIC.size).also(input::readFully)
            if (!magic.contentEquals(MAGIC)) throw LockStateFormatException("Not a TFL lock state")
            val version = input.readUnsignedByte()
            if (version != VERSION) throw LockStateFormatException("Unsupported lock state version $version")
            val state = LockState(
                kdf = KdfParams(opsLimit = input.readLong(), memLimitBytes = input.readLong()),
                keyStorage = enumValueOf<KeyStorageLevel>(input.readUTF()),
                real = input.readSlot(),
                realKeys = input.readProfile(),
                duressMode = enumValueOf<DuressMode>(input.readUTF()),
                duress = input.readSlot(),
                decoyKeys = input.readOptional { readProfile() },
                biometricUnlockKey = input.readOptional { readBytes() },
                attempts = AttemptRecord(
                    failures = input.readInt(),
                    bootCount = input.readInt(),
                    elapsedAtLastFailure = input.readLong(),
                ),
                wipeAfterFailures = input.readInt(),
                disguise = input.readOptional { DisguiseCodeHash(key = readBytes(), hash = readBytes()) },
            )
            if (input.available() != 0) throw LockStateFormatException("Trailing data in lock state")
            state
        }
    } catch (e: IOException) {
        throw LockStateFormatException("Truncated lock state")
    } catch (e: IllegalArgumentException) {
        throw LockStateFormatException("Unknown value in lock state")
    }

    private fun DataOutputStream.writeBytes(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readBytes(): ByteArray {
        val size = readInt()
        if (size !in 0..MAX_FIELD_BYTES) throw LockStateFormatException("Invalid field length")
        return ByteArray(size).also(::readFully)
    }

    private fun <T : Any> DataOutputStream.writeOptional(value: T?, writer: DataOutputStream.(T) -> Unit) {
        writeBoolean(value != null)
        if (value != null) writer(value)
    }

    private fun <T : Any> DataInputStream.readOptional(reader: DataInputStream.() -> T): T? =
        if (readBoolean()) reader() else null

    private fun DataOutputStream.writeSlot(slot: PinSlot) {
        writeBytes(slot.salt)
        writeBytes(slot.verifier)
        writeOptional(slot.wrappedUnlockKey) { writeBytes(it) }
    }

    private fun DataInputStream.readSlot() = PinSlot(
        salt = readBytes(),
        verifier = readBytes(),
        wrappedUnlockKey = readOptional { readBytes() },
    )

    private fun DataOutputStream.writeProfile(profile: ProfileKeys) {
        writeUTF(profile.databaseName)
        writeBytes(profile.wrappedDatabaseKey)
        writeBytes(profile.wrappedSeed)
    }

    private fun DataInputStream.readProfile() = ProfileKeys(
        databaseName = readUTF(),
        wrappedDatabaseKey = readBytes(),
        wrappedSeed = readBytes(),
    )
}

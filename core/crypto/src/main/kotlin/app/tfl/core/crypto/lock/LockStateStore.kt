package app.tfl.core.crypto.lock

import app.tfl.core.crypto.keystore.HardwareKeyAlias
import app.tfl.core.crypto.keystore.HardwareKeys
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * `lockstate.bin`: the [LockState] encrypted with the Keystore key `tfl.state`. A copy taken off the
 * phone is useless without that phone's hardware. Writes are atomic: a crash mid-write leaves the
 * previous version intact.
 */
class LockStateStore(directory: File, private val keys: HardwareKeys) {

    private val file = File(directory, FILE_NAME)
    private val temp = File(directory, "$FILE_NAME.tmp")

    fun exists(): Boolean = file.exists()

    /** @throws app.tfl.core.crypto.CryptoException if the file or its hardware key is unusable. */
    fun read(): LockState = LockStateCodec.decode(keys.decrypt(HardwareKeyAlias.STATE, file.readBytes()))

    fun write(state: LockState) {
        val encrypted = keys.encrypt(HardwareKeyAlias.STATE, LockStateCodec.encode(state))
        file.parentFile?.mkdirs()
        FileOutputStream(temp).use { out ->
            out.write(encrypted)
            out.fd.sync()
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun delete() {
        temp.delete()
        file.delete()
    }

    companion object {
        const val FILE_NAME = "lockstate.bin"
    }
}

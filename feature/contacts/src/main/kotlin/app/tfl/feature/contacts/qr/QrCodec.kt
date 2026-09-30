package app.tfl.feature.contacts.qr

import app.tfl.core.designsystem.component.QrMatrix
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Turns QR text into modules to draw. Error correction M recovers from glare or a scratched screen
 * without making the code much denser.
 *
 * ZXing's detector misreads about 1 in 40 codes this dense at almost any size, when the data
 * happens to form decoy patterns. So each code is read back at three sizes before it's shown, and
 * if that fails it's redrawn with another of the 8 QR masks (same data, different pattern) until
 * it reads.
 */
internal object QrEncoder {
    private val CHECK_SIZES = listOf(3, 4, 5) // pixels per module

    fun encode(text: String): QrMatrix {
        val masks = sequenceOf<Int?>(null) + (0..7).asSequence() // null: ZXing's own choice
        return masks.map { matrixOf(text, it) }.firstOrNull { it.readsBack(text) } ?: matrixOf(text, null)
    }

    private fun matrixOf(text: String, mask: Int?): QrMatrix {
        val hints = mutableMapOf<EncodeHintType, Any>(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
        )
        if (mask != null) hints[EncodeHintType.QR_MASK_PATTERN] = mask
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = bits.width
        return QrMatrix(size, BooleanArray(size * size) { bits.get(it % size, it / size) })
    }

    private fun QrMatrix.readsBack(text: String): Boolean {
        val decoder = QrDecoder()
        return CHECK_SIZES.all { decoder.decode(draw(it)) == text }
    }
}

/** A camera frame's luminance (Y) plane, or a drawn code: 0 is black, 255 white, rows [rowStride] bytes apart. */
internal class LuminanceFrame(val luminance: ByteArray, val rowStride: Int, val width: Int, val height: Int)

/** Draws the code as a screen shows it: dark on light, with the 4-module quiet zone. */
internal fun QrMatrix.draw(pixelsPerModule: Int, rowPadding: Int = 0): LuminanceFrame {
    val quietZone = 4
    val side = (size + 2 * quietZone) * pixelsPerModule
    val stride = side + rowPadding
    val pixels = ByteArray(stride * side) { WHITE }
    for (y in 0 until side) {
        val moduleY = y / pixelsPerModule - quietZone
        if (moduleY !in 0 until size) continue
        for (x in 0 until side) {
            val moduleX = x / pixelsPerModule - quietZone
            if (moduleX in 0 until size && isDark(moduleX, moduleY)) pixels[y * stride + x] = BLACK
        }
    }
    return LuminanceFrame(pixels, stride, side, side)
}

/** Reads a QR code from a camera frame's luminance plane, in memory. Not thread-safe: one per camera. */
internal class QrDecoder {
    private val reader = QRCodeReader()

    fun decode(frame: LuminanceFrame): String? = decode(frame.luminance, frame.rowStride, frame.width, frame.height)

    /** The code's text, or null if the frame holds none. Rows may be padded: [rowStride] ≥ [width]. */
    fun decode(luminance: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), HINTS).text
        } catch (e: Exception) {
            // Most frames hold no readable code (ReaderException); ZXing can also trip over a
            // damaged one. Either way the next frame gets a fresh try, never a crash.
            null
        } finally {
            reader.reset()
        }
    }

    private companion object {
        val HINTS = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
        )
    }
}

private const val WHITE = 0xFF.toByte()
private const val BLACK = 0.toByte()

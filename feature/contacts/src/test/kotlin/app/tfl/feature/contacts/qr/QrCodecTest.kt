package app.tfl.feature.contacts.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import kotlin.random.Random

class QrCodecTest {

    /** As long as a real pairing code with an answer and a long name. */
    private fun pairingSized(seed: Int) =
        "TFL-PAIR1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(Random(seed).nextBytes(200 + seed % 60))

    @Test
    fun `a code drawn on screen reads back exactly`() {
        val text = pairingSized(1)
        assertEquals(text, QrEncoder.encode(text).scan())
    }

    /**
     * About 1 in 40 codes can't be read with ZXing's automatic mask (seeds 1000 to 1299 include
     * six). Every code shown must read, at every size.
     */
    @Test
    fun `every code reads at every size, including ones the automatic mask breaks`() {
        for (seed in 1000 until 1300) {
            val text = pairingSized(seed)
            val matrix = QrEncoder.encode(text)
            for (pixelsPerModule in 2..8) {
                assertEquals("seed $seed at $pixelsPerModule px per module", text, matrix.scan(pixelsPerModule))
            }
        }
    }

    @Test
    fun `padded camera rows are read correctly`() {
        val text = pairingSized(2)
        val frame = QrEncoder.encode(text).draw(pixelsPerModule = 3, rowPadding = 64)
        assertEquals(text, QrDecoder().decode(frame))
    }

    @Test
    fun `a frame without a code reads as nothing`() {
        val blank = ByteArray(640 * 480) { 0x80.toByte() }
        assertNull(QrDecoder().decode(blank, 640, 640, 480))
        val noise = Random(2).nextBytes(640 * 480)
        assertNull(QrDecoder().decode(noise, 640, 640, 480))
    }

    @Test
    fun `codes have valid QR sizes and stay scannable at arm's length`() {
        val size = QrEncoder.encode(pairingSized(3)).size
        assertEquals(0, (size - 17) % 4) // 21, 25, 29 … modules: 17 plus 4 per version
        assertTrue("version too dense for a phone camera: $size modules", size <= 81)
    }
}

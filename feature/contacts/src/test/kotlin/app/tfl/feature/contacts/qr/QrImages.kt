package app.tfl.feature.contacts.qr

import app.tfl.core.designsystem.component.QrMatrix

/** What a phone's camera reads from this code on another phone's screen. */
internal fun QrMatrix.scan(pixelsPerModule: Int = 4): String =
    checkNotNull(QrDecoder().decode(draw(pixelsPerModule))) { "No QR code read" }

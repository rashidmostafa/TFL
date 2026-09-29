package app.tfl.core.testing

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot

/**
 * Replaces the text as a user would, then flushes snapshot changes so `snapshotFlow` observers
 * (normally woken by the next UI frame) see it in a plain unit test.
 */
fun TextFieldState.typeText(text: String) {
    setTextAndPlaceCursorAtEnd(text)
    Snapshot.sendApplyNotifications()
}

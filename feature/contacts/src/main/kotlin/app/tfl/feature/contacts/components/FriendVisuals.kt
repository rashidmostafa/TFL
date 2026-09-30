package app.tfl.feature.contacts.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.tfl.core.database.DatabaseLockedException
import app.tfl.core.designsystem.component.SecureBadge
import app.tfl.core.designsystem.component.SecureBadgeState
import app.tfl.core.model.contact.Contact
import app.tfl.core.model.contact.KeySource
import app.tfl.core.model.contact.VerificationMethod
import app.tfl.feature.contacts.R
import java.text.DateFormat
import java.util.Date

/** How far to trust a friend's keys, as every contacts screen shows it. */
enum class Trust {
    VERIFIED,
    UNVERIFIED,

    /** Their key changed and the new one isn't verified yet. */
    KEY_CHANGED,
    ;

    companion object {
        fun of(contact: Contact): Trust = when {
            contact.keyChanged && !contact.isVerified -> KEY_CHANGED
            contact.isVerified -> VERIFIED
            else -> UNVERIFIED
        }
    }
}

@Composable
internal fun TrustBadge(trust: Trust, modifier: Modifier = Modifier) = when (trust) {
    Trust.VERIFIED -> SecureBadge(modifier, SecureBadgeState.Verified, label = stringResource(R.string.trust_verified))
    Trust.UNVERIFIED -> SecureBadge(modifier, SecureBadgeState.Unverified)
    Trust.KEY_CHANGED -> SecureBadge(modifier, SecureBadgeState.KeyChanged)
}

@Composable
internal fun verifiedByLabel(method: VerificationMethod): String = stringResource(
    when (method) {
        VerificationMethod.IN_PERSON_PAIRING -> R.string.verified_by_pairing
        VerificationMethod.SAFETY_NUMBER_QR -> R.string.verified_by_safety_code
        VerificationMethod.SAFETY_NUMBER_MANUAL -> R.string.verified_by_safety_manual
    },
)

@Composable
internal fun keySourceLabel(source: KeySource): String = stringResource(
    when (source) {
        KeySource.IN_PERSON_SCAN -> R.string.key_source_scan
        KeySource.DEBUG_SIMULATION -> R.string.key_source_simulated
    },
)

/** Up to two letters or digits: the first of each of the first two words ("Valkyrie-7" → "V7"). */
internal fun initialsOf(name: String): String = name
    .split(Regex("[^\\p{L}\\p{N}]+"))
    .filter { it.isNotEmpty() }
    .take(2)
    .joinToString("") { word -> String(Character.toChars(word.codePointAt(0))).uppercase() }
    .ifEmpty { "?" }

internal fun formatDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

internal fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

/**
 * Runs [block], or returns null if TFL locked meanwhile: the gate then replaces every screen, so
 * there is nothing left to update.
 */
internal suspend fun <T> unlessLocked(block: suspend () -> T): T? = try {
    block()
} catch (e: DatabaseLockedException) {
    null
}

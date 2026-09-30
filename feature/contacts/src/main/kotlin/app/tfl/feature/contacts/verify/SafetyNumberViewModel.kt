package app.tfl.feature.contacts.verify

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.crypto.pairing.ComparisonResult
import app.tfl.core.crypto.pairing.SafetyNumber
import app.tfl.core.crypto.pairing.SafetyNumbers
import app.tfl.core.database.repository.ContactRepository
import app.tfl.core.database.repository.IdentityRepository
import app.tfl.core.designsystem.component.QrMatrix
import app.tfl.core.model.contact.VerificationMethod
import app.tfl.core.session.di.WorkDispatcher
import app.tfl.feature.contacts.components.Trust
import app.tfl.feature.contacts.components.unlessLocked
import app.tfl.feature.contacts.friend.FriendViewModel
import app.tfl.feature.contacts.qr.QrEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class CompareTab { SHOW, SCAN }

enum class CompareResult {
    MATCH,

    /** Different numbers: one phone holds a different key for the other. */
    MISMATCH,

    /** Some other QR code, such as a pairing code. */
    NOT_A_COMPARISON_CODE,
}

data class SafetyNumberUiState(
    val loading: Boolean = true,
    val name: String = "",
    val trust: Trust = Trust.UNVERIFIED,
    val verifiedBy: VerificationMethod? = null,
    val groups: List<String> = emptyList(),
    /** The comparison code: the same on both phones exactly when the numbers are. */
    val code: QrMatrix? = null,
    val tab: CompareTab = CompareTab.SHOW,
    val result: CompareResult? = null,
)

/**
 * Comparing safety numbers with a friend: read aloud, or by scanning the code their phone shows
 * for you. Both phones compute the same number from the two identity keys, so a match proves each
 * holds the other's real key. A match or a confirmed read-aloud verifies the friend.
 */
@HiltViewModel
class SafetyNumberViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    identities: IdentityRepository,
    private val safetyNumbers: SafetyNumbers,
    private val contacts: ContactRepository,
    private val clock: DeviceClock,
    @param:WorkDispatcher private val work: CoroutineDispatcher,
) : ViewModel() {

    private val contactId: Long = checkNotNull(savedStateHandle.get<Long>(FriendViewModel.CONTACT_ID_ARG)) { "No contact id" }

    private val state = MutableStateFlow(SafetyNumberUiState())
    val uiState: StateFlow<SafetyNumberUiState> = state.asStateFlow()

    /** Recomputed if their key changes while this screen is open. */
    private var number: SafetyNumber? = null
    private var handledText: String? = null

    init {
        viewModelScope.launch {
            combine(contacts.contact(contactId), identities.identity) { contact, me -> contact to me }.collect { (contact, me) ->
                if (contact == null || me == null) {
                    number = null
                    state.update { it.copy(loading = false, groups = emptyList(), code = null) }
                    return@collect
                }
                val current = safetyNumbers.between(me.signPublicKey, contact.keys.signPublicKey)
                if (current != number) {
                    number = current
                    handledText = null
                    val matrix = withContext(work) { QrEncoder.encode(safetyNumbers.comparisonCode(current)) }
                    state.update { it.copy(groups = current.groups, code = matrix, result = null) }
                }
                state.update {
                    it.copy(loading = false, name = contact.name, trust = Trust.of(contact), verifiedBy = contact.verifiedBy)
                }
            }
        }
    }

    fun selectTab(tab: CompareTab) = state.update { it.copy(tab = tab, result = it.result.takeIf { result -> result == CompareResult.MATCH }) }

    /** A code the camera read, from their profile of you. */
    fun onScanned(text: String) {
        val current = number ?: return
        if (text == handledText) return
        handledText = text
        when (safetyNumbers.compare(current, text)) {
            ComparisonResult.MATCH -> {
                // The camera stops on the "show" tab, so their phone can scan this one's code next.
                state.update { it.copy(result = CompareResult.MATCH, tab = CompareTab.SHOW) }
                verify(VerificationMethod.SAFETY_NUMBER_QR)
            }
            ComparisonResult.MISMATCH -> state.update { it.copy(result = CompareResult.MISMATCH) }
            ComparisonResult.NOT_A_COMPARISON_CODE -> state.update { it.copy(result = CompareResult.NOT_A_COMPARISON_CODE) }
        }
    }

    /** After reading every group aloud and confirming they all match. */
    fun markVerified() = verify(VerificationMethod.SAFETY_NUMBER_MANUAL)

    private fun verify(method: VerificationMethod) {
        viewModelScope.launch { unlessLocked { contacts.markVerified(contactId, method, clock.currentTimeMillis()) } }
    }
}

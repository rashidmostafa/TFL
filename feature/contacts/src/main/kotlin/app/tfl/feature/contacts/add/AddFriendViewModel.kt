package app.tfl.feature.contacts.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tfl.core.common.log.TflLog
import app.tfl.core.crypto.CryptoException
import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.crypto.pairing.InvalidCode
import app.tfl.core.crypto.pairing.PairingCodes
import app.tfl.core.crypto.pairing.PairingScan
import app.tfl.core.crypto.pairing.ScannedCode
import app.tfl.core.database.repository.ContactRepository
import app.tfl.core.database.repository.IdentityRepository
import app.tfl.core.designsystem.component.QrMatrix
import app.tfl.core.model.contact.Contact
import app.tfl.core.model.contact.KeySource
import app.tfl.core.session.PairingIdentity
import app.tfl.core.session.PairingProof
import app.tfl.core.session.di.WorkDispatcher
import app.tfl.feature.contacts.components.unlessLocked
import app.tfl.feature.contacts.qr.QrEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class AddFriendTab { MY_CODE, SCAN }

/** This phone's code on screen, and the seconds until it's replaced. */
data class ShownCode(val text: String, val matrix: QrMatrix, val secondsLeft: Int)

sealed interface PairingStep {
    /** Show your code, or scan theirs. */
    data object Ready : PairingStep

    /**
     * Their code is saved; now they scan this phone's answer.
     * @param verified this phone verified them already (their code answered this phone's), so
     *   the answer is only for their phone; otherwise both phones still need it.
     */
    data class Answering(val contactId: Long, val name: String, val verified: Boolean) : PairingStep
}

data class AddFriendUiState(
    val tab: AddFriendTab = AddFriendTab.MY_CODE,
    val myName: String = "",
    val myFingerprint: List<String> = emptyList(),
    val code: ShownCode? = null,
    val step: PairingStep = PairingStep.Ready,
    /** Why the last scanned code was refused. */
    val problem: InvalidCode? = null,
    /** A friend already has the scanned code's name, with another key: asks whether it's the same person. */
    val namesake: String? = null,
    /** An answer code went stale before the pairing finished. */
    val windowClosed: Boolean = false,
)

/**
 * Pairing in person. Each phone shows a code (a new one every 60 seconds) and scans the other's.
 * A scanned code that answers one of this phone's codes proves both phones scanned each other,
 * and the friend is saved as verified in person; otherwise they're saved unverified and this
 * phone shows its answer for them to scan. See docs/SECURITY_DESIGN.md, "Pairing in person".
 */
@HiltViewModel
class AddFriendViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    identities: IdentityRepository,
    private val pairing: PairingIdentity,
    private val contacts: ContactRepository,
    private val clock: DeviceClock,
    @param:WorkDispatcher private val work: CoroutineDispatcher,
) : ViewModel() {

    /** A scanned code this phone is answering, since when (time since boot). */
    private class Answer(val code: ScannedCode, val sinceElapsed: Long)

    private val state = MutableStateFlow(
        AddFriendUiState(tab = if (savedStateHandle.get<Boolean>(SCAN_ARG) == true) AddFriendTab.SCAN else AddFriendTab.MY_CODE),
    )
    val uiState: StateFlow<AddFriendUiState> = state.asStateFlow()

    private val addedEvents = Channel<Long>(Channel.BUFFERED)

    /** The friend to show as added: both phones are done. */
    val added: Flow<Long> = addedEvents.receiveAsFlow()

    private var ticker: Job? = null
    private var answer: Answer? = null
    private var pendingNamesake: Pair<ScannedCode, Contact>? = null
    private var busy = false

    /** The camera keeps reporting a code while it's in view; each one is handled once. */
    private var handledText: String? = null

    /** The last code refused, still in view: already explained, no need to check it again. */
    private var refusedText: String? = null

    init {
        viewModelScope.launch {
            identities.identity.collect { me ->
                state.update { it.copy(myName = me?.displayName.orEmpty(), myFingerprint = me?.fingerprintGroups.orEmpty()) }
            }
        }
    }

    fun selectTab(tab: AddFriendTab) {
        refusedText = null // its explanation goes with the tab, so check it again if it's still there
        state.update { it.copy(tab = tab, problem = null, windowClosed = false) }
    }

    /** Shows codes, a new one every 60 seconds, while the screen is visible. */
    fun start() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (true) {
                showNewCode()
                for (left in REFRESH_SECONDS downTo 1) {
                    state.update { it.copy(code = it.code?.copy(secondsLeft = left)) }
                    delay(1_000)
                }
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
    }

    fun onScanned(text: String) {
        if (busy || pendingNamesake != null || text == handledText || text == refusedText) return
        busy = true
        viewModelScope.launch {
            try {
                unlessLocked { handle(text) }
            } finally {
                busy = false
            }
        }
    }

    /** The answer to "is this the same person as your friend with that name?". */
    fun onNamesakeAnswer(samePerson: Boolean) {
        val (code, namesake) = pendingNamesake ?: return
        pendingNamesake = null
        state.update { it.copy(namesake = null) }
        busy = true
        viewModelScope.launch {
            try {
                unlessLocked { save(code, replacing = namesake.takeIf { samePerson }) }
            } finally {
                busy = false
            }
        }
    }

    /** Neither: nothing is saved. */
    fun dismissNamesake() {
        pendingNamesake = null
        state.update { it.copy(namesake = null) }
    }

    /** Finished showing the answer: on to the friend. */
    fun done() {
        val step = state.value.step as? PairingStep.Answering ?: return
        addedEvents.trySend(step.contactId)
    }

    private suspend fun handle(text: String) {
        when (val scan = pairing.check(text)) {
            is PairingScan.Invalid -> {
                refusedText = text
                state.update { it.copy(problem = scan.reason) }
            }
            is PairingScan.Valid -> {
                handledText = text
                val code = scan.code
                val known = contacts.findByKey(code.keys.signPublicKey)
                val namesake = if (known == null) contacts.othersNamed(code.displayName, code.keys).firstOrNull() else null
                if (namesake != null) {
                    pendingNamesake = code to namesake
                    state.update { it.copy(problem = null, namesake = namesake.name) }
                } else {
                    save(code, replacing = null)
                }
            }
        }
    }

    /** Saves the friend; [replacing] is a friend by the same name whose key this code replaces. */
    private suspend fun save(code: ScannedCode, replacing: Contact?) {
        val now = clock.currentTimeMillis()
        val proof = pairing.proofFor(code)
        if (replacing != null) contacts.changeKeys(replacing.id, code.keys, KeySource.IN_PERSON_SCAN, now, code.displayName)
        val contact = contacts.recordPairing(code.displayName, code.keys, mutual = proof != PairingProof.ONE_WAY, nowMillis = now)
        if (proof == PairingProof.BOTH_VERIFIED) {
            answer = null
            state.update { it.copy(step = PairingStep.Ready, problem = null) }
            addedEvents.send(contact.id)
            return
        }
        answer = Answer(code, clock.elapsedRealtime())
        state.update {
            it.copy(
                tab = AddFriendTab.MY_CODE,
                step = PairingStep.Answering(contact.id, contact.name, verified = proof == PairingProof.THEY_SCANNED_ME),
                problem = null,
                windowClosed = false,
            )
        }
        if (ticker != null) {
            stop()
            start() // the answer, straight away
        }
    }

    /** A fresh code, answering the friend being paired while their code is under 5 minutes old. */
    private suspend fun showNewCode() {
        val current = answer
        if (current != null && clock.elapsedRealtime() - current.sinceElapsed > PairingCodes.VALIDITY_MILLIS) {
            answer = null
            state.update { it.copy(step = PairingStep.Ready, windowClosed = true) }
        }
        val issued = try {
            unlessLocked { pairing.newCode(answer?.code) }
        } catch (e: CryptoException) {
            // The lock state became unreadable: the next unlock sends TFL to its recovery screen.
            TflLog.w(TAG, e) { "Couldn't sign a pairing code" }
            null
        } ?: return
        val matrix = withContext(work) { QrEncoder.encode(issued.text) }
        state.update { it.copy(code = ShownCode(issued.text, matrix, REFRESH_SECONDS)) }
    }

    internal companion object {
        private const val TAG = "AddFriend"
        const val SCAN_ARG = "scan"
        val REFRESH_SECONDS = (PairingCodes.REFRESH_MILLIS / 1_000).toInt()
    }
}

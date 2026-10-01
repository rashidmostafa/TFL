package app.tfl.feature.contacts.friend

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tfl.core.crypto.pairing.SafetyNumbers
import app.tfl.core.database.repository.ContactRepository
import app.tfl.core.database.repository.IdentityRepository
import app.tfl.core.designsystem.component.initialsOf
import app.tfl.core.model.contact.Contact
import app.tfl.core.model.contact.KeySource
import app.tfl.core.model.contact.PreviousKey
import app.tfl.core.model.contact.VerificationMethod
import app.tfl.feature.contacts.components.Trust
import app.tfl.feature.contacts.components.unlessLocked
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A friend as the contacts screens show them. */
data class FriendDetails(
    val id: Long,
    /** Your nickname for them, or else their own name. */
    val name: String,
    /** The name in their pairing code. */
    val displayName: String,
    val nickname: String?,
    val initials: String,
    val trust: Trust,
    val verifiedBy: VerificationMethod?,
    val verifiedAtMillis: Long?,
    val fingerprint: List<String>,
    /** How their current key arrived, and since when. */
    val keySource: KeySource,
    val keySinceMillis: Long,
    val firstSeenAtMillis: Long,
    val keyChangedAtMillis: Long?,
    val blocked: Boolean,
)

data class PreviousKeyItem(
    val fingerprint: List<String>,
    val firstSeenAtMillis: Long,
    val replacedAtMillis: Long,
    /** How the key that replaced it arrived. */
    val replacedBy: KeySource,
)

data class FriendUiState(
    val loading: Boolean = true,
    /** Null once they're deleted. */
    val friend: FriendDetails? = null,
    /** Newest first. */
    val history: List<PreviousKeyItem> = emptyList(),
    /** 12 groups of 5 digits, the same on their phone. */
    val safetyNumber: List<String> = emptyList(),
)

/** One friend, for their profile, the "friend added" screen and the key change warning. */
@HiltViewModel
class FriendViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    identities: IdentityRepository,
    safetyNumbers: SafetyNumbers,
    private val contacts: ContactRepository,
) : ViewModel() {

    private val contactId: Long = checkNotNull(savedStateHandle.get<Long>(CONTACT_ID_ARG)) { "No contact id" }

    val uiState: StateFlow<FriendUiState> = combine(
        contacts.contact(contactId),
        contacts.keyHistory(contactId),
        identities.identity,
    ) { contact, history, me ->
        FriendUiState(
            loading = false,
            friend = contact?.let { details(it, history) },
            history = history.map { it.toItem() },
            safetyNumber = if (contact != null && me != null) {
                safetyNumbers.between(me.signPublicKey, contact.keys.signPublicKey).groups
            } else {
                emptyList()
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendUiState())

    private val closedEvents = Channel<Unit>(Channel.CONFLATED)

    /** They were deleted: the screen closes. */
    val closed: Flow<Unit> = closedEvents.receiveAsFlow()

    fun setNickname(nickname: String) {
        viewModelScope.launch { unlessLocked { contacts.setNickname(contactId, nickname) } }
    }

    fun setBlocked(blocked: Boolean) {
        viewModelScope.launch { unlessLocked { contacts.setBlocked(contactId, blocked) } }
    }

    /** Wipes their keys and key history from this phone. */
    fun delete() {
        viewModelScope.launch {
            unlessLocked { contacts.delete(contactId) } ?: return@launch
            closedEvents.send(Unit)
        }
    }

    private fun details(contact: Contact, history: List<PreviousKey>) = FriendDetails(
        id = contact.id,
        name = contact.name,
        displayName = contact.displayName,
        nickname = contact.nickname,
        initials = initialsOf(contact.name),
        trust = Trust.of(contact),
        verifiedBy = contact.verifiedBy,
        verifiedAtMillis = contact.verifiedAtMillis,
        fingerprint = contact.keys.fingerprintGroups,
        keySource = contact.keySource,
        keySinceMillis = history.maxOfOrNull { it.replacedAtMillis } ?: contact.firstSeenAtMillis,
        firstSeenAtMillis = contact.firstSeenAtMillis,
        keyChangedAtMillis = contact.keyChangedAtMillis,
        blocked = contact.blocked,
    )

    private fun PreviousKey.toItem() = PreviousKeyItem(keys.fingerprintGroups, firstSeenAtMillis, replacedAtMillis, replacedBy)

    internal companion object {
        const val CONTACT_ID_ARG = "contactId"
    }
}

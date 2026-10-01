package app.tfl.feature.contacts.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tfl.core.database.repository.ContactRepository
import app.tfl.core.designsystem.component.initialsOf
import app.tfl.feature.contacts.components.Trust
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class FriendItem(
    val id: Long,
    val name: String,
    val initials: String,
    val trust: Trust,
    val blocked: Boolean,
    /** The first two fingerprint groups, to tell namesakes apart. */
    val fingerprintStart: String,
)

data class FriendsUiState(
    val loading: Boolean = true,
    val friends: List<FriendItem> = emptyList(),
)

@HiltViewModel
class FriendsViewModel @Inject constructor(contacts: ContactRepository) : ViewModel() {

    val uiState: StateFlow<FriendsUiState> = contacts.contacts
        .map { list ->
            FriendsUiState(
                loading = false,
                friends = list.map { contact ->
                    FriendItem(
                        id = contact.id,
                        name = contact.name,
                        initials = initialsOf(contact.name),
                        trust = Trust.of(contact),
                        blocked = contact.blocked,
                        fingerprintStart = contact.keys.fingerprintGroups.take(2).joinToString(" "),
                    )
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendsUiState())
}

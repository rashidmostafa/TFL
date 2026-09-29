package app.tfl.feature.chats

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tfl.feature.chats.fake.FakeChatsData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChatsViewModel @Inject constructor() : ViewModel() {

    /** Search text, owned here so it survives recomposition and feeds the filter synchronously. */
    val searchQuery = TextFieldState()

    private val filter = MutableStateFlow(ChatFilter.ALL)

    val uiState: StateFlow<ChatsUiState> =
        combine(snapshotFlow { searchQuery.text.toString() }, filter, ::buildUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), buildUiState("", ChatFilter.ALL))

    fun onFilterSelect(value: ChatFilter) {
        filter.value = value
    }

    private fun buildUiState(query: String, filter: ChatFilter): ChatsUiState {
        val all = FakeChatsData.conversations
        val visible = all.filterBy(filter, query)
        return ChatsUiState(
            peerCount = FakeChatsData.PEER_COUNT,
            torConnected = FakeChatsData.TOR_CONNECTED,
            filter = filter,
            counts = ChatFilter.entries.associateWith { f -> all.count { f.matches(it.kind) } },
            broadcasts = visible.filter { it.kind == ConversationKind.BROADCAST },
            threads = visible.filter { it.kind != ConversationKind.BROADCAST },
            hasQuery = query.isNotBlank(),
        )
    }
}

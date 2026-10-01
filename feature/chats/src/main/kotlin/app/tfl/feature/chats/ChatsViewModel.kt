package app.tfl.feature.chats

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tfl.core.database.repository.ContactRepository
import app.tfl.core.database.repository.ConversationRepository
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.database.repository.SettingsRepository
import app.tfl.core.model.contact.Contact
import app.tfl.core.model.message.ConversationSummary
import app.tfl.core.model.message.MessageKind
import app.tfl.core.transport.NearbyReadiness
import app.tfl.core.transport.TransportStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatsViewModel @Inject constructor(
    conversations: ConversationRepository,
    contacts: ContactRepository,
    settings: SettingsRepository,
    transport: TransportStatus,
    readiness: NearbyReadiness,
    private val clock: ChatClock,
) : ViewModel() {

    /** Search text, owned here so it survives recomposition and feeds the filter synchronously. */
    val searchQuery = TextFieldState()

    /** A minute's tick: relative times move on, and expired messages drop out of the previews. */
    private val time: Flow<TimeContext> = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_MILLIS)
        }
    }

    private val nearby: Flow<NearbyState> =
        combine(settings.observe(SettingKeys.NEARBY_ENABLED), readiness.permitted, readiness.ready, transport.reachable) { on, permitted, ready, reachable ->
            when {
                !on -> NearbyState.Off
                !ready -> NearbyState.NeedsSetup(permitted)
                else -> NearbyState.On(reachable.size)
            }
        }

    val uiState: StateFlow<ChatsUiState> = time.flatMapLatest { now ->
        combine(
            conversations.summaries(now.nowMillis),
            contacts.contacts,
            nearby,
            transport.reachable,
            snapshotFlow { searchQuery.text.toString() },
        ) { summaries, friends, nearby, reachable, query ->
            val byId = friends.associateBy { it.id }
            ChatsUiState(
                nearby = nearby,
                threads = summaries.mapNotNull { summary -> byId[summary.contactId]?.let { thread(summary, it, reachable) } }.matching(query),
                friends = friends.map { FriendItem(it.id, it.name, it.trust, it.blocked) },
                hasQuery = query.isNotBlank(),
                time = now,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ChatsUiState(NearbyState.Off, emptyList(), emptyList(), hasQuery = false, time = clock.now()),
    )

    private fun thread(summary: ConversationSummary, contact: Contact, reachable: Set<Long>): ThreadItem {
        val last = summary.lastMessage
        val preview = when {
            last == null -> ThreadPreview.Empty
            last.kind == MessageKind.TIMER_CHANGED -> ThreadPreview.Timer(last.expiresAfterSeconds, last.outgoing)
            last.deleted -> ThreadPreview.Deleted(last.outgoing)
            else -> ThreadPreview.Text(last.text.orEmpty(), last.outgoing)
        }
        return ThreadItem(
            contactId = contact.id,
            name = contact.name,
            trust = contact.trust,
            blocked = contact.blocked,
            preview = preview,
            atMillis = last?.sortAtMillis,
            unread = summary.unreadCount,
            lastStatus = last?.takeIf { it.outgoing && it.kind == MessageKind.TEXT && !it.deleted }?.status,
            nearbyNow = contact.id in reachable,
        )
    }
}

package app.tfl.feature.chats.scheduled

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tfl.core.database.repository.ContactRepository
import app.tfl.core.database.repository.ConversationRepository
import app.tfl.core.database.repository.MessageRepository
import app.tfl.core.transport.Messenger
import app.tfl.core.transport.SendOutcome
import app.tfl.core.transport.SendRefusal
import app.tfl.feature.chats.ChatClock
import app.tfl.feature.chats.TimeContext
import app.tfl.feature.chats.ticking
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A message waiting for its time. */
@Immutable
data class ScheduledItem(val id: Long, val text: String, val atMillis: Long)

@Immutable
data class ScheduledUiState(
    val name: String = "",
    val items: List<ScheduledItem> = emptyList(),
    /** Nothing can be sent to them now (blocked, or a key change to verify). */
    val canSend: Boolean = true,
    val time: TimeContext,
    val loading: Boolean = true,
)

/** One friend's scheduled messages: change them, send them now, or delete them. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduledViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    contacts: ContactRepository,
    conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val messenger: Messenger,
    private val clock: ChatClock,
) : ViewModel() {

    private val contactId: Long = checkNotNull(savedStateHandle.get<Long>(CONTACT_ID_ARG)) { "No contact id" }
    /** Re-read when a message falls due, after each change, and when the editor opens. */
    private val now = MutableStateFlow(clock.now())
    private val refusals = Channel<SendRefusal>(Channel.BUFFERED)

    /** Why a change didn't go through. */
    val refused: Flow<SendRefusal> = refusals.receiveAsFlow()

    val uiState: StateFlow<ScheduledUiState> = combine(
        contacts.contact(contactId),
        combine(conversations.withContact(contactId), now) { conversation, time -> conversation to time }.flatMapLatest { (conversation, time) ->
            conversation?.let { messages.scheduled(it.id, time.nowMillis) } ?: flowOf(emptyList())
        },
        // "Today, 18:00" becomes "Tomorrow, 18:00" at midnight, while the list is open.
        clock.ticking(now),
    ) { contact, scheduled, time ->
        ScheduledUiState(
            name = contact?.name.orEmpty(),
            items = scheduled.map { ScheduledItem(it.id, it.text.orEmpty(), checkNotNull(it.scheduledForMillis)) },
            canSend = contact?.canSend == true,
            time = time,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduledUiState(time = clock.now()))

    init {
        // Due messages leave the list at their time.
        viewModelScope.launch {
            now.flatMapLatest { time -> messages.nextChange(time.nowMillis) }.collectLatest { next ->
                if (next == null) return@collectLatest
                delay((next - clock.now().nowMillis).coerceAtLeast(0))
                now.value = clock.now()
            }
        }
    }

    /** Reads the clock again, before the editor decides which times are still ahead. */
    fun refreshTime() {
        now.value = clock.now()
    }

    fun schedule(text: String, atMillis: Long) = submit { messenger.sendText(contactId, text, scheduleAtMillis = atMillis) }

    fun change(id: Long, text: String, atMillis: Long) = submit { messenger.reschedule(id, text, atMillis) }

    fun sendNow(id: Long) = submit { messenger.sendNow(id) }

    fun delete(id: Long) = submit { messenger.cancelScheduled(id) }

    private fun submit(action: suspend () -> SendOutcome) {
        viewModelScope.launch {
            (action() as? SendOutcome.Refused)?.let { refusals.send(it.reason) }
            now.value = clock.now()
        }
    }

    internal companion object {
        const val CONTACT_ID_ARG = "contactId"
    }
}

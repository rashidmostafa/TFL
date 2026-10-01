package app.tfl.feature.chats.conversation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tfl.core.common.log.TflLog
import app.tfl.core.database.DatabaseLockedException
import app.tfl.core.database.repository.ContactRepository
import app.tfl.core.database.repository.ConversationRepository
import app.tfl.core.database.repository.MessageRepository
import app.tfl.core.model.Transport
import app.tfl.core.model.contact.Contact
import app.tfl.core.model.message.ChatMessage
import app.tfl.core.model.message.Conversation
import app.tfl.core.model.message.MessageId
import app.tfl.core.model.message.MessageKind
import app.tfl.core.model.message.MessageLimits
import app.tfl.core.transport.ChatPresence
import app.tfl.core.transport.MessageAlerts
import app.tfl.core.transport.Messenger
import app.tfl.core.transport.SendOutcome
import app.tfl.core.transport.TransportStatus
import app.tfl.feature.chats.ChatClock
import app.tfl.feature.chats.ContactTrust
import app.tfl.feature.chats.TimeContext
import app.tfl.feature.chats.ticking
import app.tfl.feature.chats.trust
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
import java.time.LocalDate
import javax.inject.Inject

/** A 1:1 conversation: its messages, the composer, and every action on a message. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contacts: ContactRepository,
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val messenger: Messenger,
    transport: TransportStatus,
    private val presence: ChatPresence,
    private val alerts: MessageAlerts,
    private val clock: ChatClock,
) : ViewModel() {

    val contactId: Long = checkNotNull(savedStateHandle.get<Long>(CONTACT_ID_ARG)) { "No contact id" }

    /** What's being typed, owned here so it survives recomposition. */
    val composer = TextFieldState()

    /**
     * Re-read whenever a message expires or a scheduled one falls due, when the screen shows again,
     * and when something opens that depends on it (a message's menu, its info, the schedule picker).
     */
    private val now = MutableStateFlow(clock.now())

    /** What the screen shows: [now], then the clock every minute (day labels move on at midnight). */
    private val shownTime: Flow<TimeContext> = clock.ticking(now)
    private val replyingTo = MutableStateFlow<ChatMessage?>(null)
    private val editing = MutableStateFlow<ChatMessage?>(null)
    private val selectedId = MutableStateFlow<Long?>(null)
    private val infoId = MutableStateFlow<Long?>(null)
    private val visible = MutableStateFlow(false)

    private val noticesChannel = Channel<ChatNotice>(Channel.BUFFERED)

    /** Messages for a snackbar: refusals, "copied", "scheduled". */
    val notices: Flow<ChatNotice> = noticesChannel.receiveAsFlow()

    private val conversation: Flow<Conversation?> = conversations.withContact(contactId)

    private val thread: Flow<List<ChatMessage>> = combine(conversation, now) { conversation, time -> conversation to time }
        .flatMapLatest { (conversation, time) -> conversation?.let { messages.messages(it.id, time.nowMillis) } ?: flowOf(emptyList()) }

    private val scheduled: Flow<List<ChatMessage>> = combine(conversation, now) { conversation, time -> conversation to time }
        .flatMapLatest { (conversation, time) -> conversation?.let { messages.scheduled(it.id, time.nowMillis) } ?: flowOf(emptyList()) }

    private data class ThreadState(val conversation: Conversation?, val messages: List<ChatMessage>, val scheduled: Int)

    private data class Focus(val replyingTo: ChatMessage?, val editing: ChatMessage?, val selected: Long?, val info: Long?)

    val uiState: StateFlow<ConversationUiState> = combine(
        contacts.contact(contactId),
        combine(conversation, thread, scheduled) { conversation, messages, scheduled -> ThreadState(conversation, messages, scheduled.size) },
        transport.reachable,
        combine(replyingTo, editing, selectedId, infoId, ::Focus),
        shownTime,
    ) { contact, thread, reachable, focus, time -> build(contact, thread, reachable, focus, time) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationUiState(contactId, time = clock.now()))

    init {
        viewModelScope.launch {
            now.flatMapLatest { time -> messages.nextChange(time.nowMillis) }.collectLatest { next ->
                if (next == null) return@collectLatest
                delay((next - clock.now().nowMillis).coerceAtLeast(0))
                now.value = clock.now()
            }
        }
        // While it's on screen, what arrives is read.
        viewModelScope.launch {
            combine(conversation, thread, visible) { conversation, messages, visible -> Triple(conversation, messages, visible) }
                .collect { (conversation, messages, visible) ->
                    if (!visible || conversation == null) return@collect
                    val latest = messages.filter { !it.outgoing }.mapNotNull { it.receivedAtMillis }.maxOrNull() ?: return@collect
                    if (latest > conversation.lastReadAtMillis) quietly { conversations.markRead(conversation.id, latest) }
                }
        }
    }

    private fun build(contact: Contact?, thread: ThreadState, reachable: Set<Long>, focus: Focus, time: TimeContext): ConversationUiState {
        val byMsgId = thread.messages.associateBy { it.msgId }
        val selected = focus.selected?.let { id -> thread.messages.firstOrNull { it.id == id } }
        val info = focus.info?.let { id -> thread.messages.firstOrNull { it.id == id } }
        return ConversationUiState(
            contactId = contactId,
            name = contact?.name.orEmpty(),
            trust = contact?.trust ?: ContactTrust.UNVERIFIED,
            fingerprint = contact?.keys?.fingerprintGroups.orEmpty(),
            nearbyNow = contactId in reachable,
            timerSeconds = thread.conversation?.expiresAfterSeconds ?: 0,
            rows = rows(thread.messages, byMsgId, time),
            block = when {
                contact == null -> SendBlock.GONE
                contact.blocked -> SendBlock.BLOCKED
                !contact.canSend -> SendBlock.KEY_CHANGED
                else -> null
            },
            replyingTo = focus.replyingTo?.let { Quote(it.outgoing, it.text) },
            editing = focus.editing != null,
            scheduledCount = thread.scheduled,
            time = time,
            loading = false,
            actions = selected?.let { actionsFor(it, byMsgId, time) },
            info = info?.let(::infoFor),
        )
    }

    private fun rows(thread: List<ChatMessage>, byMsgId: Map<MessageId, ChatMessage>, time: TimeContext): List<ChatRow> {
        val rows = ArrayList<ChatRow>(thread.size + 4)
        var day: LocalDate? = null
        for (message in thread) {
            val date = time.date(message.sortAtMillis)
            if (date != day) {
                rows += ChatRow.Day(message.sortAtMillis)
                day = date
            }
            rows += when (message.kind) {
                MessageKind.TIMER_CHANGED -> ChatRow.TimerChanged(message.id, message.expiresAfterSeconds, message.outgoing)
                MessageKind.TEXT -> ChatRow.Message(view(message, byMsgId))
            }
        }
        return rows
    }

    private fun view(message: ChatMessage, byMsgId: Map<MessageId, ChatMessage>) = MessageView(
        id = message.id,
        outgoing = message.outgoing,
        text = message.text.takeUnless { message.deleted },
        edited = message.edited && !message.deleted,
        atMillis = message.sortAtMillis,
        status = message.status,
        transport = message.transport?.takeIf { it != Transport.QUEUED },
        quote = message.replyTo?.let { target ->
            val quoted = byMsgId[target]
            Quote(fromMe = quoted?.outgoing ?: false, text = quoted?.text?.takeUnless { quoted.deleted })
        },
        reactions = message.reactions,
        disappearsAtMillis = message.expiresAtMillis,
    )

    private fun actionsFor(message: ChatMessage, byMsgId: Map<MessageId, ChatMessage>, time: TimeContext) = MessageActions(
        message = view(message, byMsgId),
        canReply = !message.deleted,
        canEdit = messenger.canEdit(message, time.nowMillis),
        // Rounded up: 14:30 left reads "15 minutes".
        editMinutesLeft = ((message.createdAtMillis + MessageLimits.EDIT_WINDOW_MILLIS - time.nowMillis + 59_999) / 60_000).toInt().coerceAtLeast(1),
        canDeleteForEveryone = messenger.canDeleteForEveryone(message),
        myReaction = message.reactions.firstOrNull { it.fromMe }?.emoji,
    )

    private fun infoFor(message: ChatMessage) = MessageInfo(
        outgoing = message.outgoing,
        status = message.status,
        transport = message.transport,
        writtenAtMillis = message.createdAtMillis,
        sentAtMillis = message.sentAtMillis,
        deliveredAtMillis = message.deliveredAtMillis,
        receivedAtMillis = message.receivedAtMillis,
        editedAtMillis = message.editedAtMillis,
        scheduledForMillis = message.scheduledForMillis,
        disappearsAtMillis = message.expiresAtMillis,
        shortId = message.msgId.hex.take(16),
    )

    /** The screen is shown: its messages are read, and don't raise alerts. */
    fun onVisible() {
        refreshTime()
        visible.value = true
        presence.visibleContact.value = contactId
        alerts.clear()
    }

    fun onHidden() {
        visible.value = false
        if (presence.visibleContact.value == contactId) presence.visibleContact.value = null
    }

    fun send() {
        val text = composer.text.toString()
        if (text.isBlank()) return
        val edit = editing.value
        submit(clearOnSuccess = true) {
            if (edit != null) messenger.edit(edit.id, text) else messenger.sendText(contactId, text, replyingTo.value?.msgId)
        }
    }

    /** Sends what's typed at [atMillis] instead of now. */
    fun schedule(atMillis: Long) {
        val text = composer.text.toString()
        if (text.isBlank() || editing.value != null) return
        submit(clearOnSuccess = true, notice = ChatNotice.Scheduled) {
            messenger.sendText(contactId, text, replyingTo.value?.msgId, scheduleAtMillis = atMillis)
        }
    }

    /** Opens a message's menu: what it offers (editing is for 15 minutes) depends on the time now. */
    fun select(messageId: Long?) {
        if (messageId != null) refreshTime()
        selectedId.value = messageId
    }

    fun showInfo(messageId: Long?) {
        if (messageId != null) refreshTime()
        selectedId.value = null
        infoId.value = messageId
    }

    /** Reads the clock again, before showing something that counts from now (the schedule picker). */
    fun refreshTime() {
        now.value = clock.now()
    }

    fun reply(messageId: Long) {
        selectedId.value = null
        viewModelScope.launch {
            editing.value = null
            replyingTo.value = quietly { messages.get(messageId) }
        }
    }

    fun startEdit(messageId: Long) {
        selectedId.value = null
        viewModelScope.launch {
            val message = quietly { messages.get(messageId) } ?: return@launch
            replyingTo.value = null
            editing.value = message
            composer.setTextAndPlaceCursorAtEnd(message.text.orEmpty())
        }
    }

    /** Leaves replying or editing. */
    fun cancelComposerMode() {
        if (editing.value != null) composer.clearText()
        replyingTo.value = null
        editing.value = null
    }

    /** Sets [emoji] as your reaction, or removes it if it's already yours. */
    fun react(messageId: Long, emoji: String) {
        val mine = uiState.value.actions?.takeIf { it.message.id == messageId }?.myReaction
        selectedId.value = null
        submit { messenger.react(messageId, emoji.takeUnless { it == mine }) }
    }

    fun deleteForMe(messageId: Long) {
        selectedId.value = null
        viewModelScope.launch { quietly { messenger.deleteForMe(messageId) } }
    }

    fun deleteForEveryone(messageId: Long) {
        selectedId.value = null
        submit { messenger.deleteForEveryone(messageId) }
    }

    fun setTimer(seconds: Int) = submit { messenger.setTimer(contactId, seconds) }

    fun unblock() {
        viewModelScope.launch { quietly { contacts.setBlocked(contactId, false) } }
    }

    fun copied() {
        selectedId.value = null
        noticesChannel.trySend(ChatNotice.Copied)
    }

    private fun submit(clearOnSuccess: Boolean = false, notice: ChatNotice? = null, action: suspend () -> SendOutcome) {
        viewModelScope.launch {
            when (val outcome = action()) {
                is SendOutcome.Queued -> {
                    if (clearOnSuccess) {
                        composer.clearText()
                        replyingTo.value = null
                        editing.value = null
                    }
                    notice?.let { noticesChannel.send(it) }
                }
                is SendOutcome.Refused -> noticesChannel.send(ChatNotice.Refused(outcome.reason))
            }
        }
    }

    /** Null if TFL locked meanwhile: the screen is going away with the database. */
    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: DatabaseLockedException) {
        TflLog.d(TAG) { "Locked meanwhile" }
        null
    }

    internal companion object {
        const val CONTACT_ID_ARG = "contactId"
        private const val TAG = "ConversationViewModel"
    }
}

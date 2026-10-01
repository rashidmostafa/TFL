package app.tfl.debug

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.R
import app.tfl.core.crypto.envelope.EnvelopeCodec
import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.database.repository.ContactRepository
import app.tfl.core.database.repository.ConversationRepository
import app.tfl.core.database.repository.IdentityRepository
import app.tfl.core.database.repository.MessageRepository
import app.tfl.core.designsystem.component.EmptyState
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.PillButton
import app.tfl.core.designsystem.component.RadioRow
import app.tfl.core.designsystem.component.SheetHeader
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflDivider
import app.tfl.core.designsystem.component.TflModalBottomSheet
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.component.ToggleRow
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.Transport
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.contact.KeySource
import app.tfl.core.model.message.MessageId
import app.tfl.core.transport.debug.SimulatedFriend
import app.tfl.core.transport.debug.TransportEvents
import app.tfl.core.transport.radio.RadioFaults
import app.tfl.feature.contacts.debug.ContactsDebugTools
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the transport's developer tools need from the app graph. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TransportDebugEntryPoint {
    fun events(): TransportEvents
    fun faults(): RadioFaults
    fun simulatedFriend(): SimulatedFriend
    fun identities(): IdentityRepository
    fun contacts(): ContactRepository
    fun conversations(): ConversationRepository
    fun messages(): MessageRepository
    fun codec(): EnvelopeCodec
    fun sodium(): SodiumApi
    fun clock(): DeviceClock
}

private fun Context.transportTools() = EntryPointAccessors.fromApplication(this, TransportDebugEntryPoint::class.java)

/** Rows for Settings → Developer: the log, the simulated friend, faults, and fake conversations. */
@Composable
internal fun TransportDebugRows(onOpenLog: () -> Unit) {
    val context = LocalContext.current
    val tools = remember(context) { context.transportTools() }
    val scope = rememberCoroutineScope()
    val simulated by tools.simulatedFriend().running.collectAsStateWithLifecycle()
    var choosingFaults by remember { mutableStateOf(false) }
    var drop by remember { mutableIntStateOf(tools.faults().dropNext) }
    var delay by remember { mutableLongStateOf(tools.faults().delayMillis) }
    var fakeAdded by remember { mutableStateOf(false) }

    ListRow(
        title = stringResource(R.string.debug_transport_log_title),
        subtitle = stringResource(R.string.debug_transport_log_subtitle),
        icon = MaterialSymbols.Terminal,
        standalone = false,
        onClick = onOpenLog,
    )
    TflDivider()
    ToggleRow(
        title = stringResource(R.string.debug_simulated_friend_title),
        subtitle = stringResource(R.string.debug_simulated_friend_subtitle),
        icon = MaterialSymbols.Sensors,
        checked = simulated,
        onCheckedChange = { on ->
            if (!on) {
                tools.simulatedFriend().stop()
                return@ToggleRow
            }
            scope.launch {
                val me = tools.identities().get() ?: return@launch
                tools.simulatedFriend().start(ContactsDebugTools.testFriendSeed(context), ContactKeys(me.signPublicKey, me.kexPublicKey, me.fingerprintHex))
            }
        },
    )
    TflDivider()
    ListRow(
        title = stringResource(R.string.debug_faults_title),
        subtitle = stringResource(R.string.debug_faults_subtitle, drop, delay / 1_000),
        icon = MaterialSymbols.SensorsOff,
        standalone = false,
        onClick = { choosingFaults = true },
    )
    TflDivider()
    ListRow(
        title = stringResource(R.string.debug_fake_threads_title),
        subtitle = stringResource(if (fakeAdded) R.string.debug_fake_threads_done else R.string.debug_fake_threads_subtitle),
        icon = MaterialSymbols.Forum,
        standalone = false,
        onClick = {
            scope.launch {
                addFakeConversations(tools)
                fakeAdded = true
            }
        },
    )

    if (choosingFaults) {
        FaultsSheet(
            drop = drop,
            delayMillis = delay,
            onDrop = {
                tools.faults().dropNext = it
                drop = it
            },
            onDelay = {
                tools.faults().delayMillis = it
                delay = it
            },
            onDismiss = { choosingFaults = false },
        )
    }
}

@Composable
private fun FaultsSheet(drop: Int, delayMillis: Long, onDrop: (Int) -> Unit, onDelay: (Long) -> Unit, onDismiss: () -> Unit) {
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(stringResource(R.string.debug_faults_title), icon = MaterialSymbols.SensorsOff, onClose = onDismiss)
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.debug_faults_drop),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = TflTheme.typography.labelMd,
                color = TflTheme.colors.textMuted,
            )
            listOf(0, 1, 3, 10).forEach { count ->
                RadioRow(pluralStringResource(R.plurals.debug_faults_frames, count, count), selected = drop == count, onSelect = { onDrop(count) })
            }
            Text(
                stringResource(R.string.debug_faults_delay),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = TflTheme.typography.labelMd,
                color = TflTheme.colors.textMuted,
            )
            listOf(0L, 2_000L, 5_000L, 15_000L).forEach { millis ->
                RadioRow(stringResource(R.string.debug_faults_seconds, millis / 1_000), selected = delayMillis == millis, onSelect = { onDelay(millis) })
            }
        }
    }
}

/** The transport's recent events: links, handshakes, frame sizes, retries. Never content or keys. */
@Composable
internal fun TransportLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val events = remember(context) { context.transportTools().events() }
    val log by events.events.collectAsStateWithLifecycle()
    val format = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT) }
    Column(
        Modifier
            .fillMaxSize()
            .background(TflTheme.colors.canvas),
    ) {
        TflTopBar(
            title = stringResource(R.string.debug_transport_log_title),
            navigationIcon = { TflBackButton(onClick = onBack) },
            actions = { PillButton(stringResource(R.string.debug_transport_log_clear), onClick = events::clear, icon = MaterialSymbols.Delete) },
        )
        if (log.isEmpty()) {
            EmptyState(
                icon = MaterialSymbols.Terminal,
                title = stringResource(R.string.debug_transport_log_empty),
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(log.asReversed()) { event ->
                Text(
                    text = "${format.format(Date(event.atMillis))}  ${event.text}",
                    style = TflTheme.typography.codeSm,
                    color = TflTheme.colors.textPrimary,
                )
            }
        }
    }
}

/**
 * Three made-up friends with conversations in every state a message can be in. Written straight
 * to this phone's database: nothing is sealed or queued, so nothing is ever sent.
 */
private suspend fun addFakeConversations(tools: TransportDebugEntryPoint) {
    val contacts = tools.contacts()
    val conversations = tools.conversations()
    val messages = tools.messages()
    val now = tools.clock().currentTimeMillis()
    val minute = 60_000L
    fun id() = MessageId(tools.sodium().randomBytes(MessageId.SIZE))
    fun keys(): ContactKeys {
        val sign = tools.sodium().randomBytes(32)
        return ContactKeys(sign, tools.sodium().randomBytes(32), sign.copyOf(16).joinToString("") { "%02X".format(it) })
    }

    val nia = contacts.recordPairing("Nia (fake)", keys(), mutual = true, nowMillis = now - 10 * 24 * 60 * minute)
    val niaChat = conversations.getOrCreate(nia.id, now).id
    val question = id()
    messages.addIncoming(niaChat, question, "Are you near the water tower?", null, now - 120 * minute, now - 120 * minute, 0, Transport.NEARBY)
    messages.setReaction(niaChat, question, fromMe = true, emoji = "👍", atMillis = now - 119 * minute)
    val answer = id()
    messages.addOutgoing(niaChat, answer, "Yes, just got here", question, now - 118 * minute, 0)
    messages.markDelivered(niaChat, listOf(answer), now - 118 * minute + 2_000, Transport.NEARBY)
    messages.applyEdit(niaChat, answer, outgoing = true, "Yes, just got here. Bring water", 1, now - 117 * minute)
    val radio = id()
    messages.addOutgoing(niaChat, radio, "Bringing the radio", null, now - 30 * minute, 0)
    messages.markSent(listOf(radio), now - 30 * minute + 1_000, Transport.NEARBY)
    messages.addOutgoing(niaChat, id(), "On my way", null, now - 2 * minute, 0)
    conversations.touch(niaChat, now - 2 * minute)

    val oskar = contacts.recordPairing("Oskar (fake)", keys(), mutual = false, nowMillis = now - 5 * 24 * 60 * minute)
    val oskarChat = conversations.getOrCreate(oskar.id, now).id
    conversations.applyTimer(oskarChat, 3_600, now - 50 * minute)
    messages.addTimerChange(oskarChat, id(), 3_600, now - 50 * minute, now - 50 * minute)
    messages.addIncoming(oskarChat, id(), "This one disappears in an hour", null, now - 40 * minute, now - 40 * minute, 3_600, Transport.NEARBY)
    val gone = id()
    messages.addIncoming(oskarChat, gone, "wrong chat, sorry", null, now - 35 * minute, now - 35 * minute, 3_600, Transport.NEARBY)
    messages.deleteForEveryone(oskarChat, gone, outgoing = false)
    val failed = id()
    messages.addOutgoing(oskarChat, failed, "Sent a month ago, never delivered", null, now - 31L * 24 * 60 * minute, 0)
    messages.markFailed(listOf(failed))
    conversations.touch(oskarChat, now - 35 * minute)

    val priya = contacts.recordPairing("Priya (fake)", keys(), mutual = true, nowMillis = now - 40 * 24 * 60 * minute)
    val priyaChat = conversations.getOrCreate(priya.id, now).id
    messages.addIncoming(priyaChat, id(), "Is this still you?", null, now - 3 * 60 * minute, now - 3 * 60 * minute, 0, Transport.NEARBY)
    contacts.changeKeys(priya.id, keys(), KeySource.DEBUG_SIMULATION, now - 60 * minute)
    conversations.touch(priyaChat, now - 3 * 60 * minute)
}

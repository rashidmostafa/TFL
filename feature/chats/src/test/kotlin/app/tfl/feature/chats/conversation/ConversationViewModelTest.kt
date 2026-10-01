package app.tfl.feature.chats.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.contact.KeySource
import app.tfl.core.model.message.Reaction
import app.tfl.core.testing.MainDispatcherRule
import app.tfl.core.testing.typeText
import app.tfl.core.transport.SendRefusal
import app.tfl.feature.chats.ChatsKit
import app.tfl.feature.chats.TestIdentity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val kit = ChatsKit(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = kit.close()

    private fun conversation(contactId: Long) = ConversationViewModel(
        SavedStateHandle(mapOf(ConversationViewModel.CONTACT_ID_ARG to contactId)),
        kit.fixture.contacts, kit.conversations, kit.messages, kit.messenger, kit.transport, kit.presence, kit.alerts, kit.chatClock,
    )

    private fun ConversationUiState.messages() = rows.filterIsInstance<ChatRow.Message>().map { it.message }

    private suspend fun ConversationViewModel.stateWhere(predicate: (ConversationUiState) -> Boolean) = uiState.first { !it.loading && predicate(it) }

    private suspend fun ConversationViewModel.type(text: String) {
        composer.typeText(text)
    }

    @Test
    fun `what you send shows at once, queued`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        val chat = conversation(elena.id)
        chat.type("Meet at the water tower")
        chat.send()

        val sent = chat.stateWhere { it.messages().isNotEmpty() }.messages().single()
        assertEquals("Meet at the water tower", sent.text)
        assertTrue(sent.outgoing)
        assertEquals(DeliveryStatus.QUEUED, sent.status)
        assertTrue("the composer is cleared", chat.composer.text.isEmpty())
        assertEquals("Elena", chat.uiState.value.name)
        assertTrue(chat.uiState.value.rows.first() is ChatRow.Day)
    }

    @Test
    fun `a reply quotes what it answers`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        kit.receiveText(elena, "Tower at 5?")
        val chat = conversation(elena.id)
        val question = chat.stateWhere { it.messages().isNotEmpty() }.messages().single()

        chat.reply(question.id)
        assertEquals(Quote(fromMe = false, text = "Tower at 5?"), chat.stateWhere { it.replyingTo != null }.replyingTo)
        chat.type("Yes")
        chat.send()
        val answer = chat.stateWhere { it.messages().size == 2 }.messages().last()
        assertEquals(Quote(fromMe = false, text = "Tower at 5?"), answer.quote)
        assertNull(chat.uiState.value.replyingTo)
    }

    @Test
    fun `your own messages can be edited for 15 minutes, theirs never`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        kit.receiveText(elena, "theirs")
        val chat = conversation(elena.id)
        chat.type("mine")
        chat.send()
        val (theirs, mine) = chat.stateWhere { it.messages().size == 2 }.messages()

        chat.select(theirs.id)
        assertFalse(chat.stateWhere { it.actions?.message?.id == theirs.id }.actions!!.canEdit)
        chat.select(mine.id)
        val actions = chat.stateWhere { it.actions?.message?.id == mine.id }.actions!!
        assertTrue(actions.canEdit)
        assertEquals(15, actions.editMinutesLeft)
        assertTrue(actions.canDeleteForEveryone)

        chat.startEdit(mine.id)
        assertEquals("mine", chat.composer.text.toString())
        assertTrue(chat.stateWhere { it.editing }.editing)
        chat.type("mine, edited")
        chat.send()
        val edited = chat.stateWhere { state -> state.messages().any { it.edited } }.messages().last()
        assertEquals("mine, edited", edited.text)

        kit.clock.advance(15 * 60_000L + 1)
        val later = conversation(elena.id)
        later.stateWhere { it.messages().size == 2 }
        later.select(mine.id)
        assertFalse(later.stateWhere { it.actions != null }.actions!!.canEdit)
    }

    @Test
    fun `a message sent after the chat opened can be edited, however long it was open`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        val chat = conversation(elena.id)
        chat.stateWhere { true }
        // The chat sits open for five minutes, with nothing new in it.
        kit.clock.advance(5 * 60_000L)
        chat.type("mine")
        chat.send()
        val mine = chat.stateWhere { it.messages().isNotEmpty() }.messages().single()

        chat.select(mine.id)
        val actions = chat.stateWhere { it.actions?.message?.id == mine.id }.actions!!
        assertTrue(actions.canEdit)
        assertEquals(15, actions.editMinutesLeft)
    }

    @Test
    fun `while it's open, the time it shows moves on every minute`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        val chat = conversation(elena.id)
        backgroundScope.launch { chat.uiState.collect {} }
        val opened = chat.stateWhere { true }.time.nowMillis

        passTime(60_000)
        assertEquals(opened + 60_000, chat.uiState.value.time.nowMillis)
    }

    @Test
    fun `reactions toggle, deletes erase`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        kit.receiveText(elena, "Found the batteries")
        val chat = conversation(elena.id)
        chat.type("Great")
        chat.send()
        val (theirs, mine) = chat.stateWhere { it.messages().size == 2 }.messages()

        chat.select(theirs.id)
        chat.stateWhere { it.actions != null }
        chat.react(theirs.id, "👍")
        assertEquals(listOf(Reaction(true, "👍")), chat.stateWhere { it.messages().first().reactions.isNotEmpty() }.messages().first().reactions)
        chat.select(theirs.id)
        chat.stateWhere { it.actions?.myReaction == "👍" }
        chat.react(theirs.id, "👍")
        assertTrue(chat.stateWhere { it.messages().first().reactions.isEmpty() }.messages().first().reactions.isEmpty())

        chat.deleteForEveryone(mine.id)
        assertTrue(chat.stateWhere { it.messages().last().deleted }.messages().last().deleted)
        chat.deleteForMe(theirs.id)
        assertEquals(1, chat.stateWhere { it.messages().size == 1 }.messages().size)
    }

    @Test
    fun `blocked and changed-key friends can't be messaged, and the screen says why`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        val chat = conversation(elena.id)
        kit.fixture.contacts.setBlocked(elena.id, true)
        assertEquals(SendBlock.BLOCKED, chat.stateWhere { it.block != null }.block)
        chat.type("hi")
        chat.send()
        assertEquals(ChatNotice.Refused(SendRefusal.BLOCKED), chat.notices.first())

        chat.unblock()
        assertNull(chat.stateWhere { it.block == null }.block)

        kit.fixture.contacts.changeKeys(elena.id, TestIdentity.keys(9), KeySource.DEBUG_SIMULATION, kit.clock.wall)
        assertEquals(SendBlock.KEY_CHANGED, chat.stateWhere { it.block != null }.block)
    }

    @Test
    fun `the disappearing timer applies to new messages, which then vanish`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        val chat = conversation(elena.id)
        chat.setTimer(300)
        val set = chat.stateWhere { it.timerSeconds == 300 }
        assertTrue(set.rows.any { it is ChatRow.TimerChanged && it.mine && it.seconds == 300 })

        chat.type("burn after reading")
        chat.send()
        val message = chat.stateWhere { it.messages().isNotEmpty() }.messages().single()
        assertEquals(kit.clock.wall + 300_000, message.disappearsAtMillis)

        passTime(300_000)
        assertTrue(chat.stateWhere { it.messages().isEmpty() }.messages().isEmpty())
    }

    @Test
    fun `on screen, messages are read and don't raise alerts`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        val chat = conversation(elena.id)
        chat.stateWhere { true }
        chat.onVisible()
        assertEquals(elena.id, kit.presence.visibleContact.value)
        assertEquals(1, kit.alerts.cleared)

        kit.receiveText(elena, "hello")
        chat.stateWhere { it.messages().isNotEmpty() }
        runCurrent()
        val summary = kit.conversations.summaries(kit.clock.wall).first().single()
        assertEquals(0, summary.unreadCount)

        chat.onHidden()
        assertNull(kit.presence.visibleContact.value)
    }

    @Test
    fun `a scheduled message waits in its own list`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        val chat = conversation(elena.id)
        chat.type("Happy birthday!")
        chat.schedule(kit.clock.wall + 3_600_000)
        assertEquals(ChatNotice.Scheduled, chat.notices.first())
        val state = chat.stateWhere { it.scheduledCount == 1 }
        assertTrue(state.messages().isEmpty())
    }

    @Test
    fun `message info tells how it went and what protects it`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        val chat = conversation(elena.id)
        chat.type("hi")
        chat.send()
        val message = chat.stateWhere { it.messages().isNotEmpty() }.messages().single()
        chat.showInfo(message.id)
        val info = checkNotNull(chat.stateWhere { it.info != null }.info)
        assertTrue(info.outgoing)
        assertEquals(DeliveryStatus.QUEUED, info.status)
        assertNull(info.sentAtMillis)
        assertEquals(16, info.shortId.length)
        assertEquals(kit.clock.wall, info.writtenAtMillis)
    }

    /** Time passes on both the phone's clock and the test's. */
    private fun TestScope.passTime(millis: Long) {
        kit.clock.advance(millis)
        advanceTimeBy(millis)
        runCurrent()
    }
}

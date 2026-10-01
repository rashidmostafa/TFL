package app.tfl.feature.chats.scheduled

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.testing.MainDispatcherRule
import app.tfl.core.transport.SendRefusal
import app.tfl.feature.chats.ChatsKit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduledViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val kit = ChatsKit(ApplicationProvider.getApplicationContext())
    private val hour = 3_600_000L

    @After
    fun tearDown() = kit.close()

    private fun scheduled(contactId: Long) = ScheduledViewModel(
        SavedStateHandle(mapOf(ScheduledViewModel.CONTACT_ID_ARG to contactId)),
        kit.fixture.contacts, kit.conversations, kit.messages, kit.messenger, kit.chatClock,
    )

    @Test
    fun `schedule, change, send now and delete`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        val list = scheduled(elena.id)
        list.schedule("Happy birthday", kit.clock.wall + hour)
        list.schedule("Pick up the radio", kit.clock.wall + 2 * hour)
        val items = list.uiState.first { it.items.size == 2 }.items
        assertEquals(listOf("Happy birthday", "Pick up the radio"), items.map { it.text })
        assertEquals("Elena", list.uiState.value.name)

        list.change(items[0].id, "Happy birthday!!", kit.clock.wall + 3 * hour)
        val changed = list.uiState.first { state -> state.items.any { it.text == "Happy birthday!!" } }.items
        assertEquals(listOf("Pick up the radio", "Happy birthday!!"), changed.map { it.text })

        list.sendNow(items[1].id)
        assertEquals(listOf("Happy birthday!!"), list.uiState.first { it.items.size == 1 }.items.map { it.text })
        val conversation = checkNotNull(kit.conversations.forContact(elena.id))
        assertEquals(listOf("Pick up the radio"), kit.messages.messages(conversation.id, kit.clock.wall).first().map { it.text })

        list.delete(changed[1].id)
        assertTrue(list.uiState.first { it.items.isEmpty() }.items.isEmpty())
    }

    @Test
    fun `nothing can be scheduled for a blocked friend`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        kit.fixture.contacts.setBlocked(elena.id, true)
        val list = scheduled(elena.id)
        assertFalse(list.uiState.first { !it.loading }.canSend)
        list.schedule("hi", kit.clock.wall + hour)
        assertEquals(SendRefusal.BLOCKED, list.refused.first())
    }
}

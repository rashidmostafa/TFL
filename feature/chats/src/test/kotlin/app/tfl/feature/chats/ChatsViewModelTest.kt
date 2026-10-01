package app.tfl.feature.chats

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.contact.KeySource
import app.tfl.core.testing.MainDispatcherRule
import app.tfl.core.testing.typeText
import app.tfl.core.transport.SendOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val kit = ChatsKit(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = kit.close()

    private fun viewModel() = ChatsViewModel(kit.conversations, kit.fixture.contacts, kit.fixture.settings, kit.transport, kit.readiness, kit.chatClock)

    @Test
    fun `each conversation shows its last message, unread count and status, newest first`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena Vance", 1)
        val soren = kit.friend("Soren K", 2, mutual = false)
        kit.messenger.sendText(elena.id, "On my way")
        kit.clock.advance(60_000)
        kit.receiveText(soren, "Did you get the file?")
        kit.receiveText(soren, "Hello?")

        val threads = viewModel().uiState.first { it.threads.size == 2 }.threads
        assertEquals(listOf("Soren K", "Elena Vance"), threads.map { it.name })
        assertEquals(ThreadPreview.Text("Hello?", mine = false), threads[0].preview)
        assertEquals(2, threads[0].unread)
        assertEquals(ContactTrust.UNVERIFIED, threads[0].trust)
        assertEquals(ThreadPreview.Text("On my way", mine = true), threads[1].preview)
        assertEquals(DeliveryStatus.QUEUED, threads[1].lastStatus)
        assertEquals(ContactTrust.VERIFIED, threads[1].trust)
    }

    @Test
    fun `Nearby shows off, needing setup, or how many friends are linked`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        kit.messenger.sendText(elena.id, "hi")
        val chats = viewModel()

        kit.fixture.settings.set(SettingKeys.NEARBY_ENABLED, false)
        assertEquals(NearbyState.Off, chats.uiState.first { it.nearby == NearbyState.Off }.nearby)

        kit.fixture.settings.set(SettingKeys.NEARBY_ENABLED, true)
        kit.readiness.permitted.value = false
        kit.readiness.ready.value = false
        val needsPermission = NearbyState.NeedsSetup(permitted = false)
        assertEquals(needsPermission, chats.uiState.first { it.nearby == needsPermission }.nearby)

        // Allowed, but Bluetooth (or Location) is off: the prompt says that, not "allow".
        kit.readiness.permitted.value = true
        val needsSwitch = NearbyState.NeedsSetup(permitted = true)
        assertEquals(needsSwitch, chats.uiState.first { it.nearby == needsSwitch }.nearby)

        kit.readiness.ready.value = true
        kit.transport.reachable.value = setOf(elena.id)
        val state = chats.uiState.first { it.nearby == NearbyState.On(1) }
        assertTrue(state.threads.single().nearbyNow)
    }

    @Test
    fun `search matches names and messages`() = runTest {
        kit.unlock()
        kit.messenger.sendText(kit.friend("Elena", 1).id, "Water tower at noon")
        kit.messenger.sendText(kit.friend("Maya", 2).id, "Bring batteries")
        val chats = viewModel()
        chats.uiState.first { it.threads.size == 2 }

        chats.searchQuery.typeText("tower")
        assertEquals(listOf("Elena"), chats.uiState.first { it.hasQuery }.threads.map { it.name })
        chats.searchQuery.typeText("MAYA")
        assertEquals(listOf("Maya"), chats.uiState.first { state -> state.threads.map { it.name } == listOf("Maya") }.threads.map { it.name })
    }

    @Test
    fun `changed keys, blocks, timer changes and deletes show in the list`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        val maya = kit.friend("Maya", 2)
        kit.messenger.sendText(elena.id, "hi")
        val sent = kit.messenger.sendText(maya.id, "oops") as SendOutcome.Queued
        kit.fixture.contacts.changeKeys(elena.id, TestIdentity.keys(9), KeySource.DEBUG_SIMULATION, kit.clock.wall)
        kit.messenger.deleteForEveryone(checkNotNull(sent.message).id)
        kit.fixture.contacts.setBlocked(maya.id, true)

        val threads = viewModel().uiState.first { state -> state.threads.size == 2 && state.threads.any { it.blocked } }.threads.associateBy { it.name }
        assertEquals(ContactTrust.KEY_CHANGED, threads.getValue("Elena").trust)
        assertEquals(ThreadPreview.Deleted(mine = true), threads.getValue("Maya").preview)
        assertTrue(threads.getValue("Maya").blocked)
    }

    @Test
    fun `a timer change is the last line until the next message`() = runTest {
        kit.unlock()
        val elena = kit.friend("Elena", 1)
        kit.messenger.setTimer(elena.id, 3_600)
        val thread = viewModel().uiState.first { it.threads.isNotEmpty() }.threads.single()
        assertEquals(ThreadPreview.Timer(3_600, mine = true), thread.preview)
    }

    @Test
    fun `friends without chats are still there to start one`() = runTest {
        kit.unlock()
        kit.friend("Elena", 1)
        val state = viewModel().uiState.first { it.hasFriends }
        assertTrue(state.threads.isEmpty())
        assertEquals(listOf("Elena"), state.friends.map { it.name })
    }
}

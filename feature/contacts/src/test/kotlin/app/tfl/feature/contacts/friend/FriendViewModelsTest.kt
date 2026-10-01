package app.tfl.feature.contacts.friend

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.designsystem.component.initialsOf
import app.tfl.core.model.contact.KeySource
import app.tfl.core.testing.MainDispatcherRule
import app.tfl.core.testing.session.SessionFixture
import app.tfl.feature.contacts.TestKeys
import app.tfl.feature.contacts.components.Trust
import app.tfl.feature.contacts.friends.FriendsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FriendViewModelsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fixture = SessionFixture(context)
    private val contacts = fixture.contacts
    private val t0 = fixture.clock.wall

    @After
    fun tearDown() = fixture.database.close()

    private suspend fun unlocked() = fixture.session.commitOnboarding(fixture.draft(fixture.tools.newSeed()))

    private fun friend(id: Long) = FriendViewModel(
        SavedStateHandle(mapOf(FriendViewModel.CONTACT_ID_ARG to id)),
        fixture.identities,
        fixture.safetyNumbers,
        contacts,
    )

    @Test
    fun `a profile shows trust, the current key, the key history and the safety number`() = runTest {
        unlocked()
        val elena = contacts.recordPairing("Elena", TestKeys.random(1), mutual = true, nowMillis = t0)
        contacts.changeKeys(elena.id, TestKeys.random(2), KeySource.DEBUG_SIMULATION, t0 + 1_000)

        val state = friend(elena.id).uiState.first { !it.loading }
        val details = checkNotNull(state.friend)
        assertEquals(Trust.KEY_CHANGED, details.trust)
        assertEquals(TestKeys.random(2).fingerprintGroups, details.fingerprint)
        assertEquals(t0 + 1_000, details.keySinceMillis)
        assertEquals(KeySource.DEBUG_SIMULATION, details.keySource)
        assertEquals(t0, details.firstSeenAtMillis)
        assertEquals(listOf(TestKeys.random(1).fingerprintGroups), state.history.map { it.fingerprint })
        val me = checkNotNull(fixture.identities.get())
        assertEquals(fixture.safetyNumbers.between(me.signPublicKey, TestKeys.random(2).signPublicKey).groups, state.safetyNumber)
    }

    @Test
    fun `nickname, block and unblock`() = runTest {
        unlocked()
        val soren = contacts.recordPairing("Soren", TestKeys.random(3), mutual = false, nowMillis = t0)
        val profile = friend(soren.id)

        profile.setNickname("Sor")
        assertEquals("Sor", profile.uiState.first { it.friend?.nickname != null }.friend?.name)
        profile.setNickname("")
        assertEquals("Soren", profile.uiState.first { !it.loading && it.friend?.nickname == null }.friend?.name)

        profile.setBlocked(true)
        assertTrue(profile.uiState.first { it.friend?.blocked == true }.friend?.blocked == true)
        assertFalse(checkNotNull(contacts.get(soren.id)).canSend)
        profile.setBlocked(false)
        assertTrue(checkNotNull(contacts.get(soren.id)).canSend)
    }

    @Test
    fun `deleting wipes the friend and closes the screen`() = runTest {
        unlocked()
        val rowan = contacts.recordPairing("Rowan", TestKeys.random(4), mutual = false, nowMillis = t0)
        val profile = friend(rowan.id)
        val closed = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { profile.closed.collect { closed += it } }

        profile.delete()
        assertEquals(1, closed.size)
        assertNull(contacts.get(rowan.id))
        assertNull(profile.uiState.first { !it.loading }.friend)
    }

    @Test
    fun `the friends list shows every state, by name`() = runTest {
        unlocked()
        contacts.recordPairing("Maya", TestKeys.random(1), mutual = true, nowMillis = t0)
        contacts.recordPairing("Soren", TestKeys.random(2), mutual = false, nowMillis = t0)
        contacts.recordPairing("Elena", TestKeys.random(3), mutual = true, nowMillis = t0).also {
            contacts.changeKeys(it.id, TestKeys.random(4), KeySource.DEBUG_SIMULATION, t0)
        }
        contacts.recordPairing("Rowan", TestKeys.random(5), mutual = false, nowMillis = t0).also { contacts.setBlocked(it.id, true) }

        val friends = FriendsViewModel(contacts).uiState.first { !it.loading }.friends
        assertEquals(listOf("Elena", "Maya", "Rowan", "Soren"), friends.map { it.name })
        assertEquals(listOf(Trust.KEY_CHANGED, Trust.VERIFIED, Trust.UNVERIFIED, Trust.UNVERIFIED), friends.map { it.trust })
        assertEquals(listOf(false, false, true, false), friends.map { it.blocked })
        assertEquals(TestKeys.random(2).fingerprintGroups.take(2).joinToString(" "), friends.last().fingerprintStart)
    }

    @Test
    fun `initials come from the first two words`() {
        assertEquals("V7", initialsOf("Valkyrie-7"))
        assertEquals("ML", initialsOf("Maya Lin"))
        assertEquals("R", initialsOf("rowan"))
        assertEquals("ÉN", initialsOf("élodie  n."))
        assertEquals("?", initialsOf("…"))
    }
}

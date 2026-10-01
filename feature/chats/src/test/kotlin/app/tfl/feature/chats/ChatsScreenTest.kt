package app.tfl.feature.chats

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.testing.SCREENSHOT_DEVICE_FULL_PAGE
import app.tfl.core.testing.TflTestSurface
import app.tfl.core.testing.captureScreenshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneOffset

/** Sample conversations for the chats list goldens, in every state a row can be in. */
internal object SampleChats {
    /** 21 September 2026, 14:10 UTC. */
    const val NOW = 1_789_999_800_000L
    private const val MINUTE = 60_000L
    val time = TimeContext(NOW, ZoneOffset.UTC, is24Hour = true)

    val threads = listOf(
        ThreadItem(1, "Kaelen (Valkyrie)", ContactTrust.VERIFIED, false, ThreadPreview.Text("Yes, connected directly. No cloud hop.", mine = false), NOW - MINUTE / 2, unread = 2, lastStatus = null, nearbyNow = true),
        ThreadItem(2, "Dr. Maya Lin", ContactTrust.VERIFIED, false, ThreadPreview.Text("Batteries are charged", mine = true), NOW - 12 * MINUTE, 0, DeliveryStatus.DELIVERED, nearbyNow = false),
        ThreadItem(3, "Soren K.", ContactTrust.UNVERIFIED, false, ThreadPreview.Text("See you at the tower", mine = true), NOW - 60 * MINUTE, 0, DeliveryStatus.QUEUED, nearbyNow = false),
        ThreadItem(4, "Noor", ContactTrust.VERIFIED, false, ThreadPreview.Timer(3_600, mine = false), NOW - 3 * 60 * MINUTE, 0, null, nearbyNow = true),
        ThreadItem(5, "Elena Vance", ContactTrust.KEY_CHANGED, false, ThreadPreview.Text("Is this still you?", mine = false), NOW - 26 * 60 * MINUTE, 0, null, nearbyNow = false),
        ThreadItem(6, "Ari", ContactTrust.UNVERIFIED, true, ThreadPreview.Deleted(mine = false), NOW - 3 * 24 * 60 * MINUTE, 0, null, nearbyNow = false),
    )

    val friends = threads.map { FriendItem(it.contactId, it.name, it.trust, it.blocked) }

    fun state(nearby: NearbyState = NearbyState.On(2), threads: List<ThreadItem> = SampleChats.threads, friends: List<FriendItem> = SampleChats.friends) =
        ChatsUiState(nearby, threads, friends, hasQuery = false, time = time)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE_FULL_PAGE)
class ChatsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun show(state: ChatsUiState, startWithNewChat: Boolean = false) {
        composeRule.setContent {
            TflTestSurface {
                ChatsContent(
                    uiState = state,
                    searchQuery = TextFieldState(),
                    onOpenProfile = { opened += "profile" },
                    onScan = { opened += "scan" },
                    onOpenChat = { opened += "chat:$it" },
                    onResolveKey = { opened += "key:$it" },
                    onSetUpNearby = { opened += "setup" },
                    startWithNewChat = startWithNewChat,
                )
            }
        }
    }

    @Test
    fun screenshot() {
        show(SampleChats.state())
        composeRule.captureScreenshot("chats_home")
    }

    @Test
    fun screenshot_nearbyNeedsSetup() {
        show(SampleChats.state(nearby = NearbyState.NeedsSetup(permitted = false), threads = SampleChats.threads.take(3)))
        composeRule.captureScreenshot("chats_home_nearby_setup")
    }

    @Test
    fun screenshot_noFriendsYet() {
        show(SampleChats.state(nearby = NearbyState.Off, threads = emptyList(), friends = emptyList()))
        composeRule.captureScreenshot("chats_home_empty")
    }

    @Test
    fun rowsOpenTheirChat_andAChangedKeyItsReview() {
        show(SampleChats.state())
        composeRule.onNodeWithText("Dr. Maya Lin").performClick()
        composeRule.onNodeWithText("Resolve key").performClick()
        composeRule.onNodeWithText("Set up Nearby").assertDoesNotExist()
        assertEquals(listOf("chat:2", "key:5"), opened)
    }

    @Test
    fun newChat_picksAFriend() {
        show(SampleChats.state(threads = emptyList()))
        composeRule.onNodeWithText("New chat", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Noor").performClick()
        assertEquals(listOf("chat:4"), opened)
    }

    @Test
    fun setupPrompt_opensNearbySetup() {
        show(SampleChats.state(nearby = NearbyState.NeedsSetup(permitted = false)))
        composeRule.onNodeWithText("Nearby needs setting up").assertExists()
        composeRule.onNodeWithText("needs your OK", substring = true).assertExists()
        composeRule.onNodeWithText("Set up Nearby").performClick()
        assertEquals(listOf("setup"), opened)
    }

    @Test
    fun setupPrompt_whenOnlyASwitchIsOff_doesNotAskForPermission() {
        show(SampleChats.state(nearby = NearbyState.NeedsSetup(permitted = true)))
        composeRule.onNodeWithText("It's on, but it needs Bluetooth switched on (and Location, on some phones).").assertExists()
        composeRule.onNodeWithText("needs your OK", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Set up Nearby").assertExists()
    }

    @Test
    fun noFriends_offersToAddOne() {
        show(SampleChats.state(threads = emptyList(), friends = emptyList()))
        composeRule.onNodeWithText("Add a friend").performClick()
        composeRule.onNodeWithContentDescription("New chat").assertDoesNotExist()
        assertEquals(listOf("scan"), opened)
    }
}

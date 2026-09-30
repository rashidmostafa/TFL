package app.tfl.feature.contacts

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.crypto.pairing.InvalidCode
import app.tfl.core.model.contact.KeySource
import app.tfl.core.model.contact.VerificationMethod
import app.tfl.core.testing.SCREENSHOT_DEVICE_FULL_PAGE
import app.tfl.core.testing.TflTestSurface
import app.tfl.core.testing.captureScreenshot
import app.tfl.feature.contacts.add.AddFriendContent
import app.tfl.feature.contacts.add.AddFriendTab
import app.tfl.feature.contacts.add.AddFriendUiState
import app.tfl.feature.contacts.add.PairingStep
import app.tfl.feature.contacts.add.ShownCode
import app.tfl.feature.contacts.components.Trust
import app.tfl.feature.contacts.friend.ContactProfileContent
import app.tfl.feature.contacts.friend.FriendAddedContent
import app.tfl.feature.contacts.friend.FriendDetails
import app.tfl.feature.contacts.friend.FriendUiState
import app.tfl.feature.contacts.friend.KeyChangeWarningContent
import app.tfl.feature.contacts.friend.PreviousKeyItem
import app.tfl.feature.contacts.friend.ProfileActions
import app.tfl.feature.contacts.friends.FriendItem
import app.tfl.feature.contacts.friends.FriendsContent
import app.tfl.feature.contacts.friends.FriendsUiState
import app.tfl.feature.contacts.preview.GroupJoinPreviewScreen
import app.tfl.feature.contacts.preview.RemoteWipePreviewScreen
import app.tfl.feature.contacts.qr.QrEncoder
import app.tfl.feature.contacts.scan.CameraBlocked
import app.tfl.feature.contacts.scan.CameraRationale
import app.tfl.feature.contacts.verify.CompareResult
import app.tfl.feature.contacts.verify.CompareTab
import app.tfl.feature.contacts.verify.SafetyNumberContent
import app.tfl.feature.contacts.verify.SafetyNumberUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Base64
import java.util.TimeZone
import kotlin.random.Random

/** 21 September 2026, midday UTC. */
private const val NOON = 1_789_992_000_000L
private const val DAY = 86_400_000L

private val MY_FINGERPRINT = "F162BE7D2746777DB5A0FF5D6329EC0E".chunked(4)
private val ELENA_FINGERPRINT = "3E11A48FCC9277B0D41F9A0B6C2E58D3".chunked(4)
private val ELENA_OLD_FINGERPRINT = "88CC014E5F2BA1907D3E62F0B94C1A57".chunked(4)
private val SAFETY_NUMBER = "01953 97817 65409 49173 11172 35446 44819 72146 06261 49463 63610 08650".split(" ")

/** Shaped like a real pairing code, so the QR has a realistic density. Not a valid code. */
private val SAMPLE_CODE = "TFL-PAIR1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(Random(7).nextBytes(230))

private val ELENA = FriendDetails(
    id = 1,
    name = "Elena Vance",
    displayName = "Elena Vance",
    nickname = null,
    initials = "EV",
    trust = Trust.VERIFIED,
    verifiedBy = VerificationMethod.IN_PERSON_PAIRING,
    verifiedAtMillis = NOON,
    fingerprint = ELENA_FINGERPRINT,
    keySource = KeySource.IN_PERSON_SCAN,
    keySinceMillis = NOON - 40 * DAY,
    firstSeenAtMillis = NOON - 40 * DAY,
    keyChangedAtMillis = null,
    blocked = false,
)

private val ELENA_KEY_CHANGED = ELENA.copy(
    trust = Trust.KEY_CHANGED,
    verifiedBy = null,
    verifiedAtMillis = null,
    keySinceMillis = NOON,
    keyChangedAtMillis = NOON,
)

private val ELENA_HISTORY = listOf(PreviousKeyItem(ELENA_OLD_FINGERPRINT, NOON - 40 * DAY, NOON, KeySource.IN_PERSON_SCAN))

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE_FULL_PAGE)
class ContactsScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private lateinit var zone: TimeZone

    @Before
    fun fixTimeZone() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(zone)

    private fun show(content: @Composable () -> Unit) = composeRule.setContent { TflTestSurface(content) }

    private fun addFriend(state: AddFriendUiState, scanner: @Composable (androidx.compose.ui.Modifier) -> Unit = {}) = show {
        AddFriendContent(
            uiState = state,
            onBack = { calls += "back" },
            onSelectTab = { calls += "tab $it" },
            onDone = { calls += "done" },
            scanner = scanner,
        )
    }

    private val myCode = AddFriendUiState(
        myName = "Valkyrie-7",
        myFingerprint = MY_FINGERPRINT,
        code = ShownCode(SAMPLE_CODE, QrEncoder.encode(SAMPLE_CODE), secondsLeft = 42),
    )

    private val profileActions = ProfileActions(
        onBack = { calls += "back" },
        onCompare = { calls += "compare" },
        onReviewKeyChange = { calls += "review" },
        onEditNickname = { calls += "nickname" },
        onSetBlocked = { calls += "blocked $it" },
        onDelete = { calls += "delete" },
    )

    // Add friend

    @Test
    fun addFriend_myCode() {
        addFriend(myCode)
        composeRule.captureScreenshot("add_friend_my_code")
    }

    @Test
    fun addFriend_answering() {
        addFriend(myCode.copy(step = PairingStep.Answering(1, "Elena", verified = false)))
        composeRule.captureScreenshot("add_friend_answering")
        composeRule.onNodeWithText("Scan their code").performClick()
        composeRule.onNodeWithText("Stop here (not verified)").performClick()
        assertEquals(listOf("tab SCAN", "done"), calls)
    }

    @Test
    fun addFriend_answeringVerified() {
        addFriend(myCode.copy(step = PairingStep.Answering(1, "Elena", verified = true)))
        composeRule.captureScreenshot("add_friend_answering_verified")
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(listOf("done"), calls)
    }

    @Test
    fun addFriend_scanRefused() {
        addFriend(myCode.copy(tab = AddFriendTab.SCAN, problem = InvalidCode.Expired(7))) { CameraRationale(onAllow = {}, modifier = it) }
        composeRule.captureScreenshot("add_friend_scan")
        composeRule.onNodeWithText("This code was made 7 minutes ago", substring = true).assertExists()
    }

    @Test
    fun addFriend_cameraBlocked() {
        addFriend(myCode.copy(tab = AddFriendTab.SCAN)) { CameraBlocked(onOpenSettings = {}, modifier = it) }
        composeRule.captureScreenshot("add_friend_camera_blocked")
    }

    @Test
    fun addFriend_tabs() {
        addFriend(myCode)
        composeRule.onNodeWithText("Scan").performClick()
        composeRule.onNodeWithText("Ready to scan theirs?").performClick()
        assertEquals(listOf("tab SCAN", "tab SCAN"), calls)
    }

    // Friend added

    @Test
    fun friendAdded_verified() {
        show { FriendAddedContent(FriendUiState(loading = false, friend = ELENA), onDone = {}, onViewProfile = {}) }
        composeRule.captureScreenshot("friend_added_verified")
    }

    @Test
    fun friendAdded_unverified() {
        show {
            FriendAddedContent(
                FriendUiState(loading = false, friend = ELENA.copy(trust = Trust.UNVERIFIED, verifiedBy = null, verifiedAtMillis = null)),
                onDone = { calls += "done" },
                onViewProfile = { calls += "profile" },
            )
        }
        composeRule.captureScreenshot("friend_added_unverified")
        composeRule.onNodeWithText("View profile").performClick()
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(listOf("profile", "done"), calls)
    }

    // Profile

    @Test
    fun contactProfile() {
        val state = FriendUiState(
            loading = false,
            friend = ELENA.copy(name = "Aunt E", nickname = "Aunt E", initials = "AE", keySinceMillis = NOON - 3 * DAY),
            history = listOf(PreviousKeyItem(ELENA_OLD_FINGERPRINT, NOON - 40 * DAY, NOON - 3 * DAY, KeySource.IN_PERSON_SCAN)),
            safetyNumber = SAFETY_NUMBER,
        )
        show { ContactProfileContent(state, profileActions) }
        composeRule.captureScreenshot("contact_profile")
        composeRule.onNodeWithText("Compare safety numbers").performClick()
        composeRule.onNodeWithText("Block Aunt E").performScrollTo().performClick()
        composeRule.onNodeWithText("Delete friend").performScrollTo().performClick()
        assertEquals(listOf("compare", "blocked true", "delete"), calls)
    }

    @Test
    fun contactProfile_keyChangedAndBlocked() {
        val state = FriendUiState(loading = false, friend = ELENA_KEY_CHANGED.copy(blocked = true), history = ELENA_HISTORY, safetyNumber = SAFETY_NUMBER)
        show { ContactProfileContent(state, profileActions) }
        composeRule.captureScreenshot("contact_profile_key_changed")
        composeRule.onNodeWithText("Review key change").performClick()
        composeRule.onNodeWithText("Unblock Elena Vance").performScrollTo().performClick()
        assertEquals(listOf("review", "blocked false"), calls)
    }

    // Key change

    @Test
    fun keyChangeWarning() {
        show {
            KeyChangeWarningContent(
                FriendUiState(loading = false, friend = ELENA_KEY_CHANGED, history = ELENA_HISTORY, safetyNumber = SAFETY_NUMBER),
                onBack = {},
                onVerifyAgain = { calls += "verify" },
                onBlock = { calls += "block" },
                onNotNow = { calls += "not now" },
            )
        }
        composeRule.captureScreenshot("key_change_warning")
        composeRule.onNodeWithText("Verify again").performScrollTo().performClick()
        composeRule.onNodeWithText("Block Elena Vance").performScrollTo().performClick()
        composeRule.onNodeWithText("Not now").performScrollTo().performClick()
        assertEquals(listOf("verify", "block", "not now"), calls)
        composeRule.onNodeWithText("Trust", substring = true).assertDoesNotExist()
    }

    @Test
    fun keyChangeWarning_resolved() {
        show {
            KeyChangeWarningContent(
                FriendUiState(loading = false, friend = ELENA.copy(keySinceMillis = NOON), history = ELENA_HISTORY),
                onBack = {},
                onVerifyAgain = { calls += "verify" },
                onBlock = { calls += "block" },
                onNotNow = { calls += "done" },
            )
        }
        composeRule.onNodeWithText("You verified Elena Vance's new key.").assertExists()
        composeRule.onNodeWithText("Verify again").assertDoesNotExist()
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(listOf("done"), calls)
    }

    // Safety numbers

    private fun safety(state: SafetyNumberUiState, scanner: @Composable (androidx.compose.ui.Modifier) -> Unit = {}) = show {
        SafetyNumberContent(
            uiState = state,
            onBack = {},
            onSelectTab = { calls += "tab $it" },
            onMarkVerified = { calls += "mark" },
            onPairAgain = { calls += "pair" },
            scanner = scanner,
        )
    }

    private val safetyState = SafetyNumberUiState(
        loading = false,
        name = "Elena Vance",
        trust = Trust.UNVERIFIED,
        groups = SAFETY_NUMBER,
        code = QrEncoder.encode("TFL-SN1:" + Base64.getEncoder().encodeToString(Random(8).nextBytes(64))),
    )

    @Test
    fun safetyNumber() {
        safety(safetyState)
        composeRule.captureScreenshot("safety_number")
        composeRule.onNodeWithText("Scan theirs").performClick()
        composeRule.onNodeWithText("Mark as verified").performScrollTo().performClick()
        composeRule.onNodeWithText("Pair again in person instead").performScrollTo().performClick()
        assertEquals(listOf("tab SCAN", "mark", "pair"), calls)
    }

    @Test
    fun safetyNumber_mismatch() {
        safety(safetyState.copy(tab = CompareTab.SCAN, result = CompareResult.MISMATCH)) { CameraRationale(onAllow = {}, modifier = it) }
        composeRule.captureScreenshot("safety_number_mismatch")
    }

    @Test
    fun safetyNumber_match() {
        safety(safetyState.copy(trust = Trust.VERIFIED, verifiedBy = VerificationMethod.SAFETY_NUMBER_QR, result = CompareResult.MATCH))
        composeRule.onNodeWithText("Safety numbers match").assertExists()
        composeRule.onNodeWithText("Mark as verified").assertDoesNotExist()
    }

    // Friends and previews

    @Test
    fun friends() {
        val friends = listOf(
            FriendItem(1, "Aunt E", "AE", Trust.VERIFIED, blocked = false, fingerprintStart = "3E11 A48F"),
            FriendItem(2, "Elena Vance", "EV", Trust.KEY_CHANGED, blocked = false, fingerprintStart = "88CC 014E"),
            FriendItem(3, "Kaelen", "K", Trust.VERIFIED, blocked = false, fingerprintStart = "D41F 9A0B"),
            FriendItem(4, "Rowan", "R", Trust.UNVERIFIED, blocked = true, fingerprintStart = "6C2E 58D3"),
            FriendItem(5, "Soren K.", "SK", Trust.UNVERIFIED, blocked = false, fingerprintStart = "5F2B A190"),
        )
        show {
            FriendsContent(
                FriendsUiState(loading = false, friends = friends),
                onBack = {},
                onAddFriend = { calls += "add" },
                onOpenFriend = { calls += "open ${it.id}" },
                onOpenGroupJoinPreview = { calls += "group" },
                onOpenRemoteWipePreview = { calls += "wipe" },
            )
        }
        composeRule.captureScreenshot("friends")
        composeRule.onNodeWithText("Elena Vance").performClick()
        composeRule.onNodeWithText("Add friend").performClick()
        composeRule.onNodeWithText("Group join approval").performScrollTo().performClick()
        composeRule.onNodeWithText("Remote wipe request").performScrollTo().performClick()
        assertEquals(listOf("open 2", "add", "group", "wipe"), calls)
    }

    @Test
    fun friends_empty() {
        show {
            FriendsContent(FriendsUiState(loading = false), onBack = {}, onAddFriend = { calls += "add" }, onOpenFriend = {}, onOpenGroupJoinPreview = {}, onOpenRemoteWipePreview = {})
        }
        composeRule.captureScreenshot("friends_empty")
        composeRule.onNodeWithText("Add friend").performClick()
        assertEquals(listOf("add"), calls)
    }

    @Test
    fun groupJoinPreview() {
        show { GroupJoinPreviewScreen(onBack = {}) }
        composeRule.captureScreenshot("group_join_preview")
        composeRule.onNodeWithText("Approve").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Reject").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun remoteWipePreview() {
        show { RemoteWipePreviewScreen(onBack = {}) }
        composeRule.captureScreenshot("remote_wipe_preview")
        composeRule.onNodeWithText("Send wipe request").performScrollTo().assertIsNotEnabled()
    }
}

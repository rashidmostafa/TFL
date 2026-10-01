package app.tfl.feature.chats.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.Transport
import app.tfl.core.model.message.Reaction
import app.tfl.core.testing.SCREENSHOT_DEVICE
import app.tfl.core.testing.SCREENSHOT_DEVICE_FULL_PAGE
import app.tfl.core.testing.TflTestSurface
import app.tfl.core.testing.captureScreenshot
import app.tfl.core.testing.typeText
import app.tfl.feature.chats.ContactTrust
import app.tfl.feature.chats.SampleChats
import app.tfl.feature.chats.SampleChats.NOW
import app.tfl.feature.chats.scheduled.ScheduledContent
import app.tfl.feature.chats.scheduled.ScheduledItem
import app.tfl.feature.chats.scheduled.ScheduledUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE)
class ConversationScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val minute = 60_000L
    private val calls = mutableListOf<String>()

    private fun message(
        id: Long,
        text: String?,
        outgoing: Boolean,
        at: Long,
        status: DeliveryStatus? = null,
        transport: Transport? = Transport.NEARBY,
        quote: Quote? = null,
        reactions: List<Reaction> = emptyList(),
        edited: Boolean = false,
        disappearsAt: Long? = null,
    ) = ChatRow.Message(MessageView(id, outgoing, text, edited, at, status, transport, quote, reactions, disappearsAt))

    /** Every kind of line a conversation can show. */
    private val rows = listOf(
        ChatRow.Day(NOW - 25 * 60 * minute),
        message(1, "Are you within range of the water tower?", outgoing = false, at = NOW - 25 * 60 * minute, reactions = listOf(Reaction(true, "👍"))),
        message(
            2, "Yes, connected directly. No cloud hop.", outgoing = true, at = NOW - 25 * 60 * minute + minute,
            status = DeliveryStatus.DELIVERED, quote = Quote(fromMe = false, text = "Are you within range of the water tower?"), edited = true,
        ),
        ChatRow.Day(NOW - 30 * minute),
        ChatRow.TimerChanged(3, 3_600, mine = false),
        message(4, "This one disappears in an hour", outgoing = false, at = NOW - 29 * minute, disappearsAt = NOW + 31 * minute),
        message(5, null, outgoing = false, at = NOW - 20 * minute),
        message(6, "Sent while you were out of range", outgoing = true, at = NOW - 10 * minute, status = DeliveryStatus.FAILED, transport = null),
        message(7, "On my way", outgoing = true, at = NOW - minute, status = DeliveryStatus.QUEUED, transport = null),
    )

    private val state = ConversationUiState(
        contactId = 1,
        name = "Kaelen (Valkyrie)",
        trust = ContactTrust.VERIFIED,
        fingerprint = listOf("7F4B", "91C2", "0D3A", "99C2"),
        nearbyNow = true,
        timerSeconds = 3_600,
        rows = rows,
        scheduledCount = 2,
        time = SampleChats.time,
        loading = false,
    )

    private fun show(state: ConversationUiState, composer: TextFieldState = TextFieldState()) {
        composeRule.setContent {
            TflTestSurface {
                ConversationContent(
                    uiState = state,
                    composer = composer,
                    actions = ConversationActions(
                        onSelect = { calls += "select:$it" },
                        onSend = { calls += "send" },
                        onSchedule = { calls += "schedule" },
                        onUnblock = { calls += "unblock" },
                        onVerify = { calls += "verify" },
                        onChooseTimer = { calls += "timer" },
                        onOpenScheduled = { calls += "scheduled" },
                        onOpenProfile = { calls += "profile" },
                    ),
                )
            }
        }
    }

    @Test
    fun screenshot() {
        show(state)
        composeRule.captureScreenshot("conversation")
    }

    @Test
    fun screenshot_replying() {
        val composer = TextFieldState()
        composer.typeText("Leaving in 5")
        show(state.copy(replyingTo = Quote(fromMe = false, text = "Are you within range of the water tower?")), composer)
        composeRule.captureScreenshot("conversation_replying")
    }

    @Test
    fun screenshot_blocked() {
        show(state.copy(block = SendBlock.BLOCKED, nearbyNow = false, timerSeconds = 0))
        composeRule.captureScreenshot("conversation_blocked")
    }

    @Test
    fun screenshot_keyChanged() {
        show(state.copy(block = SendBlock.KEY_CHANGED, trust = ContactTrust.KEY_CHANGED, nearbyNow = false))
        composeRule.captureScreenshot("conversation_key_changed")
    }

    @Test
    fun screenshot_empty() {
        show(state.copy(rows = emptyList(), trust = ContactTrust.UNVERIFIED, nearbyNow = false, timerSeconds = 0, scheduledCount = 0))
        composeRule.captureScreenshot("conversation_empty")
    }

    @Test
    fun sendIsOffUntilSomethingIsTyped() {
        val composer = TextFieldState()
        show(state, composer)
        composeRule.onNodeWithContentDescription("Send").assertIsNotEnabled()
        composer.typeText("hi")
        composeRule.onNodeWithContentDescription("Send").assertIsEnabled().performClick()
        composeRule.onNodeWithContentDescription("Schedule").performClick()
        assertEquals(listOf("send", "schedule"), calls)
    }

    @Test
    fun longPressOpensTheMessageMenu() {
        show(state)
        composeRule.onNodeWithText("On my way").performTouchInput { longClick() }
        assertEquals(listOf("select:7"), calls)
    }

    @Test
    fun blockedAndChangedKeysOfferTheWayOut() {
        show(state.copy(block = SendBlock.BLOCKED))
        composeRule.onNodeWithContentDescription("Send").assertDoesNotExist()
        composeRule.onNodeWithText("Unblock").performClick()
        assertEquals(listOf("unblock"), calls)
    }

    @Test
    fun headerOpensTimerScheduledAndProfile() {
        show(state)
        composeRule.onNodeWithContentDescription("Disappearing messages").performClick()
        composeRule.onNodeWithContentDescription("Scheduled messages: 2").performClick()
        // The header; a reply quote shows the name too.
        composeRule.onAllNodesWithText("Kaelen (Valkyrie)").onFirst().performClick()
        assertEquals(listOf("timer", "scheduled", "profile"), calls)
    }

    @Test
    fun aNewMessageComesIntoView_ifTheNewestWasInView_orIfItsYours() {
        // More than a screenful, so a new message can land out of sight.
        val history = (1L..30L).map { message(100 + it, "Message $it", outgoing = it % 2 == 0L, at = NOW - (40 - it) * minute) }
        var current by mutableStateOf(state.copy(rows = history))
        composeRule.setContent {
            TflTestSurface { ConversationContent(uiState = current, composer = TextFieldState(), actions = ConversationActions()) }
        }

        current = current.copy(rows = current.rows + message(200, "Theirs, while you look", outgoing = false, at = NOW))
        composeRule.onNodeWithText("Theirs, while you look").assertIsDisplayed()

        // Back in the history, theirs waits below; yours brings you back down.
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(20)
        current = current.copy(rows = current.rows + message(201, "Theirs, while you read back", outgoing = false, at = NOW))
        composeRule.onNodeWithText("Theirs, while you read back").assertDoesNotExist()
        current = current.copy(rows = current.rows + message(202, "Yours", outgoing = true, at = NOW, status = DeliveryStatus.QUEUED))
        composeRule.onNodeWithText("Yours").assertIsDisplayed()
    }

    @Test
    fun timerSheet_scrollsToItsButton_onAPhoneTooShortToShowItAll() {
        var set: Int? = null
        composeRule.setContent { TflTestSurface { TimerSheet(current = 0, name = "Kaelen", onSet = { set = it }, onDismiss = {}) } }
        composeRule.onNodeWithText("1 hour").performClick()
        composeRule.onNodeWithText("Set timer to 1 hour").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(3_600, set)
    }
}

/** The contents of the conversation's sheets, drawn on the sheet's surface. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE_FULL_PAGE)
class ConversationSheetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mine = MessageView(7, true, "Yes, connected directly. No cloud hop.", false, NOW - 5 * 60_000, DeliveryStatus.DELIVERED, Transport.NEARBY, null, emptyList(), null)

    private fun showSheet(content: @Composable ColumnScope.() -> Unit) {
        composeRule.setContent {
            TflTestSurface {
                // A Box doesn't stretch its child to the screen: the image is the sheet's own height.
                Box {
                    Column(
                        Modifier
                            .testTag("sheet")
                            .fillMaxWidth()
                            .background(TflTheme.colors.surface, TflTheme.shapes.sheet)
                            .padding(top = 12.dp),
                    ) { content() }
                }
            }
        }
    }

    @Test
    fun messageMenu() {
        showSheet {
            MessageActionsContent(MessageActions(mine, canReply = true, canEdit = true, editMinutesLeft = 10, canDeleteForEveryone = true, myReaction = "❤️"))
        }
        composeRule.captureScreenshot("message_long_press_menu", composeRule.onNodeWithTag("sheet"))
    }

    @Test
    fun messageMenu_theirs_offersNoEditOrDeleteForEveryone() {
        showSheet {
            MessageActionsContent(MessageActions(mine.copy(outgoing = false), canReply = true, canEdit = false, editMinutesLeft = 0, canDeleteForEveryone = false, myReaction = null))
        }
        composeRule.onNodeWithText("Edit").assertDoesNotExist()
        composeRule.onNodeWithText("Delete for everyone").assertDoesNotExist()
        composeRule.onNodeWithText("Delete for me").assertExists()
    }

    @Test
    fun messageInfo() {
        showSheet {
            MessageInfoContent(
                MessageInfo(
                    outgoing = true,
                    status = DeliveryStatus.DELIVERED,
                    transport = Transport.NEARBY,
                    writtenAtMillis = NOW - 5 * 60_000,
                    sentAtMillis = NOW - 5 * 60_000 + 1_102,
                    deliveredAtMillis = NOW - 5 * 60_000 + 2_018,
                    receivedAtMillis = null,
                    editedAtMillis = null,
                    scheduledForMillis = null,
                    disappearsAtMillis = null,
                    shortId = "8f2a49b1c07d3e55",
                ),
                name = "Kaelen",
                time = SampleChats.time,
            )
        }
        composeRule.onNodeWithText("No forward secrecy yet", substring = true).assertExists()
        composeRule.captureScreenshot("message_info", composeRule.onNodeWithTag("sheet"))
    }

    @Test
    fun disappearingMessages() {
        showSheet { TimerContent(current = 0, name = "Kaelen", initialChoice = 3_600) }
        composeRule.onNodeWithText("Set timer to 1 hour").assertIsEnabled()
        composeRule.captureScreenshot("disappearing_messages_settings", composeRule.onNodeWithTag("sheet"))
    }

    @Test
    fun scheduledMessages() {
        composeRule.setContent {
            TflTestSurface {
                ScheduledContent(
                    uiState = ScheduledUiState(
                        name = "Kaelen (Valkyrie)",
                        items = listOf(
                            ScheduledItem(1, "Happy birthday! Cake at the tower at six.", NOW + 3 * 3_600_000 + 45 * 60_000),
                            ScheduledItem(2, "Weekly check-in: all good here.", NOW + 18 * 3_600_000 + 20 * 60_000),
                        ),
                        time = SampleChats.time,
                        loading = false,
                    ),
                    onBack = {},
                    onNew = {},
                    onEdit = {},
                    onSendNow = {},
                    onDelete = {},
                )
            }
        }
        composeRule.captureScreenshot("scheduled_messages")
    }
}

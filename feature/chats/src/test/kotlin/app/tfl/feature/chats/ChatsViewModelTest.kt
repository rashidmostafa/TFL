package app.tfl.feature.chats

import app.cash.turbine.test
import app.tfl.core.testing.MainDispatcherRule
import app.tfl.core.testing.typeText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModel = ChatsViewModel()

    @Test
    fun `shows every conversation with per-filter counts`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(ChatFilter.ALL, state.filter)
            assertEquals(listOf("bulletins"), state.broadcasts.map { it.id })
            assertEquals(listOf("sector7", "kaelen", "maya", "soren", "elena"), state.threads.map { it.id })
            assertEquals(
                mapOf(ChatFilter.ALL to 6, ChatFilter.DIRECT to 4, ChatFilter.GROUPS to 1, ChatFilter.BROADCASTS to 1),
                state.counts,
            )
            assertFalse(state.isEmpty)
        }
    }

    @Test
    fun `filter narrows the list to one kind`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onFilterSelect(ChatFilter.DIRECT)
            val direct = awaitItem()
            assertTrue(direct.broadcasts.isEmpty())
            assertEquals(listOf("kaelen", "maya", "soren", "elena"), direct.threads.map { it.id })

            viewModel.onFilterSelect(ChatFilter.BROADCASTS)
            val broadcasts = awaitItem()
            assertEquals(listOf("bulletins"), broadcasts.broadcasts.map { it.id })
            assertTrue(broadcasts.threads.isEmpty())
        }
    }

    @Test
    fun `search matches names and previews, ignoring case`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.searchQuery.typeText("MAYA")
            assertEquals(listOf("maya"), awaitItem().threads.map { it.id })

            viewModel.searchQuery.typeText("ridge point")
            assertEquals(listOf("sector7"), awaitItem().threads.map { it.id })
        }
    }

    @Test
    fun `search with no match is empty and flagged as a search`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.searchQuery.typeText("nobody by this name")
            val state = awaitItem()
            assertTrue(state.isEmpty)
            assertTrue(state.hasQuery)
        }
    }
}

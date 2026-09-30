package app.tfl.feature.onboarding.stealth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.tfl.core.testing.MainDispatcherRule
import app.tfl.core.testing.session.SessionFixture
import app.tfl.feature.onboarding.stealth.CalculatorEngine.PLUS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalculatorViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = SessionFixture(ApplicationProvider.getApplicationContext())
    private val viewModel = CalculatorViewModel(fixture.disguise, Dispatchers.Unconfined)

    @After
    fun tearDown() = fixture.database.close()

    private fun type(keys: String) = keys.forEach { key ->
        viewModel.press(if (key == PLUS) CalculatorKey.Operator(PLUS) else CalculatorKey.Digit(key.digitToInt()))
    }

    @Test
    fun `the secret code then equals opens TFL, anything else just calculates`() = runTest {
        fixture.session.commitOnboarding(fixture.draft(fixture.tools.newSeed()))
        fixture.lockSettings.enableDisguise("314159".encodeToByteArray())

        viewModel.openTfl.test {
            type("314158")
            viewModel.equals()
            assertEquals("314,158", viewModel.uiState.value.result)
            assertTrue(viewModel.uiState.value.showingResult)

            type("314159${PLUS}0")
            viewModel.equals()
            assertEquals("314,159", viewModel.uiState.value.result)
            expectNoEvents()

            type("314159")
            viewModel.equals()
            awaitItem()
            assertEquals(CalculatorUiState(), viewModel.uiState.value)
        }
    }

    @Test
    fun `without an identity the calculator only calculates`() = runTest {
        viewModel.openTfl.test {
            type("123456")
            viewModel.equals()
            assertEquals("123,456", viewModel.uiState.value.result)
            expectNoEvents()
        }
    }
}

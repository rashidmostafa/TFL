package app.tfl.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelsTest {

    @Test
    fun `security toggles and panic trigger change in memory`() {
        val viewModel = SecuritySettingsViewModel()
        assertTrue(viewModel.uiState.value.isOn(SecurityToggle.APP_LOCK))

        viewModel.onToggle(SecurityToggle.APP_LOCK, false)
        viewModel.onToggle(SecurityToggle.CALCULATOR_DISGUISE, true)
        viewModel.onPanicTriggerSelect(PanicTrigger.VOLUME_DOWN)

        val state = viewModel.uiState.value
        assertFalse(state.isOn(SecurityToggle.APP_LOCK))
        assertTrue(state.isOn(SecurityToggle.CALCULATOR_DISGUISE))
        assertEquals(PanicTrigger.VOLUME_DOWN, state.panicTrigger)
    }

    @Test
    fun `placeholder switches never change what is actually enforced`() {
        val viewModel = SecuritySettingsViewModel()
        SecurityToggle.entries.forEach { viewModel.onToggle(it, true) }
        assertEquals(1, viewModel.uiState.value.enforcedProtections)
    }

    @Test
    fun `network toggles change in memory`() {
        val viewModel = NetworkSettingsViewModel()
        viewModel.onToggle(NetworkToggle.RELAY, false)
        viewModel.onToggle(NetworkToggle.BATTERY_SAVER, true)

        val state = viewModel.uiState.value
        assertFalse(state.isOn(NetworkToggle.RELAY))
        assertTrue(state.isOn(NetworkToggle.BATTERY_SAVER))
        assertTrue(state.isOn(NetworkToggle.TOR))
    }
}

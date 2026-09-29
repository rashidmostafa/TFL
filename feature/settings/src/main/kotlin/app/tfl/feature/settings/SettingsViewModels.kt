package app.tfl.feature.settings

import androidx.lifecycle.ViewModel
import app.tfl.feature.settings.fake.FakeSettingsData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    private val state = MutableStateFlow(FakeSettingsData.settings)
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()
}

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor() : ViewModel() {
    private val state = MutableStateFlow(FakeSettingsData.security)
    val uiState: StateFlow<SecuritySettingsUiState> = state.asStateFlow()

    fun onToggle(toggle: SecurityToggle, enabled: Boolean) {
        state.update { it.copy(toggles = it.toggles + (toggle to enabled)) }
    }

    fun onPanicTriggerSelect(trigger: PanicTrigger) {
        state.update { it.copy(panicTrigger = trigger) }
    }
}

@HiltViewModel
class NetworkSettingsViewModel @Inject constructor() : ViewModel() {
    private val state = MutableStateFlow(FakeSettingsData.network)
    val uiState: StateFlow<NetworkSettingsUiState> = state.asStateFlow()

    fun onToggle(toggle: NetworkToggle, enabled: Boolean) {
        state.update { it.copy(toggles = it.toggles + (toggle to enabled)) }
    }
}

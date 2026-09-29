package app.tfl.feature.vault

import androidx.lifecycle.ViewModel
import app.tfl.feature.vault.fake.FakeVaultData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor() : ViewModel() {
    // Phase 0 shows a fixed snapshot; the vault repository replaces this in Phase 5.
    private val state = MutableStateFlow(FakeVaultData.state)
    val uiState: StateFlow<VaultUiState> = state.asStateFlow()
}

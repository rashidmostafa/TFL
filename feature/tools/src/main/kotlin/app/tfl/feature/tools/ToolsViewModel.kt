package app.tfl.feature.tools

import androidx.lifecycle.ViewModel
import app.tfl.feature.tools.fake.FakeToolsData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor() : ViewModel() {
    // Phase 0 shows a fixed snapshot; the CRDT document store replaces this in Phase 9.
    private val state = MutableStateFlow(FakeToolsData.state)
    val uiState: StateFlow<ToolsUiState> = state.asStateFlow()
}

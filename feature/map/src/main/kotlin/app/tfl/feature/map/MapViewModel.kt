package app.tfl.feature.map

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tfl.feature.map.fake.FakeMapData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor() : ViewModel() {

    val searchQuery = TextFieldState()

    val uiState: StateFlow<MapUiState> = snapshotFlow { searchQuery.text.toString() }
        .map(::buildUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), buildUiState(""))

    private fun buildUiState(query: String) = MapUiState(
        peerCount = FakeMapData.PEER_COUNT,
        coordinates = FakeMapData.COORDINATES,
        sharingCount = FakeMapData.friends.size,
        friends = FakeMapData.friends.search(query),
        hasQuery = query.isNotBlank(),
    )
}

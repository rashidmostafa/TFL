package app.tfl.feature.vault

import androidx.compose.runtime.Immutable

/** Colour role of a vault item; mapped to theme colours in the UI. */
enum class VaultAccent { PRIMARY, MESH, TOR, SUCCESS, DANGER }

/** One category in the storage breakdown. */
@Immutable
data class StorageSlice(
    val label: String,
    val sizeLabel: String,
    val gigabytes: Float,
    val accent: VaultAccent,
)

/** One encrypted area of the vault (photos, files, notes…). */
@Immutable
data class VaultArea(
    val id: String,
    val title: String,
    val summary: String,
    val icon: String,
    val accent: VaultAccent,
    val tag: String? = null,
    val detail: String? = null,
    val locked: Boolean = false,
)

@Immutable
data class VaultUiState(
    val autoLockIn: String,
    val usedGigabytes: Float,
    val totalGigabytes: Float,
    val slices: List<StorageSlice>,
    val areas: List<VaultArea>,
    val lastBackup: String,
)

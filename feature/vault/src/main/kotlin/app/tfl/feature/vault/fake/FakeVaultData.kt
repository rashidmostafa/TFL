package app.tfl.feature.vault.fake

import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.feature.vault.StorageSlice
import app.tfl.feature.vault.VaultAccent
import app.tfl.feature.vault.VaultArea
import app.tfl.feature.vault.VaultUiState

/**
 * PHASE 0 PLACEHOLDER DATA adapted from the Stitch mock. The encrypted vault arrives in Phase 5
 * (shared albums and friend backup in Phase 7); nothing here is real.
 */
internal object FakeVaultData {
    val state = VaultUiState(
        autoLockIn = "14:25",
        usedGigabytes = 24.8f,
        totalGigabytes = 128f,
        slices = listOf(
            StorageSlice("Photos & videos", "14.2 GB", 14.2f, VaultAccent.PRIMARY),
            StorageSlice("Files", "5.1 GB", 5.1f, VaultAccent.MESH),
            StorageSlice("Passwords & keys", "120 MB", 0.12f, VaultAccent.TOR),
            StorageSlice("Notes", "450 MB", 0.45f, VaultAccent.SUCCESS),
        ),
        areas = listOf(
            VaultArea("media", "Photos & videos", "342 items", MaterialSymbols.PhotoLibrary, VaultAccent.PRIMARY, detail = "Encrypted"),
            VaultArea("files", "Files", "89 files", MaterialSymbols.FolderZip, VaultAccent.MESH, tag = "PDF · GPX", detail = "Offline maps"),
            VaultArea("notes", "Notes", "28 notes", MaterialSymbols.EditNote, VaultAccent.SUCCESS, detail = "Today, 11:20"),
            VaultArea("passwords", "Passwords", "42 logins", MaterialSymbols.Password, VaultAccent.TOR, tag = "LOCKER"),
            VaultArea("shared", "Shared albums", "3 albums", MaterialSymbols.PhotoAlbum, VaultAccent.MESH, tag = "MESH", detail = "With 4 friends"),
            VaultArea("hidden", "Hidden albums", "Locked", MaterialSymbols.VisibilityOff, VaultAccent.DANGER, tag = "SECOND PIN", locked = true),
        ),
        lastBackup = "Alex · 2h ago",
    )
}

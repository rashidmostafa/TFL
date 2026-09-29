package app.tfl.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tfl.R
import app.tfl.core.designsystem.catalog.CatalogSheetHeader
import app.tfl.core.designsystem.catalog.catalogSections
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.GhostButton
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflModalBottomSheet
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.theme.TflTheme

/** Every shared component, for checking the design system on a real device. Debug builds only. */
@Composable
internal fun DesignCatalogScreen(onBack: () -> Unit) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(TflTheme.colors.canvas),
    ) {
        TflTopBar(
            title = stringResource(R.string.debug_catalog_screen_title),
            navigationIcon = { TflBackButton(onClick = onBack) },
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(catalogSections, key = { it.title }) { section ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(section.title)
                    section.content()
                }
            }
            item(key = "sheet") {
                GhostButton(
                    text = stringResource(R.string.debug_catalog_sheet_demo),
                    onClick = { showSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (showSheet) {
        TflModalBottomSheet(onDismissRequest = { showSheet = false }) {
            CatalogSheetHeader()
            Callout(
                text = stringResource(R.string.debug_catalog_sheet_body),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            )
        }
    }
}

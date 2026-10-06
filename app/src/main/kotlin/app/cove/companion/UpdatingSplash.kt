package app.cove.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveTheme
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText

/** Shown until the database is ready; says "Updating…" only when the one-time encryption upgrade is running. */
@Composable
fun UpdatingSplash(migrating: Boolean) {
    CoveTheme(isSystemInDarkTheme()) {
        Box(Modifier.fillMaxSize().background(Cove.colors.canvas), contentAlignment = Alignment.Center) {
            if (migrating) CoveText("Updating…", style = CoveType.Meta, color = Cove.colors.muted)
        }
    }
}

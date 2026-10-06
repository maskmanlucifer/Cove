package app.cove.companion.design.illustrations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveTheme
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.coveTopInset

/**
 * Debug-only review screen (`--es route debug/illustrations`): every mascot pose and every scene, light beside dark.
 * Still frames (no animation) so screenshots are stable.
 */
@Composable
fun IllustrationGallery() {
    Column(
        Modifier.fillMaxSize().background(Cove.colors.canvas).verticalScroll(rememberScrollState()).coveTopInset().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CoveText("Mascot poses", style = CoveType.Meta, color = Cove.colors.muted)
        listOf(false, true).forEach { dark ->
            CoveTheme(dark = dark) {
                Column(Modifier.fillMaxWidth().background(Cove.colors.canvas).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MascotPose.entries.chunked(5).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { pose -> Box(Modifier.weight(1f)) { Mascot(pose, Modifier.fillMaxWidth(), animate = false) } }
                            repeat(5 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        Scene.entries.forEach { scene ->
            CoveText(scene.name, style = CoveType.Meta, color = Cove.colors.muted)
            val themes = listOf(false, true)
            if (scene.isWide) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                themes.forEach { dark -> CoveTheme(dark = dark) { Illustration(scene, Modifier.fillMaxWidth(), animate = false) } }
            } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                themes.forEach { dark ->
                    CoveTheme(dark = dark) {
                        Box(Modifier.weight(1f).background(Cove.colors.canvas).padding(vertical = 8.dp)) { Illustration(scene, Modifier.fillMaxWidth(), animate = false) }
                    }
                }
            }
        }
        Box(Modifier.padding(60.dp))
    }
}

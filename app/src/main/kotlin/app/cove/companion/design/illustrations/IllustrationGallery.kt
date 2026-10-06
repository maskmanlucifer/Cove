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

/** Debug-only review screen (`--es route debug/illustrations`): every scene, light beside dark, on one page. */
@Composable
fun IllustrationGallery() {
    Column(
        Modifier.fillMaxSize().background(Cove.colors.canvas).verticalScroll(rememberScrollState()).coveTopInset().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Scene.entries.forEach { scene ->
            CoveText(scene.name, style = CoveType.Meta, color = Cove.colors.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(false, true).forEach { dark ->
                    CoveTheme(dark = dark) {
                        Box(Modifier.weight(1f).background(Cove.colors.canvas).padding(vertical = 8.dp)) {
                            if (scene.isTimeOfDay) SceneBanner(scene, 90.dp, Modifier.fillMaxWidth().padding(horizontal = 4.dp), animate = false)
                            else Illustration(scene, Modifier.fillMaxWidth(), animate = false)
                        }
                    }
                }
            }
        }
        Box(Modifier.padding(60.dp))
    }
}

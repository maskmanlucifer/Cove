package app.cove.companion.feature.widgets

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.cove.companion.R
import app.cove.companion.feature.voice.listenIntent

/**
 * Square "Tap to talk" tile (frame 11) that grows into a "Tell Cove anything..." bar when stretched wide and short.
 * Tapping only opens the app in listening; no inference runs in the widget.
 */
class VoiceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(180.dp, 40.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val open = actionStartActivity(listenIntent(context))
        provideContent {
            val size = LocalSize.current
            if (size.height < 90.dp) VoiceBar(open) else VoiceTile(open)
        }
    }
}

class VoiceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VoiceWidget()
}

@androidx.compose.runtime.Composable
private fun VoiceTile(open: androidx.glance.action.Action) {
    Box(modifier = cardModifier(0, WidgetColors.voiceCard).fillMaxSize().clickable(open), contentAlignment = Alignment.BottomEnd) {
        Image(ImageProvider(R.drawable.widget_orb_corner), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.size(100.dp))
        Column(modifier = GlanceModifier.fillMaxSize().padding(18.dp)) {
            Text("Cove", style = TextStyle(fontSize = 13.sp, color = WidgetColors.onVoiceMuted))
            Spacer(GlanceModifier.defaultWeight())
            Text("Tap to talk", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = WidgetColors.onVoiceCard))
        }
    }
}

@androidx.compose.runtime.Composable
private fun VoiceBar(open: androidx.glance.action.Action) {
    Row(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(WidgetColors.card).padding(start = 20.dp, end = 8.dp).clickable(open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Tell Cove anything…", style = TextStyle(fontSize = 17.sp, color = WidgetColors.placeholder), modifier = GlanceModifier.defaultWeight())
        Image(ImageProvider(R.drawable.widget_orb), contentDescription = "Talk to Cove", modifier = GlanceModifier.size(40.dp))
    }
}

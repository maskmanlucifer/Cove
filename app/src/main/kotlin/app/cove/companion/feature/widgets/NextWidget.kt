package app.cove.companion.feature.widgets

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.cove.companion.container

/** "Next · in 25 min / 11:00 am / Coffee with Jo"; tap opens the app. */
class NextWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetData.load(context.container)
        provideContent { NextContent(snapshot.next) }
    }
}

class NextWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextWidget()
}

@androidx.compose.runtime.Composable
private fun NextContent(next: NextCard?) {
    Column(
        modifier = cardModifier(20, WidgetColors.sunCard, vertical = 14).fillMaxSize().clickable(openApp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (next == null) "Next" else "Next · ${next.inText}", style = WidgetText.label)
        if (next == null) {
            Text("A clear day", style = TextStyle(fontSize = 28.sp, color = WidgetColors.ink))
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(next.digits, style = TextStyle(fontSize = 36.sp, color = WidgetColors.ink))
                Text(next.suffix, style = TextStyle(fontSize = 36.sp, color = WidgetColors.tail))
            }
            Text(next.title, maxLines = 1, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = WidgetColors.ink))
        }
    }
}

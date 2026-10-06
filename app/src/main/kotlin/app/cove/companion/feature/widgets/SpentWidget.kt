package app.cove.companion.feature.widgets

import android.content.Context
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.cove.companion.container
import app.cove.companion.core.rupees

/** "Spent today / ₹840" with what is left of this month's budget; tap opens the app. */
class SpentWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetData.load(context.container)
        provideContent {
            Column(modifier = cardModifier(18).fillMaxSize().clickable(openApp())) {
                Text("Spent today", style = WidgetText.label)
                Spacer(GlanceModifier.defaultWeight())
                Text(rupees(snapshot.spentTodayPaise), style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Medium, color = WidgetColors.ink))
                snapshot.leftThisMonthPaise?.let {
                    Text("${rupees(it)} left this month", maxLines = 1, style = TextStyle(fontSize = 13.sp, color = WidgetColors.muted))
                }
            }
        }
    }
}

class SpentWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpentWidget()
}

package app.cove.companion

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.LinearLayout
import kotlinx.coroutines.launch
import app.cove.companion.feature.widgets.NextWidgetReceiver
import app.cove.companion.feature.widgets.SpentWidgetReceiver
import app.cove.companion.feature.widgets.TasksWidgetReceiver
import app.cove.companion.feature.widgets.VoiceWidgetReceiver

/** Debug only: hosts every Cove widget in a 390dp column like frame 11 (needs `appwidget grantbind`). */
class WidgetHostDebugActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mgr = AppWidgetManager.getInstance(this)
        val host = AppWidgetHost(this, 77)
        host.deleteHost()
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFD9D6CF.toInt()); setPadding((16 * d).toInt(), (48 * d).toInt(), (16 * d).toInt(), 0) }
        val pending = mutableListOf<Pair<androidx.glance.appwidget.GlanceAppWidget, Int>>()
        fun add(c: Class<*>, wDp: Int, hDp: Int, row: LinearLayout = root) {
            val id = host.allocateAppWidgetId()
            check(mgr.bindAppWidgetIdIfAllowed(id, ComponentName(this, c)))
            pending += (c.getDeclaredConstructor().newInstance() as androidx.glance.appwidget.GlanceAppWidgetReceiver).glanceAppWidget to id
            mgr.updateAppWidgetOptions(id, Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp); putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp) })
            val view = host.createView(this, id, mgr.getAppWidgetInfo(id))
            row.addView(view, LinearLayout.LayoutParams((wDp * d).toInt(), (hDp * d).toInt()).apply { bottomMargin = (12 * d).toInt(); rightMargin = (12 * d).toInt() })
        }
        add(NextWidgetReceiver::class.java, 358, 133)
        val row = LinearLayout(this)
        add(SpentWidgetReceiver::class.java, 173, 173, row)
        add(VoiceWidgetReceiver::class.java, 173, 173, row)
        root.addView(row)
        add(VoiceWidgetReceiver::class.java, 358, 56)
        add(TasksWidgetReceiver::class.java, 358, 200)
        setContentView(root)
        host.startListening()
        kotlinx.coroutines.MainScope().launch { kotlinx.coroutines.delay(1500); pending.forEach { (w, id) -> kotlinx.coroutines.delay(800); w.update(this@WidgetHostDebugActivity, androidx.glance.appwidget.GlanceAppWidgetManager(this@WidgetHostDebugActivity).getGlanceIdBy(id)) } }
    }
}

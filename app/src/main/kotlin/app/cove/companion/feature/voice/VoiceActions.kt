package app.cove.companion.feature.voice

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** System screens the Voice screen and the Voice check sheet send people to. */
object VoiceActions {
    /**
     * Opens the page where the offline speech pack is downloaded: Google's speech settings when present, else the
     * system voice input settings, else general settings. Every candidate is tried because none is guaranteed.
     *
     * @return false only when nothing could be opened.
     */
    fun openSpeechSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent("com.google.android.googlequicksearchbox.TTS_SETTINGS"),
            Intent().setComponent(ComponentName("com.google.android.googlequicksearchbox", "com.google.android.apps.gsa.settingsui.VoiceSearchPreferences")),
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_LOCALE_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
        return false
    }
}

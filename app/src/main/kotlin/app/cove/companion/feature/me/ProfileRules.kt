package app.cove.companion.feature.me

import app.cove.companion.design.components.Zoom
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Longest display name, in characters the user sees. */
const val NAME_MAX = 40

/** Edge of the stored profile photo, in pixels. */
const val AVATAR_EDGE = 512

/** Keeps typing within [NAME_MAX] characters and on one line; never splits an emoji. */
fun capName(raw: String): String {
    val oneLine = raw.replace('\n', ' ').replace('\r', ' ')
    if (oneLine.codePointCount(0, oneLine.length) <= NAME_MAX) return oneLine
    return oneLine.substring(0, oneLine.offsetByCodePoints(0, NAME_MAX))
}

/** The name as saved: single spaces, trimmed, at most [NAME_MAX] characters. */
fun normalizeName(raw: String): String = capName(raw.trim().replace(Regex("\\s+"), " ")).trim()

/** Upper-case first letter or digit of [name] for the avatar, or null (a blank name, or one starting with a symbol) to show the person icon. */
fun avatarInitial(name: String): String? {
    val first = name.trim().takeIf { it.isNotEmpty() }?.let { it.codePointAt(0) } ?: return null
    if (!Character.isLetterOrDigit(first)) return null
    return String(Character.toChars(first)).uppercase()
}

/** A square region of the source picture, in source pixels. */
data class CropRect(val x: Int, val y: Int, val size: Int)

/**
 * The part of a [srcW] x [srcH] picture under a circular mask of [diameter] pixels, given the crop view's [zoom]
 * (scale relative to the picture covering the mask, translation in view pixels).
 */
fun cropRect(srcW: Int, srcH: Int, diameter: Float, zoom: Zoom): CropRect {
    val cover = diameter / min(srcW, srcH)
    val s = cover * zoom.scale
    val side = min(min(srcW.toFloat(), srcH.toFloat()), diameter / s)
    val cx = srcW / 2f - zoom.x / s
    val cy = srcH / 2f - zoom.y / s
    val x = (cx - side / 2f).coerceIn(0f, max(0f, srcW - side))
    val y = (cy - side / 2f).coerceIn(0f, max(0f, srcH - side))
    val size = max(1, side.roundToInt()).coerceAtMost(min(srcW, srcH))
    return CropRect(x.roundToInt().coerceAtMost(srcW - size), y.roundToInt().coerceAtMost(srcH - size), size)
}

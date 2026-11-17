package org.bytefred.pocoview.ui

import androidx.compose.ui.graphics.Color

/**
 * Small persistent settings (browser localStorage / Java preferences), as in ktoy. Failures are
 * swallowed: a preference that cannot be read or written falls back to its default.
 */
expect fun loadPref(key: String): String?

expect fun savePref(key: String, value: String)

/** Forgets a stored setting, so its default applies again. */
expect fun removePref(key: String)

// the remembered display settings (AppState loads them at start and saves them when they change)
internal const val LAYOUT_PREF = "layout"
internal const val MAX_WIDGETS_PREF = "maxWidgets"
internal const val OPEN_WIDGETS_PREF = "openWidgets"
internal const val POINT_SIZE_PREF = "pointSize"
internal const val LINE_WIDTH_PREF = "lineWidth"
internal const val LINE_COLOR_PREF = "lineColor"
internal const val LINE_ALPHA_PREF = "lineAlpha"
internal const val SELECTED_SIZE_PREF = "selectedSize"
internal const val SELECTED_COLOR_PREF = "selectedColor"
internal const val INCIDENT_LINES_PREF = "incidentLines"
internal const val INCIDENT_COLOR_PREF = "incidentLineColor"
internal const val INCIDENT_WIDTH_PREF = "incidentLineWidth"
internal const val LINES_PREF = "lineArrangement"
internal const val LABELS_PREF = "pointLabels"
internal const val WIDE_WEIGHTS_PREF = "wideWeights"
internal const val STACKED_WEIGHTS_PREF = "stackedWeights"

/** Per property: shown / drawn ("true,false") and its highlight colour (#RRGGBB). */
internal fun togglePrefKey(id: String) = "property.$id"
internal fun colorPrefKey(id: String) = "propertyColor.$id"

// stored values are checked: anything unreadable or out of range gives the default (null)
// or is clamped, so a corrupt store can never break the GUI

internal inline fun <reified E : Enum<E>> enumPref(key: String): E? =
    loadPref(key)?.let { v -> enumValues<E>().firstOrNull { it.name == v } }

internal fun intPref(key: String, range: IntRange): Int? = loadPref(key)?.toIntOrNull()?.coerceIn(range)

internal fun floatPref(key: String, range: ClosedFloatingPointRange<Float>): Float? =
    loadPref(key)?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(range)

internal fun booleanPref(key: String): Boolean? = loadPref(key)?.toBooleanStrictOrNull()

internal fun colorPref(key: String): Color? = loadPref(key)?.let(::parseHexColor)

internal fun encodeToggle(t: PropToggle) = "${t.count},${t.draw}"

internal fun decodeToggle(s: String): PropToggle? {
    val parts = s.split(',')
    if (parts.size != 2) return null
    val count = parts[0].toBooleanStrictOrNull() ?: return null
    val draw = parts[1].toBooleanStrictOrNull() ?: return null
    return PropToggle(count, draw)
}

/** Divider shares as "canvas=2.0,PROPERTIES=1.0" (the canvas has the key null). */
internal fun encodeWeights(weights: Map<Widget?, Float>): String =
    weights.entries.joinToString(",") { (k, v) -> "${k?.name ?: CANVAS_KEY}=$v" }

internal fun decodeWeights(s: String): Map<Widget?, Float> = buildMap {
    for (part in s.split(',')) {
        val kv = part.split('=')
        if (kv.size != 2) continue
        val key = if (kv[0] == CANVAS_KEY) null else Widget.entries.firstOrNull { it.name == kv[0] } ?: continue
        val w = kv[1].toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: continue
        put(key, w.coerceIn(MIN_WEIGHT, MAX_WEIGHT))
    }
}

private const val CANVAS_KEY = "canvas"

// a stored share outside this range would hide a panel or squeeze the others away
private const val MIN_WEIGHT = 0.01f
private const val MAX_WEIGHT = 100f

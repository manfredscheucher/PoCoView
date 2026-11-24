package org.bytefred.pocoview.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.bytefred.pocoview.resources.Res
import org.bytefred.pocoview.resources.dejavu_sans_mono
import org.jetbrains.compose.resources.Font

/** Teal accents on neutral white and plain grey backgrounds (ktoy's colours). The canvas palette is separate. */
private val PoCoViewColors = lightColorScheme(
    primary = Color(0xFF2F5D62),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE3E4),
    onPrimaryContainer = Color(0xFF0E2E31),
    secondary = Color(0xFF5B6F78),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE5E9),
    onSecondaryContainer = Color(0xFF1B2A31),
    tertiary = Color(0xFF8A6A2F),
    tertiaryContainer = Color(0xFFF6E7C8),
    onTertiaryContainer = Color(0xFF2E2208),
    background = Color.White,
    onBackground = Color(0xFF1B1F22),
    surface = Color.White,
    onSurface = Color(0xFF1B1F22),
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF46535A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerHighest = Color(0xFFE6E6E6),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDDDDDD),
    outline = Color(0xFF8A979E),
    outlineVariant = Color(0xFFCCD5DA),
    error = Color(0xFFB3261E),
)

@Composable
fun PoCoViewTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PoCoViewColors, content = content)
}

/** Background of the menu bar strip. */
val MENU_BAR_COLOR = Color(0xFFE6E8EB)

/**
 * The monospace style for coordinates, hashes and matrices, provided once at the app root
 * ([ProvideMonoStyle]) so the font is loaded once. Falls back to FontFamily.Monospace outside
 * the provider.
 */
val LocalMonoStyle = staticCompositionLocalOf {
    TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
}

/** Monospace text style shared by every data view, so columns line up. */
val MonoStyle: TextStyle
    @Composable get() = LocalMonoStyle.current

/**
 * Provide the bundled DejaVu Sans Mono as [MonoStyle]. FontFamily.Monospace is not reliably
 * monospaced across targets (the web build has no system fonts and falls back to a proportional
 * face); shipping the font makes every data view line up on every platform.
 * License: doc/licenses/LICENSE_DEJAVU.txt.
 */
@Composable
fun ProvideMonoStyle(content: @Composable () -> Unit) {
    val family = FontFamily(Font(Res.font.dejavu_sans_mono))
    val style = remember(family) { TextStyle(fontFamily = family, fontSize = 13.sp) }
    CompositionLocalProvider(LocalMonoStyle provides style, content = content)
}

/** A clickable, underlined link that opens [url] in the browser (or mail program). */
@Composable
fun LinkText(label: String, url: String, style: TextStyle = LocalTextStyle.current) {
    val uri = LocalUriHandler.current
    Text(
        label,
        style = style.copy(textDecoration = TextDecoration.Underline),
        color = Color(0xFF1A4FB0),
        modifier = Modifier.clickable { openUriSafely(uri, url) }.padding(vertical = 2.dp),
    )
}

/**
 * Opens a link without ever crashing the app: the JVM throws when the desktop can't browse (and
 * Android would when no browser or mail app is installed). Then nothing happens.
 */
internal fun openUriSafely(uri: UriHandler, url: String) {
    try { uri.openUri(url) } catch (e: Exception) { }
}

/**
 * A dialog with content (examples, import/export, property selection, help): the whole window but
 * a margin, the title on top, the buttons at the bottom right, wrapping on a narrow window.
 * Short questions stay small AlertDialogs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BigDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxSize().padding(16.dp).testTag("big-dialog"),
            shape = RoundedCornerShape(16.dp),
            // the colour of an AlertDialog
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
                Column(Modifier.weight(1f).fillMaxWidth(), content = content)
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                ) { buttons() }
            }
        }
    }
}

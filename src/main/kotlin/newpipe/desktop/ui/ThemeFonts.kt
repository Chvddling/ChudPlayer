@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package newpipe.desktop.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont
import chud.theme.FontKind

/** Windows fonts for each theme font kind (all ship with Windows 10/11). */
private val cache = HashMap<FontKind, FontFamily>()

fun themeFont(kind: FontKind): FontFamily = cache.getOrPut(kind) {
    fun sys(name: String) = runCatching {
        FontFamily(
            SystemFont(name, FontWeight.Normal),
            SystemFont(name, FontWeight.Bold),
        )
    }.getOrDefault(FontFamily.Default)
    when (kind) {
        FontKind.DEFAULT -> FontFamily.Default
        FontKind.CONDENSED -> sys("Bahnschrift")
        FontKind.MONO -> sys("Consolas")
        FontKind.TAHOMA -> sys("Tahoma")
        FontKind.BLACK -> sys("Impact")
    }
}

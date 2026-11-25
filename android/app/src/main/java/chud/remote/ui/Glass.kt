package chud.remote.ui

import chud.theme.FontKind
import chud.theme.ThemeState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/** Colours of the current theme (shared with the PC app, see chud.theme.Themes). */
object Aero {
    private val t get() = ThemeState.current
    val bgDeep get() = t.bgDeep
    val bg get() = t.bgContent
    val rowA get() = t.rowA
    val rowB get() = t.rowB
    val text get() = t.text
    val bright get() = t.textBright
    val dim get() = t.textDim
    val label get() = t.label
    val link get() = t.link
    val lcdText get() = t.lcdText
    val lcdDim get() = t.lcdDim
    val onAccent get() = t.onAccent
    val onSelection get() = t.onSelection
    val edge get() = t.edge
    val edgeWidth get() = t.edgeWidth.dp
    val glow get() = t.glow
    val green = Color(0xFF5FD84F)
}

fun gloss(a: Color, b: Color, c: Color, d: Color) = chud.theme.gloss(a, b, c, d)

object Gloss {
    private val t get() = ThemeState.current
    val bar get() = t.bar
    val button get() = t.button
    /** The theme's accent (was always blue before themes). */
    val blue get() = t.accentBrush
    val rowSelected get() = t.rowSelected
    val tabSelected get() = t.selection
    val tabs get() = t.statusBar
    val lcd get() = t.lcd
    val page get() = t.content
    val progress get() = t.progress
    val red = gloss(Color(0xFFFF8A80), Color(0xFFE0261C), Color(0xFFB50E07), Color(0xFFE2392E))
    val orange = gloss(Color(0xFFFFB06A), Color(0xFFFF7417), Color(0xFFE25200), Color(0xFFFF8A30))
    val purple = gloss(Color(0xFFC6A8FF), Color(0xFF8457E0), Color(0xFF6438C4), Color(0xFF9468EA))
    val green = gloss(Color(0xFFA6E8A0), Color(0xFF45A83C), Color(0xFF2B8424), Color(0xFF52B848))
}

fun themeShape(round: Boolean = true): androidx.compose.ui.graphics.Shape {
    val t = ThemeState.current
    val r = if (round) t.corner else t.smallCorner
    return if (r >= 999f) RoundedCornerShape(50) else RoundedCornerShape(r.dp)
}

fun themeFont(kind: FontKind): FontFamily = when (kind) {
    FontKind.DEFAULT -> FontFamily.Default
    FontKind.CONDENSED -> FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
    FontKind.MONO -> FontFamily.Monospace
    FontKind.TAHOMA -> FontFamily.SansSerif
    FontKind.BLACK -> FontFamily(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL))
}

private val glossyIds = setOf("smoked", "obsidian", "obsidian-emerald", "obsidian-sapphire", "obsidian-amethyst")
val isGlossy: Boolean get() = ThemeState.current.id in glossyIds

fun serviceColor(id: Int) = when (id) { 1 -> Color(0xFFE85D00); 100 -> Color(0xFF7B4FD6); 200 -> Color(0xFF2E8B3E); else -> Color(0xFFD4231A) }
fun serviceBrush(id: Int) = when (id) { 1 -> Gloss.orange; 100 -> Gloss.purple; 200 -> Gloss.green; else -> Gloss.red }

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ThemeState.current.accent,
            onPrimary = Aero.onAccent,
            background = Aero.bgDeep,
            surface = Aero.bg,
            onSurface = Aero.text,
            onBackground = Aero.text,
            surfaceVariant = Aero.rowB,
            onSurfaceVariant = Aero.dim,
        ),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides
                androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = themeFont(ThemeState.current.font)),
            content = content,
        )
    }
}

fun formatTime(seconds: Double): String {
    if (seconds.isNaN() || seconds < 0) return "--:--"
    val s = seconds.toLong()
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun formatTotal(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "$h h $m min" else "$m min"
}

@Composable
fun GlossButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = Gloss.button,
    textColor: Color = Aero.text,
    bold: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 32.dp,
) {
    val shape = themeShape(true)
    Box(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(brush)
            .border(Aero.edgeWidth, Aero.edge, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
fun Orb(icon: ImageVector, label: String, diameter: Dp, primary: Boolean = false, onClick: () -> Unit) {
    val theme = ThemeState.current
    val colors = if (primary) theme.orb else theme.orbSmall
    val shape = if (theme.corner >= 10f) CircleShape else RoundedCornerShape(theme.smallCorner.dp)
    Box(
        Modifier
            .size(diameter)
            .drawBehind {
                if (primary && theme.glow != Color.Transparent) drawCircle(
                    brush = Brush.radialGradient(listOf(theme.glow, Color.Transparent), center = center, radius = this.size.minDimension * 0.8f),
                    radius = this.size.minDimension * 0.8f,
                )
            }
            .clip(shape)
            .drawBehind {
                drawRect(brush = Brush.radialGradient(colors, center = Offset(this.size.width / 2f, this.size.height * 0.28f), radius = this.size.width * 0.85f))
                if (isGlossy) drawOval(
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.04f)), 0f, this.size.height * 0.5f),
                    topLeft = Offset(this.size.width * 0.16f, this.size.height * 0.04f),
                    size = Size(this.size.width * 0.68f, this.size.height * 0.46f),
                )
            }
            .border(Aero.edgeWidth, Aero.edge, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (primary) theme.orbIcon else Aero.text, modifier = Modifier.size(diameter * 0.42f))
    }
}

@Composable
fun ModeToggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val shape = themeShape(false)
    Box(
        Modifier
            .size(52.dp, 46.dp)
            .clip(shape)
            .background(if (on) Gloss.blue else Gloss.button)
            .border(Aero.edgeWidth, Aero.edge, shape)
            .clickable(role = Role.Switch, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (on) Aero.onAccent else Aero.dim, modifier = Modifier.size(22.dp))
    }
}

/** Glossy slider; [value] 0..1. */
@Composable
fun GlossSlider(
    value: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinished)
    val input = if (!enabled) Modifier else Modifier
        .pointerInput(Unit) { detectTapGestures { change((it.x / size.width).coerceIn(0f, 1f)); finish() } }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(onDragEnd = { finish() }) { ch, _ ->
                ch.consume()
                change((ch.position.x / size.width).coerceIn(0f, 1f))
            }
        }
    BoxWithConstraints(modifier.height(28.dp).then(input), contentAlignment = Alignment.CenterStart) {
        val v = value.coerceIn(0f, 1f)
        val theme = ThemeState.current
        val pill = themeShape(true)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(pill).background(theme.track).border(1.dp, if (theme.edgeWidth > 1f) Aero.edge else Color.Black, pill))
        Box(Modifier.fillMaxWidth(v).height(6.dp).clip(pill).background(Gloss.progress))
        val k = 18.dp
        val ks = if (theme.corner >= 10f) CircleShape else RoundedCornerShape(theme.smallCorner.dp)
        Box(
            Modifier.offset(x = (maxWidth - k) * v).size(k).clip(ks)
                .background(Brush.verticalGradient(theme.knob))
                .border(1.dp, Color.Black, ks),
        )
    }
}

@Composable
fun PillGroup(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, selectedBrush: (Int) -> Brush = { Gloss.blue }, height: Dp = 30.dp, selectedText: (Int) -> Color = { Aero.onAccent }) {
    val shape = themeShape(true)
    Row(Modifier.height(height).clip(shape).border(Aero.edgeWidth, Aero.edge, shape)) {
        labels.forEachIndexed { i, label ->
            if (i > 0) Box(Modifier.width(Aero.edgeWidth).fillMaxHeight().background(Aero.edge))
            val sel = i == selected
            Box(
                Modifier.fillMaxHeight().background(if (sel) selectedBrush(i) else Gloss.button)
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (sel) selectedText(i) else Aero.text, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

@Composable
fun ServiceTag(id: Int) {
    Text(
        chud.remote.serviceShort(id),
        color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(serviceColor(id)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Thumbnail / cover with the 2010 gloss on top. */
@Composable
fun Thumb(url: String?, modifier: Modifier, corner: Dp = 4.dp) {
    val shape = RoundedCornerShape(corner)
    Box(modifier.clip(shape).background(Aero.rowB).border(Aero.edgeWidth, Aero.edge, shape)) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (isGlossy) Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.03f)))))
    }
}

@Composable
fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Aero.dim, fontSize = 14.sp)
    }
}

@Composable
fun RowsSpacer() = Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {}

package newpipe.desktop.ui

import chud.theme.ThemeState
import chud.theme.themePattern
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.DarkDefaultContextMenuRepresentation
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import newpipe.desktop.data.VideoItem
import newpipe.desktop.net.Http
import java.util.Locale

// =====================================================================
//  "Smoked Glass" theme: dark Windows 7 Aero look, iTunes-style layout
// =====================================================================

/** Colours of the current theme (see chud.theme.Themes); reading them in a composable follows theme changes. */
object Aero {
    private val t get() = ThemeState.current
    val bgDeep get() = t.bgDeep
    val bgContent get() = t.bgContent
    val rowA get() = t.rowA
    val rowB get() = t.rowB
    val text get() = t.text
    val textBright get() = t.textBright
    val textDim get() = t.textDim
    val label get() = t.label
    val link get() = t.link
    val accent get() = t.accent
    val lcdText get() = t.lcdText
    val lcdDim get() = t.lcdDim
    val selectionBorder get() = t.selectionBorder
    val onSelection get() = t.onSelection
    val onAccent get() = t.onAccent
    val edge get() = t.edge
    val edgeWidth get() = t.edgeWidth.dp
    val track get() = t.track
    val field get() = t.field
    val glow get() = t.glow
    // service colours stay the same in every theme
    val ytRed = Color(0xFFD4231A)
    val scOrange = Color(0xFFE85D00)
    val squidPurple = Color(0xFF7B4FD6)
    val localGreen = Color(0xFF2E8B3E)
}

/** The classic 2010 "gloss": two gradients with a hard edge in the middle. */
fun gloss(top: Color, upper: Color, lower: Color, bottom: Color): Brush = chud.theme.gloss(top, upper, lower, bottom)

object Gloss {
    private val t get() = ThemeState.current
    val bar get() = t.bar
    val button get() = t.button
    /** The theme's accent ("on" toggles, main buttons). Was always blue before themes. */
    val blue get() = t.accentBrush
    val selection get() = t.selection
    val rowSelected get() = t.rowSelected
    val header get() = t.header
    val serviceBar get() = t.serviceBar
    val statusBar get() = t.statusBar
    val sidebar get() = t.sidebar
    val content get() = t.content
    val lcd get() = t.lcd
    val progress get() = t.progress
    val frame get() = t.frame
    val splitter get() = t.splitter
    // service colours
    val orange = gloss(Color(0xFFFFB06A), Color(0xFFFF7417), Color(0xFFE25200), Color(0xFFFF8A30))
    val red = gloss(Color(0xFFFF8A80), Color(0xFFE0261C), Color(0xFFB50E07), Color(0xFFE2392E))
    val green = gloss(Color(0xFFA6E8A0), Color(0xFF45A83C), Color(0xFF2B8424), Color(0xFF52B848))
    val purple = gloss(Color(0xFFC6A8FF), Color(0xFF8457E0), Color(0xFF6438C4), Color(0xFF9468EA))
}

/** Shape of a "round" button / pill in the current theme. */
fun themeShape(round: Boolean = true): androidx.compose.ui.graphics.Shape {
    val t = ThemeState.current
    val r = if (round) t.corner else t.smallCorner
    return if (r >= 999f) RoundedCornerShape(50) else RoundedCornerShape(r.dp)
}

/** The theme's texture behind a panel. */
fun Modifier.themed(lcd: Boolean = false): Modifier {
    val t = ThemeState.current
    return if (lcd) themePattern(t.lcdPattern, Color(0x40000000)) else themePattern(t.pattern, t.patternColor)
}

fun serviceName(id: Int) = newpipe.desktop.data.Service.of(id).label
fun serviceColor(id: Int) = when (id) { 1 -> Aero.scOrange; 100 -> Aero.squidPurple; 200 -> Aero.localGreen; else -> Aero.ytRed }
fun serviceBrush(id: Int) = when (id) { 1 -> Gloss.orange; 100 -> Gloss.purple; 200 -> Gloss.green; else -> Gloss.red }
/** "plays" for music services, "views" for YouTube. */
fun playsWord(id: Int) = if (id == 0) "views" else "plays"

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Aero.accent,
            onPrimary = Aero.onAccent,
            secondary = Aero.scOrange,
            background = Aero.bgDeep,
            onBackground = Aero.text,
            surface = Aero.bgContent,
            onSurface = Aero.text,
            surfaceVariant = Aero.rowB,
            onSurfaceVariant = Aero.textDim,
            outline = ThemeState.current.outline,
        ),
    ) {
        CompositionLocalProvider(
            LocalContextMenuRepresentation provides DarkDefaultContextMenuRepresentation,
            LocalScrollbarStyle provides defaultScrollbarStyle().copy(
                unhoverColor = Color.White.copy(alpha = 0.14f),
                hoverColor = Color.White.copy(alpha = 0.32f),
            ),
            LocalContentColor provides Aero.text,
            androidx.compose.material3.LocalTextStyle provides
                androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = themeFont(ThemeState.current.font)),
            content = content,
        )
    }
}

// ---------------- glossy widgets ----------------

@Composable
fun GlossButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = Gloss.button,
    textColor: Color = Aero.text,
    enabled: Boolean = true,
    bold: Boolean = false,
    round: Boolean = true,
    height: Dp = 24.dp,
    icon: ImageVector? = null,
) {
    val shape = themeShape(round)
    Row(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(brush)
            .border(Aero.edgeWidth, Aero.edge, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = textColor, modifier = Modifier.size(14.dp))
        Text(text, color = textColor, fontSize = 11.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

/** Round glossy "orb" button (Windows 7 / WMP style). The primary one glows blue. */
@Composable
fun OrbButton(
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    diameter: Dp,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val theme = ThemeState.current
    val colors = if (primary) theme.orb else theme.orbSmall
    val glossy = theme.corner >= 999f || theme.id.startsWith("obsidian") || theme.id == "smoked"
    val orbShape = if (theme.corner >= 999f || theme.corner >= 10f) CircleShape else RoundedCornerShape(theme.smallCorner.dp)
    Box(
        Modifier
            .size(diameter)
            .alpha(if (enabled) 1f else 0.5f)
            .drawBehind {
                if (primary && theme.glow != Color.Transparent) {
                    drawCircle(
                        brush = Brush.radialGradient(listOf(theme.glow, Color.Transparent), center = center, radius = this.size.minDimension * 0.8f),
                        radius = this.size.minDimension * 0.8f,
                    )
                }
            }
            .clip(orbShape)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(colors, center = Offset(this.size.width / 2f, this.size.height * 0.28f), radius = this.size.width * 0.85f),
                )
                if (glossy) drawOval(
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.04f)), startY = 0f, endY = this.size.height * 0.5f),
                    topLeft = Offset(this.size.width * 0.16f, this.size.height * 0.04f),
                    size = Size(this.size.width * 0.68f, this.size.height * 0.46f),
                )
            }
            .border(Aero.edgeWidth, Aero.edge, orbShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (primary) theme.orbIcon else Aero.text, modifier = Modifier.size(diameter * 0.42f))
    }
}

/** Thin glossy slider. [value] is 0..1. */
@Composable
fun GlossSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
    enabled: Boolean = true,
    trackHeight: Dp = 5.dp,
    knob: Boolean = true,
    fill: Brush = Gloss.progress,
) {
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val input = if (!enabled) Modifier else Modifier
        .pointerInput(Unit) {
            detectTapGestures { change((it.x / size.width).coerceIn(0f, 1f)); finish() }
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(onDragEnd = { finish() }) { ch, _ ->
                change((ch.position.x / size.width).coerceIn(0f, 1f))
            }
        }
    BoxWithConstraints(modifier.height(16.dp).then(input), contentAlignment = Alignment.CenterStart) {
        val v = value.coerceIn(0f, 1f)
        val theme = ThemeState.current
        val pill = themeShape(true)
        Box(Modifier.fillMaxWidth().height(trackHeight).clip(pill).background(Aero.track).border(1.dp, if (theme.edgeWidth > 1f) Aero.edge else Color.Black, pill))
        Box(Modifier.fillMaxWidth(v).height(trackHeight).clip(pill).background(fill))
        if (knob && enabled) {
            val k = 12.dp
            val ks = if (theme.corner >= 10f) CircleShape else RoundedCornerShape(theme.smallCorner.dp)
            Box(
                Modifier
                    .offset(x = (maxWidth - k) * v)
                    .size(k)
                    .clip(ks)
                    .background(Brush.verticalGradient(theme.knob))
                    .border(1.dp, Color.Black, ks),
            )
        }
    }
}

/** A row of joined glossy pills (service switch, Video/Audio, tabs). */
@Composable
fun PillGroup(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    selectedBrush: (Int) -> Brush = { Gloss.blue },
    selectedText: (Int) -> Color = { Aero.onAccent },
) {
    val shape = themeShape(true)
    Row(Modifier.height(24.dp).clip(shape).border(Aero.edgeWidth, Aero.edge, shape)) {
        labels.forEachIndexed { i, label ->
            if (i > 0) Box(Modifier.width(Aero.edgeWidth).fillMaxHeight().background(Aero.edge))
            val sel = i == selected
            Box(
                Modifier
                    .fillMaxHeight()
                    .background(if (sel) selectedBrush(i) else Gloss.button)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (sel) selectedText(i) else Aero.text,
                    fontSize = 11.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold,
                )
            }
        }
    }
}

/** True while a text box has keyboard focus, so Space/arrows type instead of controlling playback. */
object InputFocus {
    @Volatile var typing: Boolean = false
}

@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search or paste a link",
) {
    val shape = themeShape(true)
    Row(
        modifier.height(26.dp).clip(shape).background(Aero.field).border(Aero.edgeWidth, Aero.edge, shape).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = Aero.label, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = Aero.label, fontSize = 12.sp, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Aero.textBright, fontSize = 12.sp),
                cursorBrush = SolidColor(Aero.textBright),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Search" }
                    .onFocusChanged { InputFocus.typing = it.isFocused }
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                            onSubmit(); true
                        } else false
                    },
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Clear, "Clear search", tint = Aero.label,
                modifier = Modifier.size(14.dp).clickable { onValueChange("") },
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Aero.label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = modifier.padding(start = 12.dp, top = 12.dp, bottom = 3.dp),
    )
}

/** iTunes "source list" item with the Windows 7 blue glass selection. */
@Composable
fun SidebarItem(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Aero.text) {
    val shape = themeShape(false)
    Text(
        text,
        color = if (selected) Aero.onSelection else color,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(Gloss.selection).border(BorderStroke(1.dp, Aero.selectionBorder), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    )
}

@Composable
fun ServiceBadge(serviceId: Int) {
    Text(
        serviceName(serviceId),
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(serviceColor(serviceId))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
fun CenterMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Aero.textDim)
    }
}

// ---------------- images ----------------

/**
 * Thumbnails and covers, scaled down to at most [MAX_SIDE] px and kept in a
 * least-recently-used cache capped by memory size rather than by count.
 */
object ImageCache {
    private const val MAX_SIDE = 800
    private const val BUDGET_BYTES = 64L * 1024 * 1024

    private val cache = LinkedHashMap<String, ImageBitmap>(128, 0.75f, true)
    private var bytes = 0L

    private fun sizeOf(b: ImageBitmap) = b.width.toLong() * b.height * 4

    fun clear() = synchronized(cache) { cache.clear(); bytes = 0 }

    private fun put(url: String, bmp: ImageBitmap) = synchronized(cache) {
        cache.put(url, bmp)?.let { bytes -= sizeOf(it) }
        bytes += sizeOf(bmp)
        val it = cache.entries.iterator()
        while (bytes > BUDGET_BYTES && cache.size > 1 && it.hasNext()) {
            val e = it.next()
            if (e.key == url) continue
            bytes -= sizeOf(e.value)
            it.remove()
        }
    }

    /** Decode and shrink in one go, so full-size bitmaps never stay around. */
    private fun decode(data: ByteArray): ImageBitmap? {
        val img = org.jetbrains.skia.Image.makeFromEncoded(data)
        try {
            val side = maxOf(img.width, img.height)
            if (side <= MAX_SIDE) return img.toComposeImageBitmap()
            val scale = MAX_SIDE.toFloat() / side
            val w = maxOf(1, (img.width * scale).toInt())
            val h = maxOf(1, (img.height * scale).toInt())
            val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
            try {
                surface.canvas.drawImageRect(
                    img,
                    org.jetbrains.skia.Rect.makeWH(img.width.toFloat(), img.height.toFloat()),
                    org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()),
                    org.jetbrains.skia.FilterMipmap(org.jetbrains.skia.FilterMode.LINEAR, org.jetbrains.skia.MipmapMode.LINEAR),
                    null,
                    true,
                )
                return surface.makeImageSnapshot().use { it.toComposeImageBitmap() }
            } finally {
                surface.close()
            }
        } finally {
            img.close()
        }
    }

    suspend fun load(url: String): ImageBitmap? {
        synchronized(cache) { cache[url] }?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                // Local album art: embedded in the file or cover.jpg next to it
                if (url.startsWith(newpipe.desktop.data.LocalMusic.COVER_PREFIX)) {
                    val f = java.io.File(url.removePrefix(newpipe.desktop.data.LocalMusic.COVER_PREFIX))
                    return@runCatching newpipe.desktop.data.LocalMusic.cover(f)?.let { decode(it) }
                }
                val req = okhttp3.Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()
                Http.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()?.let { decode(it) }
                }
            }.getOrNull()
        } ?: return null
        put(url, bmp)
        return bmp
    }
}

@Composable
fun NetImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val bmp by produceState<ImageBitmap?>(null, url) {
        value = url?.let { ImageCache.load(it) }
    }
    val b = bmp
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(Aero.rowB))
    }
}

// ---------------- formatting ----------------

fun formatTime(seconds: Double): String {
    if (seconds.isNaN() || seconds < 0) return "--:--"
    val s = seconds.toLong()
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}

fun formatTotal(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "$h h $m min" else "$m min"
}

fun formatCount(n: Long): String = when {
    n < 0 -> ""
    n >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", n / 1e9)
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1e6)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1e3)
    else -> n.toString()
}

fun VideoItem.lengthText(): String = when {
    isLive -> "LIVE"
    durationSec > 0 -> formatTime(durationSec.toDouble())
    else -> ""
}

fun VideoItem.subtitle(): String = listOfNotNull(
    uploader.takeIf { it.isNotBlank() },
    formatCount(viewCount).takeIf { it.isNotEmpty() }?.let { "$it ${playsWord(serviceId)}" },
    uploadDate?.takeIf { it.isNotBlank() },
).joinToString(" · ")

/** Small square glossy on/off button with a tooltip (repeat, shuffle, loop, autoplay). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ModeToggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val shape = themeShape(false)
    androidx.compose.foundation.TooltipArea(
        tooltip = {
            Text(
                label + if (on) " (on)" else " (off)",
                color = Aero.textBright,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Aero.bgContent)
                    .border(1.dp, Color.Black, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        },
        delayMillis = 400,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .drawBehind {
                    if (on && Aero.glow != Color.Transparent) drawCircle(
                        brush = Brush.radialGradient(listOf(Aero.glow.copy(alpha = 0.4f), Color.Transparent), center = center, radius = this.size.minDimension),
                        radius = this.size.minDimension,
                    )
                }
                .clip(shape)
                .background(if (on) Gloss.blue else Gloss.button)
                .border(Aero.edgeWidth, Aero.edge, shape)
                .clickable(role = Role.Switch, onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (on) Aero.onAccent else Aero.textDim, modifier = Modifier.size(16.dp))
        }
    }
}

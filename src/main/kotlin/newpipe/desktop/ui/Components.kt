package newpipe.desktop.ui

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

object Aero {
    val bgDeep = Color(0xFF0D1117)
    val bgContent = Color(0xFF151B23)
    val rowA = Color(0xFF10151C)
    val rowB = Color(0xFF151B23)
    val text = Color(0xFFD7E3EF)
    val textBright = Color(0xFFEEF4FA)
    val textDim = Color(0xFF93A8BD)
    val label = Color(0xFF7F98B0)
    val link = Color(0xFF8CC8FF)
    val accent = Color(0xFF3E9BEA)
    val lcdText = Color(0xFFBFE3FF)
    val lcdDim = Color(0xFF86A7C4)
    val ytRed = Color(0xFFD4231A)
    val scOrange = Color(0xFFE85D00)
    val squidPurple = Color(0xFF7B4FD6)
    val localGreen = Color(0xFF2E8B3E)
    val selectionBorder = Color(0xFF3D7FBF)
}

/** The classic 2010 "gloss": two gradients with a hard edge in the middle. */
fun gloss(top: Color, upper: Color, lower: Color, bottom: Color): Brush =
    Brush.verticalGradient(0f to top, 0.5f to upper, 0.51f to lower, 1f to bottom)

object Gloss {
    val bar = gloss(Color(0xFF3B4656), Color(0xFF222A35), Color(0xFF10151C), Color(0xFF1A212B))
    val button = gloss(Color(0xFF4A5563), Color(0xFF2B333E), Color(0xFF1B2129), Color(0xFF2C343E))
    val orange = gloss(Color(0xFFFFB06A), Color(0xFFFF7417), Color(0xFFE25200), Color(0xFFFF8A30))
    val red = gloss(Color(0xFFFF8A80), Color(0xFFE0261C), Color(0xFFB50E07), Color(0xFFE2392E))
    val green = gloss(Color(0xFFA6E8A0), Color(0xFF45A83C), Color(0xFF2B8424), Color(0xFF52B848))
    val purple = gloss(Color(0xFFC6A8FF), Color(0xFF8457E0), Color(0xFF6438C4), Color(0xFF9468EA))
    val blue = gloss(Color(0xFF8CCBFF), Color(0xFF3A8EE0), Color(0xFF1E6BC2), Color(0xFF4A9DEA))
    val selection = gloss(Color(0x736EB4FF), Color(0x4D3278D2), Color(0x591E5AB4), Color(0x66468CE6))
    val rowSelected = gloss(Color(0xFF3F8AD6), Color(0xFF1F5FAE), Color(0xFF154F99), Color(0xFF2A6FC0))
    val header = gloss(Color(0xFF34404F), Color(0xFF242C37), Color(0xFF171D25), Color(0xFF1F262F))
    val serviceBar = Brush.verticalGradient(listOf(Color(0xFF1F2731), Color(0xFF151B23)))
    val statusBar = Brush.verticalGradient(listOf(Color(0xFF252D38), Color(0xFF151B22)))
    val sidebar = Brush.verticalGradient(listOf(Color(0xFF1A212B), Color(0xFF141A22)))
    val content = Brush.verticalGradient(listOf(Color(0xFF151B23), Color(0xFF0D1117)))
    val lcd = Brush.verticalGradient(listOf(Color(0xFF0F1A26), Color(0xFF06101A)))
    val progress = Brush.horizontalGradient(listOf(Color(0xFF2D8DE0), Color(0xFF9FD8FF)))
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
            onPrimary = Color.White,
            secondary = Aero.scOrange,
            background = Aero.bgDeep,
            onBackground = Aero.text,
            surface = Aero.bgContent,
            onSurface = Aero.text,
            surfaceVariant = Color(0xFF1F2731),
            onSurfaceVariant = Aero.textDim,
            outline = Color(0xFF3A4552),
        ),
    ) {
        CompositionLocalProvider(
            LocalContextMenuRepresentation provides DarkDefaultContextMenuRepresentation,
            LocalScrollbarStyle provides defaultScrollbarStyle().copy(
                unhoverColor = Color.White.copy(alpha = 0.14f),
                hoverColor = Color.White.copy(alpha = 0.32f),
            ),
            LocalContentColor provides Aero.text,
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
    val shape = if (round) RoundedCornerShape(50) else RoundedCornerShape(3.dp)
    Row(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(brush)
            .border(1.dp, Color.Black, shape)
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
    val colors = if (primary) {
        listOf(Color(0xFFD8F0FF), Color(0xFF3E9BEA), Color(0xFF0E4C90), Color(0xFF062A52))
    } else {
        listOf(Color(0xFF7A8796), Color(0xFF2A323D), Color(0xFF11161C))
    }
    Box(
        Modifier
            .size(diameter)
            .alpha(if (enabled) 1f else 0.5f)
            .drawBehind {
                if (primary) {
                    drawCircle(
                        brush = Brush.radialGradient(listOf(Color(0x885AB4FF), Color.Transparent), center = center, radius = this.size.minDimension * 0.8f),
                        radius = this.size.minDimension * 0.8f,
                    )
                }
            }
            .clip(CircleShape)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(colors, center = Offset(this.size.width / 2f, this.size.height * 0.28f), radius = this.size.width * 0.85f),
                )
                drawOval(
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.04f)), startY = 0f, endY = this.size.height * 0.5f),
                    topLeft = Offset(this.size.width * 0.16f, this.size.height * 0.04f),
                    size = Size(this.size.width * 0.68f, this.size.height * 0.46f),
                )
            }
            .border(1.dp, Color.Black, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(diameter * 0.42f))
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
        val pill = RoundedCornerShape(50)
        Box(Modifier.fillMaxWidth().height(trackHeight).clip(pill).background(Color(0xFF05080B)).border(1.dp, Color.Black, pill))
        Box(Modifier.fillMaxWidth(v).height(trackHeight).clip(pill).background(fill))
        if (knob && enabled) {
            val k = 12.dp
            Box(
                Modifier
                    .offset(x = (maxWidth - k) * v)
                    .size(k)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color.White, Color(0xFF9CB3C9))))
                    .border(1.dp, Color.Black, CircleShape),
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
) {
    val shape = RoundedCornerShape(50)
    Row(Modifier.height(24.dp).clip(shape).border(1.dp, Color.Black, shape)) {
        labels.forEachIndexed { i, label ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
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
                    color = if (sel) Color.White else Aero.text,
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
    val shape = RoundedCornerShape(50)
    Row(
        modifier.height(26.dp).clip(shape).background(Color(0xFF0B1118)).border(1.dp, Color.Black, shape).padding(horizontal = 10.dp),
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
    val shape = RoundedCornerShape(3.dp)
    Text(
        text,
        color = if (selected) Color.White else color,
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

object ImageCache {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 400
    }

    suspend fun load(url: String): ImageBitmap? {
        synchronized(cache) { cache[url] }?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                // Local album art: embedded in the file or cover.jpg next to it
                if (url.startsWith(newpipe.desktop.data.LocalMusic.COVER_PREFIX)) {
                    val f = java.io.File(url.removePrefix(newpipe.desktop.data.LocalMusic.COVER_PREFIX))
                    return@runCatching newpipe.desktop.data.LocalMusic.cover(f)
                        ?.let { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }
                }
                val req = okhttp3.Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()
                Http.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null
                    else resp.body?.bytes()?.let { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }
                }
            }.getOrNull()
        } ?: return null
        synchronized(cache) { cache[url] = bmp }
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
        Box(modifier.background(Color(0xFF232B35)))
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
    val shape = RoundedCornerShape(4.dp)
    androidx.compose.foundation.TooltipArea(
        tooltip = {
            Text(
                label + if (on) " (on)" else " (off)",
                color = Aero.textBright,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xF01A212B))
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
                    if (on) drawCircle(
                        brush = Brush.radialGradient(listOf(Color(0x665AB4FF), Color.Transparent), center = center, radius = this.size.minDimension),
                        radius = this.size.minDimension,
                    )
                }
                .clip(shape)
                .background(if (on) Gloss.blue else Gloss.button)
                .border(1.dp, Color.Black, shape)
                .clickable(role = Role.Switch, onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (on) Color.White else Aero.textDim, modifier = Modifier.size(16.dp))
        }
    }
}

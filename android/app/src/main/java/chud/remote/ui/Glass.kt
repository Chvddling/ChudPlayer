package chud.remote.ui

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

object Aero {
    val bgDeep = Color(0xFF0D1117)
    val bg = Color(0xFF151B23)
    val rowA = Color(0xFF10151C)
    val rowB = Color(0xFF151B23)
    val text = Color(0xFFD7E3EF)
    val bright = Color(0xFFEEF4FA)
    val dim = Color(0xFF93A8BD)
    val label = Color(0xFF7F98B0)
    val link = Color(0xFF8CC8FF)
    val lcdText = Color(0xFFBFE3FF)
    val lcdDim = Color(0xFF86A7C4)
    val green = Color(0xFF5FD84F)
}

fun gloss(a: Color, b: Color, c: Color, d: Color) = Brush.verticalGradient(0f to a, 0.5f to b, 0.51f to c, 1f to d)

object Gloss {
    val bar = gloss(Color(0xFF3B4656), Color(0xFF222A35), Color(0xFF10151C), Color(0xFF1A212B))
    val button = gloss(Color(0xFF4A5563), Color(0xFF2B333E), Color(0xFF1B2129), Color(0xFF2C343E))
    val blue = gloss(Color(0xFF8CCBFF), Color(0xFF3A8EE0), Color(0xFF1E6BC2), Color(0xFF4A9DEA))
    val red = gloss(Color(0xFFFF8A80), Color(0xFFE0261C), Color(0xFFB50E07), Color(0xFFE2392E))
    val orange = gloss(Color(0xFFFFB06A), Color(0xFFFF7417), Color(0xFFE25200), Color(0xFFFF8A30))
    val purple = gloss(Color(0xFFC6A8FF), Color(0xFF8457E0), Color(0xFF6438C4), Color(0xFF9468EA))
    val green = gloss(Color(0xFFA6E8A0), Color(0xFF45A83C), Color(0xFF2B8424), Color(0xFF52B848))
    val rowSelected = gloss(Color(0xFF3F8AD6), Color(0xFF1F5FAE), Color(0xFF154F99), Color(0xFF2A6FC0))
    val tabSelected = gloss(Color(0x596EB4FF), Color(0x2E3278D2), Color(0x381E5AB4), Color(0x47468CE6))
    val tabs = Brush.verticalGradient(listOf(Color(0xFF252D38), Color(0xFF151B22)))
    val lcd = Brush.verticalGradient(listOf(Color(0xFF0F1A26), Color(0xFF06101A)))
    val page = Brush.verticalGradient(listOf(Color(0xFF151B23), Color(0xFF0D1117)))
    val progress = Brush.horizontalGradient(listOf(Color(0xFF2D8DE0), Color(0xFF9FD8FF)))
}

fun serviceColor(id: Int) = when (id) { 1 -> Color(0xFFE85D00); 100 -> Color(0xFF7B4FD6); 200 -> Color(0xFF2E8B3E); else -> Color(0xFFD4231A) }
fun serviceBrush(id: Int) = when (id) { 1 -> Gloss.orange; 100 -> Gloss.purple; 200 -> Gloss.green; else -> Gloss.red }

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF3E9BEA),
            background = Aero.bgDeep,
            surface = Aero.bg,
            onSurface = Aero.text,
            onBackground = Aero.text,
            surfaceVariant = Color(0xFF1F2731),
            onSurfaceVariant = Aero.dim,
        ),
        content = content,
    )
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
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(brush)
            .border(1.dp, Color.Black, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
fun Orb(icon: ImageVector, label: String, diameter: Dp, primary: Boolean = false, onClick: () -> Unit) {
    val colors = if (primary) listOf(Color(0xFFD8F0FF), Color(0xFF3E9BEA), Color(0xFF0E4C90), Color(0xFF062A52))
    else listOf(Color(0xFF7A8796), Color(0xFF2A323D), Color(0xFF11161C))
    Box(
        Modifier
            .size(diameter)
            .drawBehind {
                if (primary) drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0x995AB4FF), Color.Transparent), center = center, radius = this.size.minDimension * 0.8f),
                    radius = this.size.minDimension * 0.8f,
                )
            }
            .clip(CircleShape)
            .drawBehind {
                drawCircle(brush = Brush.radialGradient(colors, center = Offset(this.size.width / 2f, this.size.height * 0.28f), radius = this.size.width * 0.85f))
                drawOval(
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.04f)), 0f, this.size.height * 0.5f),
                    topLeft = Offset(this.size.width * 0.16f, this.size.height * 0.04f),
                    size = Size(this.size.width * 0.68f, this.size.height * 0.46f),
                )
            }
            .border(1.dp, Color.Black, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(diameter * 0.42f))
    }
}

@Composable
fun ModeToggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(52.dp, 46.dp)
            .clip(shape)
            .background(if (on) Gloss.blue else Gloss.button)
            .border(1.dp, Color.Black, shape)
            .clickable(role = Role.Switch, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (on) Color.White else Aero.dim, modifier = Modifier.size(22.dp))
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
        val pill = RoundedCornerShape(50)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(pill).background(Color(0xFF05080B)).border(1.dp, Color.Black, pill))
        Box(Modifier.fillMaxWidth(v).height(6.dp).clip(pill).background(Gloss.progress))
        val k = 18.dp
        Box(
            Modifier.offset(x = (maxWidth - k) * v).size(k).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFF9CB3C9))))
                .border(1.dp, Color.Black, CircleShape),
        )
    }
}

@Composable
fun PillGroup(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, selectedBrush: (Int) -> Brush = { Gloss.blue }, height: Dp = 30.dp) {
    val shape = RoundedCornerShape(50)
    Row(Modifier.height(height).clip(shape).border(1.dp, Color.Black, shape)) {
        labels.forEachIndexed { i, label ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
            val sel = i == selected
            Box(
                Modifier.fillMaxHeight().background(if (sel) selectedBrush(i) else Gloss.button)
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (sel) Color.White else Aero.text, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
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
    Box(modifier.clip(shape).background(Color(0xFF232B35)).border(1.dp, Color.Black, shape)) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.03f)))))
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

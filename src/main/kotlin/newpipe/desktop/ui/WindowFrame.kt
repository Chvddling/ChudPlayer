package newpipe.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.foundation.window.WindowDraggableArea
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.MouseEvent
import kotlin.math.max

/*
 * Windows 7 style window frame for ChudPlayer's main window (the window itself is undecorated):
 * a smoked-glass border, a glass title bar you can drag (double-click = maximize),
 * the glossy minimize / maximize / close buttons, and invisible edges to resize.
 */

private val titleShine = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.16f), 0.5f to Color.White.copy(alpha = 0.04f), 0.51f to Color.Transparent, 1f to Color.White.copy(alpha = 0.05f),
)


/** Maximize that respects the taskbar (an undecorated window would otherwise cover it). */
class WindowMaximizer {
    var maximized by androidx.compose.runtime.mutableStateOf(false); private set
    private var restore: Rectangle? = null

    fun toggle(window: java.awt.Window) {
        if (maximized) {
            restore?.let { window.bounds = it }
            maximized = false
        } else {
            restore = window.bounds
            val gc = window.graphicsConfiguration
            val b = gc.bounds
            val ins = Toolkit.getDefaultToolkit().getScreenInsets(gc)
            window.setBounds(b.x + ins.left, b.y + ins.top, b.width - ins.left - ins.right, b.height - ins.top - ins.bottom)
            maximized = true
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FrameWindowScope.GlassWindowFrame(
    title: String,
    maximizer: WindowMaximizer,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val max = maximizer.maximized
    val edge = if (max) 0.dp else 5.dp
    val theme = chud.theme.ThemeState.current
    val glossy = theme.id == "smoked" || theme.id.startsWith("obsidian")
    Box(Modifier.fillMaxSize().background(Gloss.frame).then(if (max) Modifier else Modifier.border(Aero.edgeWidth, Aero.edge))) {
        Column(Modifier.fillMaxSize().padding(start = edge, end = edge, bottom = edge)) {
            // ---------- title bar ----------
            Row(Modifier.fillMaxWidth().height(30.dp).background(theme.titleBar).then(if (glossy) Modifier.background(titleShine) else Modifier)) {
                val titleArea: @Composable () -> Unit = {
                    Row(
                        Modifier.fillMaxSize()
                            .onPointerEvent(PointerEventType.Press) { e ->
                                if ((e.nativeEvent as? MouseEvent)?.clickCount == 2) maximizer.toggle(window)
                            }
                            .padding(start = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // little glowing orb, like the app icon
                        Box(
                            Modifier.size(16.dp).clip(CircleShape)
                                .background(Brush.radialGradient(theme.orb.take(3)))
                                .border(1.dp, Color.Black, CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            title,
                            color = Aero.textBright,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = if (glossy) Shadow(Color.Black, blurRadius = 6f) else null),
                        )
                    }
                }
                if (max) Box(Modifier.weight(1f).fillMaxHeight()) { titleArea() }
                else WindowDraggableArea(Modifier.weight(1f).fillMaxHeight()) { titleArea() }

                // ---------- Windows 7 caption buttons ----------
                Row(Modifier.padding(end = if (max) 2.dp else 4.dp)) {
                    CaptionButton(CaptionKind.Minimize, 28.dp, RoundedCornerShape(bottomStart = 4.dp), onMinimize)
                    CaptionButton(if (max) CaptionKind.Restore else CaptionKind.Maximize, 27.dp, RoundedCornerShape(0.dp)) { maximizer.toggle(window) }
                    CaptionButton(CaptionKind.Close, 46.dp, RoundedCornerShape(bottomEnd = 4.dp), onClose)
                }
            }
            // ---------- the app ----------
            Box(Modifier.weight(1f).fillMaxWidth().border(Aero.edgeWidth, Aero.edge)) { content() }
        }
        if (!max) ResizeEdges(window)
    }
}

private enum class CaptionKind { Minimize, Maximize, Restore, Close }

@Composable
private fun CaptionButton(kind: CaptionKind, width: androidx.compose.ui.unit.Dp, shape: RoundedCornerShape, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val theme = chud.theme.ThemeState.current
    val brush = when {
        kind == CaptionKind.Close -> if (hovered) theme.closeHover else theme.close
        else -> if (hovered) theme.captionHover else theme.caption
    }
    val glossy = theme.id == "smoked" || theme.id.startsWith("obsidian")
    val label = when (kind) {
        CaptionKind.Minimize -> "Minimize"; CaptionKind.Maximize -> "Maximize"; CaptionKind.Restore -> "Restore"; CaptionKind.Close -> "Close"
    }
    Box(
        Modifier
            .size(width, 20.dp)
            .clip(shape)
            .background(brush)
            .border(Aero.edgeWidth.coerceAtMost(1.dp), if (theme.id == "slate") Color.Transparent else Aero.edge, shape)
            .hoverable(hover)
            .clickable(interactionSource = hover, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // glossy highlight inside the button
        if (glossy) Box(Modifier.fillMaxWidth().height(9.dp).align(Alignment.TopCenter).padding(horizontal = 1.dp, vertical = 1.dp).background(Color.White.copy(alpha = 0.10f)))
        Canvas(Modifier.size(12.dp, 10.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 2.2f
            // dark outline first, then white, like Windows 7 glyphs
            fun glyph(color: Color, extra: Float) {
                when (kind) {
                    CaptionKind.Minimize -> drawLine(color, Offset(w * 0.15f, h * 0.85f), Offset(w * 0.85f, h * 0.85f), stroke + extra, StrokeCap.Square)
                    CaptionKind.Maximize -> drawRect(color, Offset(w * 0.1f, h * 0.05f), Size(w * 0.8f, h * 0.85f), style = Stroke(width = 1.8f + extra))
                    CaptionKind.Restore -> {
                        drawRect(color, Offset(w * 0.3f, h * 0.0f), Size(w * 0.6f, h * 0.6f), style = Stroke(width = 1.4f + extra))
                        drawRect(color, Offset(w * 0.05f, h * 0.35f), Size(w * 0.6f, h * 0.6f), style = Stroke(width = 1.6f + extra))
                    }
                    CaptionKind.Close -> {
                        drawLine(color, Offset(w * 0.18f, h * 0.05f), Offset(w * 0.82f, h * 0.95f), stroke + extra, StrokeCap.Round)
                        drawLine(color, Offset(w * 0.82f, h * 0.05f), Offset(w * 0.18f, h * 0.95f), stroke + extra, StrokeCap.Round)
                    }
                }
            }
            if (glossy) glyph(Color(0xCC000000), 1.6f)
            glyph(if (glossy) Color.White else Aero.text, 0f)
        }
    }
}

/** Invisible strips along the edges that resize the window. */
@Composable
private fun BoxScope.ResizeEdges(window: java.awt.Window) {
    val e = 5.dp
    val c = 10.dp
    Box(Modifier.align(Alignment.CenterStart).width(e).fillMaxHeight().resizer(window, Cursor.W_RESIZE_CURSOR, left = true))
    Box(Modifier.align(Alignment.CenterEnd).width(e).fillMaxHeight().resizer(window, Cursor.E_RESIZE_CURSOR, right = true))
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(3.dp).resizer(window, Cursor.N_RESIZE_CURSOR, top = true))
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(e).resizer(window, Cursor.S_RESIZE_CURSOR, bottom = true))
    Box(Modifier.align(Alignment.TopStart).size(c, 4.dp).resizer(window, Cursor.NW_RESIZE_CURSOR, left = true, top = true))
    Box(Modifier.align(Alignment.BottomStart).size(c).resizer(window, Cursor.SW_RESIZE_CURSOR, left = true, bottom = true))
    Box(Modifier.align(Alignment.BottomEnd).size(c).resizer(window, Cursor.SE_RESIZE_CURSOR, right = true, bottom = true))
}

private fun Modifier.resizer(
    window: java.awt.Window,
    cursor: Int,
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = false,
): Modifier = this
    .pointerHoverIcon(PointerIcon(Cursor(cursor)))
    .pointerInput(cursor) {
        var start = Rectangle()
        var mouse = Point()
        detectDragGestures(
            onDragStart = {
                start = window.bounds
                mouse = MouseInfo.getPointerInfo()?.location ?: Point()
            },
        ) { change, _ ->
            change.consume()
            val p = MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
            val dx = p.x - mouse.x
            val dy = p.y - mouse.y
            val minW = 960
            val minH = 600
            var x = start.x
            var y = start.y
            var w = start.width
            var h = start.height
            if (right) w = max(minW, start.width + dx)
            if (bottom) h = max(minH, start.height + dy)
            if (left) { w = max(minW, start.width - dx); x = start.x + start.width - w }
            if (top) { h = max(minH, start.height - dy); y = start.y + start.height - h }
            window.setBounds(x, y, w, h)
        }
    }

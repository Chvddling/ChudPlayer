package newpipe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import newpipe.desktop.player.PlayerController

/**
 * Small always-on-top controller window: the desktop version of NewPipe's background/popup player.
 * Playback keeps going while the main window is minimized.
 */
@Composable
fun MiniPlayerWindow(player: PlayerController, onClose: () -> Unit, onRestore: () -> Unit) {
    val state = rememberWindowState(size = DpSize(520.dp, 168.dp), position = WindowPosition(Alignment.BottomEnd))
    Window(
        onCloseRequest = onClose,
        state = state,
        title = "ChudPlayer – mini player",
        alwaysOnTop = true,
        resizable = false,
    ) {
        AppTheme {
            Column(Modifier.fillMaxSize().background(Gloss.bar)) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val art = player.details?.item?.thumbnail ?: player.current?.thumbnail
                    Box(Modifier.width(150.dp).fillMaxHeight().border(1.dp, Color.Black)) {
                        NetImage(art, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxWidth().height(42.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Transparent))))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LcdDisplay(player, Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OrbButton({ player.previous() }, "Previous", Icons.Filled.SkipPrevious, 28.dp)
                            OrbButton(
                                { player.togglePause() },
                                if (player.paused) "Play" else "Pause",
                                if (player.paused || player.current == null) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                38.dp,
                                primary = true,
                            )
                            OrbButton({ player.next() }, "Next", Icons.Filled.SkipNext, 28.dp)
                            Spacer(Modifier.weight(1f))
                            GlossButton("Open", onRestore)
                        }
                    }
                }
            }
        }
    }
}

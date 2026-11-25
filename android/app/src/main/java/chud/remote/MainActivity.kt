package chud.remote

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import chud.remote.ui.RemoteApp
import chud.remote.ui.RemoteTheme

class MainActivity : ComponentActivity() {
    private val vm: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RemoteTheme { RemoteApp(vm) }
        }
    }

    /** The phone's volume buttons change ChudPlayer's volume (while this app is open). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val volumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (volumeKey && vm.volumeKeys && vm.connected) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                vm.volumeStep(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 5 else -5)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

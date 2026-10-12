package com.tome.reader

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tome.reader.domain.model.AppSettings
import com.tome.reader.ui.navigation.ReaderNavHost
import com.tome.reader.ui.reader.ReaderKeyHandler
import com.tome.reader.ui.theme.TomeTheme

class MainActivity : ComponentActivity() {

    /**
     * Route hardware volume keys to the reader's pager when one is active and the
     * user enabled volume-key paging; otherwise fall through to normal volume.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handler = ReaderKeyHandler.onVolumeKey
        if (handler != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> if (handler(true)) return true
                KeyEvent.KEYCODE_VOLUME_DOWN -> if (handler(false)) return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Swallow the matching key-up so the system volume sound/UI stays suppressed. */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (ReaderKeyHandler.onVolumeKey != null &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ReaderApp).container

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            TomeTheme(theme = settings.theme, accent = settings.accentColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ReaderNavHost(settings = settings)
                }
            }
        }
    }
}

package com.orgista.openstream

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.orgista.openstream.ui.OpenStreamApp
import com.orgista.openstream.ui.OpenStreamTheme

/**
 * Single-activity native OpenStream app. Runs edge-to-edge with the system
 * bars hidden for a clean fullscreen media surface (no tablet taskbar handle
 * on top). Escape is always available three ways: the in-app Exit control,
 * the back gesture, and swiping the bars back transiently — so the kiosk
 * never traps the viewer.
 */
class NativeAppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logs.event("app started")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            OpenStreamTheme {
                OpenStreamApp(onExit = { finish() })
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }
}

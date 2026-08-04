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
        applyKeyExtras()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            OpenStreamTheme {
                OpenStreamApp(onExit = { finish() })
            }
        }
    }

    /**
     * Debug convenience: set the TMDB/OMDb keys from adb instead of typing them
     * on the tablet, e.g.
     *   adb shell am start -n com.orgista.openstream/.NativeAppActivity \
     *     -e tmdbKey <TMDB> -e omdbKey <OMDB>
     * Only honored in debuggable builds.
     */
    private fun applyKeyExtras() {
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        val store = com.orgista.openstream.library.LibraryStore(applicationContext)
        var changed = false
        intent?.getStringExtra("tmdbKey")?.let { store.tmdbApiKey = it; changed = true }
        intent?.getStringExtra("omdbKey")?.let { store.omdbApiKey = it; changed = true }
        if (changed) {
            com.orgista.openstream.library.LibraryCache.clear(applicationContext)
            Logs.event("api keys set via intent extras; cache cleared for re-scan")
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyKeyExtras()
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

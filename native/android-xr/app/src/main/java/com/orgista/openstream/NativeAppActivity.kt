package com.orgista.openstream

import android.app.ActivityManager
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
 * Single-activity native OpenStream app. Runs edge-to-edge with the *status*
 * bar hidden — that removes the tablet taskbar/notification handle the user
 * disliked at the top — while the navigation bar stays live so the one-swipe
 * Home gesture keeps working. Full immersive (hiding the nav bar too) made
 * Android swallow the first upward swipe to reveal the bar transiently, which
 * is exactly why "swipe up to exit" felt broken. Escape is always available:
 * swipe up to Home (back to OpenPanel) and the back gesture from Discover.
 */
class NativeAppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logRecentAbnormalExit()
        Logs.event("app started")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        run { window.navigationBarColor = android.graphics.Color.TRANSPARENT }
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent {
            OpenStreamTheme {
                OpenStreamApp(onExit = { finish() })
            }
        }
    }

    private fun logRecentAbnormalExit() {
        if (Build.VERSION.SDK_INT < 30) return
        val activityManager = getSystemService(ActivityManager::class.java)
        val lastExit = activityManager.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull() ?: return
        when (lastExit.reason) {
            android.app.ApplicationExitInfo.REASON_CRASH,
            android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
            android.app.ApplicationExitInfo.REASON_ANR,
            -> Logs.warn("previous app session ended abnormally (reason ${lastExit.reason})")
            else -> Unit
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        run { window.navigationBarColor = android.graphics.Color.TRANSPARENT }
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { controller ->
                // Hide the status bar only (kills the top notification/taskbar
                // handle) and leave the navigation bar shown so the gesture
                // Home swipe still works in a single motion — hiding the nav
                // bar put the app in immersive-sticky, where the first upward
                // swipe just peeks the bar back instead of going Home, which
                // is what broke "swipe up to exit".
                controller.hide(WindowInsets.Type.statusBars())
                controller.show(WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }
}

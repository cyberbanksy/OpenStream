package com.orgista.openstream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.orgista.openstream.ui.OpenStreamApp
import com.orgista.openstream.ui.OpenStreamTheme

/**
 * Single-activity native OpenStream app. System bars stay available while
 * browsing so leaving the app always works; only the player goes immersive.
 */
class NativeAppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logs.event("app started")
        setContent {
            OpenStreamTheme {
                OpenStreamApp()
            }
        }
    }
}

package com.orgista.openstream

import android.util.Log

/**
 * One logging surface for the whole app so a test session reads coherently
 * in logcat: `adb logcat -s OpenStream`. Messages must never include
 * credentials; SMB URLs are logged host/share only.
 */
object Logs {
    private const val TAG = "OpenStream"

    fun event(message: String) {
        Log.i(TAG, message)
    }

    fun warn(message: String, error: Throwable? = null) {
        Log.w(TAG, message, error)
    }

    fun error(message: String, error: Throwable? = null) {
        Log.e(TAG, message, error)
    }
}

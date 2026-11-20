package com.moodmix.app.ui.util

import android.app.Activity
import android.os.Build
import android.view.Display

@Suppress("DEPRECATION")
fun Activity.requestHighRefreshRate() {
    val currentDisplay: Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
    val display = currentDisplay ?: return
    val current = display.mode

    // Highest refresh rate at the SAME resolution (avoids a resolution switch)
    val best = display.supportedModes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .maxByOrNull { it.refreshRate } ?: return

    val params = window.attributes
    params.preferredDisplayModeId = best.modeId
    window.attributes = params
}
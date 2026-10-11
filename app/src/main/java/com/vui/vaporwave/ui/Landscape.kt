package com.vui.vaporwave.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * True while the device is in landscape. MainActivity declares `configChanges` for orientation,
 * so rotating recomposes readers of this instead of recreating the activity.
 */
@Composable
fun rememberIsLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

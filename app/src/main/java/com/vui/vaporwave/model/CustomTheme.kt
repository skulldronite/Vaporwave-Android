package com.vui.vaporwave.model

import androidx.compose.runtime.Immutable

/**
 * The user's custom theme colour, kept separately for light and dark mode. A null colour means that
 * mode has none set and just uses the normal default theme. Colours are opaque ARGB ints.
 */
@Immutable
data class CustomThemeSettings(
    val lightColor: Int? = null,
    val darkColor: Int? = null
) {
    fun colorFor(dark: Boolean): Int? = if (dark) darkColor else lightColor
}

package com.vui.vaporwave.ui.screens

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TextButton
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.ColorLens
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vui.vaporwave.model.ThemeMode
import com.vui.vaporwave.ui.rememberIsLandscape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    useVaporwaveTheme: Boolean,
    onToggleVaporwaveTheme: (Boolean) -> Unit,
    useMaterialYou: Boolean,
    onToggleMaterialYou: (Boolean) -> Unit,
    useOledBlack: Boolean,
    onToggleOledBlack: (Boolean) -> Unit,
    useArtworkColors: Boolean,
    onToggleArtworkColors: (Boolean) -> Unit,
    /** The custom theme colour set for whichever mode (light/dark) is showing now, if any. */
    customThemeColor: Int?,
    /** Whether the *other* mode has a custom colour too -- shown as a small dot on the icon. */
    otherModeHasCustomColor: Boolean,
    onOpenColourPicker: () -> Unit,
    /** The "Now Playing keeps using album art colours" note (see MusicViewModel). */
    artworkColorsNoteVisible: Boolean,
    onDismissArtworkColorsNote: () -> Unit,
    skipSplashScreen: Boolean,
    onToggleSkipSplashScreen: (Boolean) -> Unit,
    // Whether dark colors are actually showing right now -- resolves ThemeMode.SYSTEM against
    // the device's current day/night state, since OLED black only makes sense while dark is
    // actually on screen, including when that's because the system itself is in dark mode.
    isEffectivelyDark: Boolean,
    hasLyricsFolderAccess: Boolean,
    onGrantLyricsFolderAccess: () -> Unit,
    onRescanLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val materialYouAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        modifier = modifier
            .fillMaxSize()
            // Padding goes *inside* the scroll: outside it, the 16dp margin clipped scrolled
            // content short of the top and bottom edges, leaving a visible opaque strip there.
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Appearance & Personalization Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            val isLandscape = rememberIsLandscape()
            val appearanceLeft: @Composable ColumnScope.() -> Unit = {
                // Landscape gives this side half the card to itself, so its text and the theme
                // picker are larger to fill it.
                Text(
                    text = "Appearance and Personalization",
                    style = if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 16.dp))

                // Theme mode: System Default / Light / Dark
                Text(
                    text = "Theme",
                    style = if (isLandscape) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(
                        ThemeMode.SYSTEM to "System",
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark"
                    )
                    options.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            modifier = if (isLandscape) Modifier.height(48.dp) else Modifier
                        ) {
                            Text(
                                text = label,
                                style = if (isLandscape) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
            val appearanceRight: @Composable ColumnScope.() -> Unit = {
                // Theme -- opens the colour picker. Dimmed and inert while Neon Theme or Material You
                // is on, since either one supplies its own palette that would override the colour.
                val customThemeLocked = useVaporwaveTheme || (materialYouAvailable && useMaterialYou)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !customThemeLocked, onClick = onOpenColourPicker),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ColorLens,
                        contentDescription = null,
                        tint = if (customThemeLocked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Make Vaporwave yours, and make it look how you want!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SpectrumIcon(
                        selectedColor = customThemeColor?.let { Color(it) },
                        otherModeSet = otherModeHasCustomColor,
                        enabled = !customThemeLocked
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Neon Theme
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Neon Theme",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "1980's color theme! Neon Pink.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useVaporwaveTheme,
                        onCheckedChange = onToggleVaporwaveTheme
                    )
                }

                // Material You -- hidden outright on pre-Android 12 devices, where the API this
                // relies on (dynamicDarkColorScheme/dynamicLightColorScheme) doesn't exist at
                // all, rather than showing a toggle that can never do anything on that device.
                if (materialYouAvailable) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Dimmed/disabled while Neon Theme is on, since Neon's fixed palette
                    // overrides whatever Material You would otherwise pick.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (useVaporwaveTheme) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = "Material You",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (useVaporwaveTheme) {
                                    "Disabled while Neon Theme is on"
                                } else {
                                    "Using wallpaper-based dynamic colors"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useMaterialYou,
                            onCheckedChange = onToggleMaterialYou,
                            enabled = !useVaporwaveTheme
                        )
                    }
                }

                // OLED Black -- only shown while dark is actually the effective appearance,
                // animated in/out (rather than an instant if()) since ThemeMode switching or the
                // system's day/night state changing can toggle isEffectivelyDark at any time.
                AnimatedVisibility(visible = isEffectivelyDark) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contrast,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = "OLED Theme",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Pure black backgrounds to save power on OLED screens",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useOledBlack,
                                onCheckedChange = onToggleOledBlack
                            )
                        }
                    }
                }

                // Album Art Colors -- Now Playing's glow, play button and active toggles take the
                // playing song's artwork colour. Dimmed while Neon Theme or Material You is on, since
                // either one supplies its own fixed palette and overrides it.
                val artworkColorsLocked = useVaporwaveTheme || (materialYouAvailable && useMaterialYou)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ColorLens,
                        contentDescription = null,
                        tint = if (artworkColorsLocked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Album Art Colors",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                useVaporwaveTheme -> "Disabled while Neon Theme is on"
                                materialYouAvailable && useMaterialYou -> "Disabled while Material You is on"
                                else -> "Tints Now Playing with colors from the song's artwork"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useArtworkColors,
                        onCheckedChange = onToggleArtworkColors,
                        enabled = !artworkColorsLocked
                    )
                }
                AnimatedVisibility(
                    visible = artworkColorsNoteVisible && !artworkColorsLocked && useArtworkColors,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Now Playing uses the song's album art colors, not your theme color. " +
                                "Turn Album Art Colors off to use your theme color there.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissArtworkColorsNote) { Text("Got it") }
                    }
                }

                // Skip Intro Animation -- always shown (unlike OLED Theme above, not tied to
                // dark/light), so it isn't wrapped in that AnimatedVisibility.
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Skip Intro Animation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Go straight to your library, skips the intro splash screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = skipSplashScreen,
                        onCheckedChange = onToggleSkipSplashScreen
                    )
                }
            }

            if (isLandscape) {
                // Landscape: title + theme picker on the left, the toggles on the right.
                Row(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.weight(0.5f), content = appearanceLeft)
                    Spacer(modifier = Modifier.width(24.dp))
                    Column(modifier = Modifier.weight(0.5f), content = appearanceRight)
                }
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    appearanceLeft()

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    appearanceRight()
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Library Management Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Media Library Scan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Re-query MediaStore for newly added audio tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onRescanLibrary,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Rescan Now")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lyrics Folder Access Card -- some devices/OEM skins (confirmed on a Samsung Android 10
        // device with SD card storage) block reading a sibling .lrc file entirely without this
        // one-time SAF grant; see MusicRepository.fetchLyrics for the full fallback chain.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Lyrics Folder Access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (hasLyricsFolderAccess) {
                                "Granted -- synced (.lrc) lyrics can be found reliably"
                            } else {
                                "Needed on some devices to find .lrc lyric files next to your music"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onGrantLyricsFolderAccess,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (hasLyricsFolderAccess) "Change Folder" else "Grant Access")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Read from the installed package's own manifest-declared versionName rather
                    // than a hardcoded string here, so this can't quietly drift out of sync with
                    // the actual build the user is running.
                    val context = LocalContext.current
                    val versionName = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        } catch (e: PackageManager.NameNotFoundException) {
                            null
                        }
                    }
                    Text(
                        text = "Version ${versionName ?: "unknown"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * A colour-wheel icon: a rainbow ring, filled in the middle with the chosen colour once there is
 * one. A small dot at the corner means the other mode (light/dark) has a colour of its own too.
 */
@Composable
private fun SpectrumIcon(selectedColor: Color?, otherModeSet: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    val rainbow = remember {
        Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
    }
    val outline = MaterialTheme.colorScheme.outline
    val dotColor = MaterialTheme.colorScheme.primary
    // The canvas is a little bigger than the wheel, with the wheel kept to its top-left, so the small
    // "other mode" dot at the bottom-right corner has room and isn't cut off at the canvas edge.
    Canvas(modifier = modifier.size(40.dp).graphicsLayer { alpha = if (enabled) 1f else 0.38f }) {
        val wheelRadius = 18.dp.toPx()
        val wheelCenter = Offset(wheelRadius, wheelRadius)
        drawCircle(brush = rainbow, radius = wheelRadius, center = wheelCenter)
        val inner = wheelRadius * 0.55f
        drawCircle(color = selectedColor ?: Color.White, radius = inner, center = wheelCenter)
        drawCircle(color = outline, radius = inner, center = wheelCenter, style = Stroke(width = 1.dp.toPx()))
        if (otherModeSet) {
            val dotCenter = Offset(33.dp.toPx(), 33.dp.toPx())
            drawCircle(color = Color.Black, radius = 6.dp.toPx(), center = dotCenter)
            drawCircle(color = dotColor, radius = 4.5.dp.toPx(), center = dotCenter)
        }
    }
}

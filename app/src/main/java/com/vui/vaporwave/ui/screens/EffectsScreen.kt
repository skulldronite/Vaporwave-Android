package com.vui.vaporwave.ui.screens

import android.media.audiofx.Equalizer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vui.vaporwave.ui.rememberIsLandscape
import java.util.Locale
import kotlin.math.abs

private fun approxEquals(a: Float, b: Float, epsilon: Float = 0.01f) = abs(a - b) < epsilon

@Composable
fun EffectsScreen(
    currentSpeed: Float,
    currentPitch: Float,
    isSlowedAndReverb: Boolean,
    onSetSpeedAndPitch: (Float, Float) -> Unit,
    isEqEnabled: Boolean,
    eqBandGains: List<Float>,
    onSetEqualizer: (Boolean, List<Float>) -> Unit,
    modifier: Modifier = Modifier
) {
    // Local drag state gives the slider immediate visual feedback while dragging, without
    // flooding ExoPlayer's Sonic audio processor with a playbackParameters update on every
    // touch frame -- the controller is only touched once the drag settles.
    var speedDrag by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
    var pitchDrag by remember(currentPitch) { mutableFloatStateOf(currentPitch) }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Padding goes *inside* the scroll: outside it, the 16dp margin clipped scrolled
            // content short of the top and bottom edges, leaving a visible opaque strip there.
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val isLandscape = rememberIsLandscape()

        // Live Custom Controls (speed + pitch sliders)
        val speedPitchCard: @Composable (Modifier) -> Unit = { cardModifier ->
            Card(
                modifier = cardModifier,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Playback Speed Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Playback Tempo / Speed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isSpeedAtDefault = approxEquals(speedDrag, 1.0f)
                            // Alpha/scale animated in place via graphicsLayer (a draw-phase transform)
                            // rather than AnimatedVisibility's default expand/shrink -- the IconButton
                            // always occupies its 28dp of layout space, so the Row's width never
                            // changes as the icon fades in/out, which is what was causing the tile to
                            // visibly judder-resize on every appear/disappear.
                            val resetAlpha by animateFloatAsState(
                                targetValue = if (isSpeedAtDefault) 0f else 1f,
                                label = "speed_reset_alpha"
                            )
                            val resetScale by animateFloatAsState(
                                targetValue = if (isSpeedAtDefault) 0.6f else 1f,
                                label = "speed_reset_scale"
                            )
                            IconButton(
                                onClick = { onSetSpeedAndPitch(1.0f, pitchDrag) },
                                enabled = !isSpeedAtDefault,
                                modifier = Modifier
                                    .size(28.dp)
                                    .graphicsLayer {
                                        alpha = resetAlpha
                                        scaleX = resetScale
                                        scaleY = resetScale
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset speed to 1.00x",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "%.2fx", speedDrag),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Slider(
                        value = speedDrag,
                        onValueChange = { speedDrag = it },
                        onValueChangeFinished = { onSetSpeedAndPitch(speedDrag, pitchDrag) },
                        valueRange = 0.5f..2.0f,
                        steps = 29, // 0.05 increments
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Playback Pitch Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tone & Pitch Shifter",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isPitchAtDefault = approxEquals(pitchDrag, 1.0f)
                            val resetAlpha by animateFloatAsState(
                                targetValue = if (isPitchAtDefault) 0f else 1f,
                                label = "pitch_reset_alpha"
                            )
                            val resetScale by animateFloatAsState(
                                targetValue = if (isPitchAtDefault) 0.6f else 1f,
                                label = "pitch_reset_scale"
                            )
                            IconButton(
                                onClick = { onSetSpeedAndPitch(speedDrag, 1.0f) },
                                enabled = !isPitchAtDefault,
                                modifier = Modifier
                                    .size(28.dp)
                                    .graphicsLayer {
                                        alpha = resetAlpha
                                        scaleX = resetScale
                                        scaleY = resetScale
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset pitch to 1.00x",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "%.2fx", pitchDrag),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Slider(
                        value = pitchDrag,
                        onValueChange = { pitchDrag = it },
                        onValueChangeFinished = { onSetSpeedAndPitch(speedDrag, pitchDrag) },
                        valueRange = 0.5f..1.5f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }
        }

        if (isLandscape) {
            // Landscape: presets (title, then bigger buttons underneath) on the left, the
            // tempo/pitch sliders on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(0.5f)) {
                    Text(
                        text = "Presets",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Landscape has room for more presets than the three portrait shows: a grid of
                    // six, two per row. Speed and pitch always move together, same as the originals.
                    val landscapePresets = listOf(
                        Triple("0.7x", 0.7f, MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary),
                        Triple("0.85x", 0.85f, MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary),
                        Triple("1x", 1.0f, MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary),
                        Triple("1.1x", 1.1f, MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary),
                        Triple("1.25x", 1.25f, MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary),
                        Triple("1.5x", 1.5f, MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        landscapePresets.chunked(2).forEach { rowPresets ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowPresets.forEach { (label, value, colors) ->
                                    CompactPresetButton(
                                        value = label,
                                        width = Dp.Unspecified,
                                        selected = approxEquals(currentSpeed, value) && approxEquals(currentPitch, value),
                                        containerColor = colors.first,
                                        contentColor = colors.second,
                                        onClick = { onSetSpeedAndPitch(value, value) },
                                        modifier = Modifier.weight(1f),
                                        height = 44.dp,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
                speedPitchCard(Modifier.weight(0.5f))
            }
        } else {
            // Preset Quick Action Buttons -- thin pills sitting directly next to the title (not
            // pushed to the row's trailing edge), each exactly as wide as the "Presets" text itself.
            var titleWidthPx by remember { mutableIntStateOf(0) }
            val titleWidthDp = with(LocalDensity.current) { titleWidthPx.toDp() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.onGloballyPositioned { titleWidthPx = it.size.width }
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Preset 1: Vaporwave Classic
                    CompactPresetButton(
                        value = "0.85x",
                        width = titleWidthDp,
                        selected = approxEquals(currentSpeed, 0.85f) && approxEquals(currentPitch, 0.85f),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        onClick = { onSetSpeedAndPitch(0.85f, 0.85f) }
                    )

                    // Preset 3: Normal 1.0x
                    CompactPresetButton(
                        value = "1x",
                        width = titleWidthDp,
                        selected = approxEquals(currentSpeed, 1.0f) && approxEquals(currentPitch, 1.0f),
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        onClick = { onSetSpeedAndPitch(1.0f, 1.0f) }
                    )

                    // Preset 2: Nightcore Sped Up
                    CompactPresetButton(
                        value = "1.25x",
                        width = titleWidthDp,
                        selected = approxEquals(currentSpeed, 1.25f) && approxEquals(currentPitch, 1.25f),
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        onClick = { onSetSpeedAndPitch(1.25f, 1.25f) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            speedPitchCard(Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(16.dp))

        EqualizerCard(
            isEnabled = isEqEnabled,
            bandGains = eqBandGains,
            onSetEqualizer = onSetEqualizer
        )
    }
}

/** The device's real EQ capabilities -- fixed properties of its DSP, not of any playback session. */
private data class EqualizerBandInfo(
    val bandCount: Int,
    val minGainDb: Float,
    val maxGainDb: Float,
    val centerFreqsHz: List<Int>
)

/**
 * A tile underneath the tempo/pitch card for a real, hardware-backed equalizer -- attaches
 * android.media.audiofx.Equalizer to the playback session inside VaporwavePlaybackService (see
 * that file's comment for why it can't live here) via a custom session command. Off by default;
 * enabling it warns that layering this on top of the device's own OEM sound enhancements can
 * cause glitches, since both are independent DSP effects fighting over the same audio session.
 */
@Composable
private fun EqualizerCard(
    isEnabled: Boolean,
    bandGains: List<Float>,
    onSetEqualizer: (Boolean, List<Float>) -> Unit,
    modifier: Modifier = Modifier
) {
    // audioSessionId 0 is a valid "unattached" session purely for reading capabilities (band
    // count, gain range, center frequencies) -- these are fixed properties of the device's DSP,
    // not of any particular playback session, so there's no need to wait for real playback to
    // query them. Most devices report 5-6 bands, not literally 10 -- android.media.audiofx
    // .Equalizer's band layout is fixed by the device/OEM's DSP and can't be subdivided further,
    // so this adapts to however many the device actually has rather than faking extra sliders
    // that wouldn't do anything distinct.
    val bandInfo = remember {
        var eq: Equalizer? = null
        try {
            eq = Equalizer(0, 0)
            val range = eq.bandLevelRange
            EqualizerBandInfo(
                bandCount = eq.numberOfBands.toInt(),
                minGainDb = range[0] / 100f,
                maxGainDb = range[1] / 100f,
                centerFreqsHz = (0 until eq.numberOfBands).map { eq.getCenterFreq(it.toShort()) / 1000 }
            )
        } catch (e: Exception) {
            null
        } finally {
            eq?.release()
        }
    }

    var showWarningDialog by remember { mutableStateOf(false) }

    // Local working copy for immediate slider feedback, matching the speed/pitch sliders' own
    // drag-locally-commit-on-release pattern above. Keyed on bandInfo (computed once) rather than
    // on bandGains itself -- this screen is also mounted, invisibly, in MainActivity's 0dp
    // pre-warm box before the persisted gains finish loading from disk, and re-keying on bandGains
    // would otherwise reset this list back to flat zeros the moment that late load lands, wiping
    // out whatever the user had just been dragging in the real, visible instance.
    val workingGains = remember(bandInfo) {
        mutableStateListOf<Float>().apply {
            val count = bandInfo?.bandCount ?: 0
            addAll(if (bandGains.size == count) bandGains else List(count) { 0f })
        }
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = { Text("Enable Equalizer?") },
            text = {
                Text(
                    "Using this equalizer on top of your device's built-in (OEM) sound " +
                        "enhancements could cause audio glitches or unexpected behavior. If you " +
                        "notice issues, try disabling your device's own sound effects first."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWarningDialog = false
                    onSetEqualizer(true, workingGains.toList())
                }) {
                    Text("Enable Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val isLandscape = rememberIsLandscape()

    val headerContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (bandInfo != null) "${bandInfo.bandCount}-Band Equalizer" else "Equalizer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { turningOn ->
                    if (turningOn) {
                        showWarningDialog = true
                    } else {
                        onSetEqualizer(false, workingGains.toList())
                    }
                },
                enabled = bandInfo != null
            )
        }
    }
    val bandsContent: @Composable () -> Unit = bands@{
        val info = bandInfo ?: return@bands
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (band in 0 until info.bandCount) {
                val freqHz = info.centerFreqsHz.getOrElse(band) { 0 }
                val freqLabel = if (freqHz >= 1000) "${freqHz / 1000}k" else "$freqHz"

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Same animate-in-place-via-graphicsLayer treatment as the speed/pitch
                    // reset icons above -- always occupies its layout space so appearing
                    // doesn't judder the surrounding column, only fades/scales when the band
                    // actually drifts off its untouched 0dB point.
                    val isBandAtDefault = approxEquals(workingGains[band], 0f)
                    val resetAlpha by animateFloatAsState(
                        targetValue = if (isBandAtDefault) 0f else 1f,
                        label = "eq_band_reset_alpha"
                    )
                    val resetScale by animateFloatAsState(
                        targetValue = if (isBandAtDefault) 0.6f else 1f,
                        label = "eq_band_reset_scale"
                    )
                    IconButton(
                        onClick = {
                            workingGains[band] = 0f
                            onSetEqualizer(isEnabled, workingGains.toList())
                        },
                        enabled = !isBandAtDefault,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                alpha = resetAlpha
                                scaleX = resetScale
                                scaleY = resetScale
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset band to 0dB",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%+.0f", workingGains[band]),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = if (isEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(110.dp)
                    ) {
                        VerticalEqSlider(
                            value = workingGains[band],
                            onValueChange = { workingGains[band] = it },
                            onValueChangeFinished = { onSetEqualizer(isEnabled, workingGains.toList()) },
                            valueRange = info.minGainDb..info.maxGainDb,
                            enabled = isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.tertiary,
                                activeTrackColor = MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        text = freqLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (isLandscape) {
            // Landscape: title + switch on the left, the band sliders on the right.
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(0.36f)) {
                    headerContent()
                    if (bandInfo == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Not supported on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (bandInfo != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(modifier = Modifier.weight(0.64f)) {
                        bandsContent()
                    }
                }
            }
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                headerContent()

                if (bandInfo == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Not supported on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    bandsContent()
                }
            }
        }
    }
}

/**
 * A vertical slider -- Compose's [Slider] is horizontal-only, so this rotates one 90 degrees via
 * [graphicsLayer] and swaps its measured width/height with a custom [Modifier.layout] so the
 * rotated slider still reports (and is placed at) the correct vertical footprint in its parent,
 * rather than the unrotated horizontal footprint Compose would otherwise measure it at.
 */
@Composable
private fun VerticalEqSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    colors: SliderColors,
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        enabled = enabled,
        colors = colors,
        modifier = modifier
            .graphicsLayer {
                rotationZ = 270f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            }
    )
}

/**
 * A thin preset pill, exactly [width] wide (matching the "Presets" title) and a fixed short
 * height -- built on a plain [Surface] rather than M3's [Button], since Button enforces a 58dp
 * minimum width via `defaultMinSize` that a narrower explicit width can't override.
 */
@Composable
private fun CompactPresetButton(
    value: String,
    width: Dp,
    selected: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 20.dp,
    fontSize: TextUnit = 10.sp
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier)
            .height(height),
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) containerColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) contentColor else MaterialTheme.colorScheme.onSurface
    ) {
        // fillMaxSize -- without it, this Box only wraps the Text's own size instead of the
        // Surface's full bounds, so contentAlignment = Center had nothing to center within and
        // the text sat wherever the Surface's own default placement put it.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = fontSize,
                lineHeight = fontSize,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

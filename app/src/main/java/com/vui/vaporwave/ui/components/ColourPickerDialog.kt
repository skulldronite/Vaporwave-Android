package com.vui.vaporwave.ui.components

import kotlin.math.roundToInt
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vui.vaporwave.model.CustomThemeSettings
import com.vui.vaporwave.ui.rememberIsLandscape

private const val FIXED_SATURATION = 0.65f
private const val FIXED_VALUE = 0.85f

/** What a mode shows in the picker before the user has set a colour for it. */
private val DefaultSeed = Color(0xFF6750A4)

/** One mode's (light or dark) working copy while the picker is open. */
private class ModeDraft(initialColor: Int?) {
    var isSet by mutableStateOf(initialColor != null)
    var hue by mutableStateOf(0f)

    init {
        setFromColor(initialColor ?: DefaultSeed.toArgbInt())
    }

    // Only the hue is chosen; saturation and brightness are fixed at values that build a good theme.
    // Free saturation/brightness made it easy to pick near-black, near-white or washed-out colours,
    // which gave muddy or hard-to-read themes.
    val colorInt: Int
        get() = android.graphics.Color.HSVToColor(floatArrayOf(hue, FIXED_SATURATION, FIXED_VALUE))

    /** Takes just the hue of [argb]; a grey (which has no hue to speak of) leaves the hue as it was. */
    fun setFromColor(argb: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        if (hsv[1] >= 0.08f) hue = hsv[0]
    }
}

private fun Color.toArgbInt(): Int =
    android.graphics.Color.argb(255, (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/**
 * Picks the theme colour: a hue slider, a hex field, the last colours used, and separate Light and
 * Dark tabs. "Use" saves both tabs; "Cancel" discards edits to both; "Reset" clears the current
 * tab so that mode goes back to the default theme. The card is drawn from the app's own theme, so
 * it follows light, dark and OLED on its own.
 */
@Composable
fun ColourPickerDialog(
    initial: CustomThemeSettings,
    startOnDark: Boolean,
    recentColors: List<Int>,
    onUse: (CustomThemeSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val light = remember { ModeDraft(initial.lightColor) }
    val dark = remember { ModeDraft(initial.darkColor) }
    var darkTab by remember { mutableStateOf(startOnDark) }
    val draft = if (darkTab) dark else light
    val isLandscape = rememberIsLandscape()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = if (isLandscape) 24.dp else 16.dp, vertical = 16.dp)
                .fillMaxWidth(if (isLandscape) 0.92f else 1f)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Text(
                    text = "Colour Picker",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryTabRow(
                    selectedTabIndex = if (darkTab) 1 else 0,
                    containerColor = Color.Transparent
                ) {
                    Tab(selected = !darkTab, onClick = { darkTab = false }, text = { Text("Light") })
                    Tab(selected = darkTab, onClick = { darkTab = true }, text = { Text("Dark") })
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Driven by one animated number rather than by the layout system's own size animations:
                // the height grows from nothing while the text fades in, and does the reverse when a
                // colour is picked. (animateContentSize/AnimatedVisibility did nothing visible here in
                // landscape, where the dialog is already as tall as the screen allows.)
                val messageProgress by animateFloatAsState(
                    targetValue = if (draft.isSet) 0f else 1f,
                    animationSpec = tween(300),
                    label = "colour_picker_message"
                )
                Text(
                    text = "You haven't selected a colour yet. Selecting one, will change the theme of Vaporwave.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = messageProgress }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minHeight = 0))
                            val fullHeight = placeable.height + 12.dp.roundToPx()
                            layout(placeable.width, (fullHeight * messageProgress).roundToInt()) {
                                placeable.place(0, 0)
                            }
                        }
                )

                if (isLandscape) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(modifier = Modifier.weight(1f)) { PickerControls(draft) }
                        Column(modifier = Modifier.weight(1f)) {
                            PickerExtras(draft, recentColors)
                            Spacer(modifier = Modifier.height(12.dp))
                            PickerButtons(
                                onReset = { draft.isSet = false; draft.setFromColor(DefaultSeed.toArgbInt()) },
                                onCancel = onDismiss,
                                onUse = { onUse(light.toSettings(dark)) }
                            )
                        }
                    }
                } else {
                    PickerControls(draft)
                    Spacer(modifier = Modifier.height(16.dp))
                    PickerExtras(draft, recentColors)
                    Spacer(modifier = Modifier.height(16.dp))
                    PickerButtons(
                        onReset = { draft.isSet = false; draft.setFromColor(DefaultSeed.toArgbInt()) },
                        onCancel = onDismiss,
                        onUse = { onUse(light.toSettings(dark)) }
                    )
                }
            }
        }
    }
}

private fun ModeDraft.toSettings(dark: ModeDraft) = CustomThemeSettings(
    lightColor = if (isSet) colorInt else null,
    darkColor = if (dark.isSet) dark.colorInt else null
)

/** The square, the hue slider and the preview + hex field. */
@Composable
private fun PickerControls(draft: ModeDraft) {
    HueSlider(draft)
    Spacer(modifier = Modifier.height(16.dp))
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(draft.colorInt))
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    )
}

/** The recently used colours. */
@Composable
private fun PickerExtras(draft: ModeDraft, recentColors: List<Int>) {
    Text(
        text = "Recent",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(9) { index ->
            val color = recentColors.getOrNull(index)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .clip(CircleShape)
                    .then(
                        if (color != null) {
                            Modifier.background(Color(color))
                        } else {
                            Modifier
                        }
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .then(
                        if (color != null) {
                            Modifier.clickable {
                                draft.setFromColor(color)
                                draft.isSet = true
                            }
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

@Composable
private fun PickerButtons(onReset: () -> Unit, onCancel: () -> Unit, onUse: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onReset) { Text("Reset") }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text("Cancel") }
        TextButton(onClick = onUse) { Text("Use") }
    }
}

@Composable
private fun HueSlider(draft: ModeDraft) {
    val currentDraft by rememberUpdatedState(draft)
    val rainbow = remember { (0..6).map { Color.hsv(it * 60f, 1f, 1f) } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                drawRect(Brush.horizontalGradient(rainbow))
                val x = currentDraft.hue / 360f * size.width
                val center = Offset(x.coerceIn(14.dp.toPx(), size.width - 14.dp.toPx()), size.height / 2f)
                drawCircle(Color.hsv(currentDraft.hue, 1f, 1f), radius = 11.dp.toPx(), center = center)
                drawCircle(Color.White, radius = 11.dp.toPx(), center = center, style = Stroke(width = 2.5.dp.toPx()))
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    fun update(position: Offset) {
                        currentDraft.hue = (position.x / size.width).coerceIn(0f, 1f) * 359.99f
                        currentDraft.isSet = true
                    }
                    val down = awaitFirstDown()
                    down.consume()
                    update(down.position)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull { it.id == down.id }?.let {
                            if (it.pressed) update(it.position)
                            it.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    )
}

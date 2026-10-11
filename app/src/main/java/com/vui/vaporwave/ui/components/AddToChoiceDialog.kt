package com.vui.vaporwave.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vui.vaporwave.model.AudioTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A small card asking whether to add [track] to a playlist or to the queue. Pops in (fade plus a
 * springy scale) and eases back out before acting on a choice, rather than vanishing instantly.
 */
@Composable
fun AddToChoiceDialog(
    track: AudioTrack,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { visible = true }

    // Plays the exit animation, then does whatever was chosen and closes.
    fun closeThen(action: () -> Unit) {
        scope.launch {
            visible = false
            delay(160)
            action()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = { closeThen {} },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { closeThen {} },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(160)) + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                ),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.9f, animationSpec = tween(140))
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .padding(32.dp)
                        .widthIn(max = 320.dp)
                        // Swallows taps on the card itself so only taps *outside* it dismiss.
                        .pointerInput(Unit) { detectTapGestures { } }
                ) {
                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                        Text(
                            text = "Add to\u2026",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ChoiceRow(
                            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                            label = "Playlist",
                            onClick = { closeThen(onAddToPlaylist) }
                        )
                        ChoiceRow(
                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                            label = "Queue",
                            onClick = { closeThen(onAddToQueue) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

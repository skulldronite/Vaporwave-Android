package com.vui.vaporwave.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.vui.vaporwave.model.QueueEntry

/**
 * The playback queue, shown in place of the album art on Now Playing. Rows are in play order; the
 * handle on the right drags a song up or down (and it keeps the song under the finger while the
 * rows around it shift out of the way). Dropping commits the new position to the player.
 */
@Composable
fun QueuePanel(
    entries: List<QueueEntry>,
    onEntryClick: (position: Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    /** Toggling shuffle reorders the whole queue at once -- see [crossing] below. */
    isShuffle: Boolean,
    modifier: Modifier = Modifier
) {
    // Toggling shuffle reorders every row at once. Letting the list animate that itself sent hundreds
    // of rows flying across vast distances at the same time (worst on a cold start, when the queue is
    // the whole library), which looked like two queues laid over each other. So for a moment after the
    // toggle the list's own placement animation is held off, and the rows on screen play a short,
    // bounded cascade of their own instead (see [crossing]).
    val shuffleChangedAt = remember(isShuffle) { System.nanoTime() }
    val crossing = remember { Animatable(1f) }
    var firstShuffleValue by remember { mutableStateOf(true) }
    LaunchedEffect(isShuffle) {
        if (firstShuffleValue) {
            firstShuffleValue = false
        } else {
            crossing.snapTo(0f)
            crossing.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        }
    }
    // A local copy being reordered while a drag is in progress; adopts the real queue whenever
    // that changes (which includes right after a drop is committed) -- except mid-drag, where the
    // local, partly reordered copy has to stay put or the row under the finger would jump.
    var items by remember { mutableStateOf(entries) }
    var draggedPosition by remember { mutableStateOf<Int?>(null) }
    var dragStartPosition by remember { mutableStateOf(0) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var listWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    // How far past the list's edge (or past the handle's column, sideways) the finger can go before
    // the drag counts as having left the touch zone.
    val exitMarginPx = with(density) { 36.dp.toPx() }
    val edgeZonePx = with(density) { 56.dp.toPx() }
    val maxScrollPerFramePx = with(density) { 10.dp.toPx() }

    LaunchedEffect(entries) {
        if (draggedPosition == null) items = entries
    }

    // Follows playback: whenever the playing song changes (e.g. pressing next repeatedly), scrolls
    // just far enough that the row *after* it is fully in view too, so you always see what's coming
    // instead of the playing row sitting stuck at the bottom edge. Left alone while dragging.
    val currentPosition = items.indexOfFirst { it.isCurrent }
    LaunchedEffect(currentPosition) {
        if (currentPosition < 0 || draggedPosition != null) return@LaunchedEffect
        val layout = listState.layoutInfo
        val nextPosition = (currentPosition + 1).coerceAtMost(items.lastIndex)
        val next = layout.visibleItemsInfo.firstOrNull { it.index == nextPosition }
        when {
            // Out of view entirely (a long jump): bring it up near the top, previous song in view.
            next == null -> listState.animateScrollToItem((currentPosition - 1).coerceAtLeast(0))
            next.offset + next.size > layout.viewportEndOffset ->
                listState.animateScrollBy((next.offset + next.size - layout.viewportEndOffset).toFloat())
            next.offset < layout.viewportStartOffset ->
                listState.animateScrollBy((next.offset - layout.viewportStartOffset).toFloat())
        }
    }

    // Opens scrolled to what's playing, with the song before it still in view.
    LaunchedEffect(Unit) {
        val currentPosition = entries.indexOfFirst { it.isCurrent }
        if (currentPosition > 0) listState.scrollToItem((currentPosition - 1).coerceAtLeast(0))
    }

    // Where the dragged row is actually drawn: the finger's offset, held inside the list's own bounds.
    // Without that, dragging up over the "Queue" title (or below the list) slid the row out of the
    // clipped list entirely -- it vanished, and it was impossible to tell where it had gone. The raw
    // offset keeps tracking the finger, so the row picks up again as soon as the finger comes back.
    fun clampedDragOffset(): Float {
        val current = draggedPosition ?: return 0f
        val layout = listState.layoutInfo
        val info = layout.visibleItemsInfo.firstOrNull { it.index == current } ?: return dragOffsetPx
        val min = layout.viewportStartOffset - info.offset.toFloat()
        val max = layout.viewportEndOffset - info.size - info.offset.toFloat()
        return dragOffsetPx.coerceIn(min, max.coerceAtLeast(min))
    }

    // The finger left the touch zone mid-drag: drop the row at the very top (or, if it left out the
    // bottom, the very end) of the queue and scroll there so it's in view.
    fun dropAtEdge(toFirst: Boolean) {
        val current = draggedPosition ?: return
        val reordered = items.toMutableList()
        val song = reordered.removeAt(current)
        val toIndex = if (toFirst) 0 else reordered.size
        reordered.add(toIndex, song)
        items = reordered
        draggedPosition = null
        dragOffsetPx = 0f
        if (dragStartPosition != toIndex) onMove(dragStartPosition, toIndex)
        scope.launch { listState.animateScrollToItem(if (toFirst) 0 else reordered.lastIndex) }
    }

    // Swaps the dragged row with whichever fully visible row its centre has moved over. Only a
    // fully visible row qualifies: a half-visible one at an edge has a clipped, shifting position,
    // and swapping with it made the list jump. Holding the row near the edge scrolls it into view.
    fun swapIfOverAnotherRow() {
        val current = draggedPosition ?: return
        val layout = listState.layoutInfo
        val info = layout.visibleItemsInfo.firstOrNull { it.index == current } ?: return
        val draggedCenter = info.offset + info.size / 2f + clampedDragOffset()
        val target = layout.visibleItemsInfo.firstOrNull {
            it.index != current &&
                it.offset >= layout.viewportStartOffset && it.offset + it.size <= layout.viewportEndOffset &&
                draggedCenter >= it.offset && draggedCenter <= it.offset + it.size
        } ?: return
        val reordered = items.toMutableList()
        reordered.add(target.index, reordered.removeAt(current))
        items = reordered
        draggedPosition = target.index
        // The row now lays out at the target's slot, so the finger-following offset is measured
        // from there instead.
        dragOffsetPx -= (target.offset - info.offset)
    }

    // While a row is held near the top or bottom edge, scrolls the list a little every frame (faster
    // the closer to the edge) -- tied to the frame clock rather than to pointer events, which only
    // arrive when the finger moves and made the scroll stutter. Whatever it scrolls is added to the
    // drag offset so the row stays under the finger instead of sliding away from it.
    val isDragging = draggedPosition != null
    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        while (true) {
            val current = draggedPosition ?: break
            val layout = listState.layoutInfo
            val info = layout.visibleItemsInfo.firstOrNull { it.index == current }
            if (info != null) {
                val center = info.offset + info.size / 2f + dragOffsetPx
                val towardStart = (layout.viewportStartOffset + edgeZonePx - center) / edgeZonePx
                val towardEnd = (center - (layout.viewportEndOffset - edgeZonePx)) / edgeZonePx
                val delta = when {
                    towardStart > 0f -> -towardStart.coerceAtMost(1f) * maxScrollPerFramePx
                    towardEnd > 0f -> towardEnd.coerceAtMost(1f) * maxScrollPerFramePx
                    else -> 0f
                }
                if (delta != 0f) {
                    val consumed = listState.scrollBy(delta)
                    dragOffsetPx += consumed
                    swapIfOverAnotherRow()
                }
            }
            withFrameNanos { }
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Text(
            text = "Queue \u2022 ${items.size}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { listWidthPx = it.width.toFloat() },
            contentPadding = PaddingValues(start = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(items, key = { _, entry -> entry.key }) { position, entry ->
                val isDragged = draggedPosition == position
                val rowModifier = if (isDragged) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = clampedDragOffset() }
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(10.dp))
                } else {
                    (if (System.nanoTime() - shuffleChangedAt > 700_000_000L) Modifier.animateItem() else Modifier)
                        .graphicsLayer {
                            val progress = crossing.value
                            if (progress < 1f) {
                                // A quick cascade down the visible rows: each fades down and shrinks a touch,
                                // then comes back in the new order, a moment after the row above it.
                                val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                                val rowFromTop = (position - first).coerceIn(0, 14)
                                val phase = (progress * 1.6f - rowFromTop * 0.06f).coerceIn(0f, 1f)
                                val dip = kotlin.math.sin(phase * Math.PI.toFloat())
                                alpha = 1f - dip
                                scaleX = 1f - 0.04f * dip
                                scaleY = 1f - 0.04f * dip
                            }
                        }
                }
                QueueRow(
                    entry = entry,
                    onClick = { onEntryClick(position) },
                    modifier = rowModifier,
                    handleModifier = Modifier.pointerInput(entry.key) {
                        detectDragGestures(
                            onDragStart = {
                                draggedPosition = items.indexOfFirst { it.key == entry.key }
                                dragStartPosition = draggedPosition ?: 0
                                dragOffsetPx = 0f
                            },
                            onDragEnd = {
                                val to = draggedPosition
                                draggedPosition = null
                                dragOffsetPx = 0f
                                if (to != null && to != dragStartPosition) onMove(dragStartPosition, to)
                            },
                            onDragCancel = {
                                draggedPosition = null
                                dragOffsetPx = 0f
                                items = entries
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            if (draggedPosition == null) return@detectDragGestures
                            dragOffsetPx += dragAmount.y

                            // Out of the touch zone? Above the list or off to the side sends the row to
                            // the top; below the list sends it to the end.
                            val layout = listState.layoutInfo
                            val info = layout.visibleItemsInfo.firstOrNull { it.index == draggedPosition }
                            if (info != null) {
                                val rawCenter = info.offset + info.size / 2f + dragOffsetPx
                                val handleWidth = size.width.toFloat()
                                val outTop = rawCenter < layout.viewportStartOffset - exitMarginPx
                                val outBottom = rawCenter > layout.viewportEndOffset + exitMarginPx
                                val outSide = change.position.x < -(listWidthPx - handleWidth) - exitMarginPx ||
                                    change.position.x > handleWidth + exitMarginPx
                                if (outTop || outSide) {
                                    dropAtEdge(toFirst = true)
                                    return@detectDragGestures
                                } else if (outBottom) {
                                    dropAtEdge(toFirst = false)
                                    return@detectDragGestures
                                }
                            }
                            swapIfOverAnotherRow()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier
) {
    val track = entry.track
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (entry.isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
            if (track.artworkUri != null) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(track.artworkUri) {
                        ImageRequest.Builder(context).data(track.artworkUri).size(Size(96, 96)).build()
                    },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (entry.isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (entry.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // The drag handle gets a generous touch area of its own, so grabbing it doesn't compete
        // with tapping the row (jump to song) or scrolling the list.
        Box(
            modifier = handleModifier.size(width = 44.dp, height = 44.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

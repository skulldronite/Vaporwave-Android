package com.vui.vaporwave.model

import androidx.compose.runtime.Immutable

/**
 * One row of the playback queue, listed in the order it will actually *play* (so with shuffle on,
 * the shuffled order rather than the underlying list order).
 *
 * [mediaItemIndex] is the item's index in the player's own list, which differs from its position
 * in this (play-ordered) list when shuffled.
 */
@Immutable
data class QueueEntry(
    /**
     * Identity of this queue slot that survives reordering (unlike [mediaItemIndex], which shifts
     * whenever items are moved) -- what the list keys its rows on so a dragged row stays the same
     * row through the drop and the refresh that follows it.
     */
    val key: Long,
    val mediaItemIndex: Int,
    val track: AudioTrack,
    val isCurrent: Boolean,
    /** True when it was put here through "Add to Queue" rather than being part of the original list. */
    val isUserQueued: Boolean
)

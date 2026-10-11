package com.vui.vaporwave.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.vui.vaporwave.model.AlbumSummary
import com.vui.vaporwave.model.ArtistSummary
import com.vui.vaporwave.model.AudioTrack
import com.vui.vaporwave.model.GenreSummary
import com.vui.vaporwave.model.Playlist
import com.vui.vaporwave.ui.LibraryTab
import com.vui.vaporwave.ui.SpotlightCategory
import com.vui.vaporwave.ui.components.ReachabilityPullBox
import com.vui.vaporwave.ui.rememberIsLandscape
import com.vui.vaporwave.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.util.Locale

private val ALPHABET_SCROLLBAR_WIDTH = 28.dp
private val ALPHABET_LANDSCAPE_EDGE_GAP = 12.dp

// How much a list reserves at its end for the alphabet strip -- landscape's strip sits in from
// the edge.
@Composable
private fun alphabetScrollbarReserve(): Dp =
    if (rememberIsLandscape()) ALPHABET_SCROLLBAR_WIDTH + ALPHABET_LANDSCAPE_EDGE_GAP else ALPHABET_SCROLLBAR_WIDTH

// Portrait leaves generous room under the last row; landscape can't spare that much of its short
// viewport (Scaffold already keeps the list clear of the mini player).
@Composable
private fun rememberListBottomPadding(): Dp = if (rememberIsLandscape()) 16.dp else 120.dp

// Minimum slot height per letter on the alphabet strip -- in landscape the strip is shorter than
// 26 of these, so only a spread of its letters is drawn instead of squishing them together.
private val ALPHABET_MIN_LETTER_SLOT = 14.dp
private val PRECISE_SCROLLBAR_WIDTH = 20.dp
// Extra breathing room from the screen edge so the precise scrollbar's touch area doesn't
// overlap the system's edge-swipe-back gesture zone on devices with fullscreen gesture nav.
private val PRECISE_SCROLLBAR_EDGE_INSET = 10.dp

enum class LibrarySortOption(val label: String, val icon: ImageVector) {
    NAME("Name", Icons.Default.SortByAlpha),
    DATE_ADDED("Date Added", Icons.Default.Schedule),
    ARTIST("Artist", Icons.Default.Person),
    TRACK_NUMBER("Track Number", Icons.Default.FormatListNumbered),
    SHORTEST("Shortest First", Icons.Default.ArrowUpward),
    LONGEST("Longest First", Icons.Default.ArrowDownward),
    MOST_TRACKS("Most Tracks", Icons.Default.ArrowDownward),
    LEAST_TRACKS("Least Tracks", Icons.Default.ArrowUpward)
}

@Composable
fun LibraryScreen(
    tracks: List<AudioTrack>,
    allTracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    searchQuery: String,
    favouriteTracks: List<AudioTrack>,
    albums: List<AlbumSummary>,
    genres: List<GenreSummary>,
    artists: List<ArtistSummary>,
    playlists: List<Playlist>,
    recentlyAddedTracks: List<AudioTrack>,
    mostPlayedTracks: List<AudioTrack>,
    recentlyPlayedTracks: List<AudioTrack>,
    currentTab: LibraryTab,
    onTabChanged: (LibraryTab) -> Unit,
    onSwipeDetected: () -> Unit,
    onTrackClick: (AudioTrack, List<AudioTrack>) -> Unit,
    onOpenFileClick: () -> Unit,
    onShufflePlay: (List<AudioTrack>) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSpotlight: (SpotlightCategory) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit = {},
    onAddToPlaylist: (AudioTrack) -> Unit = {},
    onOpenTrackDetails: (AudioTrack) -> Unit = {},
    modifier: Modifier = Modifier,
    // Hoisted (controlled), not owned here -- see ReachabilityPullBox's doc for why. Read by
    // every tab's own ReachabilityPullBox below, so a pull triggered on one tab (or before
    // switching away to another top-level destination entirely) is still reflected once you
    // return, instead of each tab/destination silently starting over from zero.
    reachabilityPullProgress: Float = 0f,
    onPullProgressChanged: (Float) -> Unit = {},
    isReachabilityLocked: Boolean = false,
    // One-shot signal (bump to request, any distinct value) rather than a plain "target tab"
    // value: see the effect below for why a request needs to be its own channel, separate from
    // currentTab, instead of just comparing currentTab against the pager's position.
    scrollToTracksSignal: Int = 0,
    // Hoisted up to MainActivity rather than rememberLazyListState() inside TracksList -- see the
    // comment where it's declared there for why this needs to survive this whole screen being
    // disposed and recomposed (e.g. navigating to FX Studio and back).
    tracksListState: LazyListState = rememberLazyListState()
) {
    val isLandscape = rememberIsLandscape()
    val tabs = remember { LibraryTab.entries.toList() }
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(currentTab)) { tabs.size }

    var hasComposedOnce by remember { mutableStateOf(false) }
    // currentPage, not settledPage: this also drives the top bar's title/dots (via onTabChanged
    // -> the ViewModel -> MainActivity), and settledPage only updates once a scroll/fling fully
    // stops -- noticeably late for that, especially a multi-page animated scroll (see
    // scrollToTracksSignal below), where the title should track the pager sliding through each
    // tab in between, not jump only once it lands.
    LaunchedEffect(pagerState.currentPage) {
        onTabChanged(tabs[pagerState.currentPage])
        // Only treat this as a real page change (not the initial composition) so the
        // swipe hint doesn't get dismissed before the user actually swipes.
        if (hasComposedOnce) {
            onSwipeDetected()
        }
        hasComposedOnce = true
    }

    // MainActivity's back handler jumping to Tracks from a different tab. Deliberately its own
    // signal rather than reacting to currentTab (as an earlier version of this did): the effect
    // above reports every intermediate page a multi-page animateScrollToPage passes through, and
    // since that round-trips back down as this same currentTab prop, keying off currentTab meant
    // this effect kept restarting mid-scroll on its own intermediate reports -- worse the longer
    // the jump (e.g. Albums or Artists back to Tracks crosses more intermediate pages), which cut
    // the animation short partway instead of ever reaching Tracks. A signal that only changes
    // once per real back-press can't be re-triggered by anything the resulting scroll itself
    // reports, so nothing can cut it off partway.
    LaunchedEffect(scrollToTracksSignal) {
        if (scrollToTracksSignal == 0) return@LaunchedEffect
        val targetPage = tabs.indexOf(LibraryTab.TRACKS)
        if (targetPage >= 0 && targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // beyondViewportPageCount = tabs.size - 1 (all tabs): keeps every tab composed permanently, never
        // disposed. A window of 1 was tried first, but it only delays disposal by one hop rather
        // than preventing it -- the window slides with you, so a normal explore-three-tabs-then-
        // return pattern (e.g. Tracks -> Playlists -> Artists -> Tracks) still evicts and
        // cold-rebuilds whichever tab falls furthest behind, which is exactly what was still
        // producing hitches. With only 5 tabs total, the honest way to guarantee every tab stays
        // warm is to keep all of them alive all the time. Trade-off: every tab starts composing
        // and loading its images at cold launch instead of as each is visited, and all 5 tabs'
        // worth of artwork stays resident in memory for the whole session -- offset somewhat by
        // the disk-cached ImageLoader (see VaporwaveApplication), which turns most of that
        // launch-time work into cache hits after the first cold start.
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = tabs.size - 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // Attached per-page (not around the pager) so it only ever sees scroll deltas
            // bubbling from that page's own list -- fully isolated from the pager's own
            // horizontal swipe/settle gesture handling, which lives above this in the tree.
            ReachabilityPullBox(
                pullProgress = reachabilityPullProgress,
                onPullProgressChanged = onPullProgressChanged,
                isPagerScrollInProgress = { pagerState.isScrollInProgress },
                isLocked = isReachabilityLocked,
                isEnabled = !isLandscape,
                modifier = Modifier.fillMaxSize()
            ) {
            when (tabs[page]) {
                LibraryTab.ALBUMS -> {
                    AlbumsList(
                        albums = albums,
                        onAlbumClick = onOpenAlbum,
                        onShuffleClick = { onShufflePlay(allTracks) }
                    )
                }
                LibraryTab.GENRES -> {
                    GenresList(
                        genres = genres,
                        onGenreClick = onOpenGenre,
                        onShuffleClick = { onShufflePlay(allTracks) }
                    )
                }
                LibraryTab.FAVOURITES -> {
                    val context = LocalContext.current
                    SimpleTrackList(
                        tracks = favouriteTracks,
                        currentTrack = currentTrack,
                        emptyIcon = Icons.Default.Favorite,
                        emptyMessage = "No favourites yet.\nTap the heart on Now Playing to add one.",
                        onTrackClick = { track -> onTrackClick(track, favouriteTracks) },
                        onShufflePlay = onShufflePlay,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onShare = { track -> shareTrack(context, track) },
                        onOpenTrackDetails = onOpenTrackDetails
                    )
                }
                LibraryTab.TRACKS -> {
                    val context = LocalContext.current
                    TracksList(
                        tracks = tracks,
                        currentTrack = currentTrack,
                        searchQuery = searchQuery,
                        onTrackClick = { track -> onTrackClick(track, tracks) },
                        onOpenFileClick = onOpenFileClick,
                        onShufflePlay = onShufflePlay,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onShare = { track -> shareTrack(context, track) },
                        onOpenTrackDetails = onOpenTrackDetails,
                        listState = tracksListState
                    )
                }
                LibraryTab.PLAYLISTS -> {
                    PlaylistsList(
                        playlists = playlists,
                        allTracks = allTracks,
                        onPlaylistClick = { onOpenPlaylist(it.id) },
                        recentlyAddedTrack = recentlyAddedTracks.firstOrNull(),
                        mostPlayedTrack = mostPlayedTracks.firstOrNull(),
                        recentlyPlayedTrack = recentlyPlayedTracks.firstOrNull(),
                        onCategoryClick = onOpenSpotlight
                    )
                }
                LibraryTab.ARTISTS -> {
                    ArtistsList(
                        artists = artists,
                        onArtistClick = onOpenArtist,
                        onShuffleClick = { onShufflePlay(allTracks) }
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun TracksList(
    tracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    searchQuery: String,
    onTrackClick: (AudioTrack) -> Unit,
    onOpenFileClick: () -> Unit,
    onShufflePlay: (List<AudioTrack>) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit = {},
    onAddToPlaylist: (AudioTrack) -> Unit = {},
    onShare: (AudioTrack) -> Unit = {},
    onOpenTrackDetails: (AudioTrack) -> Unit = {},
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (searchQuery.isNotEmpty()) "No tracks match '$searchQuery'" else "No tracks found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onOpenFileClick) {
                    Text("Pick Audio File from Device")
                }
            }
        }
    } else {
        var sortOption by remember { mutableStateOf(LibrarySortOption.NAME) }
        // Sorting runs off the main thread and keeps the previous ordering on screen until the
        // new one is ready, rather than blocking composition -- a several-thousand-track library
        // sorted synchronously here was landing squarely on the frames the pager needs to settle
        // its swipe animation on, which is what made switching tabs (this list is torn down and
        // re-sorted from scratch every time it's re-entered) read as janky/stuck.
        var sortedTracks by remember { mutableStateOf(tracks) }
        LaunchedEffect(tracks, sortOption) {
            sortedTracks = withContext(Dispatchers.Default) { sortTracks(tracks, sortOption) }
        }

        val canScroll by remember {
            derivedStateOf { listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size }
        }
        val (highlightedTrackId, onAlphabetJump) = rememberTrackHighlight(sortedTracks, listState, indexOffset = 1)

        // Brings the now-playing track into view when returning to this tab after it was started
        // somewhere this list isn't visible from (Search in particular -- playing a result there
        // and backing out used to land back on whatever scroll position this list happened to be
        // at, with no indication of where the playing track actually was). +1 to land on the
        // right list item: index 0 is the toolbar (see item(key = "toolbar") below), not a track.
        // Skipped when the row's already on screen so this can't fight the user's own scroll on
        // every ordinary track change (e.g. skip next/previous while idly browsing this list).
        LaunchedEffect(currentTrack?.id) {
            val targetId = currentTrack?.id ?: return@LaunchedEffect
            val trackIndex = sortedTracks.indexOfFirst { it.id == targetId }
            if (trackIndex < 0) return@LaunchedEffect
            val listIndex = trackIndex + 1
            val alreadyVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == listIndex }
            if (!alreadyVisible) {
                listState.animateScrollToItem(listIndex)
            }
        }

        // The toolbar is a real item (index 0) in the LazyColumn below rather than a separate
        // AnimatedVisibility sibling of a Modifier.weight(1f) list. That older pattern meant the
        // list's own height constraint changed on every frame of the toolbar's collapse
        // animation (weight resolves against the sibling's current measured size), forcing a full
        // re-measure of the LazyColumn's visible item window each frame -- exactly the moment
        // scrolling starts, which is when jank is most noticeable. Making the toolbar scroll away
        // as ordinary list content means the LazyColumn's own size never changes.
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = rememberListBottomPadding(),
                    top = 4.dp,
                    end = if (canScroll) alphabetScrollbarReserve() else 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "toolbar", contentType = "toolbar") {
                    LibraryToolbar(
                        sortOption = sortOption,
                        onSortOptionSelected = { sortOption = it },
                        onShuffleClick = { onShufflePlay(sortedTracks) },
                        availableSortOptions = listOf(LibrarySortOption.NAME, LibrarySortOption.ARTIST)
                    )
                }

                items(
                    items = sortedTracks,
                    key = { it.id }
                ) { track ->
                    val isPlayingThis = currentTrack?.id == track.id
                    TrackItem(
                        track = track,
                        isPlayingThisTrack = isPlayingThis,
                        onClick = { onTrackClick(track) },
                        modifier = Modifier.padding(start = 8.dp),
                        isHighlighted = track.id == highlightedTrackId,
                        onAddToQueue = { onAddToQueue(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onShare = { onShare(track) },
                        onTrackDetails = { onOpenTrackDetails(track) }
                    )
                }
            }

            if (canScroll) {
                AlphabetScrollbar(
                    labels = remember(sortedTracks, sortOption) { trackLabelsFor(sortedTracks, sortOption) },
                    onJump = onAlphabetJump,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                )
            }
        }
    }
}

// CASE_INSENSITIVE_ORDER rather than sortedBy { it.lowercase() }: the selector in sortedBy runs
// on every comparison, so lowercasing there allocates O(n log n) throwaway strings for a sort
// that is otherwise allocation-free.
private fun sortTracks(tracks: List<AudioTrack>, sortOption: LibrarySortOption): List<AudioTrack> =
    when (sortOption) {
        LibrarySortOption.NAME -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        LibrarySortOption.DATE_ADDED -> tracks.sortedByDescending { it.dateAddedMs }
        LibrarySortOption.ARTIST -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
        // Untagged tracks (trackNumber == 0) sort after every numbered one rather than jumping
        // to the front.
        LibrarySortOption.TRACK_NUMBER -> tracks.sortedBy { if (it.trackNumber == 0) Int.MAX_VALUE else it.trackNumber }
        LibrarySortOption.SHORTEST -> tracks.sortedBy { it.durationMs }
        LibrarySortOption.LONGEST -> tracks.sortedByDescending { it.durationMs }
        // Album/artist-only options; a bare track list has no "track count" of its own.
        LibrarySortOption.MOST_TRACKS, LibrarySortOption.LEAST_TRACKS ->
            tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

/**
 * The number to show on each track's left edge in [DiscTrackRow]'s disc-grouped layouts (album
 * detail, and artist detail's Tracks view): a track's own trackNumber tag where present, or a
 * sequential fallback where it's missing (0) -- so a row is never left blank just because the
 * file wasn't tagged. Computed once from [tracks] in its own name-sorted order and grouped by
 * disc (never from whatever sort the caller currently has applied), so a track's displayed number
 * stays fixed no matter how the visible list is currently sorted -- re-sorting shortest-to-longest
 * moves a track's row, not its number. [tracks] should already be scoped to one album's tracks;
 * for an artist's tracks (spanning several albums, each with its own "disc 1"), call this per
 * album and merge the resulting maps, not on the flattened list.
 */
internal fun stableTrackNumbers(tracks: List<AudioTrack>): Map<Long, Int> {
    val numbers = mutableMapOf<Long, Int>()
    tracks.groupBy { it.discNumber }.forEach { (_, discTracks) ->
        val canonicalOrder = discTracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        var nextFallback = 1
        canonicalOrder.forEach { track ->
            numbers[track.id] = if (track.trackNumber > 0) track.trackNumber else nextFallback++
        }
    }
    return numbers
}

private fun sortAlbums(albums: List<AlbumSummary>, sortOption: LibrarySortOption): List<AlbumSummary> =
    when (sortOption) {
        LibrarySortOption.MOST_TRACKS -> albums.sortedByDescending { it.trackCount }
        LibrarySortOption.LEAST_TRACKS -> albums.sortedBy { it.trackCount }
        else -> albums.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

private fun sortGenres(genres: List<GenreSummary>, sortOption: LibrarySortOption): List<GenreSummary> =
    when (sortOption) {
        LibrarySortOption.MOST_TRACKS -> genres.sortedByDescending { it.trackCount }
        LibrarySortOption.LEAST_TRACKS -> genres.sortedBy { it.trackCount }
        else -> genres.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

private fun sortArtists(artists: List<ArtistSummary>, sortOption: LibrarySortOption): List<ArtistSummary> =
    when (sortOption) {
        LibrarySortOption.MOST_TRACKS -> artists.sortedByDescending { it.trackCount }
        LibrarySortOption.LEAST_TRACKS -> artists.sortedBy { it.trackCount }
        else -> artists.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

/** How many different letters (A-Z, with everything else as one '#') the labels start with. */
private fun distinctStartLetters(labels: List<String>): Int =
    labels.map { label ->
        val first = label.firstOrNull()?.uppercaseChar()
        if (first != null && first in 'A'..'Z') first else '#'
    }.distinct().size

private fun trackLabelsFor(tracks: List<AudioTrack>, sortOption: LibrarySortOption): List<String> =
    if (sortOption == LibrarySortOption.ARTIST) tracks.map { it.artist } else tracks.map { it.title }

/**
 * Shared alphabet-scrollbar jump behaviour for a track list: scrolls to the target row *and*
 * briefly flashes it, so the highlight is actually visible even when the target isn't already on
 * screen (a highlight with no scroll is invisible off-screen -- the whole point of an A-Z index
 * is jumping to rows you can't currently see). Returns the currently highlighted track id (if
 * any, for TrackItem's isHighlighted) and the (index) -> Unit callback to hand the scrollbar.
 * Centralized so every track list -- Tracks, Favourites, and album/artist detail -- shares one
 * implementation instead of three near-identical copies.
 *
 * [indexOffset] shifts the scrollToItem target only -- album/artist detail's header now lives as
 * a real item 0 in the same LazyColumn, so a track at position N in [tracks] is list item N + 1;
 * Tracks/Favourites have no such header item and leave this at the default 0. Highlighting still
 * indexes into [tracks] directly (untouched by the offset), since that list has no header entry.
 */
@Composable
private fun rememberTrackHighlight(
    tracks: List<AudioTrack>,
    listState: LazyListState,
    indexOffset: Int = 0
): Pair<Long?, (Int) -> Unit> {
    val coroutineScope = rememberCoroutineScope()
    var highlightedTrackId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(highlightedTrackId) {
        if (highlightedTrackId != null) {
            delay(900)
            highlightedTrackId = null
        }
    }
    val onJump: (Int) -> Unit = { index ->
        highlightedTrackId = tracks.getOrNull(index)?.id
        coroutineScope.launch { listState.scrollToItem(index + indexOffset) }
    }
    return highlightedTrackId to onJump
}

/**
 * Same jump-and-flash behaviour as [rememberTrackHighlight], for the Albums/Artists lists, which
 * have no numeric id to key off -- their own display name (already unique enough to key the
 * LazyColumn's items()) doubles as the highlight key here.
 */
@Composable
private fun rememberNameHighlight(
    names: List<String>,
    listState: LazyListState,
    indexOffset: Int = 0
): Pair<String?, (Int) -> Unit> {
    val coroutineScope = rememberCoroutineScope()
    var highlightedName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(highlightedName) {
        if (highlightedName != null) {
            delay(900)
            highlightedName = null
        }
    }
    val onJump: (Int) -> Unit = { index ->
        highlightedName = names.getOrNull(index)
        coroutineScope.launch { listState.scrollToItem(index + indexOffset) }
    }
    return highlightedName to onJump
}

/** Hands the track's own file off to whatever the user picks from the system share sheet. */
fun shareTrack(context: Context, track: AudioTrack) {
    val uri = track.contentUri ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = track.mimeType ?: "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, track.title))
}

/**
 * A single track row for album detail's disc-grouped layout: track number, title, and duration --
 * no artwork (already shown once in the hero above) and no artist/album line (both are already
 * known from the album context), unlike the general-purpose TrackItem used elsewhere.
 */
@Composable
fun DiscTrackRow(
    track: AudioTrack,
    isPlayingThisTrack: Boolean,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit = {},
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onTrackDetails: () -> Unit,
    modifier: Modifier = Modifier,
    // A stable display number -- see [stableTrackNumbers]. Both callers (album detail and
    // artist detail's Tracks view) pass one in explicitly rather than relying on this default,
    // so that an untagged track still gets a number instead of leaving this blank.
    leadingNumber: Int = track.trackNumber
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isPlayingThisTrack) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "disc_track_bg"
    )
    var isMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                // Blank rather than "0" when the tag was absent, same convention as TrackItem's
                // own track-number slot.
                if (leadingNumber > 0) {
                    Text(
                        text = leadingNumber.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isPlayingThisTrack) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPlayingThisTrack) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            // Wraps instead of ellipsizing -- a long title used to get cut off with "..." here.
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isPlayingThisTrack) FontWeight.Bold else FontWeight.Medium,
                color = if (isPlayingThisTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = track.formattedDuration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Small gap before the overflow button so it doesn't crowd right up against the
            // duration it's now sitting next to.
            Spacer(modifier = Modifier.width(4.dp))
            Box {
                IconButton(onClick = { isMenuExpanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Track options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Queue") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onAddToQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Track Details") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onTrackDetails()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Sticky toolbar above a list: a sort-option picker on the left, and a dice button on the right
 * that rolls through random faces, shuffles, and plays a random track from the current order.
 */
@Composable
private fun LibraryToolbar(
    sortOption: LibrarySortOption,
    onSortOptionSelected: (LibrarySortOption) -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
    availableSortOptions: List<LibrarySortOption> = LibrarySortOption.entries
) {
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            TextButton(onClick = { isSortMenuExpanded = true }) {
                Icon(
                    imageVector = sortOption.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(sortOption.label, style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(
                expanded = isSortMenuExpanded,
                onDismissRequest = { isSortMenuExpanded = false }
            ) {
                availableSortOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        leadingIcon = { Icon(option.icon, contentDescription = null) },
                        trailingIcon = {
                            if (option == sortOption) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            onSortOptionSelected(option)
                            isSortMenuExpanded = false
                        }
                    )
                }
            }
        }

        ShuffleDiceButton(onShuffleClick = onShuffleClick)
    }
}

/**
 * The dice icon button shared by every "shuffle play this list" affordance in the library: it
 * spins and rapidly cycles random faces before settling and actually triggering [onShuffleClick].
 */
@Composable
fun ShuffleDiceButton(
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val diceRotation = remember { Animatable(0f) }
    var diceFace by remember { mutableIntStateOf(1) }
    var isRolling by remember { mutableStateOf(false) }

    IconButton(
        enabled = !isRolling,
        onClick = {
            isRolling = true
            coroutineScope.launch {
                launch {
                    diceRotation.animateTo(
                        targetValue = diceRotation.value + 360f,
                        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
                    )
                }
                // Rapidly cycle random faces so the dice appears to actually roll a number.
                repeat(5) {
                    diceFace = (1..6).random()
                    delay(150)
                }
                isRolling = false
                onShuffleClick()
            }
        },
        modifier = modifier
    ) {
        DiceFace(
            value = diceFace,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { rotationZ = diceRotation.value }
        )
    }
}

/**
 * Draws an actual six-sided die face (rounded square + pip dots) for the given [value] (1-6),
 * rather than a printed digit.
 */
@Composable
private fun DiceFace(value: Int, modifier: Modifier = Modifier) {
    val faceColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = modifier) {
        val corner = size.minDimension * 0.22f
        drawRoundRect(color = faceColor, cornerRadius = CornerRadius(corner, corner))

        val dotRadius = size.minDimension * 0.09f
        val positions: List<Pair<Float, Float>> = when (value) {
            1 -> listOf(0.5f to 0.5f)
            2 -> listOf(0.28f to 0.28f, 0.72f to 0.72f)
            3 -> listOf(0.28f to 0.28f, 0.5f to 0.5f, 0.72f to 0.72f)
            4 -> listOf(0.28f to 0.28f, 0.72f to 0.28f, 0.28f to 0.72f, 0.72f to 0.72f)
            5 -> listOf(0.28f to 0.28f, 0.72f to 0.28f, 0.5f to 0.5f, 0.28f to 0.72f, 0.72f to 0.72f)
            else -> listOf(0.28f to 0.25f, 0.72f to 0.25f, 0.28f to 0.5f, 0.72f to 0.5f, 0.28f to 0.75f, 0.72f to 0.75f)
        }
        positions.forEach { (fx, fy) ->
            drawCircle(color = dotColor, radius = dotRadius, center = Offset(fx * size.width, fy * size.height))
        }
    }
}

@Composable
private fun SimpleTrackList(
    tracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    emptyIcon: ImageVector,
    emptyMessage: String,
    onTrackClick: (AudioTrack) -> Unit,
    onShufflePlay: (List<AudioTrack>) -> Unit,
    modifier: Modifier = Modifier,
    onAddToQueue: (AudioTrack) -> Unit = {},
    onAddToPlaylist: (AudioTrack) -> Unit = {},
    onShare: (AudioTrack) -> Unit = {},
    onOpenTrackDetails: (AudioTrack) -> Unit = {}
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = emptyIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        var sortOption by remember { mutableStateOf(LibrarySortOption.NAME) }
        // See TracksList's identical comment: sorting off the main thread avoids blocking the
        // frame this list is re-composed and re-sorted on every time its tab is swiped back into.
        var sortedTracks by remember { mutableStateOf(tracks) }
        LaunchedEffect(tracks, sortOption) {
            sortedTracks = withContext(Dispatchers.Default) { sortTracks(tracks, sortOption) }
        }

        val listState = rememberLazyListState()

        // Toolbar as a real item (index 0), not a Modifier.weight(1f) sibling -- see TracksList's
        // identical comment for why: weight() re-measures the list on every frame of the
        // toolbar's collapse animation, right as scrolling starts.
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = rememberListBottomPadding(),
                top = 4.dp,
                end = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "toolbar", contentType = "toolbar") {
                LibraryToolbar(
                    sortOption = sortOption,
                    onSortOptionSelected = { sortOption = it },
                    onShuffleClick = { onShufflePlay(sortedTracks) },
                    availableSortOptions = listOf(LibrarySortOption.NAME, LibrarySortOption.ARTIST)
                )
            }

            items(
                items = sortedTracks,
                key = { it.id }
            ) { track ->
                val isPlayingThis = currentTrack?.id == track.id
                TrackItem(
                    track = track,
                    isPlayingThisTrack = isPlayingThis,
                    onClick = { onTrackClick(track) },
                    modifier = Modifier.padding(start = 8.dp),
                    showDuration = true,
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onShare = { onShare(track) },
                    onTrackDetails = { onOpenTrackDetails(track) }
                )
            }
        }
    }
}

/** A centered hero square shown above the title on an album or artist detail page. */
data class HeroArtwork(val uri: Uri?, val placeholderIcon: ImageVector)

@Composable
fun DetailTrackList(
    title: String,
    subtitle: String,
    tracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    onBack: () -> Unit,
    onTrackClick: (AudioTrack) -> Unit,
    onShufflePlay: (List<AudioTrack>) -> Unit,
    modifier: Modifier = Modifier,
    showToolbar: Boolean = true,
    heroArtwork: HeroArtwork? = null,
    // Most Played only: renders a "Your favourites" pair of equal, framed artwork squares for the
    // 2 most played tracks in the header instead of a single hero square. Mutually exclusive with
    // [heroArtwork].
    favouriteTracks: List<AudioTrack>? = null,
    showTrackArtwork: Boolean = true,
    // Artist/playlist/spotlight keep the original three; album detail overrides this to just
    // Shortest/Longest (see MainActivity), which also gates the scrollbars below (neither
    // applies once sorting is duration-based rather than alphabetical or by track number).
    availableSortOptions: List<LibrarySortOption> = listOf(
        LibrarySortOption.NAME,
        LibrarySortOption.DATE_ADDED,
        LibrarySortOption.ARTIST
    ),
    // Album detail only: renders tracks grouped under "Disk N" headers (by file metadata's disc
    // number) using a track-number/title/duration row instead of the standard artwork-forward
    // TrackItem, sorted within each disc rather than across the whole album at once.
    groupByDisc: Boolean = false,
    // True only for playlist detail -- Recently Added/Most Played/Recently Played show the
    // overflow menu but not the duration column.
    showTrackDuration: Boolean = false,
    // Album and genre detail: landscape splits into artwork + name on the left and the tracks on the
    // right. Playlist/spotlight detail keep their single-column layout.
    splitInLandscape: Boolean = false,
    // Genre detail: shows the A-Z scrollbar once the tracks start with more than this many different
    // letters (and are sorted by name or artist). Null means never.
    alphabetScrollbarOverLetters: Int? = null,
    // Only meaningful for groupByDisc rows, which carry their own overflow menu (album/artist
    // detail's other rows use the shared TrackItem and don't get this menu at all yet).
    onAddToQueue: (AudioTrack) -> Unit = {},
    onAddToPlaylist: (AudioTrack) -> Unit = {},
    onOpenTrackDetails: (AudioTrack) -> Unit = {},
    // Non-null only for album detail: pinned pencil icon (top right, alongside the back button)
    // opening the metadata editor for the whole album.
    onEditMetadata: (() -> Unit)? = null,
    // Non-null only for playlist detail: small draw icon on the hero artwork's own bottom-right
    // corner, opening a picker to choose which track's art stands in for the playlist's own.
    onEditPlaylistArtwork: (() -> Unit)? = null,
    // The overlay this screen renders in (see MainActivity) draws its own MiniPlayer as a sibling
    // rather than through Scaffold's innerPadding, so this is the mini player's real measured
    // height rather than a guess -- letting the list scroll fully clear of it regardless of the
    // device's navigation bar style/inset, which a fixed dp constant can't account for.
    bottomContentPadding: Dp = 120.dp
) {
    var sortOption by remember { mutableStateOf(availableSortOptions.first()) }
    val sortedTracks = remember(tracks, sortOption, showToolbar) {
        if (showToolbar) sortTracks(tracks, sortOption) else tracks
    }
    // Computed from the raw (unsorted) tracks, not sortedTracks -- see [stableTrackNumbers]'s doc.
    val trackNumbers = remember(tracks) { stableTrackNumbers(tracks) }

    val listState = rememberLazyListState()
    val isAtTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    val context = LocalContext.current
    val isLandscape = rememberIsLandscape()
    val useLandscapeSplit = isLandscape && splitInLandscape && heroArtwork != null && tracks.isNotEmpty()
    val startLetterCount = remember(sortedTracks, sortOption) {
        if (alphabetScrollbarOverLetters == null) 0 else distinctStartLetters(trackLabelsFor(sortedTracks, sortOption))
    }
    val showGenreAlphabet = alphabetScrollbarOverLetters != null &&
        (sortOption == LibrarySortOption.NAME || sortOption == LibrarySortOption.ARTIST) &&
        startLetterCount > alphabetScrollbarOverLetters

    // Computed here (a plain composable scope), not inside a LazyColumn's content lambda -- that's
    // a LazyListScope builder, not itself @Composable, so it can only host remember{} indirectly
    // via the item {} / items {} blocks it registers, not directly in its own body.
    val discGroups = remember(sortedTracks, groupByDisc) {
        if (groupByDisc) sortedTracks.groupBy { it.discNumber }.toSortedMap() else null
    }
    // A plain single-disc album showing a lone "Disk 0" header above its only track list
    // would just read as broken, so it's only shown once there's actually more than one
    // disc to distinguish.
    val showDiscHeaders = (discGroups?.size ?: 0) > 1

    // The track rows themselves, shared by the portrait list (under its collapsing header item)
    // and the landscape split's right-hand list.
    val trackRows: LazyListScope.(highlightedTrackId: Long?) -> Unit = { highlightedTrackId ->
            if (discGroups != null) {
                // Grouping (not sorting) by disc -- within each disc's own sub-list, the
                // relative order from the already-sorted sortedTracks is preserved, so the
                // active Shortest/Longest sort still applies per disc rather than
                // interleaving tracks from different discs by duration.
                discGroups.forEach { (discNumber, discTracks) ->
                    if (showDiscHeaders) {
                        item(key = "disc_$discNumber") {
                            Text(
                                text = "Disk $discNumber",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                            )
                        }
                    }
                    items(items = discTracks, key = { it.id }) { track ->
                        DiscTrackRow(
                            track = track,
                            isPlayingThisTrack = currentTrack?.id == track.id,
                            onClick = { onTrackClick(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onShare = { shareTrack(context, track) },
                            onTrackDetails = { onOpenTrackDetails(track) },
                            leadingNumber = trackNumbers[track.id] ?: track.trackNumber,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            } else {
                items(items = sortedTracks, key = { it.id }) { track ->
                    val isPlayingThis = currentTrack?.id == track.id
                    TrackItem(
                        track = track,
                        isPlayingThisTrack = isPlayingThis,
                        onClick = { onTrackClick(track) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                        showArtwork = showTrackArtwork,
                        // Only meaningful once the list is actually ordered by it -- shown
                        // under Name sort too it'd read as index-like clutter unrelated to
                        // the alphabetical order on screen.
                        trackNumber = if (sortOption == LibrarySortOption.TRACK_NUMBER) track.trackNumber else null,
                        isHighlighted = track.id == highlightedTrackId,
                        showDuration = showTrackDuration,
                        onAddToQueue = { onAddToQueue(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onShare = { shareTrack(context, track) },
                        onTrackDetails = { onOpenTrackDetails(track) }
                    )
                }
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (useLandscapeSplit && heroArtwork != null) {
            // No header item in this layout (it's a separate pane), so list index == track index.
            val (splitHighlightedId, splitAlphabetJump) = rememberTrackHighlight(sortedTracks, listState, indexOffset = 0)
            AlbumLandscapeSplit(
                title = title,
                subtitle = subtitle,
                heroArtwork = heroArtwork,
                onBack = onBack,
                onEditMetadata = onEditMetadata,
                onEditPlaylistArtwork = onEditPlaylistArtwork,
                showToolbar = showToolbar,
                sortOption = sortOption,
                onSortOptionSelected = { sortOption = it },
                availableSortOptions = availableSortOptions,
                onShuffleClick = { onShufflePlay(sortedTracks) },
                bottomContentPadding = bottomContentPadding,
                listState = listState,
                alphabetLabels = if (showGenreAlphabet) trackLabelsFor(sortedTracks, sortOption) else null,
                onAlphabetJump = splitAlphabetJump,
                trackRows = { trackRows(splitHighlightedId) }
            )
        } else if ((heroArtwork != null || favouriteTracks != null) && tracks.isNotEmpty()) {
            // The header (hero art + toolbar) is a real item -- index 0 -- in the LazyColumn
            // below, not a separate overlay with its own hand-tracked scroll position. Every
            // earlier version of this (a NestedScrollConnection summing drag deltas, reading
            // listState back after a jump, precomputing a max-achievable-push ceiling) kept
            // breaking in a new way, because there were two independent trackers of "how far has
            // this scrolled" -- the list's real position, and an approximation of it -- and a
            // scrollbar jump is exactly the kind of instant, non-drag movement that exposes the
            // gap between them. Making the header actual scrollable content means there's only
            // one source of truth: listState itself, always correct by construction, including
            // for jumps and clamped/short lists, with no separate sync code to fall out of step.
            var headerHeightPx by remember { mutableIntStateOf(0) }
            val heroCollapseFraction by remember {
                derivedStateOf {
                    if (headerHeightPx <= 0) {
                        0f
                    } else if (listState.firstVisibleItemIndex > 0) {
                        1f
                    } else {
                        (listState.firstVisibleItemScrollOffset / headerHeightPx.toFloat()).coerceIn(0f, 1f)
                    }
                }
            }

            // Only album detail offered these (Name/Track Number) scrollbars in the first place;
            // now that album detail is grouped by disc and sorted by duration instead, neither
            // applies there any more than it does for artist/playlist/spotlight -- excluded
            // explicitly (rather than relying only on availableSortOptions no longer containing
            // TRACK_NUMBER) so this stays correct even if album detail's options change again.
            // Otherwise the only thing that switches between the two scrollbars is which sort
            // mode is active -- neither is additionally hidden for being "not scrollable enough".
            val tracksNumberable = !groupByDisc && availableSortOptions.contains(LibrarySortOption.TRACK_NUMBER)
            val showAlphabetScrollbar = (tracksNumberable && sortOption == LibrarySortOption.NAME) || showGenreAlphabet
            val showPreciseScrollbar = tracksNumberable && sortOption == LibrarySortOption.TRACK_NUMBER
            val canScroll by remember {
                derivedStateOf { listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size }
            }
            val coroutineScope = rememberCoroutineScope()
            // The header occupies item 0, so track index N in sortedTracks is list item N + 1 --
            // rememberTrackHighlight's indexOffset handles that translation for scrollToItem.
            val (highlightedTrackId, onAlphabetJump) = rememberTrackHighlight(sortedTracks, listState, indexOffset = 1)
            // The precise (Track Number) scrollbar scrolls when it can, same +1 offset as above.
            // When the list can't actually scroll there's nothing useful to scroll to anyway
            // (everything's already on screen), so it just reuses the same highlight-and-scroll
            // handler -- scrollToItem on an unscrollable list is a harmless no-op, leaving the
            // highlight as the only visible effect.
            val onPreciseJump: (Int) -> Unit = if (canScroll) {
                { index -> coroutineScope.launch { listState.scrollToItem(index + 1) } }
            } else {
                onAlphabetJump
            }

            // Room the list keeps free at its end for the scrollbar. The header is centred over the
            // *whole* width, so it gets the same amount again at its start -- otherwise it sat
            // off-centre (centred in the narrower list area), only snapping into place when the
            // pinned copy, which spans the full width, took over.
            val listEndReserve = when {
                showAlphabetScrollbar -> alphabetScrollbarReserve()
                showPreciseScrollbar -> PRECISE_SCROLLBAR_WIDTH + PRECISE_SCROLLBAR_EDGE_INSET
                else -> 0.dp
            }

            // The header's contents, shared by the real header item (which scrolls with the list) and
            // the pinned copy shown while the A-Z strip is being used.
            val headerInner: @Composable ColumnScope.() -> Unit = {
                // Room for the back button, pinned separately below (always on top,
                // never scrolling away). The favourites header has no hero artwork
                // above its own title, so it can sit closer to the top than the
                // hero-artwork layouts below, which need the full clearance.
                // The back button (and the album's edit button) are the first row of the
                // header itself, so they scroll and fade away together with the title and
                // artwork below them instead of floating over the track rows.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (favouriteTracks != null) 44.dp else 52.dp)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    if (onEditMetadata != null) {
                        IconButton(onClick = onEditMetadata) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit album details")
                        }
                    }
                }
                if (favouriteTracks != null) {
                    FavouriteTracksSection(
                        tracks = favouriteTracks,
                        currentlyPlayingTrackId = currentTrack?.id,
                        onTrackClick = onTrackClick
                    )
                } else if (heroArtwork != null) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = heroArtwork.placeholderIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        if (heroArtwork.uri != null) {
                            val context = LocalContext.current
                            AsyncImage(
                                model = remember(heroArtwork.uri) {
                                    ImageRequest.Builder(context)
                                        .data(heroArtwork.uri)
                                        .size(Size(360, 360))
                                        .build()
                                },
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        if (onEditPlaylistArtwork != null) {
                            Surface(
                                onClick = onEditPlaylistArtwork,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .size(32.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 3.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Draw,
                                        contentDescription = "Choose playlist artwork",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                if (showToolbar) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LibraryToolbar(
                        sortOption = sortOption,
                        onSortOptionSelected = { sortOption = it },
                        onShuffleClick = { onShufflePlay(sortedTracks) },
                        availableSortOptions = availableSortOptions,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            // Pressing the A-Z strip jumps the list, which would normally scroll the header away under
            // the finger. Instead the header is pinned exactly where it was (as a copy drawn over the
            // list, which starts beneath it) and stays put while scrubbing and after letting go; it
            // slides away with the user's next scroll, as if it had been scrolling off all along.
            var pinnedOffsetPx by remember { mutableStateOf<Int?>(null) }
            var isAlphabetTouched by remember { mutableStateOf(false) }
            // How much of the pinned header has been scrolled away since letting go (px).
            var pinnedCollapsePx by remember { mutableFloatStateOf(0f) }
            // The real header item sits 4dp below the list's top edge (its contentPadding), so the
            // pinned copy does too -- otherwise it appeared to jump up a few pixels when pinned.
            val headerTopGapPx = with(LocalDensity.current) { 4.dp.toPx() }
            var pinnedListLeftTop by remember { mutableStateOf(false) }
            val pinnedVisiblePx = pinnedOffsetPx
                ?.let { (headerTopGapPx + headerHeightPx - it - pinnedCollapsePx).roundToInt().coerceAtLeast(0) } ?: 0
            // Once the list is back at the very top the real header item is on screen again, so the
            // pinned copy has to go -- both showing at once is what drew the header twice.
            LaunchedEffect(Unit) {
                snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
                    if (pinnedOffsetPx == null) {
                        pinnedListLeftTop = false
                    } else if (index > 0) {
                        pinnedListLeftTop = true
                    } else if (pinnedListLeftTop) {
                        // Back at the top, where the real header starts to scroll into view beneath the
                        // pinned copy. The two are identical, so just swap them: drop the pin (and the
                        // list's top gap that went with it) and put the list at the scroll position that
                        // leaves the real header, and every row, exactly where they are on screen right
                        // now. From there it scrolls normally -- no animation, no jump.
                        val pinned = pinnedOffsetPx ?: 0
                        val gap = (headerTopGapPx + headerHeightPx - pinned - pinnedCollapsePx).roundToInt()
                        val equivalentOffset = listState.firstVisibleItemScrollOffset - gap
                        if (equivalentOffset >= 0) {
                            pinnedOffsetPx = null
                            pinnedCollapsePx = 0f
                            pinnedListLeftTop = false
                            listState.requestScrollToItem(0, equivalentOffset)
                        } else {
                            // Flung all the way to the top in one go, so the real header is already fully in
                            // view beneath the copy and can't be lined up -- fade the copy out instead.
                            val target = headerTopGapPx + headerHeightPx - pinned
                            animate(pinnedCollapsePx, target, animationSpec = tween(250)) { value, _ ->
                                pinnedCollapsePx = value
                            }
                            pinnedOffsetPx = null
                            pinnedCollapsePx = 0f
                            pinnedListLeftTop = false
                        }
                    }
                }
            }
            // Scrolling the list after the pin makes the header slide up by exactly as far as the
            // finger moves -- before any row does -- like a collapsing toolbar, rather than the
            // header dropping away all at once. Once it's fully gone the pin ends and the list
            // scrolls normally. Scrolling back up leaves the header as it is.
            val pinnedHeaderScroll = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        val pinned = pinnedOffsetPx ?: return Offset.Zero
                        if (isAlphabetTouched) return Offset.Zero
                        if (available.y > 0f) {
                            // Scrolling up (a hard fling in particular) can carry the list straight past
                            // the point where the real header starts to appear, leaving both headers on
                            // screen with no way to line them up. So when this movement would cross that
                            // point, do the swap right here instead: move only as far as the threshold,
                            // swap the pinned copy for the real header at that exact spot, and apply what
                            // is left of the movement to the now-normal list. The threshold is when the
                            // first row's top reaches the list's content start (header just below it).
                            val firstRow = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 1 }
                                ?: return Offset.Zero
                            val toThreshold = -firstRow.offset.toFloat()
                            if (toThreshold < 0f || available.y <= toThreshold) return Offset.Zero
                            val leftover = available.y - toThreshold
                            // Scroll position that leaves the real header exactly where the pinned copy is.
                            val offsetAtSwap = pinned + pinnedCollapsePx
                            pinnedOffsetPx = null
                            pinnedCollapsePx = 0f
                            pinnedListLeftTop = false
                            listState.requestScrollToItem(0, (offsetAtSwap - leftover).coerceAtLeast(0f).roundToInt())
                            return Offset(0f, available.y)
                        }
                        if (available.y >= 0f) return Offset.Zero
                        val remaining = headerTopGapPx + headerHeightPx - pinned - pinnedCollapsePx
                        val slide = minOf(-available.y, remaining.coerceAtLeast(0f))
                        pinnedCollapsePx += slide
                        if (pinnedCollapsePx >= headerTopGapPx + headerHeightPx - pinned - 0.5f) {
                            pinnedOffsetPx = null
                            pinnedCollapsePx = 0f
                        }
                        return Offset(0f, -slide)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(pinnedHeaderScroll)) {
                LazyColumn(
                    state = listState,
                    // A real (layout-shrinking) padding, not just contentPadding -- contentPadding
                    // only adds blank scroll space before the first/after the last item, it doesn't
                    // shrink this LazyColumn's own clip bounds. Since the mini player floats as a
                    // rounded pill with transparent margins around it (see MiniPlayer.kt), rows laid
                    // out at the very bottom of a fillMaxSize list showed through those margins at
                    // any scroll position, not just once scrolled to the true end -- shrinking the
                    // list's own box means rows can never be placed there at all.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = with(LocalDensity.current) { pinnedVisiblePx.toDp() }, bottom = bottomContentPadding),
                    contentPadding = PaddingValues(
                        bottom = 16.dp,
                        top = 4.dp,
                        end = listEndReserve
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = listEndReserve)
                                .onSizeChanged { headerHeightPx = it.height }
                                .graphicsLayer { alpha = 1f - heroCollapseFraction },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            headerInner()
                        }
                    }

                    trackRows(highlightedTrackId)
                }

                pinnedOffsetPx?.let { offsetPx ->
                    val pinnedFade = (1f - (offsetPx + pinnedCollapsePx) / headerHeightPx.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .height(with(LocalDensity.current) { pinnedVisiblePx.toDp() })
                            .clipToBounds()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = listEndReserve)
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                .offset {
                                    IntOffset(0, (headerTopGapPx - offsetPx - pinnedCollapsePx).roundToInt())
                                }
                                .graphicsLayer { alpha = pinnedFade },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            headerInner()
                        }
                    }
                }

                if (showAlphabetScrollbar) {
                    AlphabetScrollbar(
                        labels = remember(sortedTracks, sortOption, showGenreAlphabet) {
                            trackLabelsFor(sortedTracks, if (showGenreAlphabet) sortOption else LibrarySortOption.NAME)
                        },
                        onJump = onAlphabetJump,
                        onActiveChange = { touched ->
                            isAlphabetTouched = touched
                            if (touched && pinnedOffsetPx == null && listState.firstVisibleItemIndex == 0) {
                                pinnedOffsetPx = listState.firstVisibleItemScrollOffset
                                pinnedCollapsePx = 0f
                                pinnedListLeftTop = false
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            // Stops above the mini player, like the list it belongs to.
                            .padding(bottom = bottomContentPadding)
                            // Its top edge follows the last visible row: just beneath the header while that is
                            // showing (or pinned), growing upward to full height as the header scrolls away.
                            .layout { measurable, constraints ->
                                // The header's bottom edge, measured exactly as the pinned copy measures it (including
                                // the list's 4dp top gap) so the strip doesn't change size at the moment of pinning.
                                val liveHeaderBottomPx = if (listState.firstVisibleItemIndex > 0) {
                                    0
                                } else {
                                    (headerTopGapPx + headerHeightPx - listState.firstVisibleItemScrollOffset)
                                        .roundToInt().coerceAtLeast(0)
                                }
                                val visibleHeaderPx = (
                                    if (pinnedOffsetPx != null) pinnedVisiblePx else liveHeaderBottomPx
                                ).coerceIn(0, constraints.maxHeight)
                                val rowsHeight = constraints.maxHeight - visibleHeaderPx
                                val placeable = measurable.measure(
                                    constraints.copy(minHeight = rowsHeight, maxHeight = rowsHeight)
                                )
                                layout(placeable.width, constraints.maxHeight) {
                                    placeable.placeRelative(0, visibleHeaderPx)
                                }
                            }
                    )
                }

                if (showPreciseScrollbar) {
                    PreciseScrollbar(
                        itemCount = sortedTracks.size,
                        listState = listState,
                        onJump = onPreciseJump,
                        // The header is list item 0, so listState.firstVisibleItemIndex runs one
                        // ahead of a track-relative position -- this tells the scrollbar's own
                        // progress/thumb math to subtract that back out.
                        indexOffset = 1,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            // Stops above the mini player, like the list it belongs to.
                            .padding(bottom = bottomContentPadding)
                            .padding(end = PRECISE_SCROLLBAR_EDGE_INSET)
                            // Same live-tracking mechanics as AlphabetScrollbar's identical
                            // modifier above.
                            .layout { measurable, constraints ->
                                val visibleHeaderPx = (headerHeightPx * (1f - heroCollapseFraction))
                                    .roundToInt().coerceIn(0, constraints.maxHeight)
                                val rowsHeight = constraints.maxHeight - visibleHeaderPx
                                val placeable = measurable.measure(
                                    constraints.copy(minHeight = rowsHeight, maxHeight = rowsHeight)
                                )
                                layout(placeable.width, constraints.maxHeight) {
                                    placeable.placeRelative(0, visibleHeaderPx)
                                }
                            }
                    )
                }
            }
        } else if (heroArtwork != null) {
            // Nothing to scroll, so the header is shown plainly (no collapse behaviour needed).
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = heroArtwork.placeholderIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp)
                    )
                    if (heroArtwork.uri != null) {
                        val context = LocalContext.current
                        AsyncImage(
                            model = remember(heroArtwork.uri) {
                                ImageRequest.Builder(context)
                                    .data(heroArtwork.uri)
                                    .size(Size(360, 360))
                                    .build()
                            },
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No songs here yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No songs here yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (showToolbar) {
                    AnimatedVisibility(visible = isAtTop) {
                        LibraryToolbar(
                            sortOption = sortOption,
                            onSortOptionSelected = { sortOption = it },
                            onShuffleClick = { onShufflePlay(sortedTracks) },
                            availableSortOptions = availableSortOptions
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    // See the header-variant LazyColumn above for why this is a real padding
                    // rather than just contentPadding.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomContentPadding),
                    contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = sortedTracks, key = { it.id }) { track ->
                        val isPlayingThis = currentTrack?.id == track.id
                        TrackItem(
                            track = track,
                            isPlayingThisTrack = isPlayingThis,
                            onClick = { onTrackClick(track) },
                            modifier = Modifier.padding(horizontal = 8.dp),
                            showArtwork = showTrackArtwork,
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onShare = { shareTrack(context, track) },
                            onTrackDetails = { onOpenTrackDetails(track) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Landscape album detail: artwork, album name and song count on the left, the sort/shuffle bar over
 * the track list on the right -- the same half-and-half split the other landscape screens use.
 */
@Composable
private fun AlbumLandscapeSplit(
    title: String,
    subtitle: String,
    heroArtwork: HeroArtwork,
    onBack: () -> Unit,
    onEditMetadata: (() -> Unit)?,
    /** Playlist detail: the small draw button on the artwork's corner, to choose which track's art it uses. */
    onEditPlaylistArtwork: (() -> Unit)?,
    showToolbar: Boolean,
    sortOption: LibrarySortOption,
    onSortOptionSelected: (LibrarySortOption) -> Unit,
    availableSortOptions: List<LibrarySortOption>,
    onShuffleClick: () -> Unit,
    bottomContentPadding: Dp,
    listState: LazyListState,
    /** Non-null shows the A-Z scrollbar over the track list, built from these (one per track, in list order). */
    alphabetLabels: List<String>?,
    onAlphabetJump: (Int) -> Unit,
    trackRows: LazyListScope.() -> Unit
) {
    // Both halves stop above the mini player the overlay draws as a sibling (see MainActivity).
    Row(modifier = Modifier.fillMaxSize().padding(bottom = bottomContentPadding)) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
        ) {
            // What's left of the half's height once the back row, the gaps and two text lines
            // are accounted for, so the artwork is as large as fits without scrolling.
            val artSize = (maxHeight - 148.dp).coerceIn(96.dp, minOf(maxWidth - 24.dp, 220.dp))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    if (onEditMetadata != null) {
                        IconButton(onClick = onEditMetadata) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit album details")
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(artSize)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = heroArtwork.placeholderIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp)
                    )
                    if (heroArtwork.uri != null) {
                        val context = LocalContext.current
                        AsyncImage(
                            model = remember(heroArtwork.uri) {
                                ImageRequest.Builder(context)
                                    .data(heroArtwork.uri)
                                    .size(Size(360, 360))
                                    .build()
                            },
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (onEditPlaylistArtwork != null) {
                        Surface(
                            onClick = onEditPlaylistArtwork,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(32.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 3.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Draw,
                                    contentDescription = "Choose playlist artwork",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
        ) {
            if (showToolbar) {
                LibraryToolbar(
                    sortOption = sortOption,
                    onSortOptionSelected = onSortOptionSelected,
                    onShuffleClick = onShuffleClick,
                    availableSortOptions = availableSortOptions,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = 16.dp,
                        top = 4.dp,
                        end = if (alphabetLabels != null) alphabetScrollbarReserve() else 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = trackRows
                )
                if (alphabetLabels != null) {
                    AlphabetScrollbar(
                        labels = alphabetLabels,
                        onJump = onAlphabetJump,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

/**
 * Most Played's top 2, as a "Your favourites" pair: two equal-sized framed artwork squares side
 * by side, with no rank distinction between them -- just the header's two most played tracks.
 */
@Composable
private fun FavouriteTracksSection(
    tracks: List<AudioTrack>,
    currentlyPlayingTrackId: Long?,
    onTrackClick: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val first = tracks.getOrNull(0)
    val second = tracks.getOrNull(1)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Your favourites",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
            if (first != null) {
                FavouriteSquare(
                    track = first,
                    isPlaying = first.id == currentlyPlayingTrackId,
                    onClick = { onTrackClick(first) }
                )
            }
            if (second != null) {
                FavouriteSquare(
                    track = second,
                    isPlaying = second.id == currentlyPlayingTrackId,
                    onClick = { onTrackClick(second) }
                )
            }
        }
    }
}

private val FAVOURITE_SQUARE_SIZE = 148.dp

@Composable
private fun FavouriteSquare(
    track: AudioTrack,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val size = FAVOURITE_SQUARE_SIZE
    val shape = RoundedCornerShape(14.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(size)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                // Playing is marked with just a thicker static border -- no animation at all.
                .border(width = if (isPlaying) 5.dp else 3.dp, color = accentColor, shape = shape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(40.dp)
            )
            if (track.artworkUri != null) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(track.artworkUri) {
                        ImageRequest.Builder(context)
                            .data(track.artworkUri)
                            .size(Size(320, 320))
                            .build()
                    },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AlbumsList(
    albums: List<AlbumSummary>,
    onAlbumClick: (String) -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (albums.isEmpty()) {
        EmptyLibrarySection(icon = Icons.Default.Album, message = "No albums found", modifier = modifier)
        return
    }

    var sortOption by remember { mutableStateOf(LibrarySortOption.NAME) }
    val sortedAlbums = remember(albums, sortOption) { sortAlbums(albums, sortOption) }

    val listState = rememberLazyListState()
    val canScroll by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size }
    }
    val albumNames = remember(sortedAlbums) { sortedAlbums.map { it.name } }
    val (highlightedAlbumName, onAlphabetJump) = rememberNameHighlight(albumNames, listState, indexOffset = 1)

    // Toolbar as a real item (index 0), not a Modifier.weight(1f) sibling -- see TracksList's
    // identical comment for why: weight() re-measures the list on every frame of the toolbar's
    // collapse animation, right as scrolling starts.
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = rememberListBottomPadding(),
                top = 8.dp,
                start = 8.dp,
                end = if (canScroll) alphabetScrollbarReserve() else 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "toolbar", contentType = "toolbar") {
                LibraryToolbar(
                    sortOption = sortOption,
                    onSortOptionSelected = { sortOption = it },
                    onShuffleClick = onShuffleClick,
                    availableSortOptions = listOf(
                        LibrarySortOption.NAME,
                        LibrarySortOption.MOST_TRACKS,
                        LibrarySortOption.LEAST_TRACKS
                    )
                )
            }

                items(sortedAlbums, key = { it.name }) { album ->
                    val backgroundColor by animateColorAsState(
                        targetValue = if (album.name == highlightedAlbumName) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        } else {
                            Color.Transparent
                        },
                        label = "album_bg"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(backgroundColor)
                            .clickable { onAlbumClick(album.name) }
                            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                            if (album.artworkUri != null) {
                                val context = LocalContext.current
                                AsyncImage(
                                    model = remember(album.artworkUri) {
                                        ImageRequest.Builder(context)
                                            .data(album.artworkUri)
                                            .size(Size(128, 128))
                                            .build()
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(14.dp))
                        Column {
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${album.artist} • ${album.trackCount} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

        if (canScroll) {
            AlphabetScrollbar(
                labels = albumNames,
                onJump = onAlphabetJump,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun GenresList(
    genres: List<GenreSummary>,
    onGenreClick: (String) -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) {
        EmptyLibrarySection(icon = Icons.Default.Category, message = "No genres found", modifier = modifier)
        return
    }

    var sortOption by remember { mutableStateOf(LibrarySortOption.NAME) }
    val sortedGenres = remember(genres, sortOption) { sortGenres(genres, sortOption) }

    val listState = rememberLazyListState()
    val canScroll by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size }
    }
    val genreNames = remember(sortedGenres) { sortedGenres.map { it.name } }
    val (highlightedGenreName, onAlphabetJump) = rememberNameHighlight(genreNames, listState, indexOffset = 1)

    // Toolbar as a real item (index 0), not a Modifier.weight(1f) sibling -- see TracksList's
    // identical comment for why: weight() re-measures the list on every frame of the toolbar's
    // collapse animation, right as scrolling starts.
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = rememberListBottomPadding(),
                top = 8.dp,
                start = 8.dp,
                end = if (canScroll) alphabetScrollbarReserve() else 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "toolbar", contentType = "toolbar") {
                LibraryToolbar(
                    sortOption = sortOption,
                    onSortOptionSelected = { sortOption = it },
                    onShuffleClick = onShuffleClick,
                    availableSortOptions = listOf(
                        LibrarySortOption.NAME,
                        LibrarySortOption.MOST_TRACKS,
                        LibrarySortOption.LEAST_TRACKS
                    )
                )
            }

                items(sortedGenres, key = { it.name }) { genre ->
                    val backgroundColor by animateColorAsState(
                        targetValue = if (genre.name == highlightedGenreName) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        } else {
                            Color.Transparent
                        },
                        label = "genre_bg"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(backgroundColor)
                            .clickable { onGenreClick(genre.name) }
                            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                            if (genre.artworkUri != null) {
                                val context = LocalContext.current
                                AsyncImage(
                                    model = remember(genre.artworkUri) {
                                        ImageRequest.Builder(context)
                                            .data(genre.artworkUri)
                                            .size(Size(128, 128))
                                            .build()
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(14.dp))
                        Column {
                            Text(
                                text = genre.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${genre.trackCount} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

        if (canScroll) {
            AlphabetScrollbar(
                labels = genreNames,
                onJump = onAlphabetJump,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun ArtistsList(
    artists: List<ArtistSummary>,
    onArtistClick: (String) -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) {
        EmptyLibrarySection(icon = Icons.Default.Person, message = "No artists found", modifier = modifier)
        return
    }

    var sortOption by remember { mutableStateOf(LibrarySortOption.NAME) }
    val sortedArtists = remember(artists, sortOption) { sortArtists(artists, sortOption) }

    val listState = rememberLazyListState()
    val canScroll by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size }
    }
    val artistNames = remember(sortedArtists) { sortedArtists.map { it.name } }
    val (highlightedArtistName, onAlphabetJump) = rememberNameHighlight(artistNames, listState, indexOffset = 1)

    // Toolbar as a real item (index 0), not a Modifier.weight(1f) sibling -- see TracksList's
    // identical comment for why: weight() re-measures the list on every frame of the toolbar's
    // collapse animation, right as scrolling starts.
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = rememberListBottomPadding(),
                top = 4.dp,
                start = 8.dp,
                end = if (canScroll) alphabetScrollbarReserve() else 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "toolbar", contentType = "toolbar") {
                LibraryToolbar(
                    sortOption = sortOption,
                    onSortOptionSelected = { sortOption = it },
                    onShuffleClick = onShuffleClick,
                    availableSortOptions = listOf(
                        LibrarySortOption.NAME,
                        LibrarySortOption.MOST_TRACKS,
                        LibrarySortOption.LEAST_TRACKS
                    )
                )
            }

                items(sortedArtists, key = { it.name }) { artist ->
                    val backgroundColor by animateColorAsState(
                        targetValue = if (artist.name == highlightedArtistName) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        } else {
                            Color.Transparent
                        },
                        label = "artist_bg"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(backgroundColor)
                            .clickable { onArtistClick(artist.name) }
                            .padding(start = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                            if (artist.artworkUri != null) {
                                val context = LocalContext.current
                                AsyncImage(
                                    model = remember(artist.artworkUri) {
                                        ImageRequest.Builder(context)
                                            .data(artist.artworkUri)
                                            .size(Size(128, 128))
                                            .build()
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(14.dp))
                        Column {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${artist.trackCount} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

        if (canScroll) {
            AlphabetScrollbar(
                labels = artistNames,
                onJump = onAlphabetJump,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun PlaylistsList(
    playlists: List<Playlist>,
    allTracks: List<AudioTrack>,
    onPlaylistClick: (Playlist) -> Unit,
    recentlyAddedTrack: AudioTrack?,
    mostPlayedTrack: AudioTrack?,
    recentlyPlayedTrack: AudioTrack?,
    onCategoryClick: (SpotlightCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    // Missing/deleted tracks are simply skipped -- a playlist's trackIds can outlive the files
    // they point at.
    val tracksById = remember(allTracks) { allTracks.associateBy { it.id } }
    val sortedPlaylists = remember(playlists) {
        playlists.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    val listState = rememberLazyListState()
    val isLandscape = rememberIsLandscape()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    // Landscape: the largest square that still leaves room for the row's own padding and the two
    // label lines under it, capped by a third of the width so the three always fit side by side.
    val landscapeTileSize = if (isLandscape) {
        val fitsHeight = maxHeight - 8.dp - 24.dp - 44.dp
        val fitsWidth = (maxWidth - 16.dp - 8.dp) / 3 - 12.dp
        minOf(fitsHeight, fitsWidth).coerceAtLeast(72.dp)
    } else {
        null
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = rememberListBottomPadding(), top = 8.dp, start = 8.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            SpotlightRow(
                recentlyAddedTrack = recentlyAddedTrack,
                mostPlayedTrack = mostPlayedTrack,
                recentlyPlayedTrack = recentlyPlayedTrack,
                onCategoryClick = onCategoryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                tileSize = landscapeTileSize
            )
        }

        if (sortedPlaylists.isEmpty()) {
            item {
                EmptyLibrarySection(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    message = "No playlists yet.\nTap the + on Now Playing to create one.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                )
            }
        } else {
                items(sortedPlaylists, key = { it.id }) { playlist ->
                    val playlistTracks = remember(playlist.trackIds, tracksById) {
                        playlist.trackIds.mapNotNull { tracksById[it] }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPlaylistClick(playlist) }
                            .padding(start = 8.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                            val artworkUri = playlist.artworkTrackId
                                ?.let { id -> tracksById[id]?.artworkUri }
                                ?: playlistTracks.firstOrNull()?.artworkUri
                            if (artworkUri != null) {
                                val context = LocalContext.current
                                AsyncImage(
                                    model = remember(artworkUri) {
                                        ImageRequest.Builder(context)
                                            .data(artworkUri)
                                            .size(Size(128, 128))
                                            .build()
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${playlist.trackIds.size} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = formatTotalDuration(playlistTracks.sumOf { it.durationMs }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    }

/** e.g. 3661000ms -> "1:01:01"; under an hour -> "12:34". */
private fun formatTotalDuration(totalMs: Long): String {
    val totalSeconds = totalMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * Three at-a-glance category tiles above the playlist list: Recently Added, Most Played and
 * Recently Played, each showing the matching track's artwork. Tapping a tile opens that
 * category's full list (Recently Added: up to 200 tracks; the other two: up to 125).
 */
@Composable
private fun SpotlightRow(
    recentlyAddedTrack: AudioTrack?,
    mostPlayedTrack: AudioTrack?,
    recentlyPlayedTrack: AudioTrack?,
    onCategoryClick: (SpotlightCategory) -> Unit,
    modifier: Modifier = Modifier,
    tileSize: Dp? = null
) {
    // Portrait: three equal-width squares filling the row. Landscape ([tileSize] set): equal
    // thirds of the very wide screen would be too tall to fit, so the caller passes the largest
    // square that still fits on screen with its labels, spread evenly across the row.
    Row(
        modifier = modifier,
        horizontalArrangement = if (tileSize != null) Arrangement.SpaceEvenly else Arrangement.spacedBy(10.dp)
    ) {
        val tileModifier = if (tileSize != null) Modifier.width(tileSize) else Modifier.weight(1f)
        SpotlightTile(
            label = SpotlightCategory.RECENTLY_ADDED.label,
            track = recentlyAddedTrack,
            onClick = { onCategoryClick(SpotlightCategory.RECENTLY_ADDED) },
            modifier = tileModifier
        )
        SpotlightTile(
            label = SpotlightCategory.MOST_PLAYED.label,
            track = mostPlayedTrack,
            onClick = { onCategoryClick(SpotlightCategory.MOST_PLAYED) },
            modifier = tileModifier
        )
        SpotlightTile(
            label = SpotlightCategory.RECENTLY_PLAYED.label,
            track = recentlyPlayedTrack,
            onClick = { onCategoryClick(SpotlightCategory.RECENTLY_PLAYED) },
            modifier = tileModifier
        )
    }
}

@Composable
private fun SpotlightTile(
    label: String,
    track: AudioTrack?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp)
            )
            if (track?.artworkUri != null) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(track.artworkUri) {
                        ImageRequest.Builder(context)
                            .data(track.artworkUri)
                            .size(Size(200, 200))
                            .build()
                    },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = track?.title ?: "No data yet",
            style = MaterialTheme.typography.labelSmall,
            color = if (track != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyLibrarySection(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * A-Z fast-scroller for a list. Tap or drag along the strip to jump to (and briefly highlight)
 * the first item whose label starts with that letter; a bubble previews the letter while
 * touching. `onJump`'s actual scroll-and-highlight behaviour is supplied by the caller via
 * rememberTrackHighlight.
 */
@Composable
private fun AlphabetScrollbar(
    labels: List<String>,
    onJump: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** True when a touch starts on the strip, false when it ends. */
    onActiveChange: (Boolean) -> Unit = {}
) {
    val currentOnActiveChange by rememberUpdatedState(onActiveChange)
    var trackHeightPx by remember { mutableStateOf(0f) }

    // First index in `labels` for each letter; labels that don't start with A-Z group under '#'.
    val letterIndex = remember(labels) {
        val map = HashMap<String, Int>()
        labels.forEachIndexed { index, label ->
            val first = label.firstOrNull()?.uppercaseChar()
            val key = if (first != null && first in 'A'..'Z') first.toString() else "#"
            if (key !in map) map[key] = index
        }
        map
    }
    // Only the letters actually present, not a fixed A-Z -- with a fixed alphabet, a short list
    // (e.g. only 5 titles, starting with A/C/E/F/L) still laid out all 26 slots evenly, so most of
    // the strip pointed at empty gaps between real letters. Dragging through one of those gaps
    // still computed a nearest letter (there's always a nearest slot), so the active-letter bubble
    // and scale-up kept jumping to whatever real letter was closest rather than tracking smoothly
    // under the finger. Sizing the strip to only the letters that actually have a match means
    // every slot is real, so every position on the strip lands on one.
    val allLetters = remember(letterIndex) { letterIndex.keys.sorted() }
    // When the strip is too short to give every letter its minimum slot (landscape), only a few
    // are drawn -- always the first and last, and evenly spaced ones between -- with a dot in each
    // gap standing for the letters that aren't drawn, so nothing is squished. Touch still resolves
    // against *every* letter by position, so dragging through a gap lands on (and shows a bubble
    // for) the letters that aren't drawn. Until the strip's height is measured, everything is drawn.
    val isLandscape = rememberIsLandscape()
    val letters = allLetters
    val minSlotPx = with(LocalDensity.current) { ALPHABET_MIN_LETTER_SLOT.toPx() }
    val fit = if (trackHeightPx > 0f) (trackHeightPx / minSlotPx).toInt().coerceAtLeast(2) else letters.size
    val isThinned = letters.size > fit
    // Indices into `letters` that get drawn. Letters and the dots between them each need a slot, so
    // only about half as many letters as slots fit.
    val shownIndices = remember(letters, fit) {
        if (letters.size <= fit) {
            letters.indices.toList()
        } else {
            val shownCount = ((fit + 1) / 2).coerceAtLeast(2)
            (0 until shownCount).map { (it * (letters.size - 1).toFloat() / (shownCount - 1)).roundToInt() }.distinct()
        }
    }
    // Landscape pulls the strip in from the screen edge so a back swipe from that edge isn't
    // swallowed by the strip (and the strip stays out of the system gesture zone).
    val edgeGap = if (isLandscape) ALPHABET_LANDSCAPE_EDGE_GAP else 0.dp

    var activeLetter by remember { mutableStateOf<String?>(null) }

    fun jumpTo(letter: String) {
        if (activeLetter == null) currentOnActiveChange(true)
        activeLetter = letter
        letterIndex[letter]?.let { index -> onJump(index) }
    }

    Box(modifier = modifier) {
        activeLetter?.let { letter ->
            // Vertically align the bubble with this letter's actual row in the strip below.
            val activeIndex = letters.indexOf(letter)
            val bubbleSizePx = with(LocalDensity.current) { 52.dp.toPx() }
            val centerY = if (trackHeightPx > 0f) {
                (activeIndex + 0.5f) / letters.size * trackHeightPx
            } else {
                trackHeightPx / 2f
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 5.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = edgeGap + ALPHABET_SCROLLBAR_WIDTH + 8.dp)
                    .offset { IntOffset(0, (centerY - bubbleSizePx / 2f).roundToInt()) }
                    .size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = letter,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }
            }
        }

        @Composable
        fun LetterText(letter: String, modifier: Modifier = Modifier) {
            val isActive = letter == activeLetter
            // Read via graphicsLayer's draw-phase lambda rather than `by` in the composable
            // body, so animating the active letter's scale doesn't recompose all 26 Texts
            // on every animation frame -- only the draw phase re-runs.
            val scale = animateFloatAsState(targetValue = if (isActive) 2.4f else 1f, label = "letterScale")
            Text(
                text = letter,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                textAlign = TextAlign.Center,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = edgeGap)
                .fillMaxHeight()
                .width(ALPHABET_SCROLLBAR_WIDTH)
                // Portrait only: the strip sits against the edge there and keeps its drag from
                // being read as the back gesture. In landscape it's inset instead (see edgeGap).
                .then(if (isLandscape) Modifier else Modifier.systemGestureExclusion())
                .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
                .pointerInput(letters, letterIndex) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val height = size.height.toFloat()
                        fun letterForY(y: Float): String {
                            val index = (y / height * letters.size).toInt().coerceIn(0, letters.size - 1)
                            return letters[index]
                        }
                        down.consume()
                        jumpTo(letterForY(down.position.y))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            jumpTo(letterForY(change.position.y))
                        }
                        activeLetter = null
                        currentOnActiveChange(false)
                    }
                }
        ) {
            if (!isThinned) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    letters.forEach { letter -> LetterText(letter) }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    shownIndices.forEachIndexed { position, index ->
                        LetterText(letters[index])
                        if (position < shownIndices.lastIndex) {
                            Text(
                                text = "\u2022",
                                fontSize = 6.sp,
                                lineHeight = 6.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A precise, position-proportional scrollbar (thumb reflects exact scroll position and viewport
 * fraction). Drag anywhere along the track to jump straight there, unlike the alphabetical index.
 */
@Composable
private fun PreciseScrollbar(
    itemCount: Int,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    // See the identical parameter on AlphabetScrollbar for why album detail overrides this.
    onJump: ((index: Int) -> Unit)? = null,
    // Album/artist detail's collapsing header lives as a real item 0 in the same LazyColumn, so
    // listState.firstVisibleItemIndex runs indexOffset ahead of a position within itemCount --
    // subtracted back out below so the thumb still reflects progress through the *tracks*, not
    // the tracks-plus-header list.
    indexOffset: Int = 0
) {
    val coroutineScope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // layoutInfo changes on every frame of a scroll, so reading it directly in the composable
    // body would recompose this scrollbar every frame. derivedStateOf narrows that down to the
    // frames where the values actually change.
    val maxFirstIndex by remember(itemCount) {
        derivedStateOf {
            val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            (itemCount - visibleCount).coerceAtLeast(1)
        }
    }
    val thumbFraction by remember(itemCount) {
        derivedStateOf {
            val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            (visibleCount.toFloat() / itemCount).coerceIn(0.1f, 1f)
        }
    }
    val progress by remember(itemCount, indexOffset) {
        derivedStateOf {
            ((listState.firstVisibleItemIndex - indexOffset).toFloat() / maxFirstIndex).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .width(PRECISE_SCROLLBAR_WIDTH)
            // Landscape: it already sits PRECISE_SCROLLBAR_EDGE_INSET in from the edge, so leave the
            // edge itself to the system back gesture.
            .then(if (rememberIsLandscape()) Modifier else Modifier.systemGestureExclusion())
            .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
            .pointerInput(itemCount, maxFirstIndex) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true
                    val height = size.height.toFloat()
                    fun scrollToY(y: Float) {
                        val fraction = (y / height).coerceIn(0f, 1f)
                        val targetIndex = (fraction * maxFirstIndex).roundToInt().coerceIn(0, itemCount - 1)
                        if (onJump != null) {
                            onJump(targetIndex)
                        } else {
                            coroutineScope.launch { listState.scrollToItem(targetIndex) }
                        }
                    }
                    down.consume()
                    scrollToY(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        scrollToY(change.position.y)
                    }
                    isDragging = false
                }
            }
    ) {
        // Track line
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        )

        // Draggable thumb
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (progress * trackHeightPx * (1f - thumbFraction)).roundToInt()) }
                .fillMaxHeight(thumbFraction)
                .width(if (isDragging) 8.dp else 5.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

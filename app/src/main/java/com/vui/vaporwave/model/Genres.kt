package com.vui.vaporwave.model

import android.net.Uri
import java.util.Locale
import kotlin.random.Random

/** Every track with no usable genre tag lands in this one genre. */
const val UNKNOWN_GENRE = "Unknown"

/**
 * Turns a raw genre tag into the genres a track belongs to. Blank or "unknown"-ish tags become
 * [UNKNOWN_GENRE]. A tag with several genres separated by ';' (or a null character, which is how
 * some taggers store multiple values) yields each genre on its own *and* the combined tag as a
 * genre of its own -- so "Rock; Metal" is listed under Rock, under Metal, and under "Rock; Metal".
 * '/' deliberately isn't a separator: it's part of real genre names ("R&B/Soul", "Hip-Hop/Rap").
 */
fun parseGenres(raw: String?): List<String> {
    fun isUnknown(value: String) = value.isBlank() ||
        value.equals("unknown", ignoreCase = true) ||
        value.equals("<unknown>", ignoreCase = true)

    val cleaned = raw.orEmpty().replace('\u0000', ';').trim()
    if (isUnknown(cleaned)) return listOf(UNKNOWN_GENRE)
    val parts = cleaned.split(';').map { it.trim() }.filter { !isUnknown(it) }
    if (parts.isEmpty()) return listOf(UNKNOWN_GENRE)
    if (parts.size == 1) return parts
    val combined = parts.joinToString("; ")
    return (parts + combined).distinctBy { it.lowercase(Locale.ROOT) }
}

/** True when [track] belongs to [genre] (case-insensitive, matching how genres are grouped). */
fun AudioTrack.isInGenre(genre: String): Boolean = genres.any { it.equals(genre, ignoreCase = true) }

/**
 * One summary per genre. Genres are grouped case-insensitively ("rock" and "Rock" are one genre,
 * shown under whichever spelling is most common). The artwork is the cover of the genre's most
 * played track (play count x duration, same ranking as Most Played) -- or, when nothing in the
 * genre has been played yet, a pseudo-random track's cover, seeded by the genre name so it stays
 * put between recompositions instead of reshuffling every time the list is rebuilt.
 */
fun buildGenreSummaries(tracks: List<AudioTrack>, playStats: Map<Long, PlayStat>): List<GenreSummary> {
    val byKey = HashMap<String, MutableList<AudioTrack>>()
    val spellings = HashMap<String, MutableMap<String, Int>>()
    for (track in tracks) {
        for (genre in track.genres) {
            val key = genre.lowercase(Locale.ROOT)
            byKey.getOrPut(key) { mutableListOf() }.add(track)
            val counts = spellings.getOrPut(key) { mutableMapOf() }
            counts[genre] = (counts[genre] ?: 0) + 1
        }
    }
    return byKey.map { (key, genreTracks) ->
        val name = spellings.getValue(key).maxByOrNull { it.value }!!.key
        val withArtwork = genreTracks.filter { it.artworkUri != null }
        val mostPlayed = withArtwork
            .filter { (playStats[it.id]?.playCount ?: 0) > 0 }
            .maxByOrNull { playStats.getValue(it.id).playCount.toLong() * it.durationMs }
        val artwork: Uri? = (mostPlayed ?: withArtwork.takeIf { it.isNotEmpty() }?.random(Random(key.hashCode())))
            ?.artworkUri
        GenreSummary(
            name = name,
            trackCount = genreTracks.size,
            artworkUri = artwork,
            latestDateAddedMs = genreTracks.maxOf { it.dateAddedMs }
        )
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

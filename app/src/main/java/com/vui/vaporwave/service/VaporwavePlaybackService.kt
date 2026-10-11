package com.vui.vaporwave.service

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vui.vaporwave.MainActivity
import com.vui.vaporwave.VaporwaveApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * VaporwavePlaybackService manages background audio playback,
 * system media notifications, audio focus, and headset disconnect events.
 * Extends AndroidX Media3 MediaSessionService.
 */
class VaporwavePlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    // The platform Equalizer lives here (not in MusicViewModel) because it must attach to the
    // real ExoPlayer's audioSessionId, which only exists inside the process that owns the
    // ExoPlayer -- MediaController on the ViewModel side never sees a session id at all. UI
    // requests reach this through the COMMAND_EQ_APPLY custom session command below.
    private var equalizer: Equalizer? = null
    private var desiredEqEnabled = false
    private var desiredEqGains: FloatArray? = null

    // The session id the currently-attached `equalizer` (if any) was built against, so a repeat
    // notification for the same id (or a burst of rapid pitch/speed changes that doesn't actually
    // change it) can skip rebuilding entirely. AudioEffect.release() only queues async teardown in
    // the mediaserver process rather than blocking on it -- rebuilding on every notification faster
    // than AudioFlinger can actually tear down the previous native effect instance can exceed the
    // device's per-mixer-thread effect cap and silently wedge the whole audio output.
    private var equalizerSessionId = C.AUDIO_SESSION_ID_UNSET
    private var equalizerRebuildJob: Job? = null

    // The session id last announced to the system via ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, so
    // it can be matched with a CLOSE broadcast when the session goes away or changes.
    private var effectControlSessionId = C.AUDIO_SESSION_ID_UNSET
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Media3's own stuck-playback detector (the one onPlayerError eventually reports) waits a
    // fixed, non-configurable 10 seconds of zero progress before reporting anything -- this polls
    // position ourselves so rebuildPlayer() can fire in ~2s instead of waiting out that full 10s.
    private var stallWatchdogJob: Job? = null
    // Guards against the watchdog and Media3's own (slower) detector both calling rebuildPlayer()
    // for the same stall -- the watchdog wins almost every time, so this just skips the redundant
    // second rebuild rather than running it twice back to back.
    private var isRecoveringPlayer = false

    // The speed/pitch the stalled player was at, to restore after rebuildPlayer() -- a fresh
    // ExoPlayer otherwise starts at 1.0x/1.0x, silently cancelling whatever effect the user had
    // dialed in. Applied once the new instance reaches READY (see playerListener's
    // onPlaybackStateChanged), not immediately alongside setMediaItems/prepare -- re-engaging the
    // exact pitch shift that's implicated in the stall before the new player has even stabilized
    // risks tripping the same deadlock again straight away.
    private var pendingRestoreParameters: PlaybackParameters? = null

    private lateinit var bitmapLoader: ContentUriBitmapLoader

    // ExoPlayer can (rarely) regenerate its AudioTrack session id across its lifetime -- the
    // Equalizer is rebuilt (not just left stale) whenever that happens, reapplying whatever
    // enabled/gain state was last requested. Reads `player` (the field) rather than closing over
    // a specific instance, so the same listener keeps working correctly across rebuildPlayer()
    // swapping which ExoPlayer that field points to.
    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            Log.i(TAG, "onAudioSessionIdChanged: $audioSessionId")
            openAudioEffectSession(audioSessionId)
            scheduleEqualizerRebuild(audioSessionId)
        }

        // Logged, debugged, and confirmed with the device attached: this is Media3's own
        // internal stall watchdog (ERROR_CODE_TIMEOUT, "Player stuck playing with no progress
        // for 10000 ms"), not a crash -- after enough rapid speed/pitch reconfiguration, the
        // renderer pipeline on this OEM's audio HAL silently deadlocks (no further AudioTrack
        // activity, no exception of its own) until this 10-second watchdog reports it.
        //
        // Two recovery attempts before this one did NOT work, both logged and confirmed
        // on-device: a plain prepare() got the player out of IDLE and into BUFFERING but never
        // any further; clearing and re-adding the whole queue on the *same* ExoPlayer instance
        // also got stuck in BUFFERING indefinitely. Both reuse the same player instance, which
        // means whatever's actually deadlocked survives them -- it's beneath ExoPlayer's own
        // queue/item bookkeeping, likely the native AudioTrack/renderer pipeline tied to this
        // specific ExoPlayer instance. Rebuilding the ExoPlayer instance itself (not just its
        // queue) is the next escalation -- see rebuildPlayer().
        override fun onPlayerError(error: PlaybackException) {
            val current = player
            Log.e(
                TAG,
                "onPlayerError: code=${error.errorCode} codeName=${error.errorCodeName} " +
                    "playbackState=${current?.playbackState} playWhenReady=${current?.playWhenReady} " +
                    "speed=${current?.playbackParameters?.speed} pitch=${current?.playbackParameters?.pitch}",
                error
            )
            // By the time this fires (Media3's own fixed 10s stall timeout), stallWatchdog below
            // has almost always already caught and fixed the exact same stall in ~2s -- skip the
            // redundant rebuild rather than doing it twice.
            if (!isRecoveringPlayer) {
                rebuildPlayer()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = when (playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> playbackState.toString()
            }
            Log.i(TAG, "onPlaybackStateChanged: $stateName")
            if (playbackState == Player.STATE_READY) {
                pendingRestoreParameters?.let { restoreParams ->
                    pendingRestoreParameters = null
                    Log.i(TAG, "onPlaybackStateChanged: restoring speed=${restoreParams.speed} pitch=${restoreParams.pitch} after rebuild")
                    player?.playbackParameters = restoreParams
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.i(TAG, "onIsPlayingChanged: $isPlaying")
            if (isPlaying) startStallWatchdog() else stopStallWatchdog()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            Log.i(TAG, "onPlaybackParametersChanged: speed=${playbackParameters.speed} pitch=${playbackParameters.pitch}")
        }

        // Warms the notification artwork cache for whatever track is coming up *next* while
        // the current one is still playing, so by the time playback actually advances to it
        // and Media3 asks its BitmapLoader for that artwork, it's often already resolved
        // instead of triggering a fresh loadThumbnail IPC right as the notification needs it
        // -- this is the gap that shows up as a brief blank-artwork flash on a fast device
        // (a Samsung S9) and, on a slower/OEM-throttled one (observed on Vivo), can miss
        // entirely if the session moves on again before the load finishes.
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val exoPlayer = player ?: return
            val nextIndex = exoPlayer.nextMediaItemIndex
            if (nextIndex == C.INDEX_UNSET) return
            val nextArtworkUri = exoPlayer.getMediaItemAt(nextIndex).mediaMetadata.artworkUri
            bitmapLoader.prewarm(nextArtworkUri)
        }
    }

    private fun buildExoPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        return ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { it.addListener(playerListener) }
    }

    // Escalation from the two recovery attempts documented on playerListener.onPlayerError that
    // didn't work -- releases the deadlocked ExoPlayer outright and hands the *same* MediaSession
    // a brand new one (MediaSession.setPlayer() rebinds it in place), so existing MediaControllers
    // -- the app's own UI -- don't need to detect a disconnect and reconnect on their own.
    private fun rebuildPlayer() {
        isRecoveringPlayer = true
        stopStallWatchdog()
        val oldPlayer = player
        val queueItems = oldPlayer?.let { p -> (0 until p.mediaItemCount).map { p.getMediaItemAt(it) } } ?: emptyList()
        val stuckIndex = oldPlayer?.currentMediaItemIndex ?: 0
        val stuckPosition = oldPlayer?.currentPosition ?: 0L
        val stuckParameters = oldPlayer?.playbackParameters ?: PlaybackParameters.DEFAULT
        Log.i(
            TAG,
            "rebuildPlayer: queueSize=${queueItems.size} index=$stuckIndex position=$stuckPosition " +
                "speed=${stuckParameters.speed} pitch=${stuckParameters.pitch}"
        )
        // Only worth restoring if it was actually non-default -- skips a pointless no-op
        // reassignment on the common case where the stall happened at normal playback speed.
        pendingRestoreParameters = stuckParameters.takeIf { it != PlaybackParameters.DEFAULT }

        closeAudioEffectSession()
        oldPlayer?.removeListener(playerListener)
        oldPlayer?.release()

        val newPlayer = buildExoPlayer()
        player = newPlayer
        mediaSession?.setPlayer(newPlayer)
        openAudioEffectSession(newPlayer.audioSessionId)
        recreateEqualizer(newPlayer.audioSessionId)

        if (queueItems.isNotEmpty()) {
            newPlayer.setMediaItems(queueItems, stuckIndex, stuckPosition)
            newPlayer.prepare()
            newPlayer.playWhenReady = true
        }
        isRecoveringPlayer = false
    }

    // Polls position every 150ms while actively playing, rather than waiting on Media3's own
    // fixed 10s stuck-playback timeout -- see isRecoveringPlayer's doc for why both exist.
    // Started/stopped from onIsPlayingChanged so it's never running while genuinely paused,
    // buffering (e.g. a normal network/disk stall between tracks), or seeking, none of which
    // should look like a stall. The 1.3s-no-progress threshold below (not something shorter) is
    // deliberate -- currentPosition's own reporting granularity isn't continuous, and a threshold
    // too close to its normal once-a-second-ish update cadence was firing on legitimate playback,
    // not just the real stall.
    private fun startStallWatchdog() {
        stallWatchdogJob?.cancel()
        stallWatchdogJob = serviceScope.launch {
            var lastPosition = player?.currentPosition ?: 0L
            var lastIndex = player?.currentMediaItemIndex ?: 0
            var lastProgressAtMs = System.currentTimeMillis()
            while (true) {
                delay(150)
                val exoPlayer = player ?: return@launch
                if (exoPlayer.playbackState != Player.STATE_READY || !exoPlayer.playWhenReady) {
                    lastPosition = exoPlayer.currentPosition
                    lastIndex = exoPlayer.currentMediaItemIndex
                    lastProgressAtMs = System.currentTimeMillis()
                    continue
                }
                val currentPosition = exoPlayer.currentPosition
                val currentIndex = exoPlayer.currentMediaItemIndex
                val now = System.currentTimeMillis()
                // A track change (or a backward seek) drops position back down rather than
                // advancing it -- checking only currentPosition > lastPosition missed both,
                // reading the reset itself as "no progress" and comparing against the *previous*
                // track's much larger position from then on, which would never catch up in time
                // and falsely tripped a rebuild on every single track transition.
                if (currentIndex != lastIndex || kotlin.math.abs(currentPosition - lastPosition) > 50) {
                    lastPosition = currentPosition
                    lastIndex = currentIndex
                    lastProgressAtMs = now
                } else if (now - lastProgressAtMs > 1_300) {
                    Log.e(TAG, "stallWatchdog: no progress for ${now - lastProgressAtMs}ms at position=$currentPosition -- rebuilding")
                    rebuildPlayer()
                    return@launch
                }
            }
        }
    }

    private fun stopStallWatchdog() {
        stallWatchdogJob?.cancel()
        stallWatchdogJob = null
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate() pid=${android.os.Process.myPid()}")

        bitmapLoader = (application as VaporwaveApplication).notificationBitmapLoader

        val exoPlayer = buildExoPlayer()
        this.player = exoPlayer
        openAudioEffectSession(exoPlayer.audioSessionId)
        recreateEqualizer(exoPlayer.audioSessionId)

        // Intent to launch MainActivity when clicking on the system media notification
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            // Media3's default BitmapLoader doesn't reliably resolve the content:// MediaStore
            // URIs this app uses for artwork -- see ContentUriBitmapLoader for why. Shared with
            // MusicViewModel's prewarm calls (see bitmapLoader assignment above) rather than a
            // fresh instance, so both sides hit the same cache.
            .setBitmapLoader(bitmapLoader)
            .setCallback(EqualizerSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // Without this, swipe-dismissing the app from Recents while paused (or with no queue) leaves
    // the service and its ExoPlayer instance alive indefinitely in the background.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val activePlayer = player
        val willStop = activePlayer == null || !activePlayer.playWhenReady || activePlayer.mediaItemCount == 0
        Log.i(TAG, "onTaskRemoved: willStop=$willStop playWhenReady=${activePlayer?.playWhenReady} mediaItemCount=${activePlayer?.mediaItemCount}")
        if (willStop) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy() pid=${android.os.Process.myPid()}", Exception("onDestroy stack trace"))
        equalizerRebuildJob?.cancel()
        serviceScope.cancel()
        closeAudioEffectSession()
        equalizer?.release()
        equalizer = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }

    // Tells the system (and any app listening for it -- Dolby/Dirac/Samsung sound panels, Wavelet,
    // Poweramp EQ, MusicFX) that this player has an audio session they may attach effects to. The
    // framework doesn't send this on a player's behalf: every media app has to broadcast it itself,
    // which is what lets those DSPs process this app's audio the way they do for other players.
    private fun openAudioEffectSession(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == effectControlSessionId) return
        closeAudioEffectSession()
        effectControlSessionId = audioSessionId
        sendAudioEffectBroadcast(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, audioSessionId)
    }

    private fun closeAudioEffectSession() {
        val sessionId = effectControlSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        effectControlSessionId = C.AUDIO_SESSION_ID_UNSET
        sendAudioEffectBroadcast(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, sessionId)
    }

    private fun sendAudioEffectBroadcast(action: String, audioSessionId: Int) {
        sendBroadcast(
            Intent(action)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        )
    }

    // Coalesces a burst of rapid-fire session-id notifications (e.g. repeatedly tapping a
    // pitch/speed preset, each of which can make ExoPlayer reconfigure its AudioTrack and report
    // a "changed" session id) into a single rebuild once things settle, instead of one rebuild
    // per notification -- see equalizerSessionId's doc for why that matters.
    private fun scheduleEqualizerRebuild(audioSessionId: Int) {
        equalizerRebuildJob?.cancel()
        equalizerRebuildJob = serviceScope.launch {
            delay(250)
            recreateEqualizer(audioSessionId)
        }
    }

    private fun recreateEqualizer(audioSessionId: Int) {
        Log.i(TAG, "recreateEqualizer: audioSessionId=$audioSessionId desiredEqEnabled=$desiredEqEnabled")
        // Same session this equalizer is already attached to -- most "changed" notifications
        // settle back on the id they started from, so without this an idle player sitting on an
        // unchanged session would still pay a release+recreate for every notification.
        if (audioSessionId == equalizerSessionId && equalizer != null) {
            return
        }
        equalizerSessionId = audioSessionId
        equalizer?.release()
        equalizer = null
        // Not attached at all until the user actually turns EQ on (see applyDesiredEqState) --
        // attaching *any* effect, even one that's immediately set disabled, forces this session
        // off its offloaded/direct output path onto the regular mixer (observed on Vivo as
        // AudioTrack flags 0x8 -- direct/offload -- getting torn down and recreated every time an
        // effect attaches), which is what was producing both the "global output mix" warning
        // (offloaded sessions can't host a per-track effect at all, so the platform silently
        // attaches it globally instead) and an audible position hiccup each time it happened.
        // Most installs never touch EQ, so there's no reason to pay that cost on every session
        // change just to attach an effect that does nothing.
        if (!desiredEqEnabled || audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
            return
        }
        attachEqualizer(audioSessionId)
        applyDesiredEqState()
    }

    private fun attachEqualizer(audioSessionId: Int) {
        equalizer = try {
            // Priority 0 (lowest): defers to any other effect -- including the device's own OEM
            // sound enhancements -- already attached to this session rather than fighting them
            // for control, which is the class of conflict the in-app warning dialog calls out.
            Equalizer(0, audioSessionId)
        } catch (e: Exception) {
            // Some OEM audio stacks refuse to attach a second equalizer effect at all.
            null
        }
    }

    private fun applyDesiredEqState() {
        // EQ just turned on and nothing is attached for the current session yet (recreateEqualizer
        // skips attaching while EQ is off -- see its comment) -- attach now, against whatever
        // session is currently active, rather than waiting for the next session-id notification
        // that might not come for a long time.
        if (desiredEqEnabled && equalizer == null && equalizerSessionId != C.AUDIO_SESSION_ID_UNSET) {
            attachEqualizer(equalizerSessionId)
        } else if (!desiredEqEnabled && equalizer != null) {
            // EQ just turned off -- release it outright (not just disable it) so this session can
            // drop back onto its offloaded/direct output path instead of staying pinned off it
            // for the rest of playback.
            equalizer?.release()
            equalizer = null
        }
        val eq = equalizer ?: return
        try {
            eq.enabled = desiredEqEnabled
            val gains = desiredEqGains ?: return
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()
            for (band in gains.indices) {
                if (band >= eq.numberOfBands) break
                // Gains arrive from the UI in dB; the platform Equalizer works in millibels.
                val millibel = (gains[band] * 100f).toInt().coerceIn(minLevel, maxLevel)
                eq.setBandLevel(band.toShort(), millibel.toShort())
            }
        } catch (e: Exception) {
            // The effect may have been released concurrently (session id changing mid-apply), or
            // an OEM DSP rejected the setting -- either way, nothing left to do but drop it.
        }
    }

    private inner class EqualizerSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(COMMAND_EQ_APPLY, Bundle.EMPTY))
                .build()
            // Media3 1.11's super.onConnect() default dropped to a read-only player command set
            // (no COMMAND_PLAY_PAUSE, seek, etc.) unless a controller is explicitly trusted -- this
            // app has exactly one controller (its own UI/notification), so there's no one to
            // restrict; granting the full command set here is what play/pause/seek/skip actually
            // need to keep working at all.
            val playerCommands = Player.Commands.Builder().addAllCommands().build()
            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == COMMAND_EQ_APPLY) {
                desiredEqEnabled = args.getBoolean(EXTRA_EQ_ENABLED, false)
                desiredEqGains = args.getFloatArray(EXTRA_EQ_GAINS)
                applyDesiredEqState()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    companion object {
        const val COMMAND_EQ_APPLY = "com.vui.vaporwave.eq.APPLY"
        const val EXTRA_EQ_ENABLED = "enabled"
        const val EXTRA_EQ_GAINS = "gains"
        private const val TAG = "VaporwaveDebug"
    }
}

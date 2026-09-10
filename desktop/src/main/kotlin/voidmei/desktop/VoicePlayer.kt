package voidmei.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import voidmei.telemetry.FlightAlert
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10

/** Matches legacy gain mapping for nonzero volume; zero now stops playback completely. */
internal fun voiceGain(volume: Int, minimum: Float, maximum: Float): Float {
    require(volume in 1..200)
    require(minimum.isFinite() && maximum.isFinite() && minimum <= 0 && maximum >= 0)
    return (if (volume <= 100) minimum + log10(volume.toFloat()) * -minimum / 2f
        else (volume - 100) * maximum / 100f).coerceIn(minimum, maximum)
}

/** One active clip, with explicit lifecycle ownership and no queue of obsolete warnings. */
class VoicePlayer(private val clipFactory: () -> Clip = AudioSystem::getClip) : AutoCloseable {
    private var clip: Clip? = null
    private var playingAlert: FlightAlert? = null
    private var closed = false

    private fun applyVolume(target: Clip, volume: Int) {
        if (!target.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            check(volume == 100) { "音频设备不支持音量调节" }
            return
        }
        val gain = target.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        gain.value = voiceGain(volume, gain.minimum, gain.maximum)
    }

    @Synchronized fun setVolume(volume: Int) {
        require(volume in 0..200)
        if (volume == 0) { stop(); return }
        clip?.let {
            try { applyVolume(it, volume) }
            catch (e: Exception) { stop(); throw e }
        }
    }

    suspend fun play(alert: FlightAlert, volume: Int = 100, resources: VoiceResources = VoiceResources()) = withContext(Dispatchers.IO) {
        require(volume in 0..200)
        val context = currentCoroutineContext()
        synchronized(this@VoicePlayer) {
            context.ensureActive()
            if (closed) return@synchronized
            stop()
            if (volume == 0) return@synchronized
            resources.open(alert).use { audio ->
                val next = clipFactory()
                try {
                    next.open(audio)
                    context.ensureActive()
                    applyVolume(next, volume)
                    next.start()
                    clip = next
                    playingAlert = alert
                }
                catch (e: Exception) {
                    try { next.close() } catch (cleanup: Exception) { if (cleanup !== e) e.addSuppressed(cleanup) }
                    throw e
                }
            }
        }
    }
    /** Only a higher severity may interrupt an active clip; equal severity waits for completion. */
    @Synchronized fun canPlay(alert: FlightAlert): Boolean = !closed &&
        (clip?.isRunning != true || playingAlert?.let { alert.severity < it.severity } == true)

    @Synchronized fun isPlaying(): Boolean = clip?.isRunning == true

    @Synchronized fun stop() {
        val previous = clip
        clip = null
        playingAlert = null
        if (previous != null) {
            var failure: Exception? = null
            try { previous.stop() } catch (e: Exception) { failure = e }
            try { previous.close() } catch (e: Exception) {
                if (failure == null) failure = e else if (failure !== e) failure.addSuppressed(e)
            }
            failure?.let { throw it }
        }
    }
    @Synchronized override fun close() { closed = true; stop() }
}

package voidmei.desktop

import java.lang.reflect.Proxy
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import kotlin.test.*
import voidmei.telemetry.FlightAlert

class VoicePlayerTest {
    @Test fun previewCannotInterruptWarningsAndStoppingPreviewPreservesWarning(): Unit = runBlocking {
        val device = Device()
        val player = VoicePlayer { device.clip }
        try {
            player.play(FlightAlert.CRITICAL_AOA, preview = true)
            assertTrue(player.canPlay(FlightAlert.LOW_FUEL))
            player.play(FlightAlert.LOW_FUEL)
            player.stopPreview()
            assertTrue(player.isPlaying())
            assertFailsWith<IllegalStateException> { player.play(FlightAlert.CONNECTION_READY, preview = true) }
            assertTrue(player.isPlaying())
            assertEquals(2, device.starts)
            player.stop()
            player.play(FlightAlert.CONNECTION_READY, preview = true)
            player.stopPreview()
            assertFalse(player.isPlaying())
        } finally { player.close() }
    }

    @Test fun connectionCueCanBeInterruptedByAdvisoriesAndWarnings(): Unit = runBlocking {
        val device = Device()
        val player = VoicePlayer { device.clip }
        try {
            player.play(FlightAlert.CONNECTION_READY)
            assertFalse(player.canPlay(FlightAlert.CONNECTION_READY))
            assertTrue(player.canPlay(FlightAlert.HIGH_AOA))
            assertTrue(player.canPlay(FlightAlert.CRITICAL_AOA))
            player.play(FlightAlert.HIGH_AOA)
            assertFalse(player.canPlay(FlightAlert.CONNECTION_READY))
        } finally { player.close() }
    }

    private class Device(val gainSupported: Boolean = true, val onOpen: () -> Unit = {},
        val onStop: () -> Unit = {}, val onClose: () -> Unit = {}) {
        var running = false
        var starts = 0
        var closes = 0
        val gain = object : FloatControl(Type.MASTER_GAIN, -80f, 6f, .1f, 0, 0f, "dB") {}
        val clip = Proxy.newProxyInstance(Clip::class.java.classLoader, arrayOf(Clip::class.java)) { _, method, _ ->
            when (method.name) {
                "open" -> { onOpen(); null }
                "isControlSupported" -> gainSupported
                "getControl" -> gain
                "isRunning" -> running
                "start" -> { starts++; running = true; null }
                "stop" -> { running = false; onStop(); null }
                "close" -> { closes++; running = false; onClose(); null }
                else -> null
            }
        } as Clip
    }

    @Test fun deviceCleanupPreservesOriginalFailureAndDetachesClip(): Unit = runBlocking {
        val stopFailure = IllegalStateException("stop failed")
        val closeFailure = IllegalStateException("close failed")
        val device = Device(onStop = { throw stopFailure }, onClose = { throw closeFailure })
        val player = VoicePlayer { device.clip }
        player.play(FlightAlert.CRITICAL_AOA)
        assertSame(stopFailure, assertFailsWith<IllegalStateException> { player.stop() })
        assertEquals(listOf(closeFailure), stopFailure.suppressed.toList())
        assertEquals(1, device.closes)
        assertFalse(player.isPlaying())
        player.close()
        assertEquals(1, device.closes)
    }

    @Test fun failedOpenKeepsItsCauseWhenCleanupAlsoFails(): Unit = runBlocking {
        val original = CancellationException("obsolete connection")
        val cleanup = IllegalStateException("close failed")
        val device = Device(onOpen = { throw original }, onClose = { throw cleanup })
        val player = VoicePlayer { device.clip }
        try {
            val thrown = assertFailsWith<CancellationException> { player.play(FlightAlert.CRITICAL_AOA) }
            // Coroutine stack recovery may copy the cancellation and retain the original as its cause.
            assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === original })
            assertEquals(listOf(cleanup), original.suppressed.toList())
            assertEquals(0, device.starts)
            assertEquals(1, device.closes)
        } finally { player.close() }
    }

    @Test fun cancelledAudioOpenNeverStartsAnObsoleteWarning(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = java.util.concurrent.CountDownLatch(1)
        val device = Device(onOpen = {
            entered.complete(Unit)
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
        })
        val player = VoicePlayer { device.clip }
        val job = launch(Dispatchers.Default) { player.play(FlightAlert.CRITICAL_AOA) }
        try {
            withTimeout(5000) { entered.await() }
            job.cancel()
            release.countDown()
            withTimeout(5000) { job.join() }
            assertTrue(job.isCancelled)
            assertEquals(0, device.starts)
            assertEquals(1, device.closes)
            assertFalse(player.isPlaying())
        } finally {
            release.countDown()
            job.cancelAndJoin()
            player.close()
        }
    }

    @Test fun activeAudioDefersQueuedControlVoicePastTheMinimumInterval() = runBlocking {
        val device = Device()
        val player = VoicePlayer { device.clip }
        val evaluator = voidmei.telemetry.FlightAlerts()
        val flight = voidmei.telemetry.ConnectionState.Flying(
            voidmei.telemetry.TelemetryParser.parse("""{"valid":true}""", """{"valid":true}""")!!
                .copy(iasKmh = 600.0), voidmei.telemetry.FlightMetrics())
        fun update(time: Long) = evaluator.update(flight, null, time, true,
            controlSpeeds = voidmei.fm.ControlEffectiveSpeeds(elevatorKmh = 500.0, rudderKmh = 500.0), voiceAvailable = player::canPlay)
        try {
            assertFalse(player.isPlaying())
            assertEquals(FlightAlert.ELEVATOR_EFFECTIVENESS, update(0).voice)
            player.play(FlightAlert.ELEVATOR_EFFECTIVENESS)
            assertTrue(player.isPlaying())
            assertNull(update(2000).voice) // The audio device has not finished this longer clip.
            device.running = false
            assertFalse(player.isPlaying())
            assertEquals(FlightAlert.RUDDER_EFFECTIVENESS, update(2200).voice)
            player.play(FlightAlert.RUDDER_EFFECTIVENESS)
            player.stop()
            assertFalse(player.isPlaying())
        } finally { player.close() }
    }

    @Test fun warningCanInterruptAdvisoryAfterGlobalIntervalButPeersWait() = runBlocking {
        val device = Device()
        val player = VoicePlayer { device.clip }
        val evaluator = voidmei.telemetry.FlightAlerts()
        val base = voidmei.telemetry.TelemetryParser.parse("""{"valid":true,"Mfuel, kg":5,"Mfuel0, kg":100}""",
            """{"valid":true,"type":"test"}""")!!
        fun update(time: Long, critical: Boolean = false) = evaluator.update(
            voidmei.telemetry.ConnectionState.Flying(base.copy(iasKmh = 300.0, angleOfAttackDeg = if (critical) 20.0 else 0.0),
                voidmei.telemetry.FlightMetrics()), voidmei.fm.WingLimits(null, null, null, 20.0), time, true,
            voiceAvailable = player::canPlay)
        try {
            assertEquals(FlightAlert.LOW_FUEL, update(0).voice)
            player.play(FlightAlert.LOW_FUEL)
            assertFalse(player.canPlay(FlightAlert.HIGH_AOA))
            assertTrue(player.canPlay(FlightAlert.CRITICAL_AOA))
            assertNull(update(1000, true).voice)
            assertEquals(FlightAlert.CRITICAL_AOA, update(2000, true).voice)
            player.play(FlightAlert.CRITICAL_AOA)
            assertEquals(1, device.closes)
            assertFalse(player.canPlay(FlightAlert.IAS_LIMIT))
            assertFalse(player.canPlay(FlightAlert.LOW_FUEL))
            assertNull(update(4000, true).voice)
            device.running = false
            assertEquals(FlightAlert.CRITICAL_AOA, update(4100, true).voice)
        } finally { player.close() }
        assertFalse(player.canPlay(FlightAlert.CRITICAL_AOA))
    }

    @Test fun unavailableDeviceCanRecoverAOneShotControlWarning() = runBlocking {
        val device = Device()
        var available = false
        val player = VoicePlayer { check(available) { "device unavailable" }; device.clip }
        val evaluator = voidmei.telemetry.FlightAlerts()
        val flight = voidmei.telemetry.ConnectionState.Flying(
            voidmei.telemetry.TelemetryParser.parse("""{"valid":true,"IAS, km/h":500}""", """{"valid":true}""")!!,
            voidmei.telemetry.FlightMetrics())
        fun update(time: Long) = evaluator.update(flight, null, time, true,
            controlSpeeds = voidmei.fm.ControlEffectiveSpeeds(400.0), voiceAvailable = player::canPlay)
        try {
            val failed = update(0)
            assertFailsWith<IllegalStateException> { player.play(failed.voice!!) }
            evaluator.voiceFailed(failed.voiceAttemptId!!)
            assertFalse(player.isPlaying())
            assertNull(update(100).voice)
            available = true
            val retry = update(2000)
            assertEquals(FlightAlert.AILERON_EFFECTIVENESS, retry.voice)
            player.play(retry.voice!!)
            assertEquals(1, device.starts)
            device.running = false
            assertNull(update(12000).voice)
        } finally { player.close() }
    }

    @Test fun gainMatchesLegacyEndpointsAndAttenuation() {
        assertEquals(-80f, voiceGain(1, -80f, 6f))
        assertEquals(-40f, voiceGain(10, -80f, 6f))
        assertEquals(0f, voiceGain(100, -80f, 6f))
        assertEquals(3f, voiceGain(150, -80f, 6f))
        assertEquals(6f, voiceGain(200, -80f, 6f))
        assertFails { voiceGain(201, -80f, 6f) }
    }

    @Test fun muteDoesNotOpenDeviceAndLiveVolumeStopsCurrentClip() = runBlocking {
        val device = Device()
        var opened = 0
        val player = VoicePlayer { opened++; device.clip }
        player.play(FlightAlert.entries.first(), 0)
        assertEquals(0, opened)
        player.play(FlightAlert.entries.first(), 10)
        assertEquals(-40f, device.gain.value)
        assertEquals(1, device.starts)
        player.setVolume(150)
        assertEquals(3f, device.gain.value)
        player.setVolume(0)
        assertEquals(1, device.closes)
        player.close()
        player.play(FlightAlert.entries.first(), 100)
        assertEquals(1, opened)
    }

    @Test fun unsupportedAttenuationNeverStartsAtFullVolume() = runBlocking {
        val device = Device(false)
        val player = VoicePlayer { device.clip }
        assertFailsWith<IllegalStateException> { player.play(FlightAlert.entries.first(), 10) }
        assertEquals(0, device.starts)
        assertEquals(1, device.closes)
        player.play(FlightAlert.entries.first(), 100)
        assertEquals(1, device.starts)
        assertFailsWith<IllegalStateException> { player.setVolume(50) }
        assertEquals(2, device.closes)
        player.close()
    }
}

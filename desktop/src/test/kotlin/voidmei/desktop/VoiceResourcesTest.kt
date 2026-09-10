package voidmei.desktop

import kotlin.test.*
import voidmei.telemetry.FlightAlert
import javax.sound.sampled.AudioSystem
import java.io.BufferedInputStream

class VoiceResourcesTest {
    @Test fun bundledWarningsAreReadableAndWithinResourceDurationLimit() {
        for (alert in FlightAlert.entries) {
            val resource = javaClass.getResourceAsStream("/voice/${alert.voice}.wav")
            assertNotNull(resource, alert.voice)
            BufferedInputStream(resource).use { stream ->
                AudioSystem.getAudioInputStream(stream).use { audio ->
                    val seconds = audio.frameLength / audio.format.frameRate
                    assertTrue(seconds > 0 && seconds <= 30, "${alert.voice}: $seconds seconds")
                }
            }
        }
    }
}

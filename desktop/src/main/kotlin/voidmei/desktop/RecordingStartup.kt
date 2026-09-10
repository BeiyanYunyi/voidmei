package voidmei.desktop

import java.nio.file.Path
import voidmei.config.AppSettings

/** Called once per application launch; manual stop does not restart recording. */
internal suspend fun startConfiguredRecording(recorder: FlightRecorder, settings: AppSettings) {
    if (settings.recordingAutoStart) recorder.start(Path.of(settings.recordingDirectory))
}

package voidmei.desktop

import kotlin.test.*
import voidmei.config.AppSettings

class StartupHudOptionsTest {
    @Test fun recoveryOverridesSavedEnabledHudAndLauncherFlagWithoutResettingPreferences() {
        val saved = AppSettings(hudEnabled = true, hudWidthDp = 680, hudCompatibilityMode = true)
        assertEquals(saved, saved.withStartupHudOptions(emptyArray()))
        assertEquals(saved.copy(hudEnabled = false), saved.withStartupHudOptions(arrayOf("--hud", "--no-hud")))
        assertEquals(saved.copy(hudEnabled = false), saved.withStartupHudOptions(arrayOf("--no-hud", "--hud")))
    }

    @Test fun compatibilityCanBeSelectedBeforeReenablingHud() {
        val saved = AppSettings(hudEnabled = false)
        assertEquals(saved.copy(hudCompatibilityMode = true), saved.withStartupHudOptions(arrayOf("--compatible-hud")))
        assertEquals(saved.copy(hudEnabled = true, hudCompatibilityMode = true),
            saved.withStartupHudOptions(arrayOf("--hud", "--compatible-hud")))
    }
}

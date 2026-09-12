package voidmei.desktop

import kotlinx.coroutines.CancellationException
import kotlin.test.*

class MacGameFocusTest {
    @Test fun matchesTheActualExecutableRatherThanTheAppDirectoryOrLauncher() {
        val base = "/Applications/WarThunderLauncher.app/Contents/WarThunder.app/Contents/MacOS/"
        for (name in listOf("aces", "ACES"))
            assertEquals(GameFocus.GAME, detectMacGameFocus { MacForegroundApplication(50, base + name) })
        for (path in listOf(base + "launcher", base + "not-aces", "/aces/browser", base + "aces.exe"))
            assertEquals(GameFocus.OTHER, detectMacGameFocus { MacForegroundApplication(50, path) })
    }

    @Test fun changesAndMissingInformationDoNotHideTheHud() {
        val first = MacForegroundApplication(50, "/Games/aces")
        for (next in listOf(null, first.copy(pid = 51), first.copy(executable = "/Applications/browser"))) {
            var calls = 0
            assertEquals(GameFocus.UNKNOWN, detectMacGameFocus { if (calls++ == 0) first else next })
        }
        for (missing in listOf(null, first.copy(pid = 0), first.copy(pid = -1), first.copy(executable = " ")))
            assertEquals(GameFocus.UNKNOWN, detectMacGameFocus { missing })
        assertEquals(GameFocus.UNKNOWN, detectMacGameFocus { throw IllegalStateException("AppKit unavailable") })
        assertEquals(GameFocus.UNKNOWN, detectMacGameFocus { throw UnsatisfiedLinkError("symbol unavailable") })
        assertFailsWith<CancellationException> { detectMacGameFocus { throw CancellationException("stopped") } }
    }
}

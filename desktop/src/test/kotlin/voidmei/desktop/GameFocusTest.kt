package voidmei.desktop

import kotlinx.coroutines.CancellationException
import kotlin.test.*

class GameFocusTest {
    private class Source(var path: String? = "C:\\Games\\WarThunder\\aces.exe") : ForegroundProcessSource {
        var active: Long? = 10L
        var pid: Int? = 50
        var afterQuery: () -> Unit = {}
        override fun activeWindow() = active
        override fun processId(window: Long) = pid
        override fun imageName(processId: Int): String? { afterQuery(); return path }
    }

    @Test fun usesExactExecutableNameInsteadOfDirectoryOrTitle() {
        val source = Source()
        assertEquals(GameFocus.GAME, detectGameFocus(source))
        source.path = "C:\\ACES.EXE"
        assertEquals(GameFocus.GAME, detectGameFocus(source))
        for (other in listOf("C:\\aces.exe\\launcher.exe", "C:\\fake-aces.exe", "C:\\notepad.exe")) {
            source.path = other
            assertEquals(GameFocus.OTHER, detectGameFocus(source))
        }
    }

    @Test fun failedOrRacingDetectionKeepsVisibilityUnknown() {
        val source = Source()
        source.path = null
        assertEquals(GameFocus.UNKNOWN, detectGameFocus(source))
        source.path = "C:\\notepad.exe"
        source.afterQuery = { source.active = 11 }
        assertEquals(GameFocus.UNKNOWN, detectGameFocus(source))
        source.afterQuery = { source.pid = 51 }
        assertEquals(GameFocus.UNKNOWN, detectGameFocus(source))
        source.afterQuery = { throw UnsatisfiedLinkError("unavailable") }
        assertEquals(GameFocus.UNKNOWN, detectGameFocus(source))
        source.afterQuery = { throw CancellationException("stopped") }
        assertFailsWith<CancellationException> { detectGameFocus(source) }
        source.pid = 0
        assertEquals(GameFocus.UNKNOWN, detectGameFocus(source))
    }
}

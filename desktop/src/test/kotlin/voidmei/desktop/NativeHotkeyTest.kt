package voidmei.desktop

import com.github.kwhat.jnativehook.GlobalScreen
import java.awt.Robot
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/** Only run with VOIDMEI_TEST_ISOLATED_X11=1 on a dedicated Xvfb display. */
class NativeHotkeyTest {
    @Test fun nativeInputWorksAfterUnregisterAndReregister() {
        check(System.getenv("VOIDMEI_TEST_ISOLATED_X11") == "1") {
            "Run this test on an isolated Xvfb display with VOIDMEI_TEST_ISOLATED_X11=1"
        }
        val robot = Robot().apply { autoDelay = 35 }
        val count = AtomicInteger()
        repeat(2) { iteration ->
            val event = CountDownLatch(1)
            HudHotkey { count.incrementAndGet(); event.countDown() }.use { session ->
                session.start()
                assertTrue(GlobalScreen.isNativeHookRegistered())
                try {
                    robot.keyPress(KeyEvent.VK_CONTROL)
                    robot.keyPress(KeyEvent.VK_SHIFT)
                    robot.keyPress(KeyEvent.VK_H)
                    robot.delay(900) // Cross the X11 repeat delay while keeping the chord held.
                } finally {
                    robot.keyRelease(KeyEvent.VK_H)
                    robot.keyRelease(KeyEvent.VK_SHIFT)
                    robot.keyRelease(KeyEvent.VK_CONTROL)
                }
                assertTrue(event.await(5, TimeUnit.SECONDS), "No callback on registration ${iteration + 1}")
            }
            assertFalse(GlobalScreen.isNativeHookRegistered())
            assertEquals(iteration + 1, count.get())
        }
    }
}

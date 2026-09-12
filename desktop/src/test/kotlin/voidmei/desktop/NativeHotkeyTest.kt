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
    @Test fun customKeysCanChangeDuringNativeSession() {
        check(System.getenv("VOIDMEI_TEST_ISOLATED_X11") == "1")
        val binding = java.util.concurrent.atomic.AtomicReference(voidmei.config.ModelHotkey.parse("P"))
        val count = AtomicInteger()
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)
        val third = CountDownLatch(1)
        val fourth = CountDownLatch(1)
        val robot = Robot().apply { autoDelay = 35 }
        fun press(key: Int, alt: Boolean = false) {
            try {
                if (alt) robot.keyPress(KeyEvent.VK_ALT)
                robot.keyPress(key)
            } finally {
                robot.keyRelease(key)
                if (alt) robot.keyRelease(KeyEvent.VK_ALT)
            }
        }
        HudHotkey(modelBinding = { binding.get() }, modelToggle = {
            when (count.incrementAndGet()) { 1 -> first.countDown(); 2 -> second.countDown(); 3 -> third.countDown(); 4 -> fourth.countDown() }
        }) {}.use { session ->
            session.start()
            press(KeyEvent.VK_P)
            assertTrue(first.await(5, TimeUnit.SECONDS))
            binding.set(voidmei.config.ModelHotkey.parse("Alt+F1"))
            press(KeyEvent.VK_P)
            press(KeyEvent.VK_F1, alt = true)
            assertTrue(second.await(5, TimeUnit.SECONDS))
            robot.delay(150)
            assertEquals(2, count.get())
            binding.set(voidmei.config.ModelHotkey.parse("Ctrl+DIGIT1"))
            try { robot.keyPress(KeyEvent.VK_CONTROL); press(KeyEvent.VK_1) }
            finally { robot.keyRelease(KeyEvent.VK_CONTROL) }
            assertTrue(third.await(5, TimeUnit.SECONDS), "No digit callback")
            binding.set(voidmei.config.ModelHotkey.parse("LEFT"))
            press(KeyEvent.VK_LEFT)
            assertTrue(fourth.await(5, TimeUnit.SECONDS), "No arrow callback")
            assertEquals(4, count.get())
            assertTrue(GlobalScreen.isNativeHookRegistered())
        }
        assertFalse(GlobalScreen.isNativeHookRegistered())
    }

    @Test fun nativeInputWorksAfterUnregisterAndReregister() {
        check(System.getenv("VOIDMEI_TEST_ISOLATED_X11") == "1") {
            "Run this test on an isolated Xvfb display with VOIDMEI_TEST_ISOLATED_X11=1"
        }
        val robot = Robot().apply { autoDelay = 35 }
        val count = AtomicInteger()
        val models = AtomicInteger()
        repeat(2) { iteration ->
            val event = CountDownLatch(1)
            val modelEvent = CountDownLatch(1)
            HudHotkey(modelToggle = { models.incrementAndGet(); modelEvent.countDown() }) { count.incrementAndGet(); event.countDown() }.use { session ->
                session.start()
                assertTrue(GlobalScreen.isNativeHookRegistered())
                try {
                    robot.keyPress(KeyEvent.VK_CONTROL)
                    robot.keyPress(KeyEvent.VK_SHIFT)
                    robot.keyPress(KeyEvent.VK_H)
                    robot.keyPress(KeyEvent.VK_M)
                    robot.delay(900) // Cross the X11 repeat delay while keeping the chord held.
                } finally {
                    robot.keyRelease(KeyEvent.VK_M)
                    robot.keyRelease(KeyEvent.VK_H)
                    robot.keyRelease(KeyEvent.VK_SHIFT)
                    robot.keyRelease(KeyEvent.VK_CONTROL)
                }
                assertTrue(modelEvent.await(5, TimeUnit.SECONDS), "No model callback")
                assertTrue(event.await(5, TimeUnit.SECONDS), "No callback on registration ${iteration + 1}")
            }
            assertFalse(GlobalScreen.isNativeHookRegistered())
            assertEquals(iteration + 1, count.get())
            assertEquals(iteration + 1, models.get())
        }
    }
}

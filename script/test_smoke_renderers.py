#!/usr/bin/env python3
"""Renderer readiness diagnostics without a native display or package launch."""
import unittest
from smoke_kmp_deb import check_reported_renderers


class RendererReportsTest(unittest.TestCase):
    def test_pending_startup_and_matching_reports_are_allowed(self):
        for text in ("", "[SKIKO] starting", "[VoidMei · Kotlin] 绘制后端：OPENGL\n",
                     "[VoidMei · Kotlin] 绘制后端：OPENGL\n[VoidMei HUD] 绘制后端：OPENGL\r\n"):
            check_reported_renderers(text, "OPENGL")
        check_reported_renderers("[VoidMei · Kotlin] 绘制后端：SOFTWARE_FAST\n", "SOFTWARE_FAST")

    def test_main_and_hud_fallbacks_identify_the_failed_window(self):
        for window in ("VoidMei · Kotlin", "VoidMei HUD"):
            with self.assertRaises(RuntimeError) as failure:
                check_reported_renderers("[%s] 绘制后端：SOFTWARE_FAST\n" % window, "OPENGL")
            self.assertIn(window, str(failure.exception))
            self.assertIn("expected OPENGL, actual SOFTWARE_FAST", str(failure.exception))
        with self.assertRaises(RuntimeError):
            check_reported_renderers("[VoidMei · Kotlin] 绘制后端：OPENGL\n"
                                     "[VoidMei · Kotlin] 绘制后端：SOFTWARE_FAST\n", "OPENGL")

    def test_independent_hud_expectation_remains_strict(self):
        reports = "[VoidMei · Kotlin] 绘制后端：SOFTWARE_FAST\n[VoidMei HUD] 绘制后端：OPENGL\n"
        check_reported_renderers(reports, "SOFTWARE_FAST", hud_renderer="OPENGL")
        with self.assertRaises(RuntimeError):
            check_reported_renderers(reports, "SOFTWARE_FAST")
        with self.assertRaises(RuntimeError):
            check_reported_renderers(reports.replace("后端：OPENGL", "后端：SOFTWARE_FAST"),
                                     "SOFTWARE_FAST", hud_renderer="OPENGL")

    def test_disabled_hud_does_not_participate_in_renderer_check(self):
        check_reported_renderers("[VoidMei · Kotlin] 绘制后端：OPENGL\n"
                                 "[VoidMei HUD] 绘制后端：SOFTWARE_FAST\n", "OPENGL", hud=False)


if __name__ == "__main__":
    unittest.main()

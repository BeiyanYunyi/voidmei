package voidmei.desktop

import org.junit.Test
import kotlin.test.*

class RendererDiagnosticsTest {
    @Test fun distinguishesRequestedApiFromObservedFallback() {
        val report = formatRendererDiagnostics("SOFTWARE_FAST", "OPENGL", "SkiaLayer", 1.5, 2.0, "OS: linux x64")
        assertTrue(report.startsWith("绘制后端：SOFTWARE_FAST\n"))
        assertTrue(report.contains("请求后端：OPENGL（与实际后端不同）"))
        assertTrue(report.contains("显示缩放：1.5 × 2.0"))
        assertTrue(report.contains("OS: linux x64"))
    }

    @Test fun compatibleHudDoesNotInventDeviceOrHardwareAcceleration() {
        val report = formatRendererDiagnostics("OPENGL", "OPENGL", "SwingGraphics", 2.0, 2.0, "")
        assertTrue(report.contains("显示路径：SwingGraphics"))
        assertTrue(report.contains("设备信息：当前显示路径未提供"))
        assertTrue(report.contains("不能单凭后端名称判断硬件加速"))
        assertFalse(report.contains("与实际后端不同"))
    }

    @Test fun initializationIsNotReportedAsFallbackAndDeviceDetailsArePreserved() {
        val pending = formatRendererDiagnostics(null, "OPENGL", "未知", null, null, "")
        assertTrue(pending.startsWith("绘制后端：等待初始化"))
        assertFalse(pending.contains("与实际后端不同"))
        assertFalse(pending.contains("显示缩放"))
        val details = "Vendor: NVIDIA Corporation\nModel: Test GPU"
        assertTrue(formatRendererDiagnostics("OPENGL", "OPENGL", "SkiaLayer", 1.0, 1.0, details).contains(details))
    }
}

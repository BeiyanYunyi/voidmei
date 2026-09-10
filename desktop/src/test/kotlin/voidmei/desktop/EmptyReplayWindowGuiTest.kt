package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import voidmei.recording.RecordedFrame
import kotlin.test.*

class EmptyReplayWindowGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun changingFieldsRebuildsValuesWithoutLosingTheSelectedFrame() {
        val text = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh,fuel_kg\n0,1000,0,test,300,50\n1,2000,1000,test,400,40\n"
        var fields by mutableStateOf(listOf("ias_kmh" to "IAS"))
        var seek by mutableStateOf(ReplaySeekRequest(0, 0))
        var frame: RecordedFrame? = null
        var cleared = 0
        compose.setContent { MaterialTheme { Column {
            RecordingReplayPanel(text, fields, seek) { frame = it; if (it == null) cleared++ }
        } } }
        compose.onNodeWithText("原始帧同步回看").performClick()
        compose.waitUntil(5000) { frame != null }
        compose.onNodeWithText("下一原始帧").performClick()
        compose.waitUntil(5000) { frame?.sampleId == 1L }
        var before = 0
        compose.runOnIdle { before = cleared; fields = listOf("fuel_kg" to "燃油", "ias_kmh" to "IAS") }
        compose.waitUntil(5000) { frame?.values?.get("fuel_kg") == 40.0 }
        compose.runOnIdle { assertEquals(1L, frame?.sampleId); assertTrue(cleared > before) }
        compose.onNodeWithText("燃油：40.0000").assertExists()
        compose.runOnIdle { fields = listOf("fuel_kg" to "剩余燃油") }
        compose.waitUntil(5000) { frame?.values?.keys == setOf("fuel_kg") }
        compose.onNodeWithText("剩余燃油：40.0000").assertExists()
        compose.onNodeWithText("IAS：400.0000").assertDoesNotExist()
        compose.runOnIdle { seek = ReplaySeekRequest(1, 0) }
        compose.waitUntil(5000) { frame?.sampleId == 0L }
        compose.onNodeWithText("剩余燃油：50.0000").assertExists()
    }

    @Test fun emptyWindowClearsThePublishedFrameAndCanReturnToFullReplay() {
        val text = "sample_id,utc_epoch_ms,elapsed_ms,aircraft,ias_kmh\n0,1000,0,test,300\n1,2000,1000,test,400\n"
        var window by mutableStateOf(0L..1000L)
        var frame: RecordedFrame? = null
        compose.setContent { MaterialTheme { Column {
            RecordingReplayPanel(text, listOf("ias_kmh" to "IAS"), window = window) { frame = it }
        } } }
        compose.onNodeWithText("原始帧同步回看").performClick()
        compose.waitUntil(5000) { frame != null }
        compose.onNodeWithContentDescription("仅回看曲线区间").performClick()
        compose.onNodeWithText("下一原始帧").performClick()
        compose.waitUntil(5000) { frame?.sampleId == 1L }
        compose.runOnIdle { window = 200L..800L }
        compose.onNodeWithText("当前区间没有原始帧").assertIsDisplayed()
        compose.onNodeWithTag("replay-frame").assertDoesNotExist()
        compose.waitUntil(5000) { frame == null }
        compose.onNodeWithText("回看完整记录").performClick()
        compose.waitUntil(5000) { frame != null }
        compose.onNodeWithTag("replay-frame").assertIsDisplayed()
        compose.onNodeWithContentDescription("仅回看曲线区间").assertIsOff()
        compose.runOnIdle { assertEquals(1L, frame?.sampleId) }
        compose.onNodeWithText("原始帧同步回看").performClick()
        compose.waitUntil(5000) { frame == null }
    }
}

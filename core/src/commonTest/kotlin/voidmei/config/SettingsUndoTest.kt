package voidmei.config

import kotlin.test.*

class SettingsUndoTest {
    @Test fun undoRestoresOnlyImportedValuesAndPreservesLaterEdits() {
        val scene = HudSceneLayout(1000, 600, listOf(HudRegion("flight", HudRegionContent.FLIGHT, 0, 0, 200, 100)))
        val before = AppSettings(hudSceneLayout = scene)
        val after = before.copy(endpoint = "http://localhost:9000", pollIntervalMs = 80,
            hudFields = listOf("ias"), hudSceneLayout = scene.moveRegion("flight", 200, 300))
        val undo = SettingsUndo.capture(before, after)!!
        assertEquals(before, undo.preview(after).settings)
        val current = after.copy(hudFields = listOf("mach"), hudEnabled = true,
            hudSceneLayout = after.hudSceneLayout!!.moveRegion("flight", 400, 300))
        val result = undo.preview(current)
        assertEquals(setOf("endpoint", "pollIntervalMs"), result.restoredKeys)
        assertEquals(setOf("hudFields", "hudSceneLayout"), result.skippedKeys)
        assertEquals(before.copy(hudFields = current.hudFields, hudEnabled = true, hudSceneLayout = current.hudSceneLayout), result.settings)
        assertEquals(result.settings, SettingsJson.decode(SettingsJson.encode(result.settings)))
    }

    @Test fun noOpDoesNotCreateRecordAndAllConflictsLeaveCurrentSettingsIntact() {
        val before = AppSettings()
        assertNull(SettingsUndo.capture(before, before.copy()))
        val after = before.copy(pollIntervalMs = 80)
        val undo = SettingsUndo.capture(before, after)!!
        val current = after.copy(pollIntervalMs = 120)
        val result = undo.preview(current)
        assertSame(current, result.settings)
        assertTrue(result.restoredKeys.isEmpty())
        assertEquals(setOf("pollIntervalMs"), result.skippedKeys)
        assertEquals(before, undo.preview(after).settings)
    }
}

package voidmei.config

import kotlin.test.*

class OfflineModelPreferencesTest {
    @Test fun persistsOnlyAppliedIdentifiersAndKeepsLiveDirectoryIndependent() {
        val expected = AppSettings(fmDataRoot = "live-data", offlineModels = OfflineModelPreferences("offline-data", "p-51c", "bf_109"))
        assertEquals(expected, SettingsJson.decode(SettingsJson.encode(expected)))
        assertEquals(OfflineModelPreferences(), SettingsJson.decode("""{"version":1}""").offlineModels)
        assertEquals(OfflineModelPreferences(), SettingsJson.decode("""{"version":1,"offlineModels":{"aircraft":null}}""").offlineModels)
        assertEquals(expected.offlineModels, expected.withHudLayout(AppSettings()).offlineModels)
    }
    @Test fun rejectsMalformedPathsIdentifiersAndJsonTypes() {
        for (name in listOf("", "P51", "../p51", "a/b", "a.blkx", "a b", "x".repeat(129))) {
            assertFails { OfflineModelPreferences(aircraft = name) }
            assertFails { OfflineModelPreferences(baselineAircraft = name) }
        }
        for (root in listOf("", " ", "data\nfile", "x".repeat(4097))) assertFails { OfflineModelPreferences(dataRoot = root) }
        for (json in listOf("null", "[]", "true", "1", "\"data\"", "{\"aircraft\":2}", "{\"baselineAircraft\":false}", "{\"dataRoot\":[]}")) {
            assertFails { SettingsJson.decode("{\"version\":1,\"offlineModels\":$json}") }
        }
    }
}

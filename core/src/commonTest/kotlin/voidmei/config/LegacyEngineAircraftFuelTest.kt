package voidmei.config

import kotlin.test.*

class LegacyEngineAircraftFuelTest {
    private fun read(type: String, value: String) = LegacySettingsReader.read("""(panel p
        (item f :type $type :target disableEngineInfoLFuel :value $value))""")
    @Test fun switchPolarityAndOptionalTargetPreserveExistingFlightMigration() {
        val one = HudRegion("one", HudRegionContent.ENGINE, 0, 0, 300, 200)
        val scene = HudSceneLayout(1000, 600, listOf(one, one.copy(id = "two", showAircraftFuel = true),
            one.copy(id = "flight", content = HudRegionContent.FLIGHT)))
        val current = AppSettings(hudSceneLayout = scene)
        for (type in listOf("switch", "switch-inv")) for (value in listOf(false, true)) {
            val parsed = read(type, "$value")
            val visible = if (type == "switch-inv") value else !value
            assertEquals(visible, parsed.engineAircraftFuel)
            assertEquals(scene, parsed.applyTo(current).hudSceneLayout)
            val selected = parsed.copy(engineAircraftFuelRegionId = "two")
            val updated = selected.applyTo(current)
            assertEquals(scene.copy(regions = scene.regions.map { if (it.id == "two") it.copy(showAircraftFuel = visible) else it }), updated.hudSceneLayout)
            assertEquals(parsed.applyTo(current).hudFields, updated.hudFields)
            assertEquals(updated, selected.applyTo(updated))
            assertEquals(updated, SettingsJson.decode(SettingsJson.encode(updated)))
            for (id in listOf("missing", "flight")) assertEquals(scene, parsed.copy(engineAircraftFuelRegionId = id).applyTo(current).hudSceneLayout)
        }
        for (bad in listOf("TRUE", "1", "null")) assertFails { read("switch", bad) }
        assertFails { read("data", "true") }
    }
    @Test fun newlyCreatedControlCanReceiveFuelWithoutChangingItsLayout() {
        val scene = HudSceneLayout.initial(AppSettings())
        val region = scene.legacyEngineRegion("enableEngineControl")
        val parsed = read("switch-inv", "true").copy(engineRegionsToCreate = listOf(region), engineAircraftFuelRegionId = region.id)
        val updated = parsed.applyToScene(scene)!!
        assertEquals(region.copy(showAircraftFuel = true), updated.regions.last())
        assertEquals(updated, parsed.applyToScene(updated))
    }
}

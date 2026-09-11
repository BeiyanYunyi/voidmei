package voidmei.config

import kotlin.test.*

class HudMechanizationFieldTest {
    @Test fun switchesMapToFieldsAndNullableSelectionRoundTrips() {
        assertEquals(listOf("gear", "flap_bar"), HudMechanizationField.inherited(AppSettings(
            hudGear = true, hudFlaps = false, hudAirbrake = false, hudFlapBar = true)))
        assertEquals(listOf("flaps", "airbrake"), HudMechanizationField.inherited(AppSettings(
            hudGear = false, hudFlaps = true, hudAirbrake = true, hudFlapBar = false)))
        for (fields in listOf(null, emptyList(), listOf("flap_bar", "future"))) {
            val settings = AppSettings(hudSceneLayout = HudSceneLayout(400, 240, listOf(
                HudRegion("one", HudRegionContent.MECHANIZATION, 0, 0, 400, 240, fields = fields))))
            assertEquals(settings, SettingsJson.decode(SettingsJson.encode(settings)))
        }
    }
}

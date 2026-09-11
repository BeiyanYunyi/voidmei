package voidmei.config

enum class HudMechanizationField(val id: String, val label: String) {
    GEAR("gear", "起落架"), FLAPS("flaps", "襟翼／自动后掠"), AIRBRAKE("airbrake", "减速板"), FLAP_BAR("flap_bar", "襟翼开度条");

    companion object {
        fun inherited(settings: AppSettings): List<String> = buildList {
            if (settings.hudGear) add(GEAR.id)
            if (settings.hudFlaps) add(FLAPS.id)
            if (settings.hudAirbrake) add(AIRBRAKE.id)
            if (settings.hudFlapBar) add(FLAP_BAR.id)
        }
    }
}

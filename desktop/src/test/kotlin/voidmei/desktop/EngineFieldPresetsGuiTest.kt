package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import voidmei.config.*
import kotlin.test.*

class EngineFieldPresetsGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun regionPresetEnablesIndependentFieldsAndReturningToInheritanceWorks() {
        val original = HudRegion("one", HudRegionContent.ENGINE, 23, 41, 400, 300)
        var region by mutableStateOf(original)
        val settings = AppSettings(hudEngineFields = listOf("rpm", "power"))
        compose.setContent { MaterialTheme { Column(Modifier.width(500.dp).height(650.dp)
            .verticalScroll(rememberScrollState())) {
            HudRegionFieldsSettings(region, settings) { region = it }
        } } }
        compose.onNodeWithTag("hud-region-one-preset-controls").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(EngineFieldPreset.CONTROLS.fields, region.fields)
            assertEquals(original.copy(fields = region.fields), region)
            assertEquals(listOf("rpm", "power"), settings.hudEngineFields)
            assertNull(original.fields)
        }
        compose.onNodeWithTag("hud-region-one-preset-power").performScrollTo().performClick()
        compose.onNodeWithText("编辑区域字段（10）").performScrollTo().performClick()
        compose.onNodeWithTag("hud-region-field-remove-one-0").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(EngineFieldPreset.POWER.fields.drop(1), region.fields) }
        compose.onNodeWithTag("hud-region-fields-one").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(original, region) }
    }

    @Test fun globalPresetReplacesSelectionThenAllowsIndividualEdits() {
        var ids by mutableStateOf(listOf("magneto"))
        compose.setContent { MaterialTheme { Column(Modifier.width(500.dp).height(650.dp)
            .verticalScroll(rememberScrollState())) {
            HudEngineFieldSettings(ids) { ids = it }
        } } }
        compose.onNodeWithTag("hud-engine-preset-controls").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(EngineFieldPreset.CONTROLS.fields, ids) }
        compose.onNodeWithTag("hud-engine-field-throttle").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(EngineFieldPreset.CONTROLS.fields - "throttle", ids) }
        compose.onNodeWithTag("hud-engine-preset-default").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(voidmei.telemetry.HudEngineField.defaults, ids) }
    }
}

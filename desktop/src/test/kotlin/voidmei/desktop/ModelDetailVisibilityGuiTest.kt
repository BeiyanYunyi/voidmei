package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import voidmei.config.ModelDetailSection
import voidmei.fm.FlightModelParameters
import voidmei.telemetry.TelemetryParser
import kotlin.test.*

class ModelDetailVisibilityGuiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun visibilityDoesNotChangePublishedParametersAndCanRestoreAll() {
        val root = Files.createTempDirectory("voidmei-model-sections")
        val directory = Files.createDirectories(root.resolve("aces/gamedata/flightmodels/fm")).parent
        Files.writeString(directory.resolve("test.blkx"), "fmFile:t=\"fm/test.blk\"")
        Files.writeString(directory.resolve("fm/test.blkx"), "Mass { EmptyMass:r=2500; MaxNitro:r=120 }\nFlapsDestructionIndSpeedP:p4=0.5,500,1,300\nAileronPowerLoss:r=0.5\nMomentOfInertia:p3=1,2,3\nEngineType0 { Main { Type:t=Inline } Afterburner { NitroConsumption:r=0.25 } Temperature { Load0 { WaterTemperature:r=80 } Load1 { WaterTemperature:r=90; WorkTime:r=120; RecoverTime:r=60 } } }\nEngine0 { Type:i=0 }\nNoFlaps { CdMin:r=0.1 } FullFlaps { CdMin:r=0.2 } Fuselage { Cl0:r=0 } Fin { Cl0:r=1 } Stab { Cl0:r=2 }")
        val telemetry = TelemetryParser.parse("""{"valid":true}""", """{"valid":true,"type":"test"}""")!!
        var hidden by mutableStateOf(emptySet<ModelDetailSection>())
        var published: FlightModelParameters? = null
        var calls = 0
        try {
            compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                FlightModelPanel(telemetry, root.toString(), onModel = { _, parameters -> published = parameters; calls++ },
                    onDataRoot = {}, hiddenSections = hidden, onHiddenSections = { hidden = it })
            } } }
            compose.waitUntil(10000) { published != null }
            compose.onNodeWithText("模型空重 2500.00 kg", substring = true).assertExists()
            compose.onNodeWithTag("model-section-WEIGHT").performScrollTo().performClick()
            compose.onNodeWithText("模型空重 2500.00 kg", substring = true).assertDoesNotExist()
            compose.onNodeWithText("模型 VNE", substring = true).assertExists()
            compose.onNodeWithTag("model-flap-limit-table").assertExists()
            compose.onNodeWithText("副翼 AileronPowerLoss：0.500").assertExists()
            compose.onNodeWithText("俯仰 P：3.000").assertExists()
            compose.onNodeWithText("全发动机持续加力理论时限：8.00 分钟").assertExists()
            compose.onNodeWithText("发动机 #1 有效档位算术平均：2.000 s/s").assertExists()
            compose.onNodeWithText("来源：NoFlaps").assertExists()
            compose.onNodeWithText("来源：Fin").assertExists()
            compose.onNodeWithText("350 km/h IAS 升力过载估算").assertExists()
            val previous = published
            val before = calls
            compose.runOnIdle { hidden = ModelDetailSection.entries.toSet() }
            compose.onNodeWithText("全发动机持续加力理论时限：8.00 分钟").assertDoesNotExist()
            compose.onNodeWithText("发动机 #1 有效档位算术平均：2.000 s/s").assertDoesNotExist()
            compose.onNodeWithText("俯仰 P：3.000").assertDoesNotExist()
            compose.onNodeWithText("副翼 AileronPowerLoss：0.500").assertDoesNotExist()
            compose.onNodeWithText("来源：NoFlaps").assertDoesNotExist()
            compose.onNodeWithText("来源：Fin").assertDoesNotExist()
            compose.onNodeWithText("350 km/h IAS 升力过载估算").assertDoesNotExist()
            compose.onNodeWithTag("model-field-list").assertDoesNotExist()
            compose.onNodeWithTag("model-flap-limit-table").assertDoesNotExist()
            compose.onNodeWithText("模型 VNE", substring = true).assertDoesNotExist()
            compose.runOnIdle { assertSame(previous, published); assertEquals(before, calls) }
            compose.onNodeWithTag("model-sections-all").performScrollTo().performClick()
            compose.onNodeWithText("模型空重 2500.00 kg", substring = true).assertExists()
            compose.onNodeWithText("副翼 AileronPowerLoss：0.500").assertExists()
            compose.onNodeWithText("俯仰 P：3.000").assertExists()
            compose.onNodeWithText("全发动机持续加力理论时限：8.00 分钟").assertExists()
            compose.onNodeWithText("发动机 #1 有效档位算术平均：2.000 s/s").assertExists()
            compose.onNodeWithText("来源：NoFlaps").assertExists()
            compose.onNodeWithText("来源：Fin").assertExists()
            compose.onNodeWithText("350 km/h IAS 升力过载估算").assertExists()
            compose.onNodeWithTag("model-field-list").assertExists()
            compose.onNodeWithTag("model-flap-limit-table").assertExists()
            compose.runOnIdle { assertTrue(hidden.isEmpty()); assertSame(previous, published) }
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}

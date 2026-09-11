package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import voidmei.config.*
import voidmei.telemetry.*

class HudMessageFilterGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun separateRegionsFilterBeforeLimitAndPersistEditorChanges() {
        val one = HudRegion("one", HudRegionContent.MESSAGES, 0, 0, 400, 300, fields = listOf("damage", "future"))
        var scene by mutableStateOf(HudSceneLayout(800, 300, listOf(one,
            one.copy(id = "two", x = 400, fields = listOf("event")))))
        var messages by mutableStateOf(HudMessageState(
            (1..6).map { HudMessage(HudMessageKind.DAMAGE, it, "damage$it") } +
                (1..6).map { HudMessage(HudMessageKind.EVENT, it, "event$it") }))
        compose.setContent { MaterialTheme { Column {
            HudRegionFieldsSettings(scene.regions.first(), AppSettings()) { region ->
                scene = scene.copy(regions = scene.regions.map { if (it.id == region.id) region else it })
            }
            Box(Modifier.size(800.dp, 300.dp)) {
                HudPanel(hudPreviewFlight(), AppSettings(hudSceneLayout = scene), emptyList(), null,
                    messages = messages) {}
            }
        } } }
        compose.onNodeWithText("损伤 #1 · damage1").assertDoesNotExist()
        compose.onNodeWithText("事件 #1 · event1").assertDoesNotExist()
        for (id in 2..6) {
            compose.onNode(hasText("损伤 #$id · damage$id") and hasAnyAncestor(hasTestTag("hud-region-one"))).assertIsDisplayed()
            compose.onNode(hasText("事件 #$id · event$id") and hasAnyAncestor(hasTestTag("hud-region-two"))).assertIsDisplayed()
        }
        compose.runOnIdle { messages = messages.copy(error = "offline") }
        compose.onAllNodesWithText("消息更新失败（保留已有记录）").assertCountEquals(2)
        compose.onNodeWithText("损伤 #6 · damage6").assertIsDisplayed()
        compose.onNodeWithTag("hud-region-message-one-damage").performClick()
        compose.onNodeWithText("未选择消息类别").assertIsDisplayed()
        compose.onNodeWithText("损伤 #6 · damage6").assertDoesNotExist()
        compose.onNodeWithTag("hud-region-message-one-event").performClick()
        compose.onAllNodesWithText("事件 #6 · event6").assertCountEquals(2)
        compose.runOnIdle {
            assertEquals(listOf("future", "event"), scene.regions.first().fields)
            assertEquals(scene, SettingsJson.decode(SettingsJson.encode(AppSettings(hudSceneLayout = scene))).hudSceneLayout)
            messages = HudMessageState(listOf(HudMessage(HudMessageKind.DAMAGE, 7, "damage7")))
        }
        compose.onAllNodesWithText("尚无所选类别的消息").assertCountEquals(2)
    }
}

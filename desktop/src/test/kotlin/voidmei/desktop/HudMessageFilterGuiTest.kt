package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
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

    @Test fun lineLimitEllipsizesLongMessagesWithoutDiscardingOriginalText() {
        val text = "长消息内容".repeat(150)
        val state = HudMessageState(listOf(HudMessage(HudMessageKind.EVENT, 1, "较早消息"),
            HudMessage(HudMessageKind.EVENT, 2, text)))
        var region by mutableStateOf(HudRegion("messages", HudRegionContent.MESSAGES, 0, 0, 400, 260))
        compose.setContent { MaterialTheme { Row {
            Column(Modifier.width(400.dp)) { HudRegionFieldsSettings(region, AppSettings()) { region = it } }
            Box(Modifier.size(400.dp, 260.dp)) {
                HudPanel(hudPreviewFlight(), AppSettings(hudSceneLayout = HudSceneLayout(400, 260, listOf(region))),
                    emptyList(), null, messages = state) {}
            }
        } } }
        fun layout(): androidx.compose.ui.text.TextLayoutResult {
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText("事件 #2 · $text").performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single()
        }
        kotlin.test.assertTrue(layout().lineCount > 3)
        compose.onNodeWithTag("hud-region-message-lines-messages-3").performClick()
        assertEquals(3, layout().lineCount)
        // Skia may report isLineEllipsized=false despite drawing an ellipsis; retain the rendered artifact.
        java.io.File("build/hud-preview/message-lines.png").apply { parentFile.mkdirs() }.writeBytes(org.jetbrains.skia.Image.makeFromBitmap(
            compose.onNodeWithTag("hud-region-messages").captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        assertEquals(androidx.compose.ui.text.style.TextOverflow.Ellipsis, layout().layoutInput.overflow)
        compose.onNodeWithText("事件 #1 · 较早消息").assertIsDisplayed()
        assertEquals(text, state.messages.last().text)
        compose.runOnIdle {
            val saved = AppSettings(hudSceneLayout = HudSceneLayout(400, 260, listOf(region)))
            assertEquals(saved, SettingsJson.decode(SettingsJson.encode(saved)))
        }
        compose.onNodeWithTag("hud-region-message-lines-messages-0").performClick()
        kotlin.test.assertTrue(layout().lineCount > 3)
    }

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
        compose.onNodeWithTag("hud-region-message-limit-one-1").performClick()
        compose.onNodeWithText("损伤 #6 · damage6").assertIsDisplayed()
        compose.onNodeWithText("损伤 #5 · damage5").assertDoesNotExist()
        compose.onNodeWithText("事件 #5 · event5").assertIsDisplayed()
        compose.onNodeWithTag("hud-region-message-limit-one-10").performClick()
        compose.onNodeWithText("损伤 #1 · damage1").assertIsDisplayed()
        compose.onNodeWithText("事件 #1 · event1").assertDoesNotExist()
        compose.runOnIdle { assertEquals(10, scene.regions.first().messageLimit) }
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

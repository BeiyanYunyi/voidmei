package voidmei.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals

class AircraftCatalogGuiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scansFiltersAndSelectsOnlyOnClick() {
        val root = Files.createTempDirectory("voidmei-catalog")
        Files.createDirectory(root.resolve("fm"))
        Files.writeString(root.resolve("alpha.blkx"), "")
        Files.writeString(root.resolve("zulu.blkx"), "")
        var selected: String? = null
        try {
            compose.setContent { MaterialTheme { Column { AircraftCatalogPanel(root.toString()) { selected = it } } } }
            compose.onNodeWithText("打开 alpha").assertDoesNotExist()
            compose.onNodeWithText("列出本地机型").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("匹配 2 / 2 个本地机型").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { assertEquals(null, selected) }
            compose.onNodeWithText("筛选本地机型").performTextReplacement("ZUL")
            compose.onNodeWithText("打开 alpha").assertDoesNotExist()
            compose.onNodeWithText("打开 zulu").performClick()
            compose.runOnIdle { assertEquals("zulu", selected) }
            compose.onNodeWithText("筛选本地机型").performTextReplacement("missing")
            compose.onNodeWithText("没有匹配的本地机型").assertExists()
            Files.writeString(root.resolve("missing.blkx"), "")
            compose.onNodeWithText("列出本地机型").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("打开 missing").fetchSemanticsNodes().isNotEmpty() }
        } finally { root.toFile().deleteRecursively() }
    }
}

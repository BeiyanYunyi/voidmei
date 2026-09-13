package voidmei.desktop

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Open the initially collapsed region editors used by existing configuration tests. */
internal fun ComposeContentTestRule.expandHudRegionEditors() {
    val tags = onAllNodes(SemanticsMatcher("region editor") {
        it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("hud-region-editor-")
    }).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
    tags.forEach { onNodeWithTag(it).performScrollTo().performClick() }
}

internal fun ComposeContentTestRule.openHudSettingsPage(category: String) {
    if (onAllNodesWithTag("settings-back-HUD 布局").fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag("settings-back-HUD 布局").performScrollTo().performClick()
    }
    onNodeWithTag("settings-open-hud-$category").performScrollTo().performClick()
}

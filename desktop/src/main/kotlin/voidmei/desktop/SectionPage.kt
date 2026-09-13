package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal enum class MainSection(val title: String) {
    SETTINGS("连接与设置"), HUD("HUD 布局"), FLIGHT("飞行与发动机"), VOICE("语音与告警"),
    MODEL("气动模型"), RECORDS("记录与回看"), MAP("地图与消息")
}

@Composable
internal fun SectionPage(content: @Composable ColumnScope.(MainSection) -> Unit) {
    var selected by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(MainSection.SETTINGS) }
    val pages = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MainSection.entries.forEach { section ->
                FilterChip(selected == section, { selected = section },
                    modifier = Modifier.testTag("section-nav-${section.name}"), label = { Text(section.title) })
            }
        }
        HorizontalDivider()
        pages.SaveableStateProvider(selected) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(selected.title, Modifier.testTag("section-${selected.name}"), style = MaterialTheme.typography.titleLarge)
                content(selected)
            }
        }
    }
}

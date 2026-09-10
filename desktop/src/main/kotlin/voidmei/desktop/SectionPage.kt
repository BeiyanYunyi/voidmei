package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class MainSection(val title: String) {
    SETTINGS("连接与设置"), HUD("HUD 布局"), FLIGHT("飞行与发动机"), VOICE("语音与告警"),
    MODEL("气动模型"), RECORDS("记录与回看"), MAP("地图与消息")
}

@Composable
internal fun SectionPage(content: @Composable ColumnScope.(Map<MainSection, BringIntoViewRequester>) -> Unit) {
    val anchors = remember { MainSection.entries.associateWith { BringIntoViewRequester() } }
    val scope = rememberCoroutineScope()
    var navigation by remember { mutableStateOf<Job?>(null) }
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MainSection.entries.forEach { section ->
                TextButton(modifier = Modifier.testTag("section-nav-${section.name}"), onClick = {
                    navigation?.cancel()
                    navigation = scope.launch { anchors.getValue(section).bringIntoView() }
                }) { Text(section.title) }
            }
        }
        HorizontalDivider()
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) { content(anchors) }
    }
}

@Composable
internal fun SectionHeading(section: MainSection, anchors: Map<MainSection, BringIntoViewRequester>) {
    Text(section.title, Modifier.bringIntoViewRequester(anchors.getValue(section)).testTag("section-${section.name}"),
        style = MaterialTheme.typography.titleLarge)
}

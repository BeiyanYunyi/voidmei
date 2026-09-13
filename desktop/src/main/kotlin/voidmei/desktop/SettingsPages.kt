package voidmei.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal class SettingsDestination(
    val id: String,
    val title: String,
    val summary: String,
    val content: @Composable () -> Unit,
)

/** A category directory that shows only the chosen configuration page. */
@Composable
internal fun SettingsPages(title: String, vararg destinations: SettingsDestination) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val pages = rememberSaveableStateHolder()
    val top = remember { BringIntoViewRequester() }
    val focus = LocalFocusManager.current
    LaunchedEffect(selected) {
        focus.clearFocus()
        withFrameNanos { }
        top.bringIntoView()
    }
    Spacer(Modifier.height(0.dp).bringIntoViewRequester(top))
    val destination = destinations.firstOrNull { it.id == selected }
    if (destination == null) {
        destinations.forEach { entry ->
            OutlinedCard(onClick = { selected = entry.id },
                modifier = Modifier.fillMaxWidth().testTag("settings-open-${entry.id}")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${entry.title}  ›", style = MaterialTheme.typography.titleMedium)
                    Text(entry.summary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    } else {
        TextButton(onClick = { selected = null }, modifier = Modifier.testTag("settings-back-$title")) {
            Text("‹ 返回$title")
        }
        Text("$title / ${destination.title}", style = MaterialTheme.typography.titleMedium)
        pages.SaveableStateProvider(destination.id) { destination.content() }
    }
}

package com.rr.numio.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rr.numio.files.BuildConfig
import com.rr.numio.files.FilesViewModel
import com.rr.numio.files.ui.theme.AppTheme
import com.rr.numio.files.ui.theme.Numio
import kotlinx.coroutines.delay

private const val SOURCE_URL = "https://github.com/rag-creation/numio-files"

@Composable
fun SettingsScreen(vm: FilesViewModel, onOpenLink: (String) -> Unit, onAuth: (String, () -> Unit) -> Unit) {
    Column {
        TitleHeader("Settings")
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Look") {
                Picker(
                    listOf(AppTheme.SYSTEM to "System", AppTheme.DARK to "Dark", AppTheme.LIGHT to "Light"),
                    vm.appTheme,
                    vm::updateTheme,
                )
                Line()
                Toggle("Open on Browse", "Start in your folders instead of Home", vm.openOnBrowse, vm::updateOpenOnBrowse)
            }
            Section("Files") {
                Toggle("Show hidden files", "Names starting with a dot", vm.showHidden) { on ->
                    if (on && vm.lockHidden) onAuth("Show hidden files") { vm.updateShowHidden(true) }
                    else vm.updateShowHidden(on)
                }
                Line()
                Toggle("Ask before moving to Trash", "Things in Trash can always be restored", vm.askBeforeTrash, vm::updateAskBeforeTrash)
                Line()
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
                    Text("Trash keeps things for", style = MaterialTheme.typography.bodyLarge)
                }
                Picker(listOf(7 to "7 days", 30 to "30 days", 90 to "90 days"), vm.trashDays, vm::updateTrashDays)
            }
            Section("Privacy") {
                Toggle("Lock Files", "Fingerprint or phone PIN to open, after 30 seconds away", vm.lockApp) { on ->
                    // Asking first proves the lock works before it can shut you out
                    onAuth(if (on) "Turn on the lock" else "Turn off the lock") { vm.updateLockApp(on) }
                }
                Line()
                Toggle("Lock hidden files", "Fingerprint or PIN before hidden files show; they hide again each time Files opens", vm.lockHidden) { on ->
                    onAuth(if (on) "Lock hidden files" else "Unlock hidden files") { vm.updateLockHidden(on) }
                }
            }
            Section("About") { About(onOpenLink) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun About(onOpenLink: (String) -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Wordmark(height = 26)
        // Seven taps on the version: the Numio thank-you, same as in Calculator and Clock
        Text(
            "Files ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable { taps++ }.padding(vertical = 4.dp),
        )
        if (taps >= 7) Thanks()
        Text("No internet permission. Your files never leave your phone.", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Source code",
            color = Numio.AmberText,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onOpenLink(SOURCE_URL) }.padding(vertical = 4.dp),
        )
        Text("Open source, GPL-3.0. Made by RR.", style = MaterialTheme.typography.bodySmall)
    }
}

/** Letter-by-letter thank-you note. */
@Composable
private fun Thanks() {
    val line1 = "Thanks for choosing Numio 💛"
    val line2 = "With Lo❤️e, R.R"
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val total = line1.length + line2.length
        while (shown < total) {
            delay(55)
            shown++
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Numio.AmberSoft)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(line1.take(shown), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Numio.Text)
        Text(line2.take((shown - line1.length).coerceAtLeast(0)), fontSize = 15.sp, color = Numio.AmberText)
    }
}

@Composable
private fun <T> Picker(options: List<Pair<T, String>>, current: T, onPick: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            val on = current == value
            Text(
                label,
                fontWeight = FontWeight.Bold,
                color = if (on) Numio.OnAmber else Numio.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) Numio.Amber else Color.Transparent)
                    .clickable { onPick(value) }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Numio.OnAmber,
                checkedTrackColor = Numio.Amber,
                uncheckedThumbColor = Numio.TextDim,
                uncheckedTrackColor = Numio.SurfaceHigh,
                uncheckedBorderColor = Numio.Line,
            ),
        )
    }
}

@Composable
private fun Line() = HorizontalDivider(color = Numio.Line, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Numio.TextDim,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 8.dp),
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Numio.Surface)) { content() }
    }
}

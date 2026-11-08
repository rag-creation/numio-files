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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rr.numio.files.BuildConfig
import com.rr.numio.files.FilesViewModel
import com.rr.numio.files.ui.theme.AppTheme
import com.rr.numio.files.ui.theme.Numio

private const val SOURCE_URL = "https://github.com/rag-creation/numio-files"

@Composable
fun SettingsScreen(vm: FilesViewModel, onOpenLink: (String) -> Unit) {
    Column {
        TitleHeader("Settings")
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Theme") {
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(AppTheme.SYSTEM to "System", AppTheme.DARK to "Dark", AppTheme.LIGHT to "Light").forEach { (t, label) ->
                        val on = vm.appTheme == t
                        Text(
                            label,
                            fontWeight = FontWeight.Bold,
                            color = if (on) Numio.OnAmber else Numio.Text,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) Numio.Amber else Color.Transparent)
                                .clickable { vm.updateTheme(t) }
                                .padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
            Section("Files") {
                Row(
                    Modifier.fillMaxWidth().clickable { vm.updateShowHidden(!vm.showHidden) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Show hidden files", style = MaterialTheme.typography.bodyLarge)
                        Text("Names starting with a dot", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = vm.showHidden,
                        onCheckedChange = vm::updateShowHidden,
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
            Section("About") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Wordmark(height = 26)
                    Text("Files ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "No internet permission. Your files never leave your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Source code",
                        color = Numio.AmberText,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onOpenLink(SOURCE_URL) }.padding(vertical = 4.dp),
                    )
                    Text("Open source, GPL-3.0. Made by RR.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

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

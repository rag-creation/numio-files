package com.rr.numio.files.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rr.numio.files.FilesViewModel
import com.rr.numio.files.Overlay
import com.rr.numio.files.Tab
import com.rr.numio.files.data.Category
import com.rr.numio.files.ui.theme.Numio
import kotlinx.coroutines.delay

@Composable
fun FilesApp(
    vm: FilesViewModel,
    onAskAccess: () -> Unit,
    onOpen: (String) -> Unit,
    onShare: (List<String>) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Numio.Bg).systemBarsPadding().imePadding()) {
        if (!vm.hasAccess) {
            AccessScreen(onAskAccess)
            return@Box
        }

        var renaming by remember { mutableStateOf<String?>(null) }
        var newFolder by remember { mutableStateOf(false) }
        var confirmDelete by remember { mutableStateOf(false) }
        var confirmForever by remember { mutableStateOf(false) }
        var confirmEmpty by remember { mutableStateOf(false) }

        // ZIPs open inside Files; everything else in the app that handles it
        val open: (String) -> Unit = { p ->
            if (com.rr.numio.files.data.extOf(p) == "zip") vm.openZip(p) else onOpen(p)
        }
        var makingZip by remember { mutableStateOf<String?>(null) }

        val overlay = vm.overlay
        val inZip = overlay is Overlay.Zip
        val inTrash = overlay == Overlay.Cat(Category.TRASH)

        BackHandler(enabled = vm.selected.isNotEmpty() || overlay != null || vm.tab != Tab.HOME || vm.pending != null) {
            when {
                vm.selected.isNotEmpty() -> vm.clearSelection()
                inZip && vm.zipUp() -> Unit
                overlay != null -> vm.closeOverlay()
                vm.tab == Tab.BROWSE && vm.up() -> Unit
                vm.pending != null -> vm.cancelPending()
                else -> vm.openTab(Tab.HOME)
            }
        }

        Column(Modifier.fillMaxSize()) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Numio.Amber, trackColor = Numio.Bg)
            Box(Modifier.weight(1f)) {
                when {
                    overlay is Overlay.Cat && overlay.category == Category.TRASH -> TrashScreen(vm) { confirmEmpty = true }
                    overlay is Overlay.Zip -> ZipScreen(vm, overlay) { entry -> vm.openZipEntry(entry, onOpen) }
                    overlay is Overlay.Cat -> CategoryScreen(vm, overlay.category, open)
                    overlay == Overlay.Search -> SearchScreen(vm, open)
                    vm.tab == Tab.HOME -> HomeScreen(vm, open)
                    vm.tab == Tab.BROWSE -> BrowseScreen(vm, open) { newFolder = true }
                    vm.tab == Tab.STARRED -> StarredScreen(vm, open)
                    else -> SettingsScreen(vm, onOpenLink)
                }
            }
            when {
                inZip -> ActionBar(
                    listOf(
                        Action(if (vm.selected.isEmpty()) "Extract all here" else "Extract here", FIcons.Download) { vm.extractHere() },
                        Action("Extract to…", FIcons.Move) { vm.startExtractTo() },
                    ),
                )
                vm.selected.isNotEmpty() && inTrash -> ActionBar(
                    listOf(
                        Action("Restore", FIcons.Restore) { vm.restoreSelected() },
                        Action("Delete forever", FIcons.Trash, danger = true) { confirmForever = true },
                    ),
                )
                vm.selected.isNotEmpty() -> ActionBar(
                    listOf(
                        Action("Copy", FIcons.Copy) { vm.startCopy(move = false) },
                        Action("Move", FIcons.Move) { vm.startCopy(move = true) },
                        Action("Share", FIcons.Share) { onShare(vm.selected.toList()) },
                        Action("Rename", FIcons.Rename, enabled = vm.selected.size == 1) { renaming = vm.selected.firstOrNull() },
                        Action("Zip", FIcons.Archive) {
                            val first = vm.selected.first().substringAfterLast('/')
                            makingZip = if (vm.selected.size == 1) first.substringBeforeLast('.').ifBlank { first } else "Archive"
                        },
                        Action("Delete", FIcons.Trash, danger = true) { confirmDelete = true },
                    ),
                )
                vm.pending != null && vm.tab == Tab.BROWSE && overlay == null -> PasteBar(vm)
                overlay == null -> NavBar(vm)
            }
        }

        Snack(vm)

        renaming?.let { path ->
            NameDialog(
                title = "Rename",
                initial = path.substringAfterLast('/'),
                confirm = "Rename",
                onDismiss = { renaming = null },
            ) { name ->
                renaming = null
                vm.clearSelection()
                vm.rename(path, name)
            }
        }
        makingZip?.let { suggested ->
            NameDialog(title = "Make ZIP", initial = "$suggested.zip", confirm = "Make ZIP", onDismiss = { makingZip = null }) { name ->
                makingZip = null
                vm.makeZip(name)
            }
        }
        if (newFolder) {
            NameDialog(title = "New folder", initial = "", confirm = "Create", onDismiss = { newFolder = false }) { name ->
                newFolder = false
                vm.newFolder(name)
            }
        }
        if (confirmDelete) {
            val n = vm.selected.size
            Confirm(
                title = "Move ${if (n == 1) "1 item" else "$n items"} to Trash?",
                body = "You can restore them from Trash for 30 days.",
                yes = "Move to Trash",
                onDismiss = { confirmDelete = false },
            ) {
                confirmDelete = false
                vm.deleteSelected()
            }
        }
        if (confirmForever) {
            Confirm(
                title = "Delete for good?",
                body = "This can't be undone.",
                yes = "Delete",
                onDismiss = { confirmForever = false },
            ) {
                confirmForever = false
                vm.deleteForeverSelected()
            }
        }
        if (confirmEmpty) {
            Confirm(
                title = "Empty Trash?",
                body = "Everything in Trash is deleted for good. This can't be undone.",
                yes = "Empty Trash",
                onDismiss = { confirmEmpty = false },
            ) {
                confirmEmpty = false
                vm.emptyTrash()
            }
        }
    }
}

@Composable
private fun AccessScreen(onAskAccess: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Wordmark(height = 30)
        Spacer(Modifier.height(36.dp))
        Text("Let Files see your storage", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "To show, copy and tidy your folders, Android needs you to turn on \"All files access\" for Files.",
            style = MaterialTheme.typography.bodyLarge,
            color = Numio.TextDim,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Files has no internet permission, so nothing ever leaves your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = Numio.TextDim,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onAskAccess,
            colors = ButtonDefaults.buttonColors(containerColor = Numio.Amber, contentColor = Numio.OnAmber),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Allow access", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Numio.OnAmber) }
    }
}

private class Action(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun ActionBar(actions: List<Action>) {
    Row(
        Modifier
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Numio.SurfaceHigh)
            .border(1.dp, Numio.Line, RoundedCornerShape(24.dp))
            .padding(6.dp),
    ) {
        actions.forEach { a ->
            val color = when {
                !a.enabled -> Numio.TextDim.copy(alpha = 0.45f)
                a.danger -> Numio.Error
                else -> Numio.Text
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = a.enabled, onClick = a.onClick)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(a.icon, null, tint = color, modifier = Modifier.size(22.dp))
                Text(a.label, color = color, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PasteBar(vm: FilesViewModel) {
    val p = vm.pending ?: return
    val n = p.paths.size
    Row(
        Modifier
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Numio.SurfaceHigh)
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                (if (p.zip != null) "Extracting " else if (p.move) "Moving " else "Copying ") + if (n == 1) "1 item" else "$n items",
                style = MaterialTheme.typography.titleSmall,
            )
            Text("Open the folder, then paste", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = vm::cancelPending) { Text("Cancel", color = Numio.Text) }
        Spacer(Modifier.width(4.dp))
        Button(
            onClick = vm::paste,
            colors = ButtonDefaults.buttonColors(containerColor = Numio.Amber, contentColor = Numio.OnAmber),
        ) { Text("Paste here", fontWeight = FontWeight.Bold, color = Numio.OnAmber) }
    }
}

@Composable
private fun NavBar(vm: FilesViewModel) {
    Column {
        HorizontalDivider(color = Numio.Line, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Tab.entries.forEach { t ->
                val on = vm.tab == t
                val icon = when (t) {
                    Tab.HOME -> FIcons.Home
                    Tab.BROWSE -> FIcons.Folder
                    Tab.STARRED -> FIcons.Star
                    Tab.SETTINGS -> FIcons.Gear
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (t == Tab.BROWSE && vm.tab != Tab.BROWSE) vm.openDir(vm.dir) else vm.openTab(t)
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier
                            .width(56.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (on) Numio.AmberSoft else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, tint = if (on) Numio.Text else Numio.TextDim, modifier = Modifier.size(22.dp)) }
                    Text(
                        t.label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (on) Numio.Text else Numio.TextDim,
                    )
                }
            }
        }
    }
}

@Composable
private fun Snack(vm: FilesViewModel) {
    val msg = vm.message ?: return
    LaunchedEffect(msg) {
        delay(2600)
        if (vm.message == msg) vm.message = null
    }
    Box(Modifier.fillMaxSize().padding(bottom = 96.dp, start = 20.dp, end = 20.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            msg,
            color = if (Numio.palette.isDark) Numio.Bg else Numio.Text,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (Numio.palette.isDark) Numio.Text else Numio.SurfaceHigh)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    // Select the name but not the extension, so typing replaces just the name
    val dot = initial.lastIndexOf('.').takeIf { it > 0 } ?: initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, dot))) }
    val ok = com.rr.numio.files.data.isValidName(value.text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Numio.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Numio.Amber,
                    unfocusedBorderColor = Numio.Line,
                    cursorColor = Numio.Amber,
                    focusedTextColor = Numio.Text,
                    unfocusedTextColor = Numio.Text,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onDone(value.text.trim()) }, enabled = ok) {
                Text(confirm, color = if (ok) Numio.AmberText else Numio.TextDim, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Numio.Text) } },
    )
}

@Composable
private fun Confirm(title: String, body: String, yes: String, onDismiss: () -> Unit, onYes: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Numio.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = Numio.TextDim) },
        confirmButton = { TextButton(onClick = onYes) { Text(yes, color = Numio.Error, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Numio.Text) } },
    )
}

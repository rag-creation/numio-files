package com.rr.numio.files

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import com.rr.numio.files.data.extOf
import com.rr.numio.files.ui.FilesApp
import com.rr.numio.files.ui.theme.FilesTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val vm: FilesViewModel by viewModels()

    // Android 8–10: the classic storage permission prompt
    private val askStorage = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.checkAccess()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            FilesTheme(theme = vm.appTheme) {
                FilesApp(
                    vm = vm,
                    onAskAccess = ::askAccess,
                    onOpen = ::openFile,
                    onShare = ::share,
                    onOpenLink = ::openLink,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A ZIP opened from another app with "Open with Files". */
    private fun handle(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (uri.scheme == "file") {
            uri.path?.let { vm.openZip(it, outside = true) }
            return
        }
        var name = "archive.zip"
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) name = c.getString(0)
            }
        } catch (e: Exception) {
            // Some apps don't say the name
        }
        vm.openZipFrom(uri, name.substringAfterLast('/'))
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the "All files access" screen
        vm.checkAccess()
    }

    private fun askAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val forApp = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            try {
                startActivity(forApp)
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) {
                    Toast.makeText(this, "Open Settings › Apps › Files › All files access", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            askStorage.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            )
        }
    }

    private fun uriFor(f: File): Uri = FileProvider.getUriForFile(this, "$packageName.files", f)

    private fun mimeOf(f: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extOf(f.name)) ?: "*/*"

    /** Opens a file in whichever app handles its type. */
    private fun openFile(path: String) {
        val f = File(path)
        try {
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uriFor(f), mimeOf(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            startActivity(Intent.createChooser(view, null))
        } catch (e: Exception) {
            Toast.makeText(this, "No app on this phone can open this file.", Toast.LENGTH_LONG).show()
        }
    }

    private fun share(paths: List<String>) {
        val files = paths.map(::File).filter { it.isFile }
        if (files.isEmpty()) {
            Toast.makeText(this, "Folders can't be shared. Pick files instead.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val uris = ArrayList(files.map(::uriFor))
            val mimes = files.map(::mimeOf).distinct()
            val type = if (mimes.size == 1) mimes[0] else "*/*"
            val send = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            send.type = type
            // Lets the receiving app read every file, not just the first
            val clip = ClipData.newRawUri(null, uris[0])
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            send.clipData = clip
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, null))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't share these files.", Toast.LENGTH_LONG).show()
        }
    }

    /** Source code link in About: opens in the phone's browser; Files itself stays offline. */
    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show()
        }
    }
}

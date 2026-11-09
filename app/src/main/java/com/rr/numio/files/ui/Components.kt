package com.rr.numio.files.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.rr.numio.files.data.Category
import com.rr.numio.files.data.Thumbs
import com.rr.numio.files.data.categoryOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rr.numio.files.R
import com.rr.numio.files.data.FileItem
import com.rr.numio.files.data.formatSize
import com.rr.numio.files.ui.theme.Numio
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The official "Numio." wordmark: cream letters on dark, ink letters on light. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, height: Int = 22) {
    Image(
        painter = painterResource(if (Numio.palette.isDark) R.drawable.numio_wordmark_dark else R.drawable.numio_wordmark_light),
        contentDescription = "Numio",
        modifier = modifier.height(height.dp),
    )
}

@Composable
fun IconBtn(icon: ImageVector, label: String, tint: Color = Numio.Text, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = label, tint = if (enabled) tint else Numio.TextDim.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
    }
}

/** Header shown while things are selected: count on top, named actions in their own row below. */
@Composable
fun SelectionHeader(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, extra: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(FIcons.Close, "Cancel selection", onClick = onClose)
            Text(
                "$count selected",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            extra()
            LabeledBtn(FIcons.SelectAll, "Select all", onClick = onSelectAll)
        }
    }
}

/** An action with its name underneath, so every button is clear. */
@Composable
fun LabeledBtn(icon: ImageVector, label: String, tint: Color = Numio.Text, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** A plain header with an optional back arrow and actions. */
@Composable
fun TitleHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = if (onBack == null) 20.dp else 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconBtn(FIcons.Back, "Back", onClick = onBack)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** The square on the left of a row: an amber folder, a preview, or the file's extension. */
@Composable
fun FileTile(item: FileItem, size: Int = 48) {
    val shape = RoundedCornerShape(12.dp)
    if (item.isDir) {
        Box(
            Modifier.size(size.dp).clip(shape).background(Numio.AmberSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(FIcons.Folder, null, tint = Numio.AmberText, modifier = Modifier.size(22.dp)) }
        return
    }
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(Thumbs.cached(item.path, item.modified), item.path, item.modified) {
        if (value == null && Thumbs.hasPreview(item.name)) {
            value = withContext(Dispatchers.IO) { Thumbs.load(context, item.path, item.modified) }
        }
    }
    val cat = categoryOf(item.name)
    Box(
        Modifier.size(size.dp).clip(shape).background(Numio.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = thumb
        if (bmp != null) {
            val isApk = cat == Category.APKS
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = if (isApk) ContentScale.Fit else ContentScale.Crop,
                modifier = if (isApk) Modifier.size((size - 8).dp) else Modifier.fillMaxSize(),
            )
            if (cat == Category.VIDEOS) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(FIcons.Play, "Video", tint = Color.White, modifier = Modifier.size(10.dp)) }
            }
        } else {
            val label = item.ext.uppercase(Locale.ROOT).take(4).ifEmpty { "FILE" }
            Text(
                label,
                color = Numio.TextDim,
                fontSize = if (label.length > 3) 9.5.sp else 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
            )
        }
    }
}

fun metaFor(item: FileItem): String {
    val first = if (item.isDir) {
        if (item.childCount == 1) "1 item" else if (item.childCount >= 0) "${item.childCount} items" else "Folder"
    } else formatSize(item.size)
    return "$first · ${whenText(item.modified)}"
}

/** "Today, 16:02", "Yesterday", "5 Nov", "5 Nov 2024". */
fun whenText(millis: Long): String {
    if (millis <= 0) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    return when {
        DateUtils.isToday(millis) -> "Today, " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
        DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
    }
}

/** One file or folder in a list. Tap opens, long-press selects. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FileItem,
    meta: String = metaFor(item),
    selecting: Boolean,
    selected: Boolean,
    starred: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Numio.AmberSoft.copy(alpha = if (Numio.palette.isDark) 0.7f else 0.55f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTile(item)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(meta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selecting) {
            Spacer(Modifier.width(10.dp))
            CheckCircle(selected)
        } else if (starred) {
            Spacer(Modifier.width(10.dp))
            Icon(FIcons.StarFilled, "Starred", tint = Numio.AmberText, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun CheckCircle(on: Boolean) {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (on) Numio.Amber else Color.Transparent)
            .border(1.5.dp, if (on) Numio.Amber else Numio.TextDim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(FIcons.Check, null, tint = Numio.OnAmber, modifier = Modifier.size(14.dp))
    }
}

/** A friendly line when a list has nothing in it. */
@Composable
fun EmptyNote(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodySmall)
    }
}

/** One square in grid view: a big preview (or folder / file type), name underneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridCell(
    item: FileItem,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(Thumbs.cached(item.path, item.modified), item.path, item.modified) {
        if (!item.isDir && value == null && Thumbs.hasPreview(item.name)) {
            value = withContext(Dispatchers.IO) { Thumbs.load(context, item.path, item.modified) }
        }
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .padding(4.dp)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(if (item.isDir) Numio.AmberSoft else Numio.SurfaceHigh)
                .then(if (selected) Modifier.border(3.dp, Numio.Amber, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = thumb
            when {
                item.isDir -> Icon(FIcons.Folder, null, tint = Numio.AmberText, modifier = Modifier.size(34.dp))
                bmp != null -> {
                    val isApk = categoryOf(item.name) == Category.APKS
                    Image(
                        bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = if (isApk) ContentScale.Fit else ContentScale.Crop,
                        modifier = if (isApk) Modifier.fillMaxSize().padding(18.dp) else Modifier.fillMaxSize(),
                    )
                    if (categoryOf(item.name) == Category.VIDEOS) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(FIcons.Play, "Video", tint = Color.White, modifier = Modifier.size(13.dp)) }
                    }
                }
                else -> Text(
                    item.ext.uppercase(Locale.ROOT).take(4).ifEmpty { "FILE" },
                    color = Numio.TextDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (selecting) {
                Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopEnd) { CheckCircle(selected) }
            }
        }
        Text(
            item.name,
            fontSize = 12.sp,
            color = Numio.Text,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 5.dp, bottom = 2.dp),
        )
    }
}

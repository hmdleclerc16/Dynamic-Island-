@file:OptIn(ExperimentalFoundationApi::class)

package com.akira.island.island

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.akira.island.core.Controls
import com.akira.island.core.IslandBus
import com.akira.island.core.Notice
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.data.IslandSettings
import com.akira.island.data.SettingsRepo
import com.akira.island.data.ShelfItem
import com.akira.island.data.ShelfKind
import com.akira.island.data.ShelfRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ShelfDim = Color.White.copy(alpha = 0.55f)
private val CardHeight = 88.dp
private val TextCardWidth = 150.dp

/** Свёрнутая полка: значок лотка слева от камеры и сколько всего на ней лежит — справа. */
@Composable
fun ShelfCompact(items: List<ShelfItem>, s: IslandSettings) {
    val h = activeHeight(s)
    CompactRow(h, compactGap(s), sideMax(s),
        left = { Icon(Icons.Rounded.Inbox, null, tint = Tints.Teal, modifier = Modifier.size(h * 0.5f)) },
        right = { Text("${items.size}", color = Tints.Teal, fontSize = 16.sp, style = Digits, maxLines = 1, softWrap = false) },
    )
}

/**
 * Раскрытая полка: ряд карточек, который листается вбок. Нажатие забирает вещь (текст копируется, фото уходит
 * в «Поделиться»), долгое нажатие — второй способ (текст — «Поделиться», фото — в буфер обмена), крестик убирает.
 */
@Composable
fun ShelfExpanded(items: List<ShelfItem>, @Suppress("UNUSED_PARAMETER") s: IslandSettings) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Inbox, null, tint = Tints.Teal, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("Полка", "Shelf"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text("${items.size}", color = ShelfDim, fontSize = 15.sp, style = Digits)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable {
                        haptic()
                        ShelfRepo.clear()
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.DeleteSweep, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(tr("Очистить", "Clear"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { item ->
                key(item.id) {
                    ShelfCard(
                        item,
                        onTake = { ShelfActions.take(context, item) },
                        onAlt = { ShelfActions.takeAlt(context, item) },
                        onRemove = { ShelfRepo.remove(item.id) },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            tr(
                "Нажмите — текст скопируется, фото откроется для отправки. Долгое нажатие — наоборот",
                "Tap: text is copied, a photo opens Share. Long press: the other way round",
            ),
            color = ShelfDim, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShelfCard(item: ShelfItem, onTake: () -> Unit, onAlt: () -> Unit, onRemove: () -> Unit) {
    val haptic = rememberHaptic()
    val image = item.kind == ShelfKind.IMAGE
    Box(
        Modifier
            .size(if (image) CardHeight else TextCardWidth, CardHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .combinedClickable(
                onClick = {
                    haptic()
                    onTake()
                },
                onLongClick = {
                    haptic()
                    onAlt()
                },
            ),
    ) {
        if (image) {
            val thumb = rememberThumb(item)
            if (thumb != null) {
                Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.Image, null, tint = ShelfDim, modifier = Modifier.align(Alignment.Center).size(30.dp))
            }
        } else {
            // Справа оставлено место под крестик
            Text(
                item.text, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, lineHeight = 17.sp,
                maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 11.dp, top = 9.dp, end = 30.dp, bottom = 9.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable {
                    haptic()
                    onRemove()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ios.close, null, tint = Color.White, modifier = Modifier.size(11.dp))
        }
    }
}

/** Миниатюра грузится не на главном потоке; пока её нет — значок. */
@Composable
private fun rememberThumb(item: ShelfItem): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, item.id) {
        value = withContext(Dispatchers.IO) {
            ShelfRepo.thumbFile(item)?.let { f -> runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
        }
    }
    return bitmap
}

/** Что остров делает с вещью, которую забирают с полки. */
object ShelfActions {
    private fun authority(context: Context) = "${context.packageName}.fileprovider"

    /** Нажатие: текст — в буфер обмена, фото — в «Поделиться». */
    fun take(context: Context, item: ShelfItem) {
        Controls.collapse()
        when (item.kind) {
            ShelfKind.TEXT -> copyText(context, item)
            ShelfKind.IMAGE -> shareImage(context, item)
        }
    }

    /** Долгое нажатие: текст — в «Поделиться», фото — в буфер обмена. */
    fun takeAlt(context: Context, item: ShelfItem) {
        Controls.collapse()
        when (item.kind) {
            ShelfKind.TEXT -> shareText(context, item)
            ShelfKind.IMAGE -> copyImage(context, item)
        }
    }

    private fun copyText(context: Context, item: ShelfItem) {
        val ok = runCatching {
            context.getSystemService(ClipboardManager::class.java)!!.setPrimaryClip(ClipData.newPlainText("", item.text))
        }.isSuccess
        if (ok) {
            done(tr("Скопировано", "Copied"))
            // Только текст: файл картинки может ещё читать приложение, которому её отдали
            if (SettingsRepo.current.shelfRemoveOnUse) ShelfRepo.remove(item.id)
        }
    }

    private fun shareText(context: Context, item: ShelfItem) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, item.text)
        runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun shareImage(context: Context, item: ShelfItem) {
        val uri = uriOf(context, item) ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = item.mime.ifBlank { "image/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            // ClipData + флаг: так «Поделиться» передаёт право на чтение файла выбранному приложению
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun copyImage(context: Context, item: ShelfItem) {
        val uri = uriOf(context, item) ?: return
        val ok = runCatching {
            context.getSystemService(ClipboardManager::class.java)!!
                .setPrimaryClip(ClipData.newUri(context.contentResolver, "image", uri))
        }.isSuccess
        if (ok) done(tr("Фото скопировано", "Photo copied"))
    }

    private fun uriOf(context: Context, item: ShelfItem) =
        ShelfRepo.file(item)?.let { runCatching { FileProvider.getUriForFile(context, authority(context), it) }.getOrNull() }

    private fun done(title: String) {
        IslandBus.post(Notice(title, null, Icons.Rounded.ContentCopy, Tints.Teal))
    }
}

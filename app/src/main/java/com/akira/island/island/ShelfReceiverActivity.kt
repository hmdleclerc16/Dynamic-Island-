package com.akira.island.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.core.content.IntentCompat
import com.akira.island.core.IslandBus
import com.akira.island.core.Notice
import com.akira.island.core.Tints
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.data.ShelfRepo

/**
 * Дверь на полку острова. Сюда приходит:
 *  - выделенный текст — пункт «Положить в остров» в меню выделения (ACTION_PROCESS_TEXT);
 *  - «Поделиться» текстом, одним фото или несколькими фото — пункт «Полка острова».
 * Экран прозрачный и сразу закрывается: вещь копируется на полку, остров коротко говорит «Добавлено».
 */
class ShelfReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsRepo.init(this)
        ShelfRepo.init(this)
        // Из меню выделения ничего не возвращаем: выделенный текст остаётся как был
        setResult(RESULT_CANCELED)

        if (!SettingsRepo.current.cardShelf) {
            toast(tr("Полка выключена в настройках острова", "The shelf is turned off in the island settings"))
            finish()
            return
        }

        val source = intent
        val texts = source?.let(::textsOf).orEmpty()
        val images = source?.let(::imagesOf).orEmpty()
        if (texts.isEmpty() && images.isEmpty()) {
            finish()
            return
        }

        val app = applicationContext
        // Фото копируются в своё хранилище, пока этот экран жив: права на чужую ссылку живут ровно столько.
        // Копирование — не на главном потоке, чтобы не подвесить экран на больших снимках
        Thread {
            var added = 0
            texts.forEach { if (ShelfRepo.addText(it) != null) added++ }
            images.forEach { if (ShelfRepo.addImage(app, it) != null) added++ }
            runOnUiThread {
                report(added)
                finish()
            }
        }.start()
    }

    private fun report(added: Int) {
        when {
            added == 0 -> toast(tr("Не удалось добавить на полку", "Couldn't add to the shelf"))
            IslandBus.serviceRunning.value -> IslandBus.post(
                Notice(
                    tr("Полка", "Shelf"),
                    if (added > 1) tr("Добавлено · $added", "Added · $added") else tr("Добавлено", "Added"),
                    Icons.Rounded.Inbox,
                    Tints.Teal,
                ),
            )
            // Остров выключен в спец. возможностях — полку не увидеть, подскажем
            else -> toast(tr("Добавлено. Включите остров в спец. возможностях, чтобы увидеть полку", "Added. Turn the island on in Accessibility to see the shelf"))
        }
    }

    private fun toast(text: String) = Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()

    private fun textsOf(intent: Intent): List<String> = when (intent.action) {
        Intent.ACTION_PROCESS_TEXT -> listOfNotNull(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString())
        Intent.ACTION_SEND ->
            if (intent.type?.startsWith("text/") == true) listOfNotNull(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
            else emptyList()
        else -> emptyList()
    }

    private fun imagesOf(intent: Intent): List<Uri> {
        if (intent.type?.startsWith("image/") != true) return emptyList()
        val streams = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        // Некоторые приложения кладут ссылку не в EXTRA_STREAM, а в ClipData
        val uris = streams.ifEmpty {
            intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
        }
        return uris.take(ShelfRepo.MAX_ITEMS)
    }
}

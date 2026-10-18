/**
 * Loads pictures linked from a note (`![](.img/cat.png)`) for Live and Preview.
 * The bytes come through `VaultFs` (via [EditorViewModel.readImage]), never a file path, and are
 * decoded off the main thread, scaled down to about screen width so big photos don't use too
 * much memory.
 */
package com.dotnote.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads an image's bytes by its link in the note; `null` when it can't be read. */
typealias ImageReader = suspend (link: String) -> ByteArray?

/** Widest picture we decode, in pixels (about a phone screen's width). */
private const val MAX_IMAGE_WIDTH = 1440

/**
 * The picture for [link], or `null` while loading or when it can't be shown.
 * `produceState` runs the loading coroutine and restarts it when [link] changes.
 */
@Composable
fun rememberNoteImage(
    link: String,
    readImage: ImageReader,
): State<ImageBitmap?> =
    produceState<ImageBitmap?>(initialValue = null, link) {
        val bytes = readImage(link) ?: return@produceState
        value = withContext(Dispatchers.Default) { decode(bytes) }
    }

private fun decode(bytes: ByteArray): ImageBitmap? {
    // First read only the size, then decode at a power-of-two scale that fits MAX_IMAGE_WIDTH.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return null // not a picture Android can read (e.g. SVG)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= MAX_IMAGE_WIDTH) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

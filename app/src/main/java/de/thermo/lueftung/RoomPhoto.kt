package de.thermo.lueftung

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun sampleOptions(bounds: BitmapFactory.Options, target: IntSize): BitmapFactory.Options {
    val result = BitmapFactory.Options().apply { inSampleSize = 1 }
    val width = target.width.coerceIn(64, 1280)
    val height = target.height.coerceIn(64, 1280)
    while (bounds.outWidth / (result.inSampleSize * 2) >= width &&
        bounds.outHeight / (result.inSampleSize * 2) >= height) result.inSampleSize *= 2
    // Also bound panoramic and unusually large photos to avoid excessive allocations.
    while (bounds.outWidth / result.inSampleSize > 1280 ||
        bounds.outHeight / result.inSampleSize > 1280) result.inSampleSize *= 2
    return result
}

@Composable
fun RoomPhoto(uri: Uri?, fallback: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val resources = context.resources
    var target by remember { mutableStateOf(IntSize.Zero) }
    var photo by remember(uri, fallback) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri, fallback, target) {
        if (target == IntSize.Zero) return@LaunchedEffect
        photo = withContext(Dispatchers.IO) {
            val selected = if (uri != null) runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                val options = sampleOptions(bounds, target)
                resolver.openInputStream(uri)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input, null, options) ?: return@use null
                    val orientation = runCatching {
                        resolver.openInputStream(uri)?.use { source ->
                            ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL)
                        }
                    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
                    val matrix = Matrix().apply {
                        when (orientation) {
                            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                        }
                    }
                    if (matrix.isIdentity) bitmap
                    else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        .also { if (it !== bitmap) bitmap.recycle() }
                }
            }.getOrNull() else null
            val bitmap = selected ?: run {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(resources, fallback, bounds)
                BitmapFactory.decodeResource(resources, fallback, sampleOptions(bounds, target))
            }
            bitmap?.asImageBitmap()
        }
    }
    val sized = modifier.onSizeChanged { target = it }
    if (photo != null) Image(photo!!, null, sized, contentScale = ContentScale.Crop)
    else Box(sized.background(Color(0xFF073D58)))
}

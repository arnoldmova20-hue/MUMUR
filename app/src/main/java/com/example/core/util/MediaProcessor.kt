package com.example.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

data class ProcessedMedia(
    val type: String, // "image" or "video"
    val encodedData: String, // data:image/jpeg;base64,... (compressed thumbnail or image)
    val durationMs: Long = 0L,
    val originalSizeBytes: Long = 0L,
    val compressedSizeBytes: Int = 0,
    val videoChunks: List<String> = emptyList(),
    val localUri: Uri? = null
)

object MediaProcessor {
    private const val MAX_IMAGE_INPUT_BYTES = 12L * 1024L * 1024L // 12 MB raw limit
    private const val MAX_VIDEO_INPUT_BYTES = 6L * 1024L * 1024L // 6 MB raw MP4/WebM limit for Firestore chunking
    private const val MAX_VIDEO_DURATION_MS = 60_000L // 60 seconds
    private const val CHUNK_RAW_BYTES = 450_000 // ~600 KB Base64 per Firestore chunk doc (< 1 MiB limit)
    private const val MAX_DIMENSION_PX = 720
    private const val TARGET_MAX_ENCODED_BYTES = 160_000 // keeps Firestore docs fast & well under 1MB

    suspend fun processImage(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<ProcessedMedia> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0.15f)
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            require(mimeType.startsWith("image/")) {
                "Formato no compatible. Selecciona una imagen JPEG, PNG o WebP."
            }
            val fileSize = queryFileSize(context, uri)
            require(fileSize <= MAX_IMAGE_INPUT_BYTES) {
                "La imagen supera el límite permitido de 12 MB."
            }

            coroutineContext.ensureActive()
            onProgress(0.35f)

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val sampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                MAX_DIMENSION_PX,
                MAX_DIMENSION_PX
            )

            coroutineContext.ensureActive()
            onProgress(0.6f)

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: error("No se pudo leer la imagen seleccionada.")

            val scaled = scaleBitmapDown(rawBitmap, MAX_DIMENSION_PX)
            if (scaled != rawBitmap) rawBitmap.recycle()

            coroutineContext.ensureActive()
            onProgress(0.85f)

            var quality = 78
            var compressedBytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                compressedBytes = out.toByteArray()
                quality -= 10
            } while (compressedBytes.size > TARGET_MAX_ENCODED_BYTES && quality >= 35)

            scaled.recycle()
            val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            onProgress(1.0f)

            ProcessedMedia(
                type = "image",
                encodedData = "data:image/jpeg;base64,$base64",
                durationMs = 0L,
                originalSizeBytes = fileSize,
                compressedSizeBytes = compressedBytes.size,
                localUri = uri
            )
        }
    }

    suspend fun processVideo(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<ProcessedMedia> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0.10f)
            val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
            require(mimeType.startsWith("video/")) {
                "Formato no compatible. Selecciona un vídeo MP4 o WebM."
            }
            val reportedSize = queryFileSize(context, uri)
            require(reportedSize <= MAX_VIDEO_INPUT_BYTES) {
                "El vídeo supera el tamaño máximo permitido de 6 MB."
            }

            coroutineContext.ensureActive()
            onProgress(0.30f)

            val retriever = MediaMetadataRetriever()
            var durationMs = 0L
            var frame: Bitmap? = null
            try {
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L
                require(durationMs <= MAX_VIDEO_DURATION_MS) {
                    "El vídeo supera la duración máxima de 60 segundos."
                }
                frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                runCatching { retriever.release() }
            }

            coroutineContext.ensureActive()
            onProgress(0.55f)

            val encodedThumb = if (frame != null) {
                val scaled = scaleBitmapDown(frame, 640)
                if (scaled != frame) frame.recycle()
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
                scaled.recycle()
                val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                "data:image/jpeg;base64,$b64"
            } else {
                ""
            }

            coroutineContext.ensureActive()
            onProgress(0.75f)

            val videoBytes = readBoundedBytes(context, uri, MAX_VIDEO_INPUT_BYTES)
            val chunks = encodeChunks(videoBytes)

            onProgress(1.0f)

            ProcessedMedia(
                type = "video",
                encodedData = encodedThumb,
                durationMs = durationMs,
                originalSizeBytes = videoBytes.size.toLong(),
                compressedSizeBytes = videoBytes.size,
                videoChunks = chunks,
                localUri = uri
            )
        }
    }

    fun encodeChunks(bytes: ByteArray): List<String> {
        if (bytes.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var offset = 0
        while (offset < bytes.size) {
            val end = (offset + CHUNK_RAW_BYTES).coerceAtMost(bytes.size)
            val slice = bytes.copyOfRange(offset, end)
            result.add(Base64.encodeToString(slice, Base64.NO_WRAP))
            offset = end
        }
        return result
    }

    suspend fun writeVideoChunksToCacheFile(
        context: Context,
        postId: String,
        chunks: List<String>
    ): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "murmur_vid_${postId}.mp4")
        FileOutputStream(target).use { fos ->
            chunks.forEach { b64 ->
                val decoded = Base64.decode(b64, Base64.DEFAULT)
                fos.write(decoded)
            }
            fos.flush()
        }
        target
    }

    fun getCachedVideoFileIfPresent(context: Context, postId: String): File? {
        val target = File(context.cacheDir, "murmur_vid_${postId}.mp4")
        return if (target.exists() && target.length() > 0L) target else null
    }

    private fun readBoundedBytes(context: Context, uri: Uri, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        context.contentResolver.openInputStream(uri)?.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                total += read
                require(total <= maxBytes) {
                    "El vídeo supera el tamaño máximo permitido de 6 MB."
                }
                out.write(buffer, 0, read)
            }
        } ?: error("No se pudo leer el archivo de vídeo seleccionado.")
        require(total > 0L) { "El archivo de vídeo está vacío." }
        return out.toByteArray()
    }

    fun decodeDataUriToBytes(dataUri: String?): ByteArray? {
        if (dataUri.isNullOrBlank()) return null
        val prefix = "base64,"
        val idx = dataUri.indexOf(prefix)
        val raw = if (idx >= 0) dataUri.substring(idx + prefix.length) else dataUri
        return runCatching { Base64.decode(raw, Base64.DEFAULT) }.getOrNull()
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) cursor.getLong(idx) else 0L
                } else 0L
            } ?: 0L
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (height > reqH || width > reqW) {
            val halfH = height / 2
            val halfW = width / 2
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val targetW: Int
        val targetH: Int
        if (ratio > 1f) {
            targetW = maxDim
            targetH = (maxDim / ratio).toInt().coerceAtLeast(1)
        } else {
            targetH = maxDim
            targetW = (maxDim * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}

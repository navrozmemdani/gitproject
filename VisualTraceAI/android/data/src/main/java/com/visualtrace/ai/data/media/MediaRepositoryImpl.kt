package com.visualtrace.ai.data.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.ExtractedFrame
import com.visualtrace.ai.domain.model.MediaKind
import com.visualtrace.ai.domain.model.MediaRules
import com.visualtrace.ai.domain.model.MediaSelection
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaRepository {

    private val resolver: ContentResolver get() = context.contentResolver
    private val framesDir get() = File(context.cacheDir, "frames").apply { mkdirs() }
    private val cameraDir get() = File(context.cacheDir, "camera").apply { mkdirs() }

    override suspend fun inspect(uri: String): Outcome<MediaSelection> = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        try {
            var name: String? = null
            var size: Long? = null
            resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    size = if (c.isNull(1)) null else c.getLong(1)
                }
            }
            if (name == null) name = parsed.lastPathSegment
            val mime = resolver.getType(parsed)
            val kind = when (val r = MediaRules.validate(mime, name, size)) {
                is Outcome.Success -> r.value
                is Outcome.Failure -> return@withContext r
            }
            if (kind == MediaKind.IMAGE) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                    return@withContext Outcome.Failure(AppError.UnsupportedFormat("The image could not be read."))
                }
                Outcome.Success(MediaSelection(uri, kind, mime, name, size, opts.outWidth, opts.outHeight))
            } else {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, parsed)
                    val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    Outcome.Success(MediaSelection(uri, kind, mime, name, size, w, h, duration))
                } catch (e: RuntimeException) {
                    Outcome.Failure(AppError.UnsupportedFormat("The video could not be read."))
                } finally {
                    r.release()
                }
            }
        } catch (e: SecurityException) {
            Outcome.Failure(AppError.Validation("Permission to read this file was revoked. Please choose it again."))
        } catch (e: java.io.FileNotFoundException) {
            Outcome.Failure(AppError.NotFound("The file is no longer available."))
        }
    }

    /**
     * Decodes, applies EXIF orientation, downsizes to [maxEdge] and re-encodes as JPEG.
     * Re-encoding drops all metadata (including GPS location) before anything is uploaded.
     */
    suspend fun prepareImageForUpload(uri: String, maxEdge: Int = 2048): Outcome<ByteArray> = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return@withContext Outcome.Failure(AppError.UnsupportedFormat("The image could not be read."))
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
            val bitmap = resolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext Outcome.Failure(AppError.UnsupportedFormat("The image could not be decoded."))
            val orientation = resolver.openInputStream(parsed)?.use {
                runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                    .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val oriented = rotate(bitmap, orientation)
            val scaled = scaleDown(oriented, maxEdge)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
            Outcome.Success(out.toByteArray())
        } catch (e: OutOfMemoryError) {
            Outcome.Failure(AppError.FileTooLarge())
        } catch (e: SecurityException) {
            Outcome.Failure(AppError.Validation("Permission to read this file was revoked. Please choose it again."))
        }
    }

    override suspend fun extractFrames(
        media: MediaSelection,
        intervalSeconds: Float,
        maxDurationSeconds: Int,
        maxFrames: Int,
    ): Outcome<List<ExtractedFrame>> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(media.uri))
            val duration = media.durationMs
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val stamps = MediaRules.frameTimestamps(duration, intervalSeconds, maxDurationSeconds, maxFrames)
            val frames = mutableListOf<ExtractedFrame>()
            var previousHash: Long? = null
            for (ts in stamps) {
                val bmp = frameAt(retriever, ts) ?: continue
                val hash = FrameHash.dHash(bmp)
                // Skip near-identical consecutive frames: only representative frames are searched.
                if (previousHash != null && FrameHash.hamming(hash, previousHash) <= 6) continue
                previousHash = hash
                val file = File(framesDir, "frame_${System.nanoTime()}_$ts.jpg")
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                frames += ExtractedFrame(file.absolutePath, ts)
            }
            if (frames.isEmpty()) Outcome.Failure(AppError.UnsupportedFormat("No frames could be extracted from this video."))
            else Outcome.Success(frames)
        } catch (e: RuntimeException) {
            Outcome.Failure(AppError.UnsupportedFormat("The video could not be decoded on this device."))
        } finally {
            retriever.release()
        }
    }

    private fun frameAt(r: MediaMetadataRetriever, ms: Long): Bitmap? {
        val us = ms * 1000
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST, 1280, 1280)
        } else {
            r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)
        } ?: return null
        return scaleDown(raw, 1280)
    }

    override suspend fun createCameraCaptureUri(): String = withContext(Dispatchers.IO) {
        val file = File(cameraDir, "capture_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
    }

    override suspend fun cleanupTemporaryFiles(olderThanMs: Long) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        listOf(framesDir, cameraDir, File(context.cacheDir, "uploads")).forEach { dir ->
            dir.listFiles()?.filter { it.lastModified() <= cutoff }?.forEach { it.delete() }
        }
    }

    private fun rotate(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.preScale(1f, -1f)
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun scaleDown(src: Bitmap, maxEdge: Int): Bitmap {
        val edge = max(src.width, src.height)
        if (edge <= maxEdge) return src
        val f = maxEdge.toFloat() / edge
        return Bitmap.createScaledBitmap(src, (src.width * f).roundToInt(), (src.height * f).roundToInt(), true)
    }
}

/** 64-bit difference hash used to skip near-duplicate video frames on device. */
object FrameHash {
    fun dHash(bitmap: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        val gray = IntArray(72) { i ->
            val p = small.getPixel(i % 9, i / 9)
            ((p shr 16 and 0xff) * 299 + (p shr 8 and 0xff) * 587 + (p and 0xff) * 114) / 1000
        }
        return dHash(gray)
    }

    /** [gray] is a 9x8 row-major grayscale grid. */
    fun dHash(gray: IntArray): Long {
        require(gray.size == 72)
        var hash = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            hash = hash shl 1
            if (gray[y * 9 + x + 1] > gray[y * 9 + x]) hash = hash or 1L
        }
        return hash
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}

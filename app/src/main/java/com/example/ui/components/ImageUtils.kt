package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

enum class PhotoFilterPreset(val label: String) {
    ORIGINAL("Original"),
    VIVID("Crisp Stock"),
    WARM("Warm Shop"),
    COOL("Cool LED"),
    BW("Receipt B&W"),
    HIGH_CONTRAST("High Contrast")
}

data class NormalizedStroke(
    val points: List<Pair<Float, Float>>,
    val colorArgb: Long,
    val strokeWidthFraction: Float = 0.01f
)

data class PhotoEditSpec(
    val rotationQuarterTurns: Int = 0,
    val straightenDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val filterPreset: PhotoFilterPreset = PhotoFilterPreset.ORIGINAL,
    val strokes: List<NormalizedStroke> = emptyList()
)

object ImageUtils {

    fun decodeBitmapFromBase64(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val cleaned = if (base64.contains("base64,")) {
                base64.substringAfter("base64,").trim()
            } else {
                base64.trim()
            }
            val bytes = Base64.decode(cleaned, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 1280): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            var sampleSize = 1
            while (
                boundsOpts.outWidth / sampleSize > maxDimension ||
                boundsOpts.outHeight / sampleSize > maxDimension
            ) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val rotation = readExifRotation(context, uri)
            if (rotation != 0f) {
                val m = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            } else {
                raw
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readExifRotation(context: Context, uri: Uri): Float {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
        }
    }

    fun applyEdits(source: Bitmap, spec: PhotoEditSpec): Bitmap {
        val totalDegrees = (spec.rotationQuarterTurns * 90f) + spec.straightenDegrees
        val matrix = Matrix().apply {
            if (spec.flipHorizontal || spec.flipVertical) {
                postScale(
                    if (spec.flipHorizontal) -1f else 1f,
                    if (spec.flipVertical) -1f else 1f
                )
            }
            if (totalDegrees != 0f) {
                postRotate(totalDegrees)
            }
        }
        val transformed = if (!matrix.isIdentity) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        val leftFrac = spec.cropLeft.coerceIn(0f, 0.9f)
        val topFrac = spec.cropTop.coerceIn(0f, 0.9f)
        val rightFrac = spec.cropRight.coerceIn(leftFrac + 0.1f, 1f)
        val bottomFrac = spec.cropBottom.coerceIn(topFrac + 0.1f, 1f)

        val cropX = (leftFrac * transformed.width).roundToInt().coerceIn(0, transformed.width - 1)
        val cropY = (topFrac * transformed.height).roundToInt().coerceIn(0, transformed.height - 1)
        val cropW = ((rightFrac - leftFrac) * transformed.width).roundToInt().coerceIn(1, transformed.width - cropX)
        val cropH = ((bottomFrac - topFrac) * transformed.height).roundToInt().coerceIn(1, transformed.height - cropY)

        val cropped = Bitmap.createBitmap(transformed, cropX, cropY, cropW, cropH)
        val result = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(buildColorMatrix(spec))
        }
        canvas.drawBitmap(cropped, 0f, 0f, paint)

        spec.strokes.forEach { stroke ->
            if (stroke.points.size >= 2) {
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.colorArgb.toInt()
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = (stroke.strokeWidthFraction * result.width).coerceAtLeast(4f)
                }
                val path = Path()
                val first = stroke.points.first()
                path.moveTo(first.first * result.width, first.second * result.height)
                stroke.points.drop(1).forEach { pt ->
                    path.lineTo(pt.first * result.width, pt.second * result.height)
                }
                canvas.drawPath(path, strokePaint)
            }
        }
        return result
    }

    fun buildColorMatrix(spec: PhotoEditSpec): ColorMatrix {
        val cm = ColorMatrix()
        when (spec.filterPreset) {
            PhotoFilterPreset.ORIGINAL -> {}
            PhotoFilterPreset.VIVID -> {
                val sat = ColorMatrix().apply { setSaturation(1.35f) }
                cm.postConcat(sat)
            }
            PhotoFilterPreset.WARM -> {
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.12f, 0f, 0f, 0f, 10f,
                            0f, 1.03f, 0f, 0f, 4f,
                            0f, 0f, 0.88f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            PhotoFilterPreset.COOL -> {
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            0.90f, 0f, 0f, 0f, 0f,
                            0f, 1.02f, 0f, 0f, 4f,
                            0f, 0f, 1.15f, 0f, 10f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            PhotoFilterPreset.BW -> {
                val bw = ColorMatrix().apply { setSaturation(0f) }
                cm.postConcat(bw)
            }
            PhotoFilterPreset.HIGH_CONTRAST -> {
                val c = 1.35f
                val t = (-0.5f * c + 0.5f) * 255f
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            c, 0f, 0f, 0f, t,
                            0f, c, 0f, 0f, t,
                            0f, 0f, c, 0f, t,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
        }

        if (spec.saturation != 1f) {
            val sat = ColorMatrix().apply { setSaturation(spec.saturation) }
            cm.postConcat(sat)
        }
        if (spec.contrast != 1f || spec.brightness != 0f) {
            val c = spec.contrast
            val b = spec.brightness * 255f
            val t = (-0.5f * c + 0.5f) * 255f + b
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, t,
                        0f, c, 0f, 0f, t,
                        0f, 0f, c, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        return cm
    }

    fun encodeToBase64(bitmap: Bitmap, maxWidth: Int = 960, quality: Int = 78): String {
        val scaled = if (bitmap.width > maxWidth || bitmap.height > maxWidth) {
            val scale = maxWidth.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        var currentQuality = quality
        val out = ByteArrayOutputStream()
        do {
            out.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, currentQuality, out)
            currentQuality -= 10
        } while (out.size() > 450_000 && currentQuality >= 35)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun saveBitmapToCacheFile(context: Context, bitmap: Bitmap, prefix: String = "shopkeep_edit"): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        }
        return Uri.fromFile(file)
    }
}

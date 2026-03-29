package com.luoshui.paycardeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.core.graphics.createBitmap
import java.io.File
import kotlin.math.max
import kotlin.math.min

object CardImageProcessor {

    private const val TARGET_WIDTH = 960
    private const val TARGET_HEIGHT = 605
    private const val BASE_RADIUS = 40f

    fun copyUriToTemp(context: Context, uri: Uri): File {
        val file = CardAssetRepository.createTempFile("pick_")
        val inputStream = requireNotNull(context.contentResolver.openInputStream(uri)) { "Unable to open image uri: $uri" }
        inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    fun makeRoundedPng(sourceFile: File, destinationFile: File) {
        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
            ?: error("Failed to decode cropped image")
        val rounded = roundBitmap(bitmap)
        destinationFile.outputStream().use { stream ->
            rounded.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    fun decodePreview(file: File, maxSize: Int = 720): Bitmap? {
        if (!file.exists()) {
            return null
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val sampleSize = max(1, min(options.outWidth, options.outHeight) / maxSize)
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    private fun roundBitmap(source: Bitmap): Bitmap {
        val output = createBitmap(source.width, source.height)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = BASE_RADIUS * (source.width / TARGET_WIDTH.toFloat())
        canvas.drawRoundRect(RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()), radius, radius, paint)
        return output
    }
}

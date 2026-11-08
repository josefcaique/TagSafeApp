package com.digitalsix.YouSafe.face

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

object FaceImage {
    /** Bake sensor rotation into JPEG pixels, keeping uploads small and upright on the API. */
    fun prepare(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Não foi possível ler a imagem capturada." }
        val options = BitmapFactory.Options()
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize.coerceAtLeast(1) > 1800) {
            options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
        }
        val original = requireNotNull(BitmapFactory.decodeFile(file.absolutePath, options))
        var rotated: Bitmap? = null
        try {
            val exif = ExifInterface(file)
            val matrix = Matrix().apply {
                if (exif.isFlipped) postScale(-1f, 1f)
                postRotate(exif.rotationDegrees.toFloat())
            }
            rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            file.outputStream().use { require(rotated.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        } finally {
            if (rotated !== original) rotated?.recycle()
            original.recycle()
        }
    }
}

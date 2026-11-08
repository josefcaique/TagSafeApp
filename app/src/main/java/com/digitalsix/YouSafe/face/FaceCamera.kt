package com.digitalsix.YouSafe.face

import android.graphics.BitmapFactory
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File

/** Shared multipart validation for internally captured images. */
object FaceCamera {
        fun multipart(file: File): MultipartBody.Part {
            require(file.length() in 1..FaceRules.MAX_IMAGE_BYTES.toLong()) { "Use uma foto de até 8 MB. Tire outra foto." }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val type = options.outMimeType
            require(type in listOf("image/jpeg", "image/png") && options.outWidth > 0 && options.outHeight > 0) {
                "Formato inválido. Use uma foto JPEG ou PNG."
            }
            return MultipartBody.Part.createFormData("file", if (type == "image/png") "face.png" else "face.jpg",
                RequestBody.create(MediaType.parse(type!!), file))
        }
        fun text(value: String): RequestBody = RequestBody.create(MediaType.parse("text/plain"), value)
}

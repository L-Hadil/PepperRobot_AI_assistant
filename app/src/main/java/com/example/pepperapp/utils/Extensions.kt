package com.example.pepperapp.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

import java.io.ByteArrayOutputStream

fun Bitmap.toBase64(): String {
    val output = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.JPEG, 80, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

fun String.toBitmap(): Bitmap {
    val bytes = Base64.decode(this, Base64.NO_WRAP)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

fun Bitmap.scaleDown(width: Int, height: Int): Bitmap {
    return Bitmap.createScaledBitmap(this, width, height, true)
}

fun Bitmap.grayscale(): Bitmap {
    val gray = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
    for (x in 0 until this.width) {
        for (y in 0 until this.height) {
            val pixel = this.getPixel(x, y)
            val avg = ((Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3)
            gray.setPixel(x, y, Color.rgb(avg, avg, avg))
        }
    }
    return gray
}

fun Bitmap.flatten(): List<Int> {
    val pixels = mutableListOf<Int>()
    for (y in 0 until height) {
        for (x in 0 until width) {
            pixels.add(Color.red(getPixel(x, y)))
        }
    }
    return pixels
}

suspend fun Task<List<Face>>.awaitFaces(): List<Face> = suspendCancellableCoroutine { cont ->
    this.addOnSuccessListener { faces ->
        cont.resume(faces) {}
    }.addOnFailureListener { e ->
        cont.resumeWithException(e)
    }
}

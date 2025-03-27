package com.example.pepperapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.example.pepperapp.data.PepperDatabase
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.runBlocking


object FaceRecognitionHelper {

    private val detector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    fun findMatchingProfile(context: Context, bitmap: Bitmap): String? {
        val image = InputImage.fromBitmap(bitmap, 0)
        var matchedId: String? = null

        val db = PepperDatabase.getDatabase(context)
        val dao = db.userProfileDao()

        runBlocking {
            val faces = detector.process(image).awaitFaces()

            if (faces.isNotEmpty()) {
                val face = faces[0]
                val faceBitmap = cropFace(bitmap, face.boundingBox)
                val allProfiles = dao.getAll()

                for (profile in allProfiles) {
                    val profileBitmap = profile.photoBase64.toBitmap()
                    val profileFaces = detector.process(InputImage.fromBitmap(profileBitmap, 0)).awaitFaces()

                    if (profileFaces.isNotEmpty()) {
                        val profileFaceBitmap = cropFace(profileBitmap, profileFaces[0].boundingBox)
                        if (compareBitmaps(faceBitmap, profileFaceBitmap)) {
                            matchedId = profile.id
                            break
                        }
                    }
                }
            }
        }

        return matchedId
    }

    private fun compareBitmaps(bitmap1: Bitmap, bitmap2: Bitmap): Boolean {
        val b1 = bitmap1.scaleDown(64, 64).grayscale().flatten()
        val b2 = bitmap2.scaleDown(64, 64).grayscale().flatten()

        val diff = b1.zip(b2) { a, b -> (a - b) * (a - b) }.sum()
        val mse = diff / b1.size.toFloat()

        return mse < 1000
    }

    private fun cropFace(original: Bitmap, rect: Rect): Bitmap {
        val safeRect = Rect(
            rect.left.coerceAtLeast(0),
            rect.top.coerceAtLeast(0),
            rect.right.coerceAtMost(original.width),
            rect.bottom.coerceAtMost(original.height)
        )
        return Bitmap.createBitmap(
            original,
            safeRect.left,
            safeRect.top,
            safeRect.width(),
            safeRect.height()
        )
    }
}
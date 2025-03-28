package com.example.pepperapp.ui.Fragments

import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView

import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pepperapp.R
import com.example.pepperapp.data.PepperDatabase
import com.example.pepperapp.model.UserProfile
import com.example.pepperapp.utils.toBase64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ProfileFragment : Fragment() {

    private lateinit var previewView: PreviewView
    private lateinit var imgFacePreview: ImageView
    private lateinit var inputName: EditText
    private lateinit var inputAge: EditText
    private lateinit var btnTakePhoto: Button
    private lateinit var btnSave: Button

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture
    private var capturedFaceBitmap: Bitmap? = null
    private var photoTaken = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        previewView = view.findViewById(R.id.previewView)
        imgFacePreview = view.findViewById(R.id.imgFacePreview)
        inputName = view.findViewById(R.id.inputName)
        inputAge = view.findViewById(R.id.inputAge)
        btnTakePhoto = view.findViewById(R.id.btnTakePhoto)
        btnSave = view.findViewById(R.id.btnSaveProfile)

        btnTakePhoto.setOnClickListener {
            if (!photoTaken) {
                capturePhoto()
            } else {
                Toast.makeText(requireContext(), "Photo déjà prise. Recharge la page pour en reprendre une.", Toast.LENGTH_SHORT).show()
            }
        }

        btnSave.setOnClickListener {
            saveProfile()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        return view
    }

    override fun onResume() {
        super.onResume()
        photoTaken = false
        capturedFaceBitmap = null
        startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun capturePhoto() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()
                    detectAndCropFace(bitmap)
                    photoTaken = true
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(requireContext(), "Erreur de capture", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val planeProxy = imageProxy.planes[0]
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun detectAndCropFace(originalBitmap: Bitmap) {
        val image = InputImage.fromBitmap(originalBitmap, 0)
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
        )

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val faceBox = faces[0].boundingBox
                    val safeBox = Rect(
                        faceBox.left.coerceAtLeast(0),
                        faceBox.top.coerceAtLeast(0),
                        faceBox.right.coerceAtMost(originalBitmap.width),
                        faceBox.bottom.coerceAtMost(originalBitmap.height)
                    )
                    val faceBitmap = Bitmap.createBitmap(
                        originalBitmap,
                        safeBox.left,
                        safeBox.top,
                        safeBox.width(),
                        safeBox.height()
                    )
                    capturedFaceBitmap = faceBitmap
                    imgFacePreview.setImageBitmap(faceBitmap)
                } else {
                    Toast.makeText(requireContext(), "Aucun visage détecté", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Erreur de détection de visage", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveProfile() {
        val name = inputName.text.toString().trim()
        val age = inputAge.text.toString().trim().toIntOrNull()
        val photoBase64 = capturedFaceBitmap?.toBase64() ?: ""

        if (name.isEmpty() || age == null) {
            Toast.makeText(requireContext(), "Nom et âge sont requis.", Toast.LENGTH_SHORT).show()
            return
        }

        val user = UserProfile(
            name = name,
            age = age,
            photoBase64 = photoBase64,
            threadId = null // à remplir plus tard
        )

        lifecycleScope.launch {
            val db = PepperDatabase.getDatabase(requireContext())
            db.userProfileDao().insert(user)

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Profil enregistré avec succès !", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

package com.example.pepperapp.ui.Fragments

import android.content.pm.PackageManager
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
import com.aldebaran.qi.sdk.QiContext
import com.example.pepperapp.R
import com.example.pepperapp.data.PepperDatabase
import com.example.pepperapp.model.UserProfile
import com.example.pepperapp.utils.AzureFaceApiHelper
import com.example.pepperapp.utils.toBase64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ProfileFragment : Fragment() {

    private var qiContext: QiContext? = null
    fun setQiContext(qiContext: QiContext) {
        this.qiContext = qiContext
    }

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
            if (!photoTaken) capturePhoto()
            else Toast.makeText(requireContext(), "Photo déjà prise.", Toast.LENGTH_SHORT).show()
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

        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Permission caméra refusée", Toast.LENGTH_LONG).show()
        }
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
                Log.e("CameraX", "Camera init failed", exc)
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
                    detectFacePresence(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(requireContext(), "Erreur de capture", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun detectFacePresence(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
        )

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    capturedFaceBitmap = bitmap
                    imgFacePreview.setImageBitmap(bitmap)
                    photoTaken = true
                } else {
                    Toast.makeText(requireContext(), "Aucun visage détecté", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Erreur détection visage", Toast.LENGTH_SHORT).show()
            }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun saveProfile() {
        val name = inputName.text.toString().trim()
        val age = inputAge.text.toString().trim().toIntOrNull()
        val photo = capturedFaceBitmap ?: return Toast.makeText(requireContext(), "Photo requise", Toast.LENGTH_SHORT).show()

        if (name.isEmpty() || age == null) {
            Toast.makeText(requireContext(), "Nom et âge requis", Toast.LENGTH_SHORT).show()
            return
        }

        val photoBase64 = photo.toBase64()

        lifecycleScope.launch {
            try {
                val personId = AzureFaceApiHelper.createPerson(name)
                val faceAdded = AzureFaceApiHelper.addFaceToPerson(personId, photo)
                AzureFaceApiHelper.trainPersonGroup()

                val user = UserProfile(
                    name = name,
                    age = age,
                    photoBase64 = photoBase64,
                    threadIdAzure = personId
                )

                val db = PepperDatabase.getDatabase(requireContext())
                db.userProfileDao().insert(user)

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Profil enregistré avec Azure !", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Erreur Azure: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

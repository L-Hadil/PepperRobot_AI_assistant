package com.example.pepperapp.ui.Fragments

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ProfileFragment : Fragment() {

    private var qiContext: QiContext? = null
    fun setQiContext(qiContext: QiContext) {
        this.qiContext = qiContext
        Log.d("ProfileFragment", "QiContext initialisé")
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
        Log.d("ProfileFragment", "onCreateView appelé")
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        previewView = view.findViewById(R.id.previewView)
        imgFacePreview = view.findViewById(R.id.imgFacePreview)
        inputName = view.findViewById(R.id.inputName)
        inputAge = view.findViewById(R.id.inputAge)
        btnTakePhoto = view.findViewById(R.id.btnTakePhoto)
        btnSave = view.findViewById(R.id.btnSaveProfile)

        btnTakePhoto.setOnClickListener {
            Log.d("ProfileFragment", "Bouton 'Prendre Photo' cliqué")
            if (!photoTaken) {
                capturePhoto()
            } else {
                Toast.makeText(requireContext(), "Photo déjà prise.", Toast.LENGTH_SHORT).show()
                Log.d("ProfileFragment", "Tentative de capture alors qu'une photo a déjà été prise")
            }
        }

        btnSave.setOnClickListener {
            Log.d("ProfileFragment", "Bouton 'Sauvegarder' cliqué")
            saveProfile()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        return view
    }

    override fun onResume() {
        super.onResume()
        Log.d("ProfileFragment", "onResume appelé")
        photoTaken = false
        capturedFaceBitmap = null

        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("ProfileFragment", "Permission caméra déjà accordée")
            startCamera()
        } else {
            Log.d("ProfileFragment", "Demande de permission caméra")
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ProfileFragment", "onDestroy appelé, arrêt du cameraExecutor")
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            Log.d("ProfileFragment", "Permission caméra accordée")
            startCamera()
        } else {
            Log.d("ProfileFragment", "Permission caméra refusée")
            Toast.makeText(requireContext(), "Permission caméra refusée", Toast.LENGTH_LONG).show()
        }
    }

    private fun startCamera() {
        Log.d("ProfileFragment", "Initialisation de la caméra")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder().build()
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                Log.d("ProfileFragment", "Caméra démarrée avec succès")
            } catch (exc: Exception) {
                Log.e("ProfileFragment", "Échec de l'initialisation de la caméra", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun capturePhoto() {
        Log.d("ProfileFragment", "Début de la capture photo")
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    Log.d("ProfileFragment", "Capture photo réussie")
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()
                    detectFacePresence(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("ProfileFragment", "Erreur de capture photo", exception)
                    Toast.makeText(requireContext(), "Erreur de capture", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun detectFacePresence(bitmap: Bitmap) {
        Log.d("ProfileFragment", "Début de la détection de visage")
        val image = InputImage.fromBitmap(bitmap, 0)
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
        )

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    Log.d("ProfileFragment", "Visage détecté, nombre de visages : ${faces.size}")
                    capturedFaceBitmap = bitmap
                    imgFacePreview.setImageBitmap(bitmap)
                    photoTaken = true
                } else {
                    Log.d("ProfileFragment", "Aucun visage détecté")
                    Toast.makeText(requireContext(), "Aucun visage détecté", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ProfileFragment", "Erreur lors de la détection de visage", e)
                Toast.makeText(requireContext(), "Erreur détection visage", Toast.LENGTH_SHORT).show()
            }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        Log.d("ProfileFragment", "Conversion de l'image capturée en Bitmap")
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun saveProfile() {
        Log.d("ProfileFragment", "Début de la sauvegarde du profil")
        val name = inputName.text.toString().trim()
        val age = inputAge.text.toString().trim().toIntOrNull()
        val photo = capturedFaceBitmap ?: run {
            Log.d("ProfileFragment", "Sauvegarde annulée : photo requise")
            Toast.makeText(requireContext(), "Photo requise", Toast.LENGTH_SHORT).show()
            return
        }

        if (name.isEmpty() || age == null) {
            Log.d("ProfileFragment", "Sauvegarde annulée : nom ou âge manquant/invalide")
            Toast.makeText(requireContext(), "Nom et âge requis", Toast.LENGTH_SHORT).show()
            return
        }

        val photoBase64 = photo.toBase64()
        Log.d("ProfileFragment", "Conversion de la photo en Base64 effectuée")

        lifecycleScope.launch {
            try {
                val user = UserProfile(
                    name = name,
                    age = age,
                    photoBase64 = photoBase64,
                    threadIdGPT = ""  // Laisser ce champ vide ou null pour l'instant
                )

                val db = PepperDatabase.getDatabase(requireContext())
                db.userProfileDao().insert(user)
                Log.d("ProfileFragment", "Profil inséré dans la base de données avec succès")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Profil enregistré en local dans la base de données!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Erreur lors de l'enregistrement du profil", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Erreur lors de l'enregistrement: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

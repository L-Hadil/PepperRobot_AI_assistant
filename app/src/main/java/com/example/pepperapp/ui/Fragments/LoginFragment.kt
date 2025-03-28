package com.example.pepperapp.ui.Fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pepperapp.R
import com.example.pepperapp.data.PepperDatabase

import com.example.pepperapp.utils.AzureFaceApiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LoginFragment : Fragment() {

    private lateinit var previewView: PreviewView
    private lateinit var btnVerify: Button
    private lateinit var inputNameLogin: EditText
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        previewView = view.findViewById(R.id.previewViewLogin)
        btnVerify = view.findViewById(R.id.btnVerifyFace)
        inputNameLogin = view.findViewById(R.id.inputNameLogin) // Assure-toi que ce champ existe dans ton layout XML

        btnVerify.setOnClickListener {
            val inputName = inputNameLogin.text.toString().trim()
            if (inputName.isNotEmpty()) {
                // Connexion via prénom
                lifecycleScope.launch {
                    val db = PepperDatabase.getDatabase(requireContext())
                    val user = db.userProfileDao().getUserByName(inputName)
                    withContext(Dispatchers.Main) {
                        if (user != null) {
                            Toast.makeText(requireContext(), "Bienvenue ${user.name} !", Toast.LENGTH_LONG).show()
                            // Navigation vers autre fragment si nécessaire
                        } else {
                            Toast.makeText(requireContext(), "Aucun utilisateur trouvé avec ce prénom.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                // Connexion via reconnaissance faciale
                captureAndIdentifyFace()
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        return view
    }

    override fun onResume() {
        super.onResume()
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
            } catch (e: Exception) {
                Log.e("CameraX", "Camera start failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun captureAndIdentifyFace() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val buffer = imageProxy.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    imageProxy.close()

                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    val byteArray = outputStream.toByteArray()

                    AzureFaceApiHelper.identifyFace(byteArray) { matchedName ->
                        requireActivity().runOnUiThread {
                            if (matchedName != null) {
                                Toast.makeText(requireContext(), "Bienvenue $matchedName !", Toast.LENGTH_LONG).show()
                                // Navigation ici si tu veux
                            } else {
                                Toast.makeText(requireContext(), "Visage non reconnu.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(requireContext(), "Erreur de capture", Toast.LENGTH_SHORT).show()
                }
            })
    }
}

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
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.`object`.locale.Locale
import com.aldebaran.qi.sdk.`object`.locale.Language
import com.aldebaran.qi.sdk.`object`.locale.Region
import com.example.pepperapp.R
import com.example.pepperapp.data.PepperDatabase
import com.example.pepperapp.ui.MainActivity
import com.example.pepperapp.utils.AzureFaceApiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LoginFragment : Fragment() {

    private lateinit var previewView: PreviewView
    private lateinit var btnVerifyFace: Button
    private lateinit var btnConfirmName: Button
    private lateinit var inputNameLogin: EditText
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("LoginFragment", "onCreateView appelé")
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        previewView = view.findViewById(R.id.previewViewLogin)
        btnVerifyFace = view.findViewById(R.id.btnVerifyFace)
        btnConfirmName = view.findViewById(R.id.btnConfirmName)
        inputNameLogin = view.findViewById(R.id.inputNameLogin)

        btnConfirmName.setOnClickListener {
            Log.d("LoginFragment", "Bouton 'Confirmer Nom' cliqué")
            val inputName = inputNameLogin.text.toString().trim()
            if (inputName.isNotEmpty()) {
                lifecycleScope.launch {
                    Log.d("LoginFragment", "Requête en base pour l'utilisateur: $inputName")
                    val db = PepperDatabase.getDatabase(requireContext())
                    val user = db.userProfileDao().getUserByName(inputName)
                    withContext(Dispatchers.Main) {
                        if (user != null) {
                            Log.d("LoginFragment", "Utilisateur trouvé: ${user.name}")
                            Toast.makeText(
                                requireContext(),
                                "Bienvenue ${user.name} !",
                                Toast.LENGTH_LONG
                            ).show()

                            val qiContext = (activity as? MainActivity)?.getQiContext()
                            if (qiContext != null) {
                                Log.d("LoginFragment", "Création de SayBuilder pour l'utilisateur: ${user.name}")
                                // Exécution de la commande Pepper dans un thread IO
                                lifecycleScope.launch(Dispatchers.IO) {
                                    try {
                                        val say = SayBuilder.with(qiContext)
                                            .withText("Bienvenue ${user.name}")
                                            .withLocale(Locale(Language.FRENCH, Region.FRANCE))
                                            .build()
                                        Log.d("LoginFragment", "Exécution de SayBuilder en IO")
                                        say.run() // Attendre que Pepper termine de parler

                                        // Naviguer vers ChatFragment après le message
                                        withContext(Dispatchers.Main) {
                                            val chatFragment = ChatFragment().apply {
                                                arguments = Bundle().apply {
                                                    putString("userName", user.name) // passe le prénom à ChatFragment
                                                }
                                            }

                                            parentFragmentManager.beginTransaction()
                                                .replace(R.id.container, chatFragment)
                                                .addToBackStack(null)
                                                .commit()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("LoginFragment", "Erreur lors de l'exécution de Say", e)
                                    }
                                }

                            } else {
                                Log.d("LoginFragment", "QiContext est null, impossible de créer SayBuilder")
                            }
                        } else {
                            Log.d("LoginFragment", "Aucun utilisateur trouvé pour le nom: $inputName")
                            Toast.makeText(
                                requireContext(),
                                "Aucun utilisateur trouvé avec ce prénom.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } else {
                Log.d("LoginFragment", "Champ prénom vide")
                Toast.makeText(requireContext(), "Veuillez entrer un prénom", Toast.LENGTH_SHORT).show()
            }
        }

        btnVerifyFace.setOnClickListener {
            Log.d("LoginFragment", "Bouton 'Vérifier Visage' cliqué")
            captureAndIdentifyFace()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        return view
    }

    override fun onResume() {
        super.onResume()
        Log.d("LoginFragment", "onResume appelé")
        startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LoginFragment", "onDestroy appelé, arrêt du cameraExecutor")
        cameraExecutor.shutdown()
    }

    private fun startCamera() {
        Log.d("LoginFragment", "Démarrage de la caméra")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                Log.d("LoginFragment", "CameraProvider obtenu")

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
                Log.d("LoginFragment", "Caméra démarrée avec succès")
            } catch (e: Exception) {
                Log.e("LoginFragment", "Échec du démarrage de la caméra", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun captureAndIdentifyFace() {
        Log.d("LoginFragment", "Début de la capture et identification du visage")
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    Log.d("LoginFragment", "Capture d'image réussie")
                    val buffer = imageProxy.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    imageProxy.close()
                    Log.d("LoginFragment", "Conversion de l'image en Bitmap réussie")

                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    val byteArray = outputStream.toByteArray()
                    Log.d("LoginFragment", "Conversion du Bitmap en tableau de bytes réussie")

                    AzureFaceApiHelper.identifyFace(byteArray) { matchedName ->
                        Log.d("LoginFragment", "Callback AzureFaceApiHelper reçu, matchedName: $matchedName")
                        requireActivity().runOnUiThread {
                            if (matchedName != null) {
                                Toast.makeText(
                                    requireContext(),
                                    "Bienvenue $matchedName !",
                                    Toast.LENGTH_LONG
                                ).show()

                                val qiContext = (activity as? MainActivity)?.getQiContext()
                                if (qiContext != null) {
                                    Log.d("LoginFragment", "Création de SayBuilder pour le visage identifié: $matchedName")
                                    // Exécuter la commande Pepper dans un thread IO
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            val say = SayBuilder.with(qiContext)
                                                .withText("Bienvenue $matchedName")
                                                .withLocale(Locale(Language.FRENCH, Region.FRANCE))
                                                .build()
                                            Log.d("LoginFragment", "Exécution de SayBuilder pour l'identification du visage en IO")
                                            say.run()
                                        } catch (e: Exception) {
                                            Log.e("LoginFragment", "Erreur lors de l'exécution de Say pour le visage identifié", e)
                                        }
                                    }
                                } else {
                                    Log.d("LoginFragment", "QiContext est null, impossible d'exécuter SayBuilder")
                                }
                            } else {
                                Log.d("LoginFragment", "Aucun visage reconnu par AzureFaceApiHelper")
                                Toast.makeText(requireContext(), "Visage non reconnu.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("LoginFragment", "Erreur lors de la capture d'image", exception)
                    Toast.makeText(requireContext(), "Erreur de capture", Toast.LENGTH_SHORT).show()
                }
            })
    }
}

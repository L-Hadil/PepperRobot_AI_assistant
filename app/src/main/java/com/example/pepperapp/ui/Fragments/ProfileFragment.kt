package com.example.pepperapp.ui.Fragments

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.builder.TakePictureBuilder
import com.aldebaran.qi.sdk.`object`.image.TimestampedImageHandle
import com.example.pepperapp.data.PepperDatabase
import com.example.pepperapp.model.UserProfile
import com.example.pepperapp.utils.SharedBitmapHolder
import com.example.pepperapp.utils.toBase64
import com.example.pepperrobot_ai_assistant.R
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.UUID

class ProfileFragment : Fragment() {

    private var capturedFaceBitmap: android.graphics.Bitmap? = null

    private lateinit var imgFacePreview: ImageView
    private lateinit var inputName: EditText
    private lateinit var inputAge: EditText
    private lateinit var btnTakePhoto: Button
    private lateinit var btnSave: Button

    private var qiContext: QiContext? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Charge le layout fragment_profile.xml
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        imgFacePreview = view.findViewById(R.id.imgFacePreview)
        inputName = view.findViewById(R.id.inputName)
        inputAge = view.findViewById(R.id.inputAge)
        btnTakePhoto = view.findViewById(R.id.btnTakePhoto)
        btnSave = view.findViewById(R.id.btnSaveProfile)

        // Si une photo a déjà été capturée, l’afficher
        capturedFaceBitmap = SharedBitmapHolder.bitmap
        capturedFaceBitmap?.let {
            imgFacePreview.setImageBitmap(it)
        }

        // Bouton pour prendre une photo
        btnTakePhoto.setOnClickListener {
            takePictureFromPepper()
        }

        // Bouton pour sauvegarder le profil
        btnSave.setOnClickListener {
            saveProfile()
        }
        return view
    }

    // Permet de recevoir le QiContext depuis MainActivity
    fun setQiContext(context: QiContext) {
        this.qiContext = context
    }

    private fun takePictureFromPepper() {
        val context = qiContext
        if (context == null) {
            Toast.makeText(requireContext(), "Pepper n'est pas prêt.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // Construire et exécuter l'action de prise de photo
            TakePictureBuilder.with(context).buildAsync()
                .andThenCompose { it.async().run() }
                .andThenConsume { imageHandle: TimestampedImageHandle ->
                    // Récupération des données de l'image
                    val encodedImage = imageHandle.image.value
                    val buffer: ByteBuffer = encodedImage.data
                    buffer.rewind()
                    val byteArray = ByteArray(buffer.remaining())
                    buffer.get(byteArray)
                    // Décodage en Bitmap
                    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                    // Sauvegarde dans le SharedBitmapHolder pour utilisation ultérieure
                    SharedBitmapHolder.bitmap = bitmap
                    capturedFaceBitmap = bitmap
                    // Mise à jour de l'interface utilisateur sur le thread UI
                    requireActivity().runOnUiThread {
                        imgFacePreview.setImageBitmap(bitmap)
                        Toast.makeText(requireContext(), "Photo prise avec succès !", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: Exception) {
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "Erreur lors de la prise de photo : ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveProfile() {
        val name = inputName.text.toString().trim()
        val age = inputAge.text.toString().trim().toIntOrNull()
        // Convertit le bitmap en Base64 via votre fonction d’extension
        val photoBase64 = capturedFaceBitmap?.toBase64() ?: ""

        if (name.isEmpty() || age == null) {
            Toast.makeText(requireContext(), "Nom et âge sont requis.", Toast.LENGTH_SHORT).show()
            return
        }

        // Création de l'objet UserProfile
        val profile = UserProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            age = age,
            photoBase64 = photoBase64,
            threadId = UUID.randomUUID().toString()
        )

        // Insertion dans la base de données via Room
        val dao = PepperDatabase.getDatabase(requireContext()).userProfileDao()
        lifecycleScope.launch {
            dao.insert(profile)
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "Profil enregistré avec succès !", Toast.LENGTH_SHORT).show()
                // Optionnel : retournez à un autre écran ou réinitialisez le formulaire

            }
        }
    }
}

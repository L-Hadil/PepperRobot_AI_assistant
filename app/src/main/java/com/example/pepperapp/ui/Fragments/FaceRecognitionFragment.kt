package com.example.pepperapp.ui.Fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.pepperapp.data.PepperDatabase
import com.example.pepperapp.utils.FaceRecognitionHelper
import com.example.pepperapp.utils.SharedBitmapHolder
import com.example.pepperrobot_ai_assistant.R
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.builder.TakePictureBuilder
import com.aldebaran.qi.sdk.`object`.image.TimestampedImageHandle
import kotlinx.coroutines.launch
import android.util.Log
import java.nio.ByteBuffer

class FaceRecognitionFragment : Fragment() {

    private lateinit var imgPreview: ImageView
    private lateinit var btnTakePicture: Button
    private var qiContext: QiContext? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.face_recognition, container, false)

        imgPreview = view.findViewById(R.id.imgRecognitionPreview)
        btnTakePicture = view.findViewById(R.id.btnRecognizeTakePhoto)

        btnTakePicture.setOnClickListener {
            takePictureFromPepper()
        }

        return view
    }

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
            TakePictureBuilder.with(context).buildAsync()
                .andThenCompose { it.async().run() }
                .andThenConsume { imageHandle: TimestampedImageHandle ->
                    val encodedImage = imageHandle.image.value
                    val buffer: ByteBuffer = encodedImage.data
                    buffer.rewind()
                    val byteArray = ByteArray(buffer.remaining())
                    buffer.get(byteArray)

                    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

                    requireActivity().runOnUiThread {
                        imgPreview.setImageBitmap(bitmap)
                        Toast.makeText(requireContext(), "Photo prise avec succès !", Toast.LENGTH_SHORT).show()
                    }

                    val matchedProfileId = FaceRecognitionHelper.findMatchingProfile(requireContext(), bitmap)
                    if (matchedProfileId != null) {
                        lifecycleScope.launch {
                            val dao = PepperDatabase.getDatabase(requireContext()).userProfileDao()
                            val profile = dao.getById(matchedProfileId)
                            requireActivity().runOnUiThread {
                                if (profile != null) {
                                    Toast.makeText(requireContext(), "Bienvenue de retour ${profile.name} !", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(requireContext(), "Profil reconnu, mais erreur de chargement.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } else {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Visage inconnu. Veuillez créer un profil.", Toast.LENGTH_LONG).show()
                            SharedBitmapHolder.bitmap = bitmap
                            findNavController().navigate(R.id.action_recognitionFragment_to_profileFragment)
                        }
                    }
                }
        } catch (e: Exception) {
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "Erreur prise de photo : ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

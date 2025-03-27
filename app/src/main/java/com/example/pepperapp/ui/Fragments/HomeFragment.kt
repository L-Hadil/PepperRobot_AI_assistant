package com.example.pepperapp.ui.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aldebaran.qi.sdk.QiContext
import com.example.pepperapp.utils.SharedBitmapHolder
import com.example.pepperrobot_ai_assistant.R

class HomeFragment : Fragment() {

    private lateinit var btnStart: Button
    private lateinit var btnWelcomeBack: Button
    private var qiContext: QiContext? = null

    // Cette méthode est appelée depuis MainActivity pour transmettre le QiContext
    fun setQiContext(context: QiContext) {
        this.qiContext = context
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        btnStart = view.findViewById(R.id.btnTakePhoto)
        btnWelcomeBack = view.findViewById(R.id.btnRecognizeFace)

        // "Let's start" redirige directement vers le formulaire de création de profil
        btnStart.setOnClickListener {
            SharedBitmapHolder.bitmap = null  // réinitialise l'image
            findNavController().navigate(R.id.action_homeFragment_to_profileFragment)
        }

        // "Welcome back" redirige vers la reconnaissance faciale
        btnWelcomeBack.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_recognitionFragment)
        }

        return view
    }
}

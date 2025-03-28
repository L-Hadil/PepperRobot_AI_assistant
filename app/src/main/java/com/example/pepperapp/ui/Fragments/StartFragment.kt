package com.example.pepperapp.ui.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aldebaran.qi.sdk.QiContext
import com.example.pepperapp.R
import com.example.pepperapp.ui.MainActivity

class StartFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_start, container, false)

        val btnCreateAccount = view.findViewById<Button>(R.id.btnCreateAccount)
        val btnLogin = view.findViewById<Button>(R.id.btnLogin)

        btnCreateAccount.setOnClickListener {
            val activity = activity as? MainActivity
            val qiContext: QiContext? = activity?.getQiContext()

            val profileFragment = ProfileFragment()

            // Si QiContext est disponible, on le passe au fragment
            if (qiContext != null) {
                profileFragment.setQiContext(qiContext)
            }

            parentFragmentManager.beginTransaction()
                .replace(R.id.container, profileFragment)
                .addToBackStack(null)
                .commit()
        }

        btnLogin.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, LoginFragment())  // On va créer cette classe juste après
                .addToBackStack(null)
                .commit()
        }


        return view
    }
}

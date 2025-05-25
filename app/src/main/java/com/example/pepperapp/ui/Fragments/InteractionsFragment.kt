package com.example.pepperapp.ui.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.pepperapp.R

class InteractionsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_interactions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.buttonSad).setOnClickListener {
            findNavController().navigate(R.id.sadFragment)
        }

        view.findViewById<Button>(R.id.buttonHappy).setOnClickListener {
            findNavController().navigate(R.id.happyFragment)
        }

     view.findViewById<Button>(R.id.buttonHug).setOnClickListener {
         findNavController().navigate(R.id.hugFragment)
     }

  view.findViewById<Button>(R.id.buttonByebye).setOnClickListener {
      findNavController().navigate(R.id.goodbyeFragment)
  }
        view.findViewById<Button>(R.id.buttonColere).setOnClickListener {
                        findNavController().navigate(R.id.colereFragment)
                }
    }
}

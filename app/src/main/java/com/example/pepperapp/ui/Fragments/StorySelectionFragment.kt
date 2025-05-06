package com.example.pepperapp.ui.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.pepperapp.R

class StorySelectionFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_story_selection, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Mammouth
        view.findViewById<Button>(R.id.buttonMammothStory).setOnClickListener {
            findNavController().navigate(R.id.action_storySelectionFragment_to_listenStoryFragment)
        }

        // Nuage
        view.findViewById<Button>(R.id.buttonNuageStory).setOnClickListener {
            findNavController().navigate(R.id.action_storySelectionFragment_to_nuageFragment)
        }

        // Pepper
        view.findViewById<Button>(R.id.buttonPepperStory).setOnClickListener {
            findNavController().navigate(R.id.action_storySelectionFragment_to_pepperStoryFragment)
        }
    }
}

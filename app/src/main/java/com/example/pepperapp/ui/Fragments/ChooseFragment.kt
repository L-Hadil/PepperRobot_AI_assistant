package com.example.pepperapp.ui.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aldebaran.qi.sdk.`object`.locale.Language
import com.aldebaran.qi.sdk.`object`.locale.Locale
import com.aldebaran.qi.sdk.`object`.locale.Region
import com.aldebaran.qi.sdk.builder.SayBuilder

import com.example.pepperapp.R
import com.example.pepperapp.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChooseFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        inflater.inflate(R.layout.fragment_choose, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        (activity as? MainActivity)?.getQiContext()?.let { ctx ->
            lifecycleScope.launch(Dispatchers.IO) {
                SayBuilder.with(ctx)
                    .withLocale(Locale(Language.FRENCH, Region.FRANCE))
                    .withText("Choisissez une option !")
                    .build()
                    .async()
                    .run()
            }
        }


        view.findViewById<Button>(R.id.buttonListenStory).setOnClickListener {
            findNavController().navigate(R.id.action_chooseFragment_to_storySelectionFragment)

        }

        view.findViewById<Button>(R.id.buttonPlayGame).setOnClickListener {
            findNavController().navigate(R.id.action_chooseFragment_to_gameFragment)
        }
        view.findViewById<Button>(R.id.buttonInteractions).setOnClickListener {
            findNavController().navigate(R.id.action_chooseFragment_to_interactionsFragment)
        }

        view.findViewById<Button>(R.id.buttonChatGptFeedback).setOnClickListener {
            findNavController().navigate(R.id.action_chooseFragment_to_chatFragment)
        }

    }
}

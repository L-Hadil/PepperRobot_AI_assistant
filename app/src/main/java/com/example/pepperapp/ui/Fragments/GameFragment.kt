package com.example.pepperapp.ui.Fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pepperapp.R
import com.example.pepperapp.data.GameDatabase
import com.example.pepperapp.data.GameResponse
import kotlinx.coroutines.launch

data class Question(val text: String, val options: List<Pair<String, Int>>, val correctIndex: Int)

class GameFragment : Fragment() {

    private lateinit var editTextName: EditText
    private lateinit var editTextAge: EditText
    private lateinit var radioGroupKnown: RadioGroup
    private lateinit var questionText: TextView
    private lateinit var optionButtons: List<Button>
    private lateinit var buttonRestart: Button

    private var currentIndex = 0
    private var score = 0
    private val answers = mutableListOf<Int>()

    private var questions = listOf(
        Question(
            "La tristesse est de quelle couleur ?",
            listOf("Jaune" to R.color.jaune, "Bleu" to R.color.bleu, "Rouge" to R.color.rouge, "Vert" to R.color.vert),
            1
        ),
        Question(
            "La joie est de quelle couleur ?",
            listOf("Jaune" to R.color.jaune, "Bleu" to R.color.bleu, "Rose" to R.color.rose, "Gris" to R.color.gris),
            0
        ),
        Question(
            "La colère est de quelle couleur ?",
            listOf("Rouge" to R.color.rouge, "Violet" to R.color.purple_700, "Bleu" to R.color.bleu, "Vert" to R.color.vert),
            0
        ),
        Question(
            "La peur est de quelle couleur ?",
            listOf("Noir" to R.color.black, "Rouge" to R.color.rouge, "Jaune" to R.color.jaune, "Vert" to R.color.vert),
            0
        ),
        Question(
            "Le calme est de quelle couleur ?",
            listOf("Vert" to R.color.vert, "Bleu" to R.color.bleu, "Rose" to R.color.rose, "Rouge" to R.color.rouge),
            0
        ),
        Question(
            "L'amour est de quelle couleur ?",
            listOf("Rose" to R.color.rose, "Jaune" to R.color.jaune, "Bleu" to R.color.bleu, "Gris" to R.color.gris),
            0
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_game, container, false)

        editTextName = view.findViewById(R.id.editTextName)
        editTextAge = view.findViewById(R.id.editTextAge)
        radioGroupKnown = view.findViewById(R.id.radioGroupKnown)
        questionText = view.findViewById(R.id.questionText)
        buttonRestart = view.findViewById(R.id.buttonRestart)

        optionButtons = listOf(
            view.findViewById(R.id.optionA),
            view.findViewById(R.id.optionB),
            view.findViewById(R.id.optionC),
            view.findViewById(R.id.optionD)
        )

        optionButtons.forEachIndexed { idx, btn ->
            btn.setOnClickListener { onOptionSelected(idx) }
        }

        buttonRestart.setOnClickListener { resetGame() }

        showQuestion()
        return view
    }

    private fun showQuestion() {
        if (currentIndex >= questions.size) return

        val original = questions[currentIndex]
        questionText.text = original.text

        val shuffled = original.options.shuffled()
        val correctLabel = original.options[original.correctIndex].first
        val newCorrectIndex = shuffled.indexOfFirst { it.first == correctLabel }


        questions = questions.toMutableList().apply {
            this[currentIndex] = Question(original.text, shuffled, newCorrectIndex)
        }

        shuffled.forEachIndexed { i, (label, colorRes) ->
            val btn = optionButtons[i]
            btn.text = label
            val color = ContextCompat.getColor(requireContext(), colorRes)
            btn.backgroundTintList = ColorStateList.valueOf(color)
            val textColor = if (colorRes == R.color.jaune) R.color.black else R.color.white
            btn.setTextColor(ContextCompat.getColor(requireContext(), textColor))
            btn.isEnabled = true
        }
    }

    private fun onOptionSelected(selected: Int) {
        if (currentIndex >= questions.size) return

        if (selected == questions[currentIndex].correctIndex) score++
        answers.add(selected)
        currentIndex++

        if (currentIndex < questions.size) {
            showQuestion()
        } else {
            finishGame()
        }
    }

    private fun finishGame() {
        optionButtons.forEach { it.isEnabled = false }

        val name = editTextName.text.toString().ifBlank { "Enfant" }
        val age = editTextAge.text.toString().toIntOrNull() ?: 0
        val isKnown = if (radioGroupKnown.checkedRadioButtonId == R.id.radioKnownYes) 1 else 0

        val correctness = questions.mapIndexed { i, q ->
            if (answers.getOrNull(i) == q.correctIndex) 1 else 0
        }

        val score = correctness.sum()

        lifecycleScope.launch {
            GameDatabase.getInstance(requireContext())
                .gameResponseDao()
                .insert(
                    GameResponse(
                        childName = name,
                        age = age,
                        isKnown = isKnown,
                        q1 = correctness.getOrNull(0) ?: 0,
                        q2 = correctness.getOrNull(1) ?: 0,
                        q3 = correctness.getOrNull(2) ?: 0,
                        q4 = correctness.getOrNull(3) ?: 0,
                        q5 = correctness.getOrNull(4) ?: 0,
                        q6 = correctness.getOrNull(5) ?: 0,
                        score = score
                    )
                )

            Toast.makeText(requireContext(), "Merci $name ! Score : $score/6", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetGame() {
        currentIndex = 0
        score = 0
        answers.clear()

        editTextName.text.clear()
        editTextAge.text.clear()
        radioGroupKnown.clearCheck()

        showQuestion()
    }
}

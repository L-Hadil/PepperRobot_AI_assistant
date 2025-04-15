package com.example.pepperapp.ui.Fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aldebaran.qi.sdk.*
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.`object`.locale.*
import com.aldebaran.qi.sdk.`object`.locale.Locale
import com.example.pepperapp.R
import com.example.pepperapp.data.PepperDatabase
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.*

class ChatFragment : Fragment(), RobotLifecycleCallbacks {

    private val TAG = "ChatFragment"
    private var qiContext: QiContext? = null
    private lateinit var userName: String
    private val apiKey = "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"

    private val client = OkHttpClient()

    private lateinit var messageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var askButton: Button
    private lateinit var answerButton: Button
    private lateinit var speechRecognizer: SpeechRecognizer
    private var latestQuestion: String = ""
    private var threadIdGPT = ""

    private val storyParagraphs = listOf(
        "Il était une fois un loup qui vivait dans une belle forêt, entouré de tous ses amis. Il s’appelait Loup.",
        "Mais ce loup avait un souci : il était trop émotif. Joyeux, fâché, triste, excité… il changeait d’humeur à cent à l’heure !",
        "Ainsi, quand Loup était d’humeur joyeuse, il sifflotait, faisait des blagues et débordait d’idées pour s’amuser.",
        "Mais si quelque chose le contrariait… Ah ! Il se fâchait très fort !",
        "Un jour, Maître Hibou lui dit : 'Tu dois apprendre à te calmer, Loup. Nous allons t’aider !'"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        QiSDK.register(requireActivity(), this)

        userName = arguments?.getString("userName") ?: ""
        messageContainer = view.findViewById(R.id.messageContainer)
        scrollView = view.findViewById(R.id.scrollView)
        askButton = view.findViewById(R.id.buttonAskQuestion)
        answerButton = view.findViewById(R.id.buttonAnswer)

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }

        setupSpeechRecognizer()

        askButton.setOnClickListener {
            Log.d(TAG, "Bouton poser une question cliqué")
            speak("Je t'écoute.") {
                requireActivity().runOnUiThread {
                    startListening()
                }
            }
        }

        answerButton.setOnClickListener {
            Log.d(TAG, "Bouton répondre cliqué")
            if (latestQuestion.isNotEmpty()) {
                lifecycleScope.launch {
                    val response = sendToGPT(threadIdGPT, latestQuestion)
                    speak(response)
                    addMessageBubble(response, isRobot = true)
                }
            } else {
                Toast.makeText(requireContext(), "Aucune question enregistrée.", Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch {
            val db = PepperDatabase.getDatabase(requireContext())
            val user = db.userProfileDao().getUserByName(userName)
            threadIdGPT = user?.threadIdGPT ?: createThreadOnOpenAI().also {
                if (user != null) db.userProfileDao().update(user.copy(threadIdGPT = it))
            }

            speak("Bonjour $userName ! Installe-toi bien, je vais te raconter une histoire.") {
                lifecycleScope.launch {
                    delay(1000)
                    for (line in storyParagraphs) {
                        Log.d(TAG, "Robot parle: $line")
                        // Exécuter la parole du robot en mode asynchrone
                        val say = withContext(Dispatchers.IO) {
                            SayBuilder.with(qiContext)
                                .withText(line)
                                .withLocale(Locale(Language.FRENCH, Region.FRANCE))
                                .build()
                        }
                        say.async().run().get() // Vous pouvez également gérer le résultat de manière asynchrone
                        Log.d(TAG, "Fin de parole: $line")
                        addMessageBubble(line, isRobot = true)
                    }
                    speak("L'histoire est terminée. Appuie sur le bouton pour me poser ta question.")
                }
            }
        }

        return view
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        speechRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: Bundle?) {
                latestQuestion = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                Log.d(TAG, "Reconnu: $latestQuestion")
                addMessageBubble(latestQuestion, isRobot = false)
            }
            override fun onError(error: Int) {
                Log.e(TAG, "Erreur reconnaissance vocale code: $error")
                if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                    Toast.makeText(requireContext(), "Je n’ai pas bien entendu. Veux-tu réessayer ?", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
        }
        Log.d(TAG, "startListening déclenché")
        speechRecognizer.startListening(intent)
    }

    // Méthode speak corrigée pour utiliser withContext(Dispatchers.IO)
    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Robot parle: $text")
                // Exécuter la construction du SayBuilder sur le dispatcher IO
                val say = withContext(Dispatchers.IO) {
                    SayBuilder.with(qiContext)
                        .withText(text)
                        .withLocale(Locale(Language.FRENCH, Region.FRANCE))
                        .build()
                }
                // Lancer l'exécution asynchrone, puis consommer le résultat
                say.async().run().thenConsume {
                    Log.d(TAG, "Fin de parole: $text")
                    onDone?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur pendant speak", e)
                onDone?.invoke()
            }
        }
    }

    private fun addMessageBubble(text: String, isRobot: Boolean) {
        val bubble = TextView(requireContext())
        bubble.text = text
        bubble.setPadding(16, 12, 16, 12)
        bubble.setBackgroundResource(if (isRobot) R.drawable.bubble_robot else R.drawable.bubble_child)
        bubble.setTextColor(android.graphics.Color.WHITE)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 8, 0, 8)
            gravity = if (isRobot) Gravity.START else Gravity.END
        }

        bubble.layoutParams = params
        messageContainer.addView(bubble)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onRobotFocusGained(qiContext: QiContext?) {
        this.qiContext = qiContext
        Log.d(TAG, "Robot focus gagné")
    }

    override fun onRobotFocusLost() {
        qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {
        Toast.makeText(requireContext(), "Focus refusé : $reason", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        speechRecognizer.destroy()
        QiSDK.unregister(requireActivity(), this)
        super.onDestroyView()
    }

    private suspend fun createThreadOnOpenAI(): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.openai.com/v1/threads")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "assistants=v2")
            .addHeader("Content-Type", "application/json")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        val res = client.newCall(req).execute()
        JSONObject(res.body?.string() ?: "").getString("id")
    }

    private suspend fun sendToGPT(threadId: String, message: String): String = withContext(Dispatchers.IO) {
        val msg = JSONObject().apply {
            put("role", "user")
            put("content", message)
        }

        client.newCall(Request.Builder()
            .url("https://api.openai.com/v1/threads/$threadId/messages")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "assistants=v2")
            .addHeader("Content-Type", "application/json")
            .post(msg.toString().toRequestBody("application/json".toMediaType()))
            .build()).execute()

        val assistantId = "asst_7NjhZUtxh1mBgCC1ZphdeQqS"

        val runReq = JSONObject().apply {
            put("assistant_id", assistantId)
        }

        val runRes = client.newCall(Request.Builder()
            .url("https://api.openai.com/v1/threads/$threadId/runs")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "assistants=v2")
            .addHeader("Content-Type", "application/json")
            .post(runReq.toString().toRequestBody("application/json".toMediaType()))
            .build()).execute()

        val runId = JSONObject(runRes.body?.string() ?: "").getString("id")
        repeat(30) {
            delay(1000)
            val check = client.newCall(Request.Builder()
                .url("https://api.openai.com/v1/threads/$threadId/runs/$runId")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("OpenAI-Beta", "assistants=v2")
                .get().build()).execute()

            val status = JSONObject(check.body?.string() ?: "").optString("status")
            if (status == "completed") {
                return@withContext fetchLastResponse(threadId)
            }
        }

        return@withContext "Je n’ai pas reçu de réponse."
    }

    private suspend fun fetchLastResponse(threadId: String): String = withContext(Dispatchers.IO) {
        val res = client.newCall(Request.Builder()
            .url("https://api.openai.com/v1/threads/$threadId/messages")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "assistants=v2")
            .get().build()).execute()

        val data = JSONObject(res.body?.string() ?: "").getJSONArray("data")
        for (i in 0 until data.length()) {
            val msg = data.getJSONObject(i)
            if (msg.getString("role") == "assistant") {
                return@withContext msg.getJSONArray("content")
                    .getJSONObject(0)
                    .getJSONObject("text")
                    .getString("value")
            }
        }
        "Aucune réponse trouvée."
    }
}

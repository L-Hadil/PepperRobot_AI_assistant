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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aldebaran.qi.QiException
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
    // Remplacer "your_api_key_here" par une méthode plus sécurisée en production.
    private val apiKey = "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"



    private val client = OkHttpClient()

    private lateinit var messageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var askButton: Button
    private lateinit var answerButton: Button
    private lateinit var speechRecognizer: SpeechRecognizer
    private var latestQuestion: String = ""
    private var threadIdGPT = ""
    private val RECORD_AUDIO_REQUEST_CODE = 100

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

        userName = arguments?.getString("userName") ?: "Utilisateur"
        messageContainer = view.findViewById(R.id.messageContainer)
        scrollView = view.findViewById(R.id.scrollView)
        askButton = view.findViewById(R.id.buttonAskQuestion)
        answerButton = view.findViewById(R.id.buttonAnswer)

        // Vérifier et demander la permission RECORD_AUDIO (Android 6+)
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
        } else {
            setupSpeechRecognizer()
        }

        askButton.setOnClickListener {
            Log.d(TAG, "Bouton poser une question cliqué")
            speak("Je t'écoute.") {
                // On démarre l'écoute sur le thread UI après la parole
                requireActivity().runOnUiThread { startListening() }
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
            // On salue l'utilisateur et on raconte une histoire
            speak("Bonjour $userName ! Installe-toi bien, je vais te raconter une histoire.") {
                lifecycleScope.launch {
                    delay(1000)
                    for (line in storyParagraphs) {
                        if (qiContext == null) {
                            Log.e(TAG, "QiContext perdu, arrêt de la narration.")
                            break
                        }
                        Log.d(TAG, "Robot parle: $line")
                        try {
                            withContext(Dispatchers.IO) {
                                val say = SayBuilder.with(qiContext)
                                    .withText(line)
                                    .withLocale(Locale(Language.FRENCH, Region.FRANCE))
                                    .build()
                                // Ici, nous attendons la fin de la parole.
                                say.async().run().get()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Erreur lors de la parole pour la ligne: $line", e)
                        }
                        Log.d(TAG, "Fin de parole: $line")
                        addMessageBubble(line, isRobot = true)
                    }
                    speak("L'histoire est terminée. Appuie sur le bouton pour me poser ta question.")
                }
            }
        }
        return view
    }

    // Mise en place du SpeechRecognizer et implémentation des callbacks
    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        speechRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Le recognizer est prêt à écouter.")
            }
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Début de la parole détecté.")
            }
            override fun onRmsChanged(rmsdB: Float) {
                Log.d(TAG, "Niveau sonore: $rmsdB dB")
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.d(TAG, "Fin de la parole.")
            }
            override fun onResults(results: Bundle?) {
                val recognizedText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                Log.d(TAG, "Texte final reconnu: $recognizedText")
                latestQuestion = recognizedText
                addMessageBubble(recognizedText, isRobot = false)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partialText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                Log.d(TAG, "Résultat partiel: $partialText")
            }
            override fun onError(error: Int) {
                Log.e(TAG, "Erreur reconnaissance vocale code: $error")
                Toast.makeText(requireContext(), "Je n’ai pas bien entendu. Veux-tu réessayer ?", Toast.LENGTH_SHORT).show()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    // Préparation et démarrage de l'intent de reconnaissance vocale
    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Forcer la langue à être française
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Augmenter la durée de silence pour éviter une détection prématurée de la fin de la parole
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
        }
        Log.d(TAG, "startListening déclenché")
        speechRecognizer.startListening(intent)
    }

    // La méthode speak déclenche la parole du robot.
    // Elle vérifie que le qiContext est disponible avant d’appeler le SayBuilder.
    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (qiContext == null) {
            Log.e(TAG, "QiContext ou SpeechEngine indisponible. Impossible de parler.")
            onDone?.invoke()
            return
        }
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Robot parle: $text")
                val say = withContext(Dispatchers.IO) {
                    // Vérifier encore une fois que le qiContext n'est pas nul
                    if (qiContext == null)
                        throw IllegalStateException("QiContext indisponible lors de la construction du SayBuilder.")
                    SayBuilder.with(qiContext)
                        .withText(text)
                        .withLocale(Locale(Language.FRENCH, Region.FRANCE))
                        .build()
                }
                // Lancer l'exécution asynchrone du SayBuilder et consommer le résultat
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

    // Ajout d'une bulle de message dans l'interface
    private fun addMessageBubble(text: String, isRobot: Boolean) {
        val bubble = TextView(requireContext()).apply {
            this.text = text
            setPadding(16, 12, 16, 12)
            setBackgroundResource(if (isRobot) R.drawable.bubble_robot else R.drawable.bubble_child)
            setTextColor(android.graphics.Color.WHITE)
        }
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

    // Gestion de la réponse à la demande de permission
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupSpeechRecognizer()
            } else {
                Toast.makeText(requireContext(), "La permission d'accéder au micro est requise.", Toast.LENGTH_SHORT).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    // Méthodes réseau pour OpenAI (à adapter pour votre usage)
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
        client.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/threads/$threadId/messages")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("OpenAI-Beta", "assistants=v2")
                .addHeader("Content-Type", "application/json")
                .post(msg.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        val assistantId = "asst_7NjhZUtxh1mBgCC1ZphdeQqS"
        val runReq = JSONObject().apply { put("assistant_id", assistantId) }
        val runRes = client.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/threads/$threadId/runs")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("OpenAI-Beta", "assistants=v2")
                .addHeader("Content-Type", "application/json")
                .post(runReq.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        val runId = JSONObject(runRes.body?.string() ?: "").getString("id")
        repeat(30) {
            delay(1000)
            val check = client.newCall(
                Request.Builder()
                    .url("https://api.openai.com/v1/threads/$threadId/runs/$runId")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("OpenAI-Beta", "assistants=v2")
                    .get().build()
            ).execute()
            val status = JSONObject(check.body?.string() ?: "").optString("status")
            if (status == "completed") {
                return@withContext fetchLastResponse(threadId)
            }
        }
        return@withContext "Je n’ai pas reçu de réponse."
    }

    private suspend fun fetchLastResponse(threadId: String): String = withContext(Dispatchers.IO) {
        val res = client.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/threads/$threadId/messages")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("OpenAI-Beta", "assistants=v2")
                .get().build()
        ).execute()
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

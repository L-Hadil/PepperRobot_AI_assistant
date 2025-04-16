package com.example.pepperapp.ui.Fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
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

    // Attention : en production, gérez la clé API de manière sécurisée.
    private val apiKey = "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"

    private val client = OkHttpClient()

    private lateinit var messageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var questionEditText: EditText
    private lateinit var sendButton: Button

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

        userName = arguments?.getString("userName") ?: "Utilisateur"
        messageContainer = view.findViewById(R.id.messageContainer)
        scrollView = view.findViewById(R.id.scrollView)
        questionEditText = view.findViewById(R.id.editTextQuestion)
        sendButton = view.findViewById(R.id.buttonSendQuestion)

        // Assurer que l'EditText obtienne le focus immédiatement.
        questionEditText.requestFocus()

        sendButton.setOnClickListener {
            val typedQuestion = questionEditText.text.toString()
            // Ajout du log pour vérifier le texte saisi par l'utilisateur.
            Log.d(TAG, "Texte saisi par l'utilisateur: \"$typedQuestion\"")
            if (typedQuestion.isNotBlank()) {
                latestQuestion = typedQuestion
                addMessageBubble(latestQuestion, isRobot = false)
                questionEditText.text.clear()
                hideKeyboard()
                lifecycleScope.launch {
                    val response = sendToGPT(threadIdGPT, latestQuestion)
                    speak(response)
                    addMessageBubble(response, isRobot = true)
                }
            } else {
                Toast.makeText(requireContext(), "Veuillez saisir une question.", Toast.LENGTH_SHORT).show()
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
                        if (qiContext == null) {
                            Log.e(TAG, "QiContext perdu, arrêt de la narration.")
                            break
                        }
                        Log.d(TAG, "Robot raconte: $line")
                        try {
                            withContext(Dispatchers.IO) {
                                val say = SayBuilder.with(qiContext)
                                    .withText(line)
                                    .withLocale(Locale(Language.FRENCH, Region.FRANCE))
                                    .build()
                                say.async().run().get() // Attend la fin de la phrase
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Erreur lors de la narration: $line", e)
                        }
                        addMessageBubble(line, isRobot = true)
                    }
                    speak("L'histoire est terminée. Veuillez taper votre question.") {
                        questionEditText.requestFocus()
                        showKeyboard()
                    }
                }
            }
        }
        return view
    }

    private fun showKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(questionEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(questionEditText.windowToken, 0)
    }

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (qiContext == null) {
            Log.e(TAG, "QiContext indisponible. Impossible de parler.")
            onDone?.invoke()
            return
        }
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Robot parle: $text")
                val say = withContext(Dispatchers.IO) {
                    if (qiContext == null)
                        throw IllegalStateException("QiContext indisponible lors de la construction du SayBuilder.")
                    SayBuilder.with(qiContext)
                        .withText(text)
                        .withLocale(Locale(Language.FRENCH, Region.FRANCE))
                        .build()
                }
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
        QiSDK.unregister(requireActivity(), this)
        super.onDestroyView()
    }

    // Création du thread côté OpenAI (sans instructions supplémentaires)
    private suspend fun createThreadOnOpenAI(): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.openai.com/v1/threads")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "assistants=v2")
            .addHeader("Content-Type", "application/json")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        val res = client.newCall(req).execute()
        val body = res.body?.string()
        Log.d(TAG, "createThreadOnOpenAI response: $body")
        val json = JSONObject(body ?: "{}")
        val threadId = json.optString("id", "")
        if (threadId.isEmpty()) {
            throw Exception("Impossible de récupérer un 'id' lors de la création du thread.")
        }
        threadId
    }

    private suspend fun sendToGPT(threadId: String, message: String): String = withContext(Dispatchers.IO) {
        // 1) Envoyer la question dans le thread existant
        val msgObject = JSONObject().apply {
            put("role", "user")
            put("content", message)
        }

        val messageRequest = Request.Builder()
            .url("https://api.openai.com/v1/threads/$threadId/messages")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "assistants=v2")
            .addHeader("Content-Type", "application/json")
            .post(msgObject.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(messageRequest).execute()

        // 2) Lancer un run sur le thread
        val assistantId = "asst_7NjhZUtxhimBgCClZphdeQqS"
        val runObject = JSONObject().apply {
            put("assistant_id", assistantId)
        }

        val runRequest = Request.Builder()
            .url("https://api.openai.com/v1/threads/$threadId/runs")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "assistants=v2")
            .addHeader("Content-Type", "application/json")
            .post(runObject.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val runResponse = client.newCall(runRequest).execute()
        val runBody = runResponse.body?.string()
        Log.d(TAG, "sendToGPT runRes: $runBody")

        val runJson = JSONObject(runBody ?: "{}")
        val runId = runJson.optString("id", "")

        if (runId.isEmpty()) {
            Log.e(TAG, "❌ runId est vide. JSON: $runJson")
            return@withContext "Échec du run : Aucune 'id' dans la réponse."
        }

        val statusUrl = "https://api.openai.com/v1/threads/$threadId/runs/$runId"
        Log.d(TAG, "🛰️ Polling URL: $statusUrl")

        // 3) Poller le run jusqu’à ce qu’il soit terminé
        repeat(30) {
            delay(1000)
            val checkRequest = Request.Builder()
                .url(statusUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("OpenAI-Beta", "assistants=v2")
                .get()
                .build()

            val checkResponse = client.newCall(checkRequest).execute()
            val checkBody = checkResponse.body?.string()
            Log.d(TAG, "Statut run ($it): $checkBody")

            val status = JSONObject(checkBody ?: "{}").optString("status", "")
            if (status == "completed") {
                return@withContext fetchLastResponse(threadId)
            }
        }

        return@withContext "Je n’ai pas reçu de réponse."
    }



    // Récupère la dernière réponse au format texte.
    private suspend fun fetchLastResponse(threadId: String): String = withContext(Dispatchers.IO) {
        val resp = client.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/threads/$threadId/messages")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("OpenAI-Beta", "assistants=v2")
                .get()
                .build()
        ).execute()
        val body = resp.body?.string().orEmpty()
        Log.d(TAG, "fetchLastResponse: $body")
        val data = JSONObject(body).optJSONArray("data") ?: return@withContext "Aucune donnée renvoyée."
        for (i in 0 until data.length()) {
            val msg = data.getJSONObject(i)
            if (msg.optString("role") == "assistant") {
                val contents = msg.optJSONArray("content") ?: continue
                for (j in 0 until contents.length()) {
                    val contentObj = contents.getJSONObject(j)
                    if (contentObj.optString("type") == "text") {
                        val textObj = contentObj.optJSONObject("text")
                        if (textObj != null) {
                            val value = textObj.optString("value", "")
                            if (value.isNotEmpty()) {
                                return@withContext value
                            }
                        } else {
                            val directText = contentObj.optString("text", "")
                            if (directText.isNotEmpty()) {
                                return@withContext directText
                            }
                        }
                    }
                }
            }
        }
        "Aucune réponse trouvée."
    }
}

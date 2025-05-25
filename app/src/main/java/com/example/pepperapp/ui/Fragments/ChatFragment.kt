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
import java.util.concurrent.TimeUnit

class ChatFragment : Fragment(), RobotLifecycleCallbacks {

    private val TAG = "ChatFragment"
    private var qiContext: QiContext? = null
    private lateinit var userName: String


    private val apiKey = "Your_API_Key"


    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)
        QiSDK.register(requireActivity(), this)

        userName = arguments?.getString("userName") ?: "Utilisateur"
        messageContainer = view.findViewById(R.id.messageContainer)
        scrollView = view.findViewById(R.id.scrollView)
        questionEditText = view.findViewById(R.id.editTextQuestion)
        sendButton = view.findViewById(R.id.buttonSendQuestion)

        // Focus direct sur le champ
        questionEditText.requestFocus()

        sendButton.setOnClickListener {
            val typedQuestion = questionEditText.text.toString()
            Log.d(TAG, "Texte saisi par l'utilisateur : \"$typedQuestion\"")
            if (typedQuestion.isNotBlank()) {
                latestQuestion = typedQuestion
                addMessageBubble(latestQuestion, isRobot = false)
                questionEditText.text.clear()
                hideKeyboard()
                lifecycleScope.launch {
                    Log.d(TAG, " threadId utilisé pour la requête: $threadIdGPT")
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

            threadIdGPT = if (!user?.threadIdGPT.isNullOrBlank()) {
                Log.d(TAG, "✅ Thread existant trouvé pour ${user?.name} : ${user?.threadIdGPT}")
                user!!.threadIdGPT!!
            } else {
                Log.d(TAG, "🆕 Aucun thread trouvé. Création d’un nouveau...")
                val newThreadId = createThreadOnOpenAI()
                Log.d(TAG, "🧵 Nouveau thread ID généré : $newThreadId")
                if (user != null) {
                    db.userProfileDao().update(user.copy(threadIdGPT = newThreadId))
                    Log.d(TAG, "💾 Thread ID sauvegardé en base pour ${user.name}")
                }
                newThreadId
            }

            Log.d(TAG, " Démarrage de l'histoire...")
            speak("Bonjour $userName ! Installe-toi bien, je vais te raconter une histoire.") {
                lifecycleScope.launch {
                    delay(1000)
                    for (line in storyParagraphs) {
                        if (qiContext == null) {
                            Log.e(TAG, "❌ QiContext perdu, arrêt de la narration.")
                            break
                        }
                        Log.d(TAG, " Robot raconte: $line")
                        try {
                            withContext(Dispatchers.IO) {
                                val say = SayBuilder.with(qiContext)
                                    .withText(line)
                                    .withLocale(Locale(Language.FRENCH, Region.FRANCE))
                                    .build()
                                say.async().run().get()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erreur lors de la narration: $line", e)
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
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
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



    /**
     * Crée un nouveau thread côté OpenAI.
     */
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

    /**
     * Envoie le message utilisateur dans le thread, démarre un run et attend la réponse.
     * IMPORTANT : On utilise toujours le même thread (threadId) pour garder la continuité de la conversation.
     */
    private suspend fun sendToGPT(threadId: String, message: String): String = withContext(Dispatchers.IO) {
        // 1) Envoyer le message utilisateur
        val msgObject = JSONObject().apply {
            put("role", "user")
            put("content", message)
        }
        client.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/threads/$threadId/messages")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("OpenAI-Beta", "assistants=v2")
                .addHeader("Content-Type", "application/json")
                .post(msgObject.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()


        val assistantId = "asst_7NjhZUtxhimBgCClZphdeQqS"
        val runObject = JSONObject().apply {
            put("assistant_id", assistantId)

        }
        val runRes = client.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/threads/$threadId/runs")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("OpenAI-Beta", "assistants=v2")
                .addHeader("Content-Type", "application/json")
                .post(runObject.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        val runBody = runRes.body?.string()
        Log.d(TAG, "sendToGPT runRes: $runBody")

        val runJson = JSONObject(runBody ?: "{}")
        val runId = runJson.optString("id", "")

        if (runId.isEmpty()) {
            return@withContext "Échec du run : Aucune 'id' dans la réponse."
        }


        repeat(30) {
            delay(1000)
            val check = client.newCall(
                Request.Builder()
                    .url("https://api.openai.com/v1/threads/$threadId/runs/$runId")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("OpenAI-Beta", "assistants=v2")
                    .get()
                    .build()
            ).execute()
            val checkBody = check.body?.string()
            Log.d(TAG, "Statut run: $checkBody")
            val status = JSONObject(checkBody ?: "{}").optString("status", "")
            if (status == "completed") {
                return@withContext fetchLastResponse(threadId)
            }
        }
        "Je n’ai pas reçu de réponse."
    }

    /**
     * Récupère la dernière réponse du modèle OpenAI depuis le thread.
     */
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

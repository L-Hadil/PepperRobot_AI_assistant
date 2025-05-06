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
import com.example.pepperapp.R
import com.example.pepperapp.data.PepperDatabase
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ChatFragment : Fragment(), RobotLifecycleCallbacks {

    private val TAG = "ChatFragment"
    private var qiContext: QiContext? = null
    private lateinit var userName: String

    // ATTENTION : en production, gérez votre clé API de façon sécurisée.
    private val apiKey = "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"

    // Client OkHttp avec délais étendus
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)
        QiSDK.register(requireActivity(), this)

        userName = arguments?.getString("userName") ?: "petit enfant"
        messageContainer = view.findViewById(R.id.messageContainer)
        scrollView = view.findViewById(R.id.scrollView)
        questionEditText = view.findViewById(R.id.editTextQuestion)
        sendButton = view.findViewById(R.id.buttonSendQuestion)

        questionEditText.requestFocus()

        sendButton.setOnClickListener {
            val typedQuestion = questionEditText.text.toString()
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

            threadIdGPT = if (!user?.threadIdGPT.isNullOrBlank()) {
                user!!.threadIdGPT!!
            } else {
                val newThreadId = createThreadOnOpenAI()
                if (user != null) {
                    db.userProfileDao().update(user.copy(threadIdGPT = newThreadId))
                }
                newThreadId
            }

            // Salutation et invitation à poser une question
            speak("Bonjour $userName ! Installe-toi bien et pose ta question.") {
                questionEditText.requestFocus()
                showKeyboard()
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
            onDone?.invoke()
            return
        }
        lifecycleScope.launch {
            val say = SayBuilder.with(qiContext)
                .withText(text)
                .withLocale(Locale(Language.FRENCH, Region.FRANCE))
                .build()
            say.async().run().thenConsume {
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

    private suspend fun createThreadOnOpenAI(): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.openai.com/v1/threads")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "assistants=v2")
            .addHeader("Content-Type", "application/json")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        val res = client.newCall(req).execute()
        val json = JSONObject(res.body?.string() ?: "{}")
        return@withContext json.optString("id", "")
    }

    private suspend fun sendToGPT(threadId: String, message: String): String = withContext(Dispatchers.IO) {
        val msgObject = JSONObject().apply { put("role", "user"); put("content", message) }
        client.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/threads/$threadId/messages")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("OpenAI-Beta", "assistants=v2")
                .addHeader("Content-Type", "application/json")
                .post(msgObject.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()

        val runObject = JSONObject().apply { put("assistant_id", "asst_7NjhZUtxhimBgCClZphdeQqS") }
        val runRes = client.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/threads/$threadId/runs")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("OpenAI-Beta", "assistants=v2")
                .addHeader("Content-Type", "application/json")
                .post(runObject.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        val runId = JSONObject(runRes.body?.string() ?: "{}").optString("id", "")
        if (runId.isEmpty()) return@withContext "Échec du run : Aucune 'id'"

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
            val status = JSONObject(check.body?.string() ?: "{}").optString("status", "")
            if (status == "completed") return@withContext fetchLastResponse(threadId)
        }
        "Je n’ai pas reçu de réponse."
    }

    private suspend fun fetchLastResponse(threadId: String): String = withContext(Dispatchers.IO) {
        val resp = client.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/threads/$threadId/messages")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("OpenAI-Beta", "assistants=v2")
                .get()
                .build()
        ).execute()
        val data = JSONObject(resp.body?.string().orEmpty()).optJSONArray("data") ?: return@withContext "Aucune donnée"
        for (i in 0 until data.length()) {
            val msg = data.getJSONObject(i)
            if (msg.optString("role") == "assistant") {
                val contents = msg.optJSONArray("content") ?: continue
                for (j in 0 until contents.length()) {
                    val contentObj = contents.getJSONObject(j)
                    if (contentObj.optString("type") == "text") {
                        return@withContext contentObj.optJSONObject("text")?.optString("value") ?: contentObj.optString("text", "")
                    }
                }
            }
        }
        "Aucune réponse trouvée."
    }
}

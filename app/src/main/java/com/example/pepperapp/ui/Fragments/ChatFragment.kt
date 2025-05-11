/*package com.example.pepperapp.ui.Fragments

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
*/


/*

// celui la est plus rapide
package com.example.pepperapp.ui.Fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.`object`.locale.Language
import com.aldebaran.qi.sdk.`object`.locale.Locale
import com.aldebaran.qi.sdk.`object`.locale.Region
import com.example.pepperapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ChatFragment : Fragment(), RobotLifecycleCallbacks {

    private val TAG = "ChatFragment"
    private var qiContext: QiContext? = null
    private var greeted = false

    // En prod, ne laissez jamais la clé en dur !
    private val apiKey = "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"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private lateinit var messageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var questionEditText: EditText
    private lateinit var sendButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)
        QiSDK.register(requireActivity(), this)

        messageContainer   = view.findViewById(R.id.messageContainer)
        scrollView         = view.findViewById(R.id.scrollView)
        questionEditText   = view.findViewById(R.id.editTextQuestion)
        sendButton         = view.findViewById(R.id.buttonSendQuestion)

        sendButton.setOnClickListener { submitQuestion() }
        return view
    }

    private fun submitQuestion() {
        val question = questionEditText.text.toString().trim()
        if (question.isEmpty()) {
            Toast.makeText(requireContext(), "Veuillez saisir une question.", Toast.LENGTH_SHORT).show()
            return
        }
        questionEditText.text.clear()
        hideKeyboard()

        addMessageBubble(question, isRobot = false)

        lifecycleScope.launchWhenStarted {
            val response = try {
                sendToGPT(question)
            } catch (e: Exception) {
                Log.e(TAG, "sendToGPT error", e)
                "Désolé, j’ai rencontré une erreur : ${e.message}"
            }

            addMessageBubble(response, isRobot = true)
            speak(response)
        }
    }
    private suspend fun sendToGPT(userMessage: String): String = withContext(Dispatchers.IO) {
        val systemPrompt = """
        Tu es Pepper, un petit robot gentil.
        Tu viens de raconter l’histoire du monstre des couleurs à des enfants de 3 à 7 ans.
        
        Voici ce qu’ils ont entendu :
        Le monstre des émotions est tout mélangé. Avec l’aide de son amie, il apprend à reconnaître et ranger ses émotions :
        - La joie est jaune comme le soleil.
        - La tristesse est bleue comme la pluie.
        - La colère est rouge comme un volcan.
        - La peur est noire comme la nuit.
        - Le calme est vert comme les feuilles des arbres.
        
        À chaque question, tu réponds  en parlant de ces émotions et de ces couleurs.
        
        Tes réponses doivent être :
        - Très courtes (1 à 3 phrases),
        -Madalina est la maman de Elisa et Hadil est la fille forte en math qui t'as appris les histoires et a parlé
        -
        - Très simples, pour des enfants de maternelle,utilise trop de ponctuations dans les réponses
        - Pleines de douceur et faciles à comprendre.
        Tu peux aussi poser une petite question simple à la fin, si c’est pertinent.
    """.trimIndent()
        // Prépare le JSON
        val payload = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system"); put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user"); put("content", userMessage)
                })
            })
        }

        val body = payload.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string().orEmpty()
            Log.d(TAG, "HTTP ${resp.code} /body: $respBody")

            if (!resp.isSuccessful) {
                throw Exception("API error ${resp.code}")
            }

            val json = JSONObject(respBody)
            val choices = json.optJSONArray("choices")
                ?: throw Exception("Pas de choices dans la réponse")
            if (choices.length() == 0) {
                throw Exception("Choices vide")
            }

            // La réponse du bot
            val messageObj = choices
                .getJSONObject(0)
                .getJSONObject("message")
            return@withContext messageObj.optString("content", "…").trim()
        }
    }

    private fun addMessageBubble(text: String, isRobot: Boolean): TextView {
        val bubble = TextView(requireContext()).apply {
            this.text = text
            setPadding(16,12,16,12)
            setBackgroundResource(if (isRobot) R.drawable.bubble_robot else R.drawable.bubble_child)
            setTextColor(android.graphics.Color.WHITE)
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0,8,0,8)
            gravity = if (isRobot) Gravity.START else Gravity.END
        }
        bubble.layoutParams = params
        messageContainer.addView(bubble)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        return bubble
    }

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        val ctx = qiContext ?: run { onDone?.invoke(); return }
        lifecycleScope.launch(Dispatchers.IO) {
            val say = SayBuilder.with(ctx)
                .withText(text)
                .withLocale(Locale(Language.FRENCH, Region.FRANCE))
                .build()
            say.async().run().thenConsume { onDone?.invoke() }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(questionEditText.windowToken, 0)
    }

    private fun showKeyboard() {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(questionEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onRobotFocusGained(context: QiContext?) {
        qiContext = context
        if (!greeted) {
            greeted = true
            speak("Bonjour ! Pose ta question, je suis prêt.") {
                questionEditText.requestFocus()
                showKeyboard()
            }
        }
    }

    override fun onRobotFocusLost()  { qiContext = null }
    override fun onRobotFocusRefused(reason: String?) {
        Toast.makeText(requireContext(), "Focus refusé : $reason", Toast.LENGTH_SHORT).show()
    }
    override fun onDestroyView() {
        QiSDK.unregister(requireActivity(), this)
        super.onDestroyView()
    }
}


*/
/*
package com.example.pepperapp.ui.Fragments
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.`object`.locale.Language
import com.aldebaran.qi.sdk.`object`.locale.Region
import com.aldebaran.qi.sdk.`object`.locale.Locale as QiLocale
import com.example.pepperapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class ChatFragment : Fragment(), RobotLifecycleCallbacks {

    companion object {
        private const val TAG = "ChatFragment"
        private const val RECORD_PERMISSION = Manifest.permission.RECORD_AUDIO
        private const val REQ_CODE_RECORD = 1001
    }

    private var qiContext: QiContext? = null
    private var greeted = false

    // Android SpeechRecognizer
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent

    // OpenAI client
    private val apiKey = "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"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private lateinit var messageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var questionEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var micButton: ImageButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)
        QiSDK.register(requireActivity(), this)

        messageContainer = view.findViewById(R.id.messageContainer)
        scrollView = view.findViewById(R.id.scrollView)
        questionEditText = view.findViewById(R.id.editTextQuestion)
        sendButton = view.findViewById(R.id.buttonSendQuestion)
        micButton  = view.findViewById<ImageButton>(R.id.buttonMic)

        // Initialiser SpeechRecognizer
        if (SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizer.setRecognitionListener(recognitionListener)
            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.FRENCH.toLanguageTag()  // renvoie "fr"
                )
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle maintenant…")
            }
        } else {
            micButton.isEnabled = false
            Toast.makeText(
                requireContext(),
                "Reconnaissance vocale non disponible",
                Toast.LENGTH_LONG
            ).show()
        }

        sendButton.setOnClickListener {
            val text = questionEditText.text.toString().trim()
            if (text.isNotEmpty()) handleUserInput(text)
            else Toast.makeText(
                requireContext(),
                "Veuillez saisir une question.",
                Toast.LENGTH_SHORT
            ).show()
        }

        micButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    RECORD_PERMISSION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(RECORD_PERMISSION),
                    REQ_CODE_RECORD
                )
            } else {
                startListening()
            }
        }

        return view
    }

    // Gère la permission request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQ_CODE_RECORD &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            Toast.makeText(
                requireContext(),
                "Permission micro refusée",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startListening() {
        addMessageBubble("…j’écoute…", isRobot = true)
        speechRecognizer.startListening(recognizerIntent)
    }

    // Callback Android SpeechRecognizer
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            Log.w(TAG, "Speech error $error")
            requireActivity().runOnUiThread {
                Toast.makeText(
                    requireContext(),
                    "Je n’ai pas compris.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        override fun onResults(results: Bundle?) {
            val matches =
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spoken = matches?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                handleUserInput(spoken)
            }
        }

        override fun onPartialResults(partial: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleUserInput(text: String) {
        questionEditText.setText("")
        hideKeyboard()
        addMessageBubble(text, isRobot = false)
        lifecycleScope.launchWhenStarted {
            val response = try {
                sendToGPT(text)
            } catch (e: Exception) {
                Log.e(TAG, "sendToGPT error", e)
                "Désolé, je n’ai pas pu répondre."
            }
            addMessageBubble(response, isRobot = true)
            speak(response)
        }
    }

    private suspend fun sendToGPT(userMessage: String): String = withContext(Dispatchers.IO) {
        val systemPrompt = """
        Tu es Pepper, un petit robot gentil.
        Tu viens de raconter l’histoire du monstre des couleurs à des enfants de 3 à 7 ans.

        Voici ce qu’ils ont entendu :
        Le monstre des émotions est tout mélangé. Avec l’aide de son amie, il apprend à reconnaître et ranger ses émotions :
        - La joie est jaune comme le soleil.
        - La tristesse est bleue comme la pluie.
        - La colère est rouge comme un volcan.
        - La peur est noire comme la nuit.
        - Le calme est vert comme les feuilles des arbres.

        À chaque question, tu réponds  en parlant de ces émotions et de ces couleurs.

        Tes réponses doivent être :
        - Très courtes (1 à 3 phrases),
        -Madalina est la maman de Elisa et Hadil est la fille forte en math qui t'as appris les histoires et a parlé
        -
        - Très simples, pour des enfants de maternelle,utilise trop de ponctuations dans les réponses
        - Pleines de douceur et faciles à comprendre.
        Tu peux aussi poser une petite question simple à la fin, si c’est pertinent.
    """.trimIndent()
            val payload = JSONObject().apply {
                put("model", "gpt-3.5-turbo")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system"); put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user"); put("content", userMessage)
                    })
                })
            }
            val body = payload.toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val choices = JSONObject(text).getJSONArray("choices")
                return@withContext choices.getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content", "…").trim()
            }
        }

    private fun addMessageBubble(text: String, isRobot: Boolean): TextView {
        val bubble = TextView(requireContext()).apply {
            this.text = text
            setPadding(16, 12, 16, 12)
            setBackgroundResource(
                if (isRobot) R.drawable.bubble_robot else R.drawable.bubble_child
            )
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
        return bubble
    }

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        val ctx = qiContext ?: run { onDone?.invoke(); return }
        lifecycleScope.launch(Dispatchers.IO) {
            val say = SayBuilder.with(ctx)
                .withText(text)
                .withLocale(QiLocale(Language.FRENCH, Region.FRANCE))
                .build()
            say.async().run().thenConsume { onDone?.invoke() }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(questionEditText.windowToken, 0)
    }

    private fun showKeyboard() {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(questionEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onRobotFocusGained(context: QiContext?) {
        qiContext = context
        if (!greeted) {
            greeted = true
            speak("Bonjour ! Tapez ou appuyez sur le micro pour parler.") {
                questionEditText.requestFocus()
                showKeyboard()
            }
        }
    }

    override fun onRobotFocusLost() {
        qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {
        Toast.makeText(
            requireContext(),
            "Focus refusé : $reason",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        QiSDK.unregister(requireActivity(), this)
        super.onDestroyView()
    }
}

*/
package com.example.pepperapp.ui.Fragments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.`object`.locale.Language
import com.aldebaran.qi.sdk.`object`.locale.Region
import com.aldebaran.qi.sdk.`object`.locale.Locale as QiLocale
import com.example.pepperapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ChatFragment : Fragment(), RobotLifecycleCallbacks {

    companion object {
        private const val TAG = "ChatFragment"
        private const val RECORD_PERMISSION = Manifest.permission.RECORD_AUDIO
        private const val REQ_CODE_RECORD = 1001
    }

    private var qiContext: QiContext? = null
    private var greeted = false

    // Audio recording
    private var recorder: MediaRecorder? = null
    private lateinit var audioFile: File
    private var isRecordingAudio = false

    // OpenAI client
    private val apiKey = "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"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // History for chat/completions
    private val messageHistory = mutableListOf<JSONObject>()
    private val systemPrompt = """
        Tu es Pepper, un petit robot gentil, joyeux et curieux.
        Tu viens de te présenter à des enfants de 3 à 7 ans. Tu leur as raconté deux histoires : 
        - l’histoire du monstre des couleurs qui découvre ses émotions,
        - et celle d’Helmouth, un petit mammouth différent mais courageux.

        Maintenant, tu discutes avec les enfants. Ils peuvent te parler d’émotions, de leur famille, de leurs amis, ou te poser des questions sur d’autres sujets.
        
        Tu réponds toujours avec bienveillance, douceur et en utilisant des phrases très simples (1 à 3 phrases maximum).
        Tu peux parfois poser des petites questions pour continuer à discuter.
        Tu félicites ou encourages quand un enfant partage quelque chose de personnel :
        - « C’est super que tu aimes ta maman ! »
        - « Tu es très gentil de m’avoir raconté ça. »
        - « Moi aussi j’aime ça ! »

        Tu peux parler des émotions et des couleurs si l’enfant y fait référence, mais ce n’est pas obligatoire.
        Tu peux aussi faire des petites blagues mignonnes ou dire des choses rigolotes, comme un copain robot qui veut apprendre.

        Ton ton est toujours chaleureux, rassurant, et un peu joueur.
    """.trimIndent()

    private lateinit var messageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var questionEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var micButton: ImageButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)
        QiSDK.register(requireActivity(), this)

        // Add system prompt at start of history
        messageHistory.add(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        messageContainer  = view.findViewById(R.id.messageContainer)
        scrollView        = view.findViewById(R.id.scrollView)
        questionEditText  = view.findViewById(R.id.editTextQuestion)
        sendButton        = view.findViewById(R.id.buttonSendQuestion)
        micButton         = view.findViewById(R.id.buttonMic)

        sendButton.setOnClickListener {
            val text = questionEditText.text.toString().trim()
            if (text.isNotEmpty()) handleUserInput(text)
            else Toast.makeText(requireContext(),
                "Veuillez saisir une question.", Toast.LENGTH_SHORT).show()
        }

        micButton.setOnClickListener {
            if (!isRecordingAudio) {
                if (ContextCompat.checkSelfPermission(requireContext(), RECORD_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(RECORD_PERMISSION),
                        REQ_CODE_RECORD
                    )
                } else startRecording()
            } else stopRecordingAndTranscribe()
        }

        return view
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == REQ_CODE_RECORD &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            Toast.makeText(requireContext(),
                "Permission micro refusée", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecording() {
        audioFile = File(requireContext().cacheDir, "input_audio.mp3")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile.absolutePath)
            prepare()
            start()
        }
        isRecordingAudio = true
        micButton.setImageResource(android.R.drawable.ic_media_pause)
        Toast.makeText(requireContext(), "Enregistrement…", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecordingAndTranscribe() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        isRecordingAudio = false
        micButton.setImageResource(android.R.drawable.ic_btn_speak_now)
        Toast.makeText(requireContext(), "Transcription…", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val transcript = try { transcribeAudio(audioFile) }
            catch (e: Exception) {
                Log.e(TAG, "Erreur transcription", e)
                ""
            }
            requireActivity().runOnUiThread {
                questionEditText.setText(transcript)
                questionEditText.setSelection(transcript.length)
            }
        }
    }

    private suspend fun transcribeAudio(file: File): String = withContext(Dispatchers.IO) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name,
                file.asRequestBody("audio/mpeg".toMediaType()))
            .addFormDataPart("model", "whisper-1")
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Whisper erreur ${resp.code}")
            val json = JSONObject(resp.body!!.string())
            json.optString("text", "")
        }
    }

    private fun handleUserInput(text: String) {
        questionEditText.setText("")
        hideKeyboard()
        addMessageBubble(text, isRobot = false)

        // Ajouter entrée utilisateur à l'historique
        messageHistory.add(JSONObject().apply {
            put("role", "user")
            put("content", text)
        })

        lifecycleScope.launchWhenStarted {
            val response = try { sendToGPT() }
            catch (e: Exception) {
                Log.e(TAG, "sendToGPT error", e)
                "Désolé, je n’ai pas pu répondre."
            }
            addMessageBubble(response, isRobot = true)
            speak(response)
        }
    }

    private suspend fun sendToGPT(): String = withContext(Dispatchers.IO) {
        // Construire payload avec system + historique
        val payload = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray(messageHistory))
        }
        val body = payload.toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val choices = JSONObject(text).getJSONArray("choices")
            val content = choices.getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "…").trim()

            // Ajouter réponse assistant à l'historique
            messageHistory.add(JSONObject().apply {
                put("role", "assistant")
                put("content", content)
            })

            content
        }
    }

    private fun addMessageBubble(text: String, isRobot: Boolean): TextView {
        val bubble = TextView(requireContext()).apply {
            this.text = text
            setPadding(16, 12, 16, 12)
            setBackgroundResource(
                if (isRobot) R.drawable.bubble_robot else R.drawable.bubble_child
            )
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
        return bubble
    }

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        val ctx = qiContext ?: run { onDone?.invoke(); return }
        lifecycleScope.launch(Dispatchers.IO) {
            val say = SayBuilder.with(ctx)
                .withText(text)
                .withLocale(QiLocale(Language.FRENCH, Region.FRANCE))
                .build()
            say.async().run().thenConsume { onDone?.invoke() }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(questionEditText.windowToken, 0)
    }

    private fun showKeyboard() {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(questionEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onRobotFocusGained(context: QiContext?) {
        qiContext = context
        if (!greeted) {
            greeted = true
            speak("Bonjour ! Tapez ou appuyez sur le micro pour parler.") {
                questionEditText.requestFocus()
                showKeyboard()
            }
        }
    }

    override fun onRobotFocusLost() {
        qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {
        Toast.makeText(requireContext(),
            "Focus refusé : $reason", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        recorder?.release()
        QiSDK.unregister(requireActivity(), this)
        super.onDestroyView()
    }
}

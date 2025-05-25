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


    private var recorder: MediaRecorder? = null
    private lateinit var audioFile: File
    private var isRecordingAudio = false


    private val apiKey = "YOUR_API_KEY"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()


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


        messageHistory.add(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        messageContainer  = view.findViewById(R.id.messageContainer)
        scrollView        = view.findViewById(R.id.scrollView)
        questionEditText  = view.findViewById(R.id.editTextQuestion)
        sendButton        = view.findViewById(R.id.buttonSendQuestion)
        micButton         = view.findViewById(R.id.buttonMic)
        questionEditText.post {
            questionEditText.requestFocus()
            showKeyboard()
        }


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

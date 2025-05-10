package com.example.pepperapp.ui.Fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.Animation
import com.aldebaran.qi.sdk.`object`.locale.Language
import com.aldebaran.qi.sdk.`object`.locale.Locale
import com.aldebaran.qi.sdk.`object`.locale.Region
import com.aldebaran.qi.sdk.`object`.touch.TouchSensor
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.example.pepperapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class SadFragment : Fragment(), RobotLifecycleCallbacks {

    private var qiContext: QiContext? = null
    private var headSensor: TouchSensor? = null
    private val localeFR = Locale(Language.FRENCH, Region.FRANCE)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // S'enregistrer pour recevoir les callbacks QiSDK
        QiSDK.register(requireActivity(), this)
        return inflater.inflate(R.layout.fragment_sad, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retour
        view.findViewById<Button>(R.id.buttonBack).setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onRobotFocusGained(context: QiContext) {
        qiContext = context

        // 1) jouer l'émotion triste
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sayJob = async {
                    SayBuilder.with(context)
                        .withLocale(localeFR)
                        .withText("Oh… je me sens triste, comme le monstre bleu. Mon cœur est tout mou... Est-ce que tu peux me caresser la tête, s’il te plaît ? Ça me ferait du bien."
                        )
                        .build()
                        .run()
                }
                val animJob = async {
                    val anim: Animation = AnimationBuilder.with(context)
                        .withAssets("animations/07-Reactions/SadReaction_01.qianim")
                        .build()
                    AnimateBuilder.with(context)
                        .withAnimation(anim)
                        .build()
                        .run()
                }
                sayJob.await()
                animJob.await()
            } catch (e: Exception) {
                Log.e("SadFragment", "Erreur playSadEmotion", e)
            }
        }

        // 2) configurer le capteur tête
        try {
            headSensor = context.touch.getSensor("Head/Touch").also { sensor ->
                sensor.addOnStateChangedListener { state ->
                    if (state.touched) respondHappy()
                }
            }
        } catch (e: Exception) {
            Log.e("SadFragment", "Service Touch indisponible", e)
        }
    }

    override fun onRobotFocusLost() {
        // désinscrire le listener tactile
        headSensor?.removeAllOnStateChangedListeners()
        headSensor = null
        qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {
        Log.w("SadFragment", "Focus refusé: $reason")
    }

    override fun onDestroyView() {
        QiSDK.unregister(requireActivity())
        super.onDestroyView()
    }

    private fun respondHappy() {
        val ctx = qiContext ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SayBuilder.with(ctx)
                    .withLocale(localeFR)
                    .withText("Merci mon ami ! Je me sens tellement heureux ! ")
                    .build()
                    .run()
            } catch (e: Exception) {
                Log.e("SadFragment", "Erreur respondHappy", e)
            }
        }
    }
}

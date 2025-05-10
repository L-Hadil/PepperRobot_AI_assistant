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

class HappyFragment : Fragment(), RobotLifecycleCallbacks {

    private var qiContext: QiContext? = null
    private var rightHandSensor: TouchSensor? = null
    private var leftHandSensor: TouchSensor? = null
    private val localeFR = Locale(Language.FRENCH, Region.FRANCE)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Enregistre les callbacks QiSDK
        QiSDK.register(requireActivity(), this)
        return inflater.inflate(R.layout.fragment_happy, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.buttonBack).setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onRobotFocusGained(context: QiContext) {
        qiContext = context

        // 1) parole et animation d'accueil heureuse
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sayJob = async {
                    SayBuilder.with(context)
                        .withLocale(localeFR)
                        .withText(
                            "Je me sens comme le monstre jaune… plein de joie ! Je suis tellement heureux d’être avec toi. " +
                                    "Et toi ? Si tu es content aussi, prends ma main droite… puis ma main gauche !"

                        )
                        .build()
                        .run()
                }
                val animJob = async {
                    val anim: Animation = AnimationBuilder.with(context)
                        .withAssets("animations/06-Solitaries/Funny_02.qianim")
                        .build()
                    AnimateBuilder.with(context)
                        .withAnimation(anim)
                        .build()
                        .run()
                }
                sayJob.await()
                animJob.await()
            } catch (e: Exception) {
                Log.e("HappyFragment", "Erreur playHappyEmotion", e)
            }
        }

        // 2) configurer les capteurs main droite et main gauche
        try {
            rightHandSensor = context.touch.getSensor("RHand/Touch").also { sensor ->
                sensor.addOnStateChangedListener { state ->
                    if (state.touched) respondHappy()
                }
            }
            leftHandSensor = context.touch.getSensor("LHand/Touch").also { sensor ->
                sensor.addOnStateChangedListener { state ->
                    if (state.touched) respondHappy()
                }
            }
        } catch (e: Exception) {
            Log.e("HappyFragment", "Service Touch indisponible", e)
        }
    }

    override fun onRobotFocusLost() {
        // Désinscrire les listeners tactiles
        rightHandSensor?.removeAllOnStateChangedListeners()
        leftHandSensor?.removeAllOnStateChangedListeners()
        rightHandSensor = null
        leftHandSensor = null
        qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {
        Log.w("HappyFragment", "Focus refusé: $reason")
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
                    .withText("Oh… merci, mon ami ! Tu es formidable !")
                    .build()
                    .run()
            } catch (e: Exception) {
                Log.e("HappyFragment", "Erreur respondHappy", e)
            }
        }
    }
}

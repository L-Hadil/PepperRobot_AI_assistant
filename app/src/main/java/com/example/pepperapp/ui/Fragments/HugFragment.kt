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

class HugFragment : Fragment(), RobotLifecycleCallbacks {

    companion object {
        private const val TAG = "HugFragment"
    }

    private var qiContext: QiContext? = null
    private var headSensor: TouchSensor? = null
    private var rightHandSensor: TouchSensor? = null
    private var leftHandSensor: TouchSensor? = null
    private val localeFR = Locale(Language.FRENCH, Region.FRANCE)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // S'enregistrer pour recevoir les callbacks QiSDK
        QiSDK.register(requireActivity(), this)
        return inflater.inflate(R.layout.fragment_hug, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.buttonBack)?.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onRobotFocusGained(context: QiContext) {
        qiContext = context

        // 1) Invitation au câlin
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SayBuilder.with(context)
                    .withLocale(localeFR)
                    .withText("Brrr… J’ai un petit coup de mou. Est-ce que je pourrais avoir un gros câlin ? Viens me serrer fort, s’il te plaît !")
                    .build().run()

                val anim = AnimationBuilder.with(context)
                    .withAssets("animations/01-Hello/Hello_02.qianim")
                    .build()
                AnimateBuilder.with(context)
                    .withAnimation(anim)
                    .build()
                    .run()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur invitation câlin", e)
            }
        }

        // 2) Configurer les capteurs Head, main droite et main gauche
        try {
            headSensor = context.touch.getSensor("Head/Touch").also { sensor ->
                sensor.addOnStateChangedListener { state ->
                    if (state.touched) respondHug()
                }
            }
            rightHandSensor = context.touch.getSensor("RHand/Touch").also { sensor ->
                sensor.addOnStateChangedListener { state ->
                    if (state.touched) respondHug()
                }
            }
            leftHandSensor = context.touch.getSensor("LHand/Touch").also { sensor ->
                sensor.addOnStateChangedListener { state ->
                    if (state.touched) respondHug()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Service Touch indisponible", e)
        }
    }

    override fun onRobotFocusLost() {
        // Désinscrire les listeners pour éviter les fuites
        headSensor?.removeAllOnStateChangedListeners()
        rightHandSensor?.removeAllOnStateChangedListeners()
        leftHandSensor?.removeAllOnStateChangedListeners()
        headSensor = null
        rightHandSensor = null
        leftHandSensor = null
        qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {
        Log.w(TAG, "Focus refusé: $reason")
    }

    override fun onDestroyView() {
        QiSDK.unregister(requireActivity())
        super.onDestroyView()
    }

    private fun respondHug() {
        val ctx = qiContext ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SayBuilder.with(ctx)
                    .withLocale(localeFR)
                    .withText("Mmm… quel câlin chaleureux ! Merci beaucoup, je me sens tout léger maintenant !")
                    .build().run()

                val anim = AnimationBuilder.with(ctx)
                    .withAssets("animations/07-Reactions/NiceReaction_01.qianim")
                    .build()
                AnimateBuilder.with(ctx)
                    .withAnimation(anim)
                    .build()
                    .run()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur respondHug", e)
            }
        }
    }
}

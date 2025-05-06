package com.example.pepperapp.ui.Fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.locale.Language
import com.aldebaran.qi.sdk.`object`.locale.Locale
import com.aldebaran.qi.sdk.`object`.locale.Region
import com.aldebaran.qi.sdk.`object`.actuation.Animation
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import com.example.pepperapp.R

class GoodbyeFragment : Fragment(), RobotLifecycleCallbacks {

    companion object {
        private const val TAG = "GoodbyeFragment"
    }

    private var qiContext: QiContext? = null
    private val localeFR = Locale(Language.FRENCH, Region.FRANCE)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        QiSDK.register(requireActivity(), this)
        return inflater.inflate(R.layout.fragment_goodbye, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Pas de bouton retour : on quitte l’activité ou on navigue ailleurs après l’au revoir
    }

    override fun onRobotFocusGained(context: QiContext) {
        qiContext = context

        // 1) Phrase d’au revoir mignonne + animation de la main
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1.a) Dire au revoir
                val sayJob = async {
                    SayBuilder.with(context)
                        .withLocale(localeFR)
                        .withText(
                            "Au revoir mes petits amis ! " +
                                    "Merci pour ce super moment à l’école ! " +
                                    "Je reviendrai bientôt pour de nouvelles aventures ! " +
                                    "Bisous, bisous !"
                        )
                        .build()
                        .run()
                }

                // 1.b) Animation de la main qui fait « bye »
                val waveAnim: Animation = AnimationBuilder.with(context)
                    .withAssets("animations/01-Hello/Hello_09.qianim")
                    .build()
                val animJob = async {
                    AnimateBuilder.with(context)
                        .withAnimation(waveAnim)
                        .build()
                        .run()
                }

                // 1.c) Synchronisation
                sayJob.await()
                animJob.await()

            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de l’au revoir", e)
            }
        }
    }

    override fun onRobotFocusLost() {
        qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {
        Log.w(TAG, "Focus refusé : $reason")
    }

    override fun onDestroyView() {
        QiSDK.unregister(requireActivity())
        super.onDestroyView()
    }
}

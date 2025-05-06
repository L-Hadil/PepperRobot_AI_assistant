package com.example.pepperapp.ui.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.Animation
import com.aldebaran.qi.sdk.`object`.locale.Language
import com.aldebaran.qi.sdk.`object`.locale.Locale
import com.aldebaran.qi.sdk.`object`.locale.Region
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.example.pepperapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NuageFragment : Fragment(), RobotLifecycleCallbacks {

    private lateinit var tvSection: TextView
    private var qiContext: QiContext? = null
    private val localeFR = Locale(Language.FRENCH, Region.FRANCE)

    // Texte complet de l'histoire
    private val storyLines = listOf(
        "Il était une fois un petit nuage tout bleu.",
        "Il s'appelait Nimbus.",
        "Chaque matin, Nimbus se promenait dans le ciel.",
        "Il dansait avec le soleil, il sifflotait avec le vent.",
        "Il était très joyeux !",
        "Mais un jour, Nimbus regarda en bas.",
        "Le sol était tout sec...",
        "Les fleurs avaient la tête toute basse.",
        "Les oiseaux ne chantaient plus.",
        "Nimbus se sentit triste. Il voulait aider !",
        "Il réfléchit... réfléchit…",
        "Puis il dit : « Je sais ce que je vais faire ! »",
        "Il gonfla ses joues…",
        "et laissa tomber de jolies gouttes de pluie.",
        "Ploc, ploc, ploc… la pluie tombait doucement.",
        "Les fleurs se redressèrent.",
        "Les oiseaux chantèrent très fort : cui-cui, cui-cui !",
        "Tout le monde était heureux !",
        "Nimbus aussi, car il avait partagé.",
        "Depuis ce jour, Nimbus continue de voyager…",
        "…et d’arroser les endroits qui ont besoin de lui.",
        "Parce que partager, c’est rendre les autres heureux !"
    )

    // Animation associée à chaque ligne (null = pas d'animation)
    private val animationsByLine = listOf(
        "animations/07-Reactions/Surprise_01.qianim",   // Il était une fois...
        null,                                           // Il s'appelait Nimbus.
        "animations/07-Reactions/Success_01.qianim",    // Chaque matin...
        "animations/02-Body_Parts/Show_Body_03.qianim", // Il dansait...
        "animations/07-Reactions/Success_01.qianim",    // Il était très joyeux !
        "animations/06-Solitaries/Looking_around_01.qianim", // Mais un jour...
        null,                                           // Le sol était sec...
        "animations/07-Reactions/SadReaction_01.qianim",// Les fleurs avaient...
        "animations/07-Reactions/SadReaction_01.qianim",// Les oiseaux ne chantaient plus.
        "animations/07-Reactions/Surprise_01.qianim",   // Nimbus se sentit triste.
        null,                                           // Il réfléchit...
        "animations/07-Reactions/NiceReaction_01.qianim",// Puis il dit...
        null,                                           // Il gonfla ses joues…
        "animations/02-Body_Parts/Show_Body_01.qianim", // et laissa tomber...
        null,                                           // Ploc, ploc, ploc…
        "animations/07-Reactions/Success_01.qianim",    // Les fleurs se redressèrent.
        "animations/02-Body_Parts/Show_Body_02.qianim", // Les oiseaux chantèrent.
        "animations/07-Reactions/Success_01.qianim",    // Tout le monde était heureux !
        "animations/07-Reactions/NiceReaction_01.qianim",// Nimbus aussi...
        null,                                           // Depuis ce jour...
        null,                                           // …et d’arroser...
        "animations/07-Reactions/NiceReaction_02.qianim"// Parce que partager...
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Enregistrer pour recevoir les callbacks QiSDK
        QiSDK.register(requireActivity(), this)
        return inflater.inflate(R.layout.fragment_listen_story, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvSection = view.findViewById(R.id.tvStorySection)
    }

    override fun onRobotFocusGained(context: QiContext) {
        qiContext = context

        lifecycleScope.launch(Dispatchers.IO) {
            storyLines.forEachIndexed { index, line ->
                // Met à jour le texte sur le thread UI
                withContext(Dispatchers.Main) {
                    tvSection.text = line
                }

                // Lecture de la phrase
                val sayJob = async {
                    SayBuilder.with(context)
                        .withLocale(localeFR)
                        .withText(line)
                        .build()
                        .run()
                }

                // Animation éventuelle
                val animJob = async {
                    animationsByLine.getOrNull(index)?.let { path ->
                        try {
                            val anim: Animation = AnimationBuilder.with(context)
                                .withAssets(path)
                                .build()
                            AnimateBuilder.with(context)
                                .withAnimation(anim)
                                .build()
                                .run()
                        } catch (_: Exception) { /* ignore */ }
                    }
                }

                sayJob.await()
                animJob.await()
            }
        }
    }

    override fun onRobotFocusLost() {
        qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {
        // Rien à faire
    }

    override fun onDestroyView() {
        // Se désinscrire des callbacks QiSDK
        QiSDK.unregister(requireActivity())
        super.onDestroyView()
    }
}

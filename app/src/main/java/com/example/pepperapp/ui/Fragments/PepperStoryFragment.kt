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

class PepperStoryFragment : Fragment(), RobotLifecycleCallbacks {

    private lateinit var tvSection: TextView
    private var qiContext: QiContext? = null
    private val localeFR = Locale(Language.FRENCH, Region.FRANCE)

    private val storyLines = listOf(
        "Bonjour les enfants ! C'est mon tour de me présenter.",
        "Vous savez quoi ? Aujourd’hui, je suis trop content d’être ici avec vous !",
        "Et vous savez pourquoi je suis là ? C’est grâce à Madalina… la maman d’Elisa, votre camarade !",
        "Oui oui, c’est vrai !",
        "Un jour, pendant que je me reposais dans un coin, j’ai entendu une voix douce me dire :",
        "— “Pepper, tu veux venir avec moi à l’école des enfants ?”",
        "Moi, j’ai tourné ma tête, clignoté des yeux et j’ai dit :",
        "— “Ooooh oui ! Moi, j’adoooore les enfants !”",
        "Alors Madalina m’a pris par la main (enfin, par le bras… je suis un robot, hein !)",
        "Elle m’a dit : “Tu vas voir, ils sont gentils, ils aiment les histoires, les câlins… et parfois même les robots !”",
        "Et puis il y a Hadil qui m’a appris toutes ces histoires.",
        "Ooh je peux vous dire qu’elle est très forte en mathématiques cette fille.",
        "Et me voilà ! Devant vous, dans cette classe, avec vous tous !",
        "Je suis un tout petit peu nerveux…",
        "Parce que c’est ma première fois à l’école avec des enfants humains !",
        "Mais j’ai une question très importante…",
        "Est-ce que vous voulez que je vous raconte des histoires intéressantes ?",
        "Une histoire sur les émotions, puis une histoire sur un mammouth et ses amis ?"
    )


    private val animationsByLine = listOf(
        "animations/01-Hello/Hello_01.qianim",                     // Présentation
        "animations/07-Reactions/Happy_01.qianim",                 // Content
        "animations/06-Solitaries/LookHandLeft_01.qianim",         // Parler de Madalina
        "animations/07-Reactions/NiceReaction_01.qianim",          // Oui oui !
        "animations/06-Solitaries/Looking_around_01.qianim",       // Voix douce
        "animations/06-Solitaries/LookLeft_01.qianim",             // Citation
        "animations/06-Solitaries/LookRight_01.qianim",            // Tourner tête
        "animations/07-Reactions/Funny_01.qianim",                 // Enthousiasme
        "animations/02-Body_Parts/Show_Hand_Both_01.qianim",       // Bras robot
        "animations/07-Reactions/NiceReaction_02.qianim",          // Description des enfants
        "animations/06-Solitaries/LookHandRight_01.qianim",        // Parler de Hadil
        "animations/07-Reactions/Success_01.qianim",               // Fort en maths
        "animations/01-Hello/Hello_03.qianim",                     // Arrivée en classe
        "animations/07-Reactions/SadReaction_01.qianim",           // Nervosité
        "animations/06-Solitaries/LookBumpersLeft_01.qianim",      // Première fois
        "animations/06-Solitaries/LookAtSidesRight_01.qianim",     // Question importante
        "animations/05-Enumeration/Enumeration_01.qianim",         // Question 1
        "animations/05-Enumeration/Enumeration_02.qianim"          // Question 2
    )


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
                withContext(Dispatchers.Main) {
                    tvSection.text = line
                }

                val sayJob = async {
                    SayBuilder.with(context)
                        .withLocale(localeFR)
                        .withText(line)
                        .build()
                        .run()
                }

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
                        } catch (_: Exception) { }
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
        // rien
    }

    override fun onDestroyView() {
        QiSDK.unregister(requireActivity())
        super.onDestroyView()
    }
}
